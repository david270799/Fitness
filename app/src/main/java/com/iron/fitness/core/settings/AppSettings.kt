package com.iron.fitness.core.settings

enum class ThemeMode { FIXED, SYSTEM }

data class AppSettings(
    val themeId: String = "midnight",
    val themeMode: ThemeMode = ThemeMode.FIXED,
    val onboardingDone: Boolean = false,
    // Тренировки
    val defaultRestSeconds: Int = 90,
    val keepScreenOn: Boolean = true,
    val autoStartRestTimer: Boolean = true,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val voiceHintsEnabled: Boolean = true,
    val barWeightKg: Double = 20.0,
    val plates: List<Double> = DEFAULT_PLATES,
    // Напоминания
    val reminderRepeatMinutes: Int = 15,
    val reminderRepeatCount: Int = 3,
    // Тело
    val weightGoalKg: Double? = null,
    // Ассистент
    val geminiModel: String = DEFAULT_GEMINI_MODEL,
    val sendBodyDataToAssistant: Boolean = false,
    val inBodyWarningAccepted: Boolean = false,
    // Библиотека
    val libraryVersion: Int = 0,
    // Бэкап
    val autoBackupEnabled: Boolean = false,
    val driveAccount: String? = null,
    val lastBackupAt: Long? = null,
) {
    companion object {
        val DEFAULT_PLATES = listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25)
        const val DEFAULT_GEMINI_MODEL = "gemini-3.8-flash"
        const val DEFAULT_BODY_WEIGHT_KG = 75.0
    }
}
