package com.iron.fitness.navigation

import androidx.compose.runtime.Composable
import com.iron.fitness.feature.workouts.exercise.ExerciseHistoryContent

/** История упражнения в карточке: рекорды, график, последние тренировки. */
@Composable
fun ExerciseHistorySlot(exerciseId: String, navigate: (String) -> Unit) {
    ExerciseHistoryContent(onOpenWorkout = { navigate(Routes.workoutDetail(it)) })
}
