package com.iron.fitness

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iron.fitness.core.settings.AppSettings
import com.iron.fitness.core.settings.ThemeMode
import com.iron.fitness.core.ui.theme.IronPalette
import com.iron.fitness.core.ui.theme.IronPalettes
import com.iron.fitness.core.ui.theme.IronTheme
import com.iron.fitness.core.util.withRussianLocale
import com.iron.fitness.navigation.IronAppRoot
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    /** Последний «внешний» запрос навигации (из уведомления, виджета). */
    val pendingRoute = mutableStateOf<String?>(null)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.withRussianLocale())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val current = settings
            if (current == null) {
                Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xFF0A1020)))
            } else {
                val palette = resolvePalette(current, isSystemInDarkTheme())
                LaunchedEffect(palette.isLight) {
                    val style = if (palette.isLight) {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    }
                    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                }
                IronTheme(palette) {
                    IronAppRoot(
                        settings = current,
                        pendingRoute = pendingRoute,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val route = intent?.getStringExtra(EXTRA_ROUTE) ?: return
        pendingRoute.value = route
    }

    companion object {
        const val EXTRA_ROUTE = "com.iron.fitness.ROUTE"

        fun resolvePalette(settings: AppSettings, systemDark: Boolean): IronPalette {
            val selected = IronPalettes.byId(settings.themeId)
            return when (settings.themeMode) {
                ThemeMode.FIXED -> selected
                ThemeMode.SYSTEM -> when {
                    !systemDark -> IronPalettes.Light
                    selected.isLight -> IronPalettes.default
                    else -> selected
                }
            }
        }

        fun routeIntent(context: Context, route: String): Intent =
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_ROUTE, route)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
}
