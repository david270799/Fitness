package com.iron.fitness.feature.more

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.iron.fitness.R
import com.iron.fitness.core.ui.IronIcons
import com.iron.fitness.core.ui.components.EmptyState
import com.iron.fitness.core.ui.components.IronScaffold

/** Временный экран для разделов, которые появятся на следующих этапах. */
@Composable
fun PlaceholderScreen(title: String, onBack: (() -> Unit)? = null) {
    IronScaffold(title = title, onBack = onBack) { padding ->
        EmptyState(
            text = stringResource(R.string.coming_soon),
            icon = IronIcons.Hourglass,
            modifier = Modifier.padding(padding),
        )
    }
}
