package com.iron.fitness.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.iron.fitness.R
import com.iron.fitness.core.settings.AppSettings
import com.iron.fitness.core.ui.IronIcons
import com.iron.fitness.core.ui.components.IronIcon
import com.iron.fitness.core.ui.theme.Iron

@Composable
fun IronAppRoot(
    settings: AppSettings,
    pendingRoute: MutableState<String?>,
    navController: NavHostController = rememberNavController(),
) {
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val topLevel = TopLevel.entries.firstOrNull { it.route == currentRoute }

    val externalRoute = pendingRoute.value
    LaunchedEffect(externalRoute) {
        if (externalRoute != null) {
            pendingRoute.value = null
            runCatching { navController.navigate(externalRoute) { launchSingleTop = true } }
        }
    }

    Scaffold(
        containerColor = Iron.colors.background,
        contentColor = Iron.colors.text,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (topLevel != null) {
                IronBottomBar(current = topLevel) { navController.navigateTopLevel(it.route) }
            }
        },
        floatingActionButton = {
            if (topLevel != null && topLevel != TopLevel.MORE) {
                FloatingActionButton(
                    onClick = { navController.navigate(Routes.ASSISTANT) },
                    shape = RoundedCornerShape(6.dp),
                    containerColor = Iron.colors.accent,
                    contentColor = Iron.colors.onAccent,
                    elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
                ) {
                    IronIcon(IronIcons.Assistant, stringResource(R.string.nav_assistant), tint = Iron.colors.onAccent)
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.TODAY,
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding),
            enterTransition = { fadeIn(tween(160)) },
            exitTransition = { fadeOut(tween(120)) },
            popEnterTransition = { fadeIn(tween(160)) },
            popExitTransition = { fadeOut(tween(120)) },
        ) {
            ironGraph(navController, settings)
        }
    }
}

fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun IronBottomBar(current: TopLevel, onSelect: (TopLevel) -> Unit) {
    NavigationBar(
        containerColor = Iron.colors.surface,
        tonalElevation = 0.dp,
        modifier = Modifier.border(width = 1.dp, color = Iron.colors.border, shape = RoundedCornerShape(0.dp)),
    ) {
        TopLevel.entries.forEach { item ->
            val selected = item == current
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(item) },
                icon = {
                    IronIcon(
                        item.icon,
                        contentDescription = null,
                        tint = if (selected) Iron.colors.accentText else Iron.colors.textSecondary,
                    )
                },
                label = {
                    Text(
                        stringResource(item.label),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedTextColor = Iron.colors.text,
                    unselectedTextColor = Iron.colors.textSecondary,
                    indicatorColor = Iron.colors.surfaceHigh,
                ),
            )
        }
    }
}
