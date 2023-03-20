/*
 * Copyright 2023 Joel Kanyi.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.kanyideveloper.mealplanner.data.repository

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.kanyideveloper.core.data.MealTimePreferences
import com.kanyideveloper.core.model.Favorite
import com.kanyideveloper.core.model.Meal
import com.kanyideveloper.core.model.MealPlanPreference
import com.kanyideveloper.core.notifications.NotificationReceiver
import com.kanyideveloper.core.util.Resource
import com.kanyideveloper.core.util.safeApiCall
import com.kanyideveloper.core_database.dao.FavoritesDao
import com.kanyideveloper.core_database.dao.MealDao
import com.kanyideveloper.core_database.dao.MealPlanDao
import com.kanyideveloper.core_database.model.FavoriteEntity
import com.kanyideveloper.core_database.model.MealEntity
import com.kanyideveloper.core_database.model.MealPlanEntity
import com.kanyideveloper.core_network.MealDbApi
import com.kanyideveloper.mealplanner.data.mapper.toEntity
import com.kanyideveloper.mealplanner.data.mapper.toGeneralMeal
import com.kanyideveloper.mealplanner.data.mapper.toMeal
import com.kanyideveloper.mealplanner.data.mapper.toMealPlan
import com.kanyideveloper.mealplanner.data.mapper.toOnlineMeal
import com.kanyideveloper.mealplanner.domain.repository.MealPlannerRepository
import com.kanyideveloper.mealplanner.model.MealPlan
import java.util.Calendar
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

class MealPlannerRepositoryImpl(
    private val mealTimePreferences: MealTimePreferences,
    private val mealPlanDao: MealPlanDao,
    private val favoritesDao: FavoritesDao,
    private val mealDao: MealDao,
    private val mealDbApi: MealDbApi,
    private val context: Context,
    private val databaseReference: DatabaseReference,
    private val firebaseAuth: FirebaseAuth
) : MealPlannerRepository {

    override suspend fun hasMealPlanPref(isSubscribed: Boolean): Flow<MealPlanPreference?> {
        return mealTimePreferences.mealPlanPreferences(isSubscribed = isSubscribed)
    }

    override suspend fun saveMealToPlan(mealPlan: MealPlan, isSubscribed: Boolean) {
        Timber.e("Trying to insert meal to plan: $mealPlan")
        if (isSubscribed) {
            saveMealToPlanRemoteDataSource(mealPlan = mealPlan)
        } else {
            mealPlanDao.insertMealPlan(mealPlanEntity = mealPlan.toEntity())
        }
    }

    private suspend fun saveMealToPlanRemoteDataSource(mealPlan: MealPlan): Resource<Boolean> {
        return try {
            databaseReference
                .child("mealPlans")
                .child(firebaseAuth.currentUser?.uid.toString())
                .child(mealPlan.id.toString())
                .setValue(mealPlan).await()
            mealPlanDao.insertMealPlan(mealPlanEntity = mealPlan.toEntity())

            Resource.Success(data = true)
        } catch (e: Exception) {
            return Resource.Error(e.localizedMessage ?: "Unknown error occurred")
        }
    }

    override suspend fun searchMeal(
        source: String,
        searchBy: String,
        searchString: String,
        isSubscribed: Boolean
    ): Resource<Flow<List<Meal>>> {
        return when (source) {
            "Online" -> {
                searchFromInternet(
                    searchString = searchString,
                    searchBy = searchBy
                )
            }
            "My Meals" -> {
                getMyMeals(isSubscribed = isSubscribed)
            }
            "My Favorites" -> {
                getFavorites(isSubscribed = isSubscribed)
            }
            else -> {
                Resource.Error("Invalid source: $source", null)
            }
        }
    }

    override suspend fun removeMealFromPlan(id: String, isSubscribed: Boolean) {
        if (isSubscribed) {
            deleteAMealPlanFromRemoteDatasource(id = id)
        } else {
            mealPlanDao.deleteAMealFromPlan(id = id)
        }
    }

    private suspend fun deleteAMealPlanFromRemoteDatasource(id: String): Resource<Boolean> {
        return try {
            databaseReference
                .child("mealPlans")
                .child(firebaseAuth.currentUser?.uid.toString())
                .child(id)
                .removeValue()
                .await()

            mealPlanDao.deleteAMealFromPlan(id = id)

            Resource.Success(data = true)
        } catch (e: Exception) {
            Timber.e("Error deleting isFavorite: $e")
            return Resource.Error(e.localizedMessage ?: "Unknown error occurred")
        }
    }

    override suspend fun getAllIngredients(): Resource<List<String>> {
        return safeApiCall(Dispatchers.IO) {
            val response = mealDbApi.getAllIngredients()
            Timber.d("Ingredients response: $response")
            response.meals.map { it.strIngredient }
        }
    }

    override suspend fun saveMealPlannerPreferences(
        allergies: List<String>,
        numberOfPeople: String,
        dishTypes: List<String>,
        isSubscribed: Boolean
    ) {
        // Set Alarms
        setAlarm(isSubscribed = isSubscribed)

        if (isSubscribed) {
            saveMealPlannerPreferencesToRemoteDataSource(
                allergies = allergies,
                numberOfPeople = numberOfPeople,
                dishTypes = dishTypes
            )
        } else {
            mealTimePreferences.saveMealPlanPreferences(
                allergies = allergies,
                numberOfPeople = numberOfPeople,
                dishTypes = dishTypes
            )
        }
    }

    private suspend fun saveMealPlannerPreferencesToRemoteDataSource(
        allergies: List<String>,
        numberOfPeople: String,
        dishTypes: List<String>
    ): Resource<Boolean> {
        return try {
            databaseReference
                .child("mealPlannerPreferences")
                .child(firebaseAuth.currentUser?.uid.toString())
                .setValue(
                    MealPlanPreference(
                        allergies = allergies,
                        numberOfPeople = numberOfPeople,
                        dishTypes = dishTypes
                    )
                ).await()

            mealTimePreferences.saveMealPlanPreferences(
                allergies = allergies,
                numberOfPeople = numberOfPeople,
                dishTypes = dishTypes
            )

            Resource.Success(data = true)
        } catch (e: Exception) {
            return Resource.Error(e.localizedMessage ?: "Unknown error occurred")
        }
    }

    override suspend fun getMealsInMyPlan(
        filterDay: String,
        isSubscribed: Boolean
    ): Resource<Flow<List<MealPlan>>> {
        return if (isSubscribed) {
            getMealsInMyPlanRemoteDataSource(filterDay = filterDay)
        } else {
            Resource.Success(
                data = mealPlanDao.getPlanMeals(filterDay = filterDay).map { meals ->
                    meals.map { it.toMealPlan() }
                }
            )
        }
    }

    private suspend fun getMealsInMyPlanRemoteDataSource(
        filterDay: String
    ): Resource<Flow<List<MealPlan>>> {
        /**
         * Do offline caching
         */
        // first read from the local database
        val mealsInMyPlan = mealPlanDao.getPlanMeals(filterDay = filterDay).map { meals ->
            meals.map { it.toMealPlan() }
        }

        return try {
            val newMealsFromMyPlan = withTimeoutOrNull(10000L) {
                // fetch from the remote database
                val mealsRemote: MutableList<MealPlan> = mutableListOf()
                val favs = databaseReference
                    .child("mealPlans")
                    .child(firebaseAuth.currentUser?.uid.toString())
                val auctionsListFromDb = favs.get().await()
                for (i in auctionsListFromDb.children) {
                    val result = i.getValue(MealPlan::class.java)
                    mealsRemote.add(result!!)
                }

                // clear the local database
                mealPlanDao.deleteAllMealsFromPlan()

                // save the remote data to the local database
                mealsRemote.forEach { onlineMeal ->
                    mealPlanDao.insertMealPlan(
                        MealPlanEntity(
                            mealTypeName = onlineMeal.mealTypeName,
                            meals = onlineMeal.meals,
                            mealDate = onlineMeal.date,
                            id = onlineMeal.id ?: UUID.randomUUID().toString()
                        )
                    )
                }

                // read from the local database
                mealPlanDao.getPlanMeals(filterDay = filterDay).map { meals ->
                    meals.map { it.toMealPlan() }
                }
            }

            if (newMealsFromMyPlan == null) {
                Resource.Error(
                    message = "Viewing offline data",
                    data = mealsInMyPlan
                )
            } else {
                Resource.Success(data = newMealsFromMyPlan)
            }
        } catch (e: Exception) {
            Resource.Error(
                e.localizedMessage ?: "Unknown error occurred",
                data = mealsInMyPlan
            )
        }
    }

    override fun getExistingMeals(mealType: String, date: String): List<Meal> {
        return emptyList()
    }

    override suspend fun deleteAMealFromPlan(id: String) {
        mealPlanDao.deleteAMealFromPlan(id = id)
    }

    override fun setAlarm(isSubscribed: Boolean) {
        CoroutineScope(Dispatchers.Default).launch {
            hasMealPlanPref(isSubscribed = isSubscribed).collectLatest { mealPlanPreference ->
                mealPlanPreference?.dishTypes?.forEach {
                    if (it == "Breakfast") {
                        setBreakfastAlarm()
                    }
                    if (it == "Lunch") {
                        setLunchAlarm()
                    }

                    if (it == "Dinner") {
                        setDinnerAlarm()
                    }

                    if (it == "Dessert") {
                        setDessertAlarm()
                    }
                }
            }
        }
    }

    private fun setLunchAlarm() {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 13)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)

        val alarmManager = context.getSystemService(ComponentActivity.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, NotificationReceiver::class.java)

        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

        // Add the desired parameters to the intent
        intent.putExtra("MESSAGE", "Lunch")
        intent.putExtra("DESCRIPTION", "Time to prepare and eat your lunch")

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            1,
            intent,
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )

        alarmManager.setRepeating(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pendingIntent
        )
    }

    private fun setBreakfastAlarm() {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 8)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)

        val alarmManager = context.getSystemService(ComponentActivity.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, NotificationReceiver::class.java)

        // Add the desired parameters to the intent
        intent.putExtra("MESSAGE", "Breakfast")
        intent.putExtra("DESCRIPTION", "Time to prepare and eat your breakfast")

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            2,
            intent,
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )

        alarmManager.setRepeating(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pendingIntent
        )
    }

    private fun setDinnerAlarm() {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 19)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)

        val alarmManager = context.getSystemService(ComponentActivity.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, NotificationReceiver::class.java)

        // Add the desired parameters to the intent
        intent.putExtra("MESSAGE", "Dinner")
        intent.putExtra("DESCRIPTION", "Time to prepare and eat your dinner")

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            3,
            intent,
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )

        alarmManager.setRepeating(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pendingIntent
        )
    }

    private fun setDessertAlarm() {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 20)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)

        val alarmManager = context.getSystemService(ComponentActivity.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, NotificationReceiver::class.java)

        // Add the desired parameters to the intent
        intent.putExtra("MESSAGE", "Dessert")
        intent.putExtra("DESCRIPTION", "Time to prepare and eat your dessert")

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            4,
            intent,
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )

        alarmManager.setRepeating(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pendingIntent
        )
    }

    private suspend fun getFavorites(isSubscribed: Boolean): Resource<Flow<List<Meal>>> {
        return if (isSubscribed) {
            getFavoritesFromRemoteDataSource()
        } else {
            Resource.Success(
                data = favoritesDao.getFavorites().map { favs ->
                    favs.map { it.toMeal() }
                }
            )
        }
    }

    private suspend fun getFavoritesFromRemoteDataSource(): Resource<Flow<List<Meal>>> {
        /**
         * Do offline caching
         */
        // first read from the local database
        val favorites = favoritesDao.getFavorites()

        return try {
            val newFavorites = withTimeoutOrNull(10000L) {
                // fetch from the remote database
                val favoritesRemote: MutableList<Favorite> = mutableListOf()
                val favs = databaseReference
                    .child("favorites")
                    .child(firebaseAuth.currentUser?.uid.toString())
                val auctionsListFromDb = favs.get().await()
                for (i in auctionsListFromDb.children) {
                    val result = i.getValue(Favorite::class.java)
                    favoritesRemote.add(result!!)
                }

                // clear the local database
                favoritesDao.deleteAllFavorites()

                // save the remote data to the local database
                favoritesRemote.forEach { onlineFavorite ->
                    favoritesDao.insertAFavorite(
                        FavoriteEntity(
                            id = onlineFavorite.id,
                            onlineMealId = onlineFavorite.onlineMealId,
                            localMealId = onlineFavorite.localMealId,
                            isOnline = onlineFavorite.online,
                            mealName = onlineFavorite.mealName,
                            mealImageUrl = onlineFavorite.mealImageUrl,
                            isFavorite = onlineFavorite.favorite
                        )
                    )
                }

                // read from the local database
                favoritesDao.getFavorites().map { favoriteEntities ->
                    favoriteEntities.map { favoriteEntity ->
                        favoriteEntity.toMeal()
                    }
                }
            }

            if (newFavorites == null) {
                Resource.Error(
                    "Viewing offline data",
                    data = favorites.map {
                        it.map { favoriteEntity ->
                            favoriteEntity.toMeal()
                        }
                    }
                )
            } else {
                Resource.Success(data = newFavorites)
            }
        } catch (e: Exception) {
            Resource.Error(
                e.localizedMessage ?: "Unknown error occurred",
                data = favorites.map {
                    it.map { favoriteEntity ->
                        favoriteEntity.toMeal()
                    }
                }
            )
        }
    }

    private suspend fun getMyMeals(isSubscribed: Boolean): Resource<Flow<List<Meal>>> {
        return if (isSubscribed) {
            getMyMealsFromRemoteDataSource()
        } else {
            val a = mealDao.getAllMeals().map { meals ->
                meals.map { it.toMeal() }
            }

            Resource.Success(a)
        }
    }

    private suspend fun getMyMealsFromRemoteDataSource(): Resource<Flow<List<Meal>>> {
        /**
         * Do offline caching
         */
        // first read from the local database
        val myMeals = mealDao.getAllMeals()

        return try {
            val newMyMeals = withTimeoutOrNull(10000L) {
                // fetch from the remote database
                val myMealsRemote: MutableList<Meal> = mutableListOf()
                val auctions = databaseReference
                    .child("mymeals")
                    .child(firebaseAuth.currentUser?.uid.toString())
                val auctionsListFromDb = auctions.get().await()
                for (i in auctionsListFromDb.children) {
                    val result = i.getValue(Meal::class.java)
                    myMealsRemote.add(result!!)
                }

                // clear the local database
                mealDao.deleteAllMeals()

                // save the remote data to the local database
                myMealsRemote.forEach { onlineMeal ->
                    mealDao.insertMeal(
                        mealEntity = MealEntity(
                            id = onlineMeal.id ?: UUID.randomUUID().toString(),
                            name = onlineMeal.name,
                            imageUrl = onlineMeal.imageUrl,
                            cookingTime = onlineMeal.cookingTime,
                            category = onlineMeal.category,
                            cookingDifficulty = onlineMeal.cookingDifficulty,
                            ingredients = onlineMeal.ingredients,
                            cookingInstructions = onlineMeal.cookingDirections,
                            isFavorite = onlineMeal.favorite,
                            servingPeople = onlineMeal.servingPeople
                        )
                    )
                }

                // read from the local database
                mealDao.getAllMeals().map { mealEntityList ->
                    mealEntityList.map { mealEntity ->
                        mealEntity.toMeal()
                    }
                }
            }

            if (newMyMeals == null) {
                Resource.Error(
                    "Viewing offline data",
                    data = myMeals.map {
                        it.map { mealEntity ->
                            mealEntity.toMeal()
                        }
                    }
                )
            } else {
                Resource.Success(data = newMyMeals)
            }
        } catch (e: Exception) {
            Resource.Error(
                e.localizedMessage ?: "Unknown error occurred",
                data = myMeals.map {
                    it.map { mealEntity ->
                        mealEntity.toMeal()
                    }
                }
            )
        }
    }

    private suspend fun searchFromInternet(
        searchString: String,
        searchBy: String
    ): Resource<Flow<List<Meal>>> {
        return when (searchBy) {
            "Name" -> {
                safeApiCall(Dispatchers.IO) {
                    val response = mealDbApi.searchMealsByName(query = searchString)
                    val mealsList = response.meals.map { it.toOnlineMeal().toGeneralMeal() }
                    flowOf(mealsList)
                }
            }
            "Ingredient" -> {
                safeApiCall(Dispatchers.IO) {
                    val response = mealDbApi.searchMealsByIngredient(query = searchString)
                    val mealsList = response.meals.map { it.toOnlineMeal().toGeneralMeal() }

                    flowOf(mealsList)
                }
            }
            "Category" -> {
                safeApiCall(Dispatchers.IO) {
                    val response = mealDbApi.searchMealsByCategory(query = searchString)
                    val mealsList = response.meals.map { it.toOnlineMeal().toGeneralMeal() }

                    flowOf(mealsList)
                }
            }
            else -> {
                Resource.Error("Unknown online search by")
            }
        }
    }
}
