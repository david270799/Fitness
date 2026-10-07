package com.iron.fitness.feature.workouts

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.iron.fitness.R
import com.iron.fitness.feature.more.PlaceholderScreen

@Composable
fun WorkoutsScreen(navigate: (String) -> Unit) {
    PlaceholderScreen(title = stringResource(R.string.workouts_title))
}
