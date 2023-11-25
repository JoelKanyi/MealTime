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
package com.kanyideveloper.mealtime.navigation

import com.joelkanyi.auth.presentation.auth.destinations.ForgotPasswordScreenDestination
import com.joelkanyi.auth.presentation.auth.destinations.LandingPageScreenDestination
import com.joelkanyi.auth.presentation.auth.destinations.SignInScreenDestination
import com.joelkanyi.auth.presentation.auth.destinations.SignUpScreenDestination
import com.joelkanyi.kitchen_timer.presentation.timer.destinations.KitchenTimerScreenDestination
import com.joelkanyi.presentation.favorites.destinations.FavoritesScreenDestination
import com.joelkanyi.presentation.home.destinations.DetailsScreenDestination
import com.joelkanyi.presentation.home.destinations.HomeScreenDestination
import com.joelkanyi.presentation.mealplanner.destinations.AllergiesScreenDestination
import com.joelkanyi.presentation.mealplanner.destinations.MealPlannerScreenDestination
import com.joelkanyi.presentation.mealplanner.destinations.MealTypesScreenDestination
import com.joelkanyi.presentation.mealplanner.destinations.NumberOfPeopleScreenDestination
import com.joelkanyi.presentation.search.destinations.SearchScreenDestination
import com.kanyideveloper.addmeal.presentation.addmeal.destinations.AddMealScreenDestination
import com.kanyideveloper.addmeal.presentation.addmeal.destinations.NextAddMealScreenDestination
import com.kanyideveloper.settings.presentation.settings.destinations.SettingsScreenDestination
import com.ramcosta.composedestinations.dynamic.routedIn
import com.ramcosta.composedestinations.spec.DestinationSpec
import com.ramcosta.composedestinations.spec.NavGraphSpec

object NavGraphs {

    val auth = object : NavGraphSpec {
        override val route = "auth"

        override val startRoute = LandingPageScreenDestination routedIn this

        override val destinationsByRoute = listOf<DestinationSpec<*>>(
            HomeScreenDestination,
            LandingPageScreenDestination,
            SignInScreenDestination,
            SignUpScreenDestination,
            ForgotPasswordScreenDestination
        ).routedIn(this)
            .associateBy { it.route }
    }

    val home = object : NavGraphSpec {
        override val route = "home"

        override val startRoute = HomeScreenDestination routedIn this

        override val destinationsByRoute = listOf<DestinationSpec<*>>(
            HomeScreenDestination,
            DetailsScreenDestination,
            AddMealScreenDestination,
            NextAddMealScreenDestination,
            SearchScreenDestination,
            LandingPageScreenDestination,
            SettingsScreenDestination,
            AllergiesScreenDestination,
            NumberOfPeopleScreenDestination,
            MealTypesScreenDestination,
            MealPlannerScreenDestination,
        ).routedIn(this)
            .associateBy { it.route }
    }

    val search = object : NavGraphSpec {
        override val route = "search"

        override val startRoute = SearchScreenDestination routedIn this

        override val destinationsByRoute = listOf<DestinationSpec<*>>(
            SearchScreenDestination,
        ).routedIn(this)
            .associateBy { it.route }
    }

    val kitchenTimer = object : NavGraphSpec {
        override val route = "kitchen-timer"

        override val startRoute = KitchenTimerScreenDestination routedIn this

        override val destinationsByRoute = listOf<DestinationSpec<*>>(
            KitchenTimerScreenDestination
        ).routedIn(this)
            .associateBy { it.route }
    }

    val favorites = object : NavGraphSpec {
        override val route = "favorites"

        override val startRoute = FavoritesScreenDestination routedIn this

        override val destinationsByRoute = listOf<DestinationSpec<*>>(
            FavoritesScreenDestination,
            DetailsScreenDestination
        ).routedIn(this)
            .associateBy { it.route }
    }

    val mealPlanner = object : NavGraphSpec {
        override val route = "meal_planner"

        override val startRoute = MealPlannerScreenDestination routedIn this

        override val destinationsByRoute = listOf<DestinationSpec<*>>(
            MealPlannerScreenDestination,
            AllergiesScreenDestination,
            NumberOfPeopleScreenDestination,
            MealTypesScreenDestination,
            DetailsScreenDestination
        ).routedIn(this)
            .associateBy { it.route }
    }

    val settings = object : NavGraphSpec {
        override val route = "settings"

        override val startRoute = SettingsScreenDestination routedIn this

        override val destinationsByRoute = listOf<DestinationSpec<*>>(
            SettingsScreenDestination,
            AllergiesScreenDestination,
            NumberOfPeopleScreenDestination,
            MealTypesScreenDestination,
            MealPlannerScreenDestination,
            LandingPageScreenDestination
        ).routedIn(this)
            .associateBy { it.route }
    }

    fun root(isLoggedIn: Boolean) = object : NavGraphSpec {
        override val route = "root"
        override val startRoute = if (isLoggedIn) home else auth
        override val destinationsByRoute = emptyMap<String, DestinationSpec<*>>()
        override val nestedNavGraphs = listOf(
            auth,
            home,
            search,
            mealPlanner,
            favorites,
            settings,
            kitchenTimer
        )
    }
}
