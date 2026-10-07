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
import com.iron.fitness.feature.cardio.run.IntervalRunScreen
import com.iron.fitness.feature.cardio.ui.CardioLogScreen
import com.iron.fitness.feature.cardio.ui.IntervalEditorScreen
import com.iron.fitness.feature.stretching.ui.StretchEditorScreen
import com.iron.fitness.feature.workouts.history.HistoryScreen
import com.iron.fitness.feature.workouts.history.WorkoutDetailScreen
import com.iron.fitness.feature.workouts.routine.RoutineEditorScreen
import com.iron.fitness.feature.workouts.session.SessionScreen
import com.iron.fitness.feature.workouts.summary.WorkoutSummaryScreen
import com.iron.fitness.feature.workouts.tools.ToolsScreen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry

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

    // ---------- Силовые тренировки ----------
    composable(
        Routes.WORKOUT_SESSION_PATTERN,
        arguments = listOf(
            navArgument("id") { type = NavType.LongType },
            navArgument("edit") { type = NavType.BoolType; defaultValue = false },
        ),
    ) { entry ->
        val picked = entry.pickedExercises()
        SessionScreen(
            onBack = back,
            onFinished = { id ->
                if (id == null) {
                    nav.popBackStack()
                } else {
                    nav.navigate(Routes.workoutSummary(id)) {
                        popUpTo(Routes.WORKOUT_SESSION_PATTERN) { inclusive = true }
                    }
                }
            },
            onAddExercises = { go(Routes.library(mode = "pickMany", category = "STRENGTH")) },
            onOpenExercise = { go(Routes.exercise(it)) },
            pickedExercises = picked,
            onPickedHandled = { entry.savedStateHandle[Routes.RESULT_PICKED_EXERCISES] = null },
        )
    }
    composable(
        Routes.WORKOUT_SUMMARY_PATTERN,
        arguments = listOf(navArgument("id") { type = NavType.LongType }),
    ) {
        WorkoutSummaryScreen(onDone = back, onStretchStarted = { go(Routes.INTERVAL_RUN) })
    }
    composable(
        Routes.WORKOUT_DETAIL_PATTERN,
        arguments = listOf(navArgument("id") { type = NavType.LongType }),
    ) {
        WorkoutDetailScreen(
            onBack = back,
            onEdit = { go(Routes.workoutSession(it, edit = true)) },
            onEditCardio = { go(Routes.cardioLog(it)) },
            onOpenExercise = { go(Routes.exercise(it)) },
        )
    }
    composable(Routes.HISTORY) {
        HistoryScreen(onBack = back, onOpen = { go(Routes.workoutDetail(it)) })
    }
    composable(
        Routes.ROUTINE_EDIT_PATTERN,
        arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L }),
    ) { entry ->
        val picked = entry.pickedExercises()
        RoutineEditorScreen(
            onBack = back,
            onAddExercises = { go(Routes.library(mode = "pickMany", category = "STRENGTH")) },
            pickedExercises = picked,
            onPickedHandled = { entry.savedStateHandle[Routes.RESULT_PICKED_EXERCISES] = null },
        )
    }
    composable(Routes.TOOLS) { ToolsScreen(onBack = back) }

    // ---------- Кардио и интервалы ----------
    composable(Routes.INTERVAL_RUN) { IntervalRunScreen(onBack = back) }
    composable(
        Routes.INTERVAL_EDIT_PATTERN,
        arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L }),
    ) { entry ->
        val picked = entry.pickedExercises()
        IntervalEditorScreen(
            onBack = back,
            onPickExercise = { go(Routes.library(mode = "pick")) },
            pickedExercises = picked,
            onPickedHandled = { entry.savedStateHandle[Routes.RESULT_PICKED_EXERCISES] = null },
        )
    }
    composable(
        Routes.STRETCH_EDIT_PATTERN,
        arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L }),
    ) { entry ->
        val picked = entry.pickedExercises()
        StretchEditorScreen(
            onBack = back,
            onAddExercises = { go(Routes.library(mode = "pickMany", category = "STRETCHING")) },
            pickedExercises = picked,
            onPickedHandled = { entry.savedStateHandle[Routes.RESULT_PICKED_EXERCISES] = null },
        )
    }
    composable(
        Routes.CARDIO_LOG_PATTERN,
        arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L }),
    ) {
        CardioLogScreen(onBack = back)
    }
}

/** Результат выбора упражнений из библиотеки (кладётся в SavedStateHandle этого экрана). */
@Composable
private fun NavBackStackEntry.pickedExercises(): List<String>? {
    val flow = remember(this) { savedStateHandle.getStateFlow<ArrayList<String>?>(Routes.RESULT_PICKED_EXERCISES, null) }
    val value by flow.collectAsStateWithLifecycle()
    return value
}
