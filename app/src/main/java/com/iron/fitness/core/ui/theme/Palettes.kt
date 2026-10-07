package com.iron.fitness.core.ui.theme

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.iron.fitness.R

/**
 * Набор цветовых токенов темы. Новая тема = ещё один объект [IronPalette] в [IronPalettes.all].
 */
@Immutable
data class IronPalette(
    val id: String,
    @StringRes val nameRes: Int,
    val isLight: Boolean,
    /** Фон экрана. */
    val background: Color,
    /** Поверхность карточек. */
    val surface: Color,
    /** Приподнятая поверхность: выделенные элементы, поля ввода. */
    val surfaceHigh: Color,
    /** Тонкие рамки и разделители. */
    val border: Color,
    /** Основной текст. */
    val text: Color,
    /** Второстепенный текст. */
    val textSecondary: Color,
    /** Акцент: кнопки, прогресс, выделение. */
    val accent: Color,
    /** Текст и иконки поверх акцента. */
    val onAccent: Color,
    val error: Color,
    val success: Color,
    /** Предупреждения и рекорды. */
    val warning: Color,
)

object IronPalettes {
    val Midnight = IronPalette(
        id = "midnight",
        nameRes = R.string.theme_midnight,
        isLight = false,
        background = Color(0xFF0A1020),
        surface = Color(0xFF111A2E),
        surfaceHigh = Color(0xFF1A2540),
        border = Color(0xFF26345A),
        text = Color(0xFFEAF2FF),
        textSecondary = Color(0xFF8C9DBC),
        accent = Color(0xFF7FD4FF),
        onAccent = Color(0xFF04131F),
        error = Color(0xFFFF5C64),
        success = Color(0xFF4ADE80),
        warning = Color(0xFFFFC857),
    )

    val Graphite = IronPalette(
        id = "graphite",
        nameRes = R.string.theme_graphite,
        isLight = false,
        background = Color(0xFF0A0A0A),
        surface = Color(0xFF141414),
        surfaceHigh = Color(0xFF1E1E1E),
        border = Color(0xFF2C2C2C),
        text = Color(0xFFFFFFFF),
        textSecondary = Color(0xFF9A9A9A),
        accent = Color(0xFFFFFFFF),
        onAccent = Color(0xFF000000),
        error = Color(0xFFFF4D4D),
        success = Color(0xFF5BE38A),
        warning = Color(0xFFFFD166),
    )

    val Steel = IronPalette(
        id = "steel",
        nameRes = R.string.theme_steel,
        isLight = false,
        background = Color(0xFF1A1E24),
        surface = Color(0xFF22272F),
        surfaceHigh = Color(0xFF2B313B),
        border = Color(0xFF39404C),
        text = Color(0xFFE6EAF0),
        textSecondary = Color(0xFF9AA3B2),
        accent = Color(0xFF8FA8C8),
        onAccent = Color(0xFF10151C),
        error = Color(0xFFE5534B),
        success = Color(0xFF57C78A),
        warning = Color(0xFFE3B341),
    )

    val Blood = IronPalette(
        id = "blood",
        nameRes = R.string.theme_blood,
        isLight = false,
        background = Color(0xFF050505),
        surface = Color(0xFF120E0E),
        surfaceHigh = Color(0xFF1C1515),
        border = Color(0xFF2E2020),
        text = Color(0xFFF5F0F0),
        textSecondary = Color(0xFFA59696),
        accent = Color(0xFFB3121B),
        onAccent = Color(0xFFFFFFFF),
        error = Color(0xFFFF8A65),
        success = Color(0xFF4CC38A),
        warning = Color(0xFFFFC857),
    )

    val Amber = IronPalette(
        id = "amber",
        nameRes = R.string.theme_amber,
        isLight = false,
        background = Color(0xFF070605),
        surface = Color(0xFF13110B),
        surfaceHigh = Color(0xFF1D1A11),
        border = Color(0xFF2F2A1C),
        text = Color(0xFFF7F3EA),
        textSecondary = Color(0xFFA59E8C),
        accent = Color(0xFFFF8C1A),
        onAccent = Color(0xFF140A00),
        error = Color(0xFFFF4D4D),
        success = Color(0xFF5BD68A),
        warning = Color(0xFFFFD166),
    )

    val Light = IronPalette(
        id = "light",
        nameRes = R.string.theme_light,
        isLight = true,
        background = Color(0xFFE9ECF0),
        surface = Color(0xFFFFFFFF),
        surfaceHigh = Color(0xFFF2F4F7),
        border = Color(0xFFCDD2DA),
        text = Color(0xFF0B0D10),
        textSecondary = Color(0xFF5A6270),
        accent = Color(0xFF12306B),
        onAccent = Color(0xFFFFFFFF),
        error = Color(0xFFC62828),
        success = Color(0xFF1B7F45),
        warning = Color(0xFFB26A00),
    )

    val all: List<IronPalette> = listOf(Midnight, Graphite, Steel, Blood, Amber, Light)

    val default: IronPalette = Midnight

    fun byId(id: String?): IronPalette = all.firstOrNull { it.id == id } ?: default
}
