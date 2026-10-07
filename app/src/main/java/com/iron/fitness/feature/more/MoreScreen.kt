package com.iron.fitness.feature.more

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.iron.fitness.R
import com.iron.fitness.core.ui.IronIcons
import com.iron.fitness.core.ui.components.IronCard
import com.iron.fitness.core.ui.components.IronIcon
import com.iron.fitness.core.ui.components.IronScaffold
import com.iron.fitness.core.ui.components.ListRow
import com.iron.fitness.core.ui.theme.Iron
import com.iron.fitness.navigation.Routes

private data class MoreItem(
    val route: String,
    @StringRes val title: Int,
    @StringRes val subtitle: Int,
    @DrawableRes val icon: Int,
)

private val items = listOf(
    MoreItem(Routes.SETTINGS, R.string.more_settings, R.string.more_settings_sub, IronIcons.Settings),
    MoreItem(Routes.ABOUT, R.string.more_about, R.string.more_about_sub, IronIcons.Info),
)

@Composable
fun MoreScreen(navigate: (String) -> Unit) {
    IronScaffold(title = stringResource(R.string.more_title)) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(items, key = { it.route }) { item ->
                IronCard(onClick = { navigate(item.route) }, contentPadding = PaddingValues(0.dp)) {
                    ListRow(
                        title = stringResource(item.title),
                        subtitle = stringResource(item.subtitle),
                        icon = item.icon,
                        iconTint = Iron.colors.accent,
                        trailing = { IronIcon(IronIcons.ChevronRight, null, tint = Iron.colors.textSecondary) },
                    )
                }
            }
        }
    }
}
