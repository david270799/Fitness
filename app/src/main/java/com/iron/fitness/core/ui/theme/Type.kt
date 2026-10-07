package com.iron.fitness.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.iron.fitness.R

/** Плотный гротеск для заголовков. */
val Oswald = FontFamily(
    Font(R.font.oswald_medium, FontWeight.Medium),
    Font(R.font.oswald_bold, FontWeight.Bold),
)

/** Читаемый шрифт для текста. */
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

/** Моноширинные цифры для веса, повторов и таймеров. */
val Mono = FontFamily(
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
)

val IronTypography = Typography(
    displayLarge = TextStyle(fontFamily = Oswald, fontWeight = FontWeight.Bold, fontSize = 52.sp, lineHeight = 58.sp),
    displayMedium = TextStyle(fontFamily = Oswald, fontWeight = FontWeight.Bold, fontSize = 42.sp, lineHeight = 48.sp),
    displaySmall = TextStyle(fontFamily = Oswald, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 40.sp),
    headlineLarge = TextStyle(fontFamily = Oswald, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = 0.02.em),
    headlineMedium = TextStyle(fontFamily = Oswald, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = 0.02.em),
    headlineSmall = TextStyle(fontFamily = Oswald, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = 0.02.em),
    titleLarge = TextStyle(fontFamily = Oswald, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = 0.03.em),
    titleMedium = TextStyle(fontFamily = Oswald, fontWeight = FontWeight.Medium, fontSize = 18.sp, lineHeight = 24.sp, letterSpacing = 0.03.em),
    titleSmall = TextStyle(fontFamily = Oswald, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = 0.04.em),
    bodyLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.04.em),
    labelMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.05.em),
    labelSmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.05.em),
)

/** Стили крупных чисел (моноширинные). */
@Immutable
data class IronNumbers(
    val huge: TextStyle = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 88.sp, lineHeight = 92.sp, letterSpacing = (-0.02).em),
    val large: TextStyle = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 44.sp, lineHeight = 50.sp),
    val medium: TextStyle = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp),
    val small: TextStyle = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp),
    val tiny: TextStyle = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp),
)
