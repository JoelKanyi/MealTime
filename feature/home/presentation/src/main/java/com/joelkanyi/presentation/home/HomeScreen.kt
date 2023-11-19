/*
 * Copyright 2022 Joel Kanyi.
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
package com.joelkanyi.presentation.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.kanyideveloper.compose_ui.components.StandardToolbar
import com.kanyideveloper.compose_ui.theme.MealTimeTheme
import com.kanyideveloper.compose_ui.theme.Shapes
import com.kanyideveloper.compose_ui.theme.Theme
import com.kanyideveloper.core.components.EmptyStateComponent
import com.kanyideveloper.core.components.ErrorStateComponent
import com.kanyideveloper.core.components.SwipeRefreshComponent
import com.kanyideveloper.core.model.Category
import com.kanyideveloper.core.model.Meal
import com.kanyideveloper.mealtime.core.R
import com.joelkanyi.presentation.HomeNavigator
import com.ramcosta.composedestinations.annotation.Destination

@Destination
@Composable
fun HomeScreen(
    navigator: HomeNavigator,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val mealsState = viewModel.mealsUiState.value
    val categoriesState = viewModel.categories.value
    val selectedCategory = viewModel.selectedCategory.value
    val snackbarHostState = remember { SnackbarHostState() }
    val favorites = viewModel.favorites.collectAsState().value

    var hasCamPermission by remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            mutableStateOf(
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            )
        } else {
            mutableStateOf(true)
        }
    }

    val notificationsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasCamPermission = granted
        }
    )

    LaunchedEffect(key1 = true, block = {
        viewModel.trackUserEvent("view_home_screen")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationsPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    })

    OnlineMealScreenContent(
        categoriesState = categoriesState,
        selectedCategory = selectedCategory,
        mealsUiState = mealsState,
        snackbarHostState = snackbarHostState,
        onMealClick = { mealId ->
            viewModel.trackUserEvent("home_screen_open_meal_details")
            navigator.openMealDetails(mealId = mealId)
        },
        onSelectCategory = { categoryName ->
            viewModel.trackUserEvent("home_screen_select_category")
            viewModel.setSelectedCategory(categoryName)
            viewModel.getMeals(viewModel.selectedCategory.value)
        },
        addToFavorites = { meal ->
            viewModel.trackUserEvent("home_screen_add_to_favorites")
            viewModel.insertAFavorite(meal)
        },
        removeFromFavorites = { id ->
            viewModel.trackUserEvent("home_screen_remove_from_favorites")
            viewModel.deleteAFavorite(mealId = id)
        },
        openRandomMeal = {
            viewModel.trackUserEvent("home_screen_open_random_meal")
            navigator.openMealDetails()
        },
        onRefreshData = {
            viewModel.trackUserEvent("home_screen_refresh_data")
            viewModel.getMeals(viewModel.selectedCategory.value)
        },
        onClickSearch = {
            viewModel.trackUserEvent("home_screen_click_search")
            navigator.onSearchClick()
        },
        onClickSettings = {
            viewModel.trackUserEvent("home_screen_click_settings")
            navigator.openSettings()
        },
        isFavorite = { mealId ->
            favorites.any { it.mealId == mealId }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@VisibleForTesting
@Composable
fun OnlineMealScreenContent(
    categoriesState: CategoriesState,
    selectedCategory: String,
    mealsUiState: MealsUiState,
    snackbarHostState: SnackbarHostState,
    onSelectCategory: (category: String) -> Unit,
    onMealClick: (mealId: String) -> Unit,
    addToFavorites: (meal: Meal) -> Unit,
    removeFromFavorites: (mealId: String) -> Unit,
    isFavorite: (mealId: String) -> Boolean,
    openRandomMeal: () -> Unit,
    onRefreshData: () -> Unit,
    onClickSearch: () -> Unit,
    onClickSettings: () -> Unit,
) {
    Scaffold(
        topBar = {
            StandardToolbar(
                navigate = {},
                title = {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {

                        SearchBox(
                            modifier = Modifier
                                .fillMaxWidth(0.9f),
                            onClick = onClickSearch
                        )

                        IconButton(onClick = onClickSettings) {
                            Icon(
                                painterResource(id = R.drawable.ic_settings),
                                contentDescription = stringResource(id = R.string.settings_strg),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                },
                showBackArrow = false,
                navActions = {}
            )
        },
        snackbarHost = {
            SnackbarHost(
                snackbarHostState
            )
        }
    ) { paddingValues ->
        SwipeRefreshComponent(
            isRefreshingState = mealsUiState.isLoading,
            onRefreshData = onRefreshData
        ) {
            Box(
                modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize()
            ) {
                if (mealsUiState.meals.isNotEmpty()) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item(span = { GridItemSpan(2) }) {
                            CategorySelection(
                                state = categoriesState,
                                selectedCategory = selectedCategory,
                                onClick = { categoryName ->
                                    onSelectCategory(categoryName)
                                }
                            )
                        }
                        item(span = { GridItemSpan(2) }) {
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        item(span = { GridItemSpan(2) }) {
                            RandomMealItem(
                                modifier = Modifier
                                    .fillMaxWidth(),
                                onClick = {
                                    openRandomMeal()
                                }
                            )
                        }

                        item(span = { GridItemSpan(2) }) {
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        items(mealsUiState.meals) { meal ->
                            MealItem(
                                meal = meal,
                                onClick = { mealId ->
                                    onMealClick(mealId)
                                },
                                addToFavorites = addToFavorites,
                                removeFromFavorites = removeFromFavorites,
                                isFavorite = isFavorite
                            )
                        }
                    }
                }

                if (!mealsUiState.isLoading && mealsUiState.error != null && mealsUiState.meals.isEmpty()) {
                    ErrorStateComponent(errorMessage = mealsUiState.error)
                }

                if (!mealsUiState.isLoading && mealsUiState.error == null && mealsUiState.meals.isEmpty()) {
                    EmptyStateComponent()
                }
            }
        }
    }
}

@Composable
fun RandomMealItem(
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.what_should_i_eat_today),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.we_ve_all_faced_the_dilemma_of_deciding_what_to_eat_for_the_day_but_fret_not_click_the_button_below_and_let_the_decision_be_made_for_you_randomly),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onClick,
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 30.dp
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_refresh),
                        contentDescription = stringResource(R.string.random_meal_shuffle_icon),
                    )
                    Text(
                        text = stringResource(R.string.get_a_random_meal),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

@Composable
fun MealItem(
    meal: Meal,
    onClick: (String) -> Unit,
    addToFavorites: (meal: Meal) -> Unit,
    removeFromFavorites: (mealId: String) -> Unit,
    isFavorite: (mealId: String) -> Boolean,
) {
    val favorite = isFavorite(meal.mealId)
    Card(
        modifier = Modifier
            .fillMaxSize()
            .wrapContentHeight()
            .padding(vertical = 5.dp)
            .clickable {
                onClick(meal.mealId)
            },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Image(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp),
                contentDescription = meal.name,
                painter = rememberAsyncImagePainter(
                    ImageRequest.Builder(LocalContext.current)
                        .data(data = meal.imageUrl)
                        .apply(block = fun ImageRequest.Builder.() {
                            placeholder(R.drawable.placeholder)
                        }).build()
                ),
                contentScale = ContentScale.Crop
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp, horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    modifier = Modifier
                        .fillMaxWidth(0.75f)
                        .padding(vertical = 3.dp),
                    text = meal.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                IconButton(
                    onClick = {
                        if (favorite) {
                            removeFromFavorites(meal.mealId)
                        } else {
                            addToFavorites(meal)
                        }
                    }
                ) {
                    Icon(
                        modifier = Modifier
                            .size(30.dp),
                        painter = if (favorite) {
                            painterResource(id = R.drawable.filled_favorite)
                        } else {
                            painterResource(id = R.drawable.heart_plus)
                        },
                        contentDescription = null,
                        tint = if (favorite) {
                            Color(0xFFfa4a0c)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun CategorySelection(
    state: CategoriesState,
    onClick: (String) -> Unit,
    selectedCategory: String
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(state.categories) { category ->
            CategoryItem(
                category = category,
                onClick = {
                    onClick(category.categoryName)
                },
                selectedCategory = selectedCategory
            )
        }
    }
}

@Composable
fun CategoryItem(
    category: Category,
    selectedCategory: String,
    onClick: () -> Unit
) {
    val selected = selectedCategory == category.categoryName
    Card(
        Modifier
            .width(100.dp)
            .wrapContentHeight()
            .clickable {
                onClick()
            },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = category.categoryName,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}


@Composable
fun SearchBox(
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = Shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_search),
                contentDescription = stringResource(id = R.string.search_strg),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.search_for_a_meal),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
    }
}


@Preview
@Composable
fun OnlineMealScreenContentPreview() {
    MealTimeTheme(theme = Theme.FOLLOW_SYSTEM.themeValue) {
        OnlineMealScreenContent(
            categoriesState = CategoriesState(
                isLoading = false,
                error = null,
                categories = sampleCategories
            ),
            selectedCategory = "All",
            mealsUiState = MealsUiState(
                isLoading = false,
                error = null,
                meals = sampleMeals
            ),
            onSelectCategory = {},
            onMealClick = {},
            addToFavorites = {},
            removeFromFavorites = {},
            openRandomMeal = {},
            onRefreshData = {},
            snackbarHostState = SnackbarHostState(),
            onClickSearch = {},
            onClickSettings = {},
            isFavorite = { false }
        )
    }
}

val sampleMeals = listOf(
    Meal(
        mealId = "1",
        name = "Beef and Mustard Pie",
        imageUrl = "https://www.themealdb.com/images/media/meals/sytuqu1511553755.jpg",
        category = "Beef",
    ),
    Meal(
        mealId = "2",
        name = "Beef and Oyster pie",
        imageUrl = "https://www.themealdb.com/images/media/meals/wrssvt1511556563.jpg",
        category = "Beef",
    ),
    Meal(
        mealId = "3",
        name = "Beef and Pickle Pie",
        imageUrl = "https://www.themealdb.com/images/media/meals/ytpstt1511554622.jpg",
        category = "Beef",
    ),
    Meal(
        mealId = "4",
        name = "Beef Banh Mi Bowls with Sriracha Mayo, Carrot & Pickled Cucumber",
        imageUrl = "https://www.themealdb.com/images/media/meals/z0ageb1583189517.jpg",
        category = "Beef",
    ),
    Meal(
        mealId = "1",
        name = "Beef and Mustard Pie",
        imageUrl = "https://www.themealdb.com/images/media/meals/sytuqu1511553755.jpg",
        category = "Beef",
    ),
    Meal(
        mealId = "2",
        name = "Beef and Oyster pie",
        imageUrl = "https://www.themealdb.com/images/media/meals/wrssvt1511556563.jpg",
        category = "Beef",
    ),
    Meal(
        mealId = "3",
        name = "Beef and Pickle Pie",
        imageUrl = "https://www.themealdb.com/images/media/meals/ytpstt1511554622.jpg",
        category = "Beef",
    ),
    Meal(
        mealId = "4",
        name = "Beef Banh Mi Bowls with Sriracha Mayo, Carrot & Pickled Cucumber",
        imageUrl = "https://www.themealdb.com/images/media/meals/z0ageb1583189517.jpg",
        category = "Beef",
    ),
)

val sampleCategories = listOf(
    Category(
        categoryName = "All",
        categoryId = -1
    ),
    Category(
        categoryName = "Beef",
        categoryId = 1
    ),
    Category(
        categoryName = "Chicken",
        categoryId = 2
    ),
    Category(
        categoryName = "Dessert",
        categoryId = 3
    ),
    Category(
        categoryName = "Lamb",
        categoryId = 4
    ),
    Category(
        categoryName = "Miscellaneous",
        categoryId = 5
    ),
    Category(
        categoryName = "Pasta",
        categoryId = 6
    ),
)