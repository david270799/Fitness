package com.iron.fitness.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.iron.fitness.core.settings.AppSettings
import com.iron.fitness.feature.assistant.AssistantPlaceholderScreen
import com.iron.fitness.feature.body.BodyScreen
import com.iron.fitness.feature.daily.DailyScreen
import com.iron.fitness.feature.more.AboutScreen
import com.iron.fitness.feature.more.MoreScreen
import com.iron.fitness.feature.settings.SettingsScreen
import com.iron.fitness.feature.today.TodayScreen
import com.iron.fitness.feature.workouts.WorkoutsScreen

fun NavGraphBuilder.ironGraph(nav: NavHostController, settings: AppSettings) {
    val back: () -> Unit = { nav.popBackStack() }

    composable(Routes.TODAY) { TodayScreen(navigate = { nav.navigate(it) }) }
    composable(Routes.WORKOUTS) { WorkoutsScreen(navigate = { nav.navigate(it) }) }
    composable(Routes.DAILY) { DailyScreen(navigate = { nav.navigate(it) }) }
    composable(Routes.BODY) { BodyScreen(navigate = { nav.navigate(it) }) }
    composable(Routes.MORE) { MoreScreen(navigate = { nav.navigate(it) }) }

    composable(Routes.SETTINGS) { SettingsScreen(onBack = back, navigate = { nav.navigate(it) }) }
    composable(Routes.ABOUT) { AboutScreen(onBack = back) }
    composable(Routes.ASSISTANT) { AssistantPlaceholderScreen(onBack = back) }
}
