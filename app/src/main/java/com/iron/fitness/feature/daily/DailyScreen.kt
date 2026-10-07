package com.iron.fitness.feature.daily

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.iron.fitness.R
import com.iron.fitness.feature.more.PlaceholderScreen

@Composable
fun DailyScreen(navigate: (String) -> Unit) {
    PlaceholderScreen(title = stringResource(R.string.daily_title))
}
