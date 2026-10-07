package com.iron.fitness.feature.body

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.iron.fitness.R
import com.iron.fitness.feature.more.PlaceholderScreen

@Composable
fun BodyScreen(navigate: (String) -> Unit) {
    PlaceholderScreen(title = stringResource(R.string.body_title))
}
