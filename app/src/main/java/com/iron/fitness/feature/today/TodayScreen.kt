package com.iron.fitness.feature.today

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.iron.fitness.R
import com.iron.fitness.feature.more.PlaceholderScreen

@Composable
fun TodayScreen(navigate: (String) -> Unit) {
    PlaceholderScreen(title = stringResource(R.string.today_title))
}
