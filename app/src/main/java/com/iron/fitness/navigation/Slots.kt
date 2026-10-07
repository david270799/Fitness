package com.iron.fitness.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.iron.fitness.R
import com.iron.fitness.core.ui.components.EmptyState

/** История упражнения в карточке. Наполняется на этапе силовых тренировок. */
@Composable
fun ExerciseHistorySlot(exerciseId: String, navigate: (String) -> Unit) {
    EmptyState(stringResource(R.string.exercise_no_history))
}
