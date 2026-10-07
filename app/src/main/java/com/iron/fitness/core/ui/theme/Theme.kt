package com.iron.fitness.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val LocalIronPalette = staticCompositionLocalOf { IronPalettes.default }
val LocalIronNumbers = staticCompositionLocalOf { IronNumbers() }

val IronShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(6.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(8.dp),
)

/** Короткий доступ к токенам темы и стилям чисел. */
object Iron {
    val colors: IronPalette
        @Composable @ReadOnlyComposable get() = LocalIronPalette.current
    val numbers: IronNumbers
        @Composable @ReadOnlyComposable get() = LocalIronNumbers.current
}

private fun IronPalette.toColorScheme() = if (isLight) {
    lightColorScheme(
        primary = accent, onPrimary = onAccent,
        primaryContainer = surfaceHigh, onPrimaryContainer = text,
        secondary = accent, onSecondary = onAccent,
        secondaryContainer = surfaceHigh, onSecondaryContainer = text,
        tertiary = success, onTertiary = Color.White,
        background = background, onBackground = text,
        surface = surface, onSurface = text,
        surfaceVariant = surfaceHigh, onSurfaceVariant = textSecondary,
        surfaceTint = Color.Transparent,
        surfaceContainerLowest = background, surfaceContainerLow = surface,
        surfaceContainer = surface, surfaceContainerHigh = surfaceHigh, surfaceContainerHighest = surfaceHigh,
        surfaceBright = surfaceHigh, surfaceDim = background,
        inverseSurface = text, inverseOnSurface = background, inversePrimary = accent,
        outline = border, outlineVariant = border,
        error = error, onError = Color.White,
        errorContainer = error, onErrorContainer = Color.White,
        scrim = Color.Black,
    )
} else {
    darkColorScheme(
        primary = accent, onPrimary = onAccent,
        primaryContainer = surfaceHigh, onPrimaryContainer = text,
        secondary = accent, onSecondary = onAccent,
        secondaryContainer = surfaceHigh, onSecondaryContainer = text,
        tertiary = success, onTertiary = Color.Black,
        background = background, onBackground = text,
        surface = surface, onSurface = text,
        surfaceVariant = surfaceHigh, onSurfaceVariant = textSecondary,
        surfaceTint = Color.Transparent,
        surfaceContainerLowest = background, surfaceContainerLow = surface,
        surfaceContainer = surface, surfaceContainerHigh = surfaceHigh, surfaceContainerHighest = surfaceHigh,
        surfaceBright = surfaceHigh, surfaceDim = background,
        inverseSurface = text, inverseOnSurface = background, inversePrimary = accent,
        outline = border, outlineVariant = border,
        error = error, onError = Color.Black,
        errorContainer = error, onErrorContainer = Color.Black,
        scrim = Color.Black,
    )
}

@Composable
fun IronTheme(palette: IronPalette, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalIronPalette provides palette,
        LocalIronNumbers provides IronNumbers(),
    ) {
        MaterialTheme(
            colorScheme = palette.toColorScheme(),
            typography = IronTypography,
            shapes = IronShapes,
            content = content,
        )
    }
}
