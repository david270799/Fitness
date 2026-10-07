package com.iron.fitness.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.iron.fitness.core.settings.AppSettings
import com.iron.fitness.feature.assistant.AssistantPlaceholderScreen
import com.iron.fitness.feature.body.BodyScreen
import com.iron.fitness.feature.daily.DailyScreen
import com.iron.fitness.feature.exercises.ui.ExerciseDetailScreen
import com.iron.fitness.feature.exercises.ui.ExerciseEditScreen
import com.iron.fitness.feature.exercises.ui.ExerciseLibraryScreen
import com.iron.fitness.feature.more.AboutScreen
import com.iron.fitness.feature.more.MoreScreen
import com.iron.fitness.feature.settings.SettingsScreen
import com.iron.fitness.feature.today.TodayScreen
import com.iron.fitness.feature.workouts.WorkoutsScreen

fun NavGraphBuilder.ironGraph(nav: NavHostController, settings: AppSettings) {
    val back: () -> Unit = { nav.popBackStack() }
    val go: (String) -> Unit = { nav.navigate(it) }

    composable(Routes.TODAY) { TodayScreen(navigate = go) }
    composable(Routes.WORKOUTS) { WorkoutsScreen(navigate = go) }
    composable(Routes.DAILY) { DailyScreen(navigate = go) }
    composable(Routes.BODY) { BodyScreen(navigate = go) }
    composable(Routes.MORE) { MoreScreen(navigate = go) }

    composable(Routes.SETTINGS) { SettingsScreen(onBack = back, navigate = go) }
    composable(Routes.ABOUT) { AboutScreen(onBack = back) }
    composable(Routes.ASSISTANT) { AssistantPlaceholderScreen(onBack = back) }

    composable(
        Routes.LIBRARY_PATTERN,
        arguments = listOf(
            navArgument("mode") { type = NavType.StringType; defaultValue = "browse" },
            navArgument("category") { type = NavType.StringType; nullable = true; defaultValue = null },
        ),
    ) { entry ->
        ExerciseLibraryScreen(
            onBack = back,
            onOpenExercise = { go(Routes.exercise(it)) },
            onCreateCustom = { go(Routes.exerciseEdit(category = entry.arguments?.getString("category"))) },
            onPicked = { ids ->
                nav.previousBackStackEntry?.savedStateHandle?.set(Routes.RESULT_PICKED_EXERCISES, ArrayList(ids))
                nav.popBackStack()
            },
        )
    }
    composable(
        Routes.EXERCISE_PATTERN,
        arguments = listOf(navArgument("id") { type = NavType.StringType }),
    ) {
        ExerciseDetailScreen(
            onBack = back,
            onEdit = { go(Routes.exerciseEdit(id = it)) },
            historyContent = { id -> ExerciseHistorySlot(id, go) },
        )
    }
    composable(
        Routes.EXERCISE_EDIT_PATTERN,
        arguments = listOf(
            navArgument("id") { type = NavType.StringType; nullable = true; defaultValue = null },
            navArgument("category") { type = NavType.StringType; nullable = true; defaultValue = null },
        ),
    ) {
        ExerciseEditScreen(
            onBack = back,
            onSaved = { id ->
                nav.popBackStack()
                if (nav.currentBackStackEntry?.destination?.route != Routes.EXERCISE_PATTERN) {
                    go(Routes.exercise(id))
                }
            },
        )
    }
}
