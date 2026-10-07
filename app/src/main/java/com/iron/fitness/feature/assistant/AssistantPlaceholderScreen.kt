package com.iron.fitness.feature.assistant

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.iron.fitness.R
import com.iron.fitness.feature.more.PlaceholderScreen

@Composable
fun AssistantPlaceholderScreen(onBack: () -> Unit) {
    PlaceholderScreen(title = stringResource(R.string.nav_assistant), onBack = onBack)
}
