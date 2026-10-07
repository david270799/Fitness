package com.iron.fitness.core.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val THEME_ID = stringPreferencesKey("theme_id")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val DEFAULT_REST = intPreferencesKey("default_rest_seconds")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val AUTO_REST = booleanPreferencesKey("auto_rest_timer")
        val SOUND = booleanPreferencesKey("sound_enabled")
        val VIBRATION = booleanPreferencesKey("vibration_enabled")
        val VOICE = booleanPreferencesKey("voice_hints_enabled")
        val BAR_WEIGHT = doublePreferencesKey("bar_weight_kg")
        val PLATES = stringPreferencesKey("plates")
        val REMINDER_REPEAT_MIN = intPreferencesKey("reminder_repeat_minutes")
        val REMINDER_REPEAT_COUNT = intPreferencesKey("reminder_repeat_count")
        val WEIGHT_GOAL = doublePreferencesKey("weight_goal_kg")
        val GEMINI_MODEL = stringPreferencesKey("gemini_model")
        val SEND_BODY = booleanPreferencesKey("send_body_data")
        val INBODY_WARNING = booleanPreferencesKey("inbody_warning_accepted")
        val LIBRARY_VERSION = intPreferencesKey("library_version")
        val AUTO_BACKUP = booleanPreferencesKey("auto_backup")
        val DRIVE_ACCOUNT = stringPreferencesKey("drive_account")
        val LAST_BACKUP = longPreferencesKey("last_backup_at")
    }

    val settings: Flow<AppSettings> = context.settingsStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it.toSettings() }

    suspend fun current(): AppSettings = settings.first()

    private fun Preferences.toSettings(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            themeId = this[Keys.THEME_ID] ?: d.themeId,
            themeMode = this[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: d.themeMode,
            onboardingDone = this[Keys.ONBOARDING_DONE] ?: d.onboardingDone,
            defaultRestSeconds = this[Keys.DEFAULT_REST] ?: d.defaultRestSeconds,
            keepScreenOn = this[Keys.KEEP_SCREEN_ON] ?: d.keepScreenOn,
            autoStartRestTimer = this[Keys.AUTO_REST] ?: d.autoStartRestTimer,
            soundEnabled = this[Keys.SOUND] ?: d.soundEnabled,
            vibrationEnabled = this[Keys.VIBRATION] ?: d.vibrationEnabled,
            voiceHintsEnabled = this[Keys.VOICE] ?: d.voiceHintsEnabled,
            barWeightKg = this[Keys.BAR_WEIGHT] ?: d.barWeightKg,
            plates = this[Keys.PLATES]?.split(';')?.mapNotNull { it.toDoubleOrNull() }?.takeIf { it.isNotEmpty() } ?: d.plates,
            reminderRepeatMinutes = this[Keys.REMINDER_REPEAT_MIN] ?: d.reminderRepeatMinutes,
            reminderRepeatCount = this[Keys.REMINDER_REPEAT_COUNT] ?: d.reminderRepeatCount,
            weightGoalKg = this[Keys.WEIGHT_GOAL],
            geminiModel = this[Keys.GEMINI_MODEL]?.takeIf { it.isNotBlank() } ?: d.geminiModel,
            sendBodyDataToAssistant = this[Keys.SEND_BODY] ?: d.sendBodyDataToAssistant,
            inBodyWarningAccepted = this[Keys.INBODY_WARNING] ?: d.inBodyWarningAccepted,
            libraryVersion = this[Keys.LIBRARY_VERSION] ?: d.libraryVersion,
            autoBackupEnabled = this[Keys.AUTO_BACKUP] ?: d.autoBackupEnabled,
            driveAccount = this[Keys.DRIVE_ACCOUNT],
            lastBackupAt = this[Keys.LAST_BACKUP],
        )
    }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        context.settingsStore.edit { block(it) }
    }

    suspend fun setTheme(id: String) = edit { it[Keys.THEME_ID] = id }
    suspend fun setThemeMode(mode: ThemeMode) = edit { it[Keys.THEME_MODE] = mode.name }
    suspend fun setOnboardingDone(done: Boolean) = edit { it[Keys.ONBOARDING_DONE] = done }
    suspend fun setDefaultRest(seconds: Int) = edit { it[Keys.DEFAULT_REST] = seconds.coerceIn(10, 900) }
    suspend fun setKeepScreenOn(value: Boolean) = edit { it[Keys.KEEP_SCREEN_ON] = value }
    suspend fun setAutoRest(value: Boolean) = edit { it[Keys.AUTO_REST] = value }
    suspend fun setSound(value: Boolean) = edit { it[Keys.SOUND] = value }
    suspend fun setVibration(value: Boolean) = edit { it[Keys.VIBRATION] = value }
    suspend fun setVoiceHints(value: Boolean) = edit { it[Keys.VOICE] = value }
    suspend fun setBarWeight(kg: Double) = edit { it[Keys.BAR_WEIGHT] = kg }
    suspend fun setPlates(plates: List<Double>) = edit {
        it[Keys.PLATES] = plates.sortedDescending().joinToString(";")
    }
    suspend fun setReminderRepeat(minutes: Int, count: Int) = edit {
        it[Keys.REMINDER_REPEAT_MIN] = minutes.coerceIn(5, 120)
        it[Keys.REMINDER_REPEAT_COUNT] = count.coerceIn(0, 3)
    }
    suspend fun setWeightGoal(kg: Double?) = edit {
        if (kg == null) it.remove(Keys.WEIGHT_GOAL) else it.set(Keys.WEIGHT_GOAL, kg)
    }
    suspend fun setGeminiModel(model: String) = edit { it[Keys.GEMINI_MODEL] = model.trim() }
    suspend fun setSendBodyData(value: Boolean) = edit { it[Keys.SEND_BODY] = value }
    suspend fun setInBodyWarningAccepted(value: Boolean) = edit { it[Keys.INBODY_WARNING] = value }
    suspend fun setLibraryVersion(version: Int) = edit { it[Keys.LIBRARY_VERSION] = version }
    suspend fun setAutoBackup(value: Boolean) = edit { it[Keys.AUTO_BACKUP] = value }
    suspend fun setDriveAccount(email: String?) = edit {
        if (email == null) it.remove(Keys.DRIVE_ACCOUNT) else it.set(Keys.DRIVE_ACCOUNT, email)
    }
    suspend fun setLastBackup(at: Long) = edit { it[Keys.LAST_BACKUP] = at }

    /** Все настройки как пары ключ→строка (для бэкапа; секретов здесь нет). */
    suspend fun exportRaw(): Map<String, String> {
        val prefs = context.settingsStore.data.first()
        return prefs.asMap().entries.associate { (k, v) -> k.name to "${typeTag(v)}:$v" }
    }

    /** Восстановление из бэкапа. Неизвестные ключи и типы пропускаются. */
    suspend fun importRaw(values: Map<String, String>) {
        context.settingsStore.edit { prefs ->
            prefs.clear()
            for ((name, raw) in values) {
                val tag = raw.substringBefore(':')
                val v = raw.substringAfter(':')
                when (tag) {
                    "s" -> prefs[stringPreferencesKey(name)] = v
                    "b" -> v.toBooleanStrictOrNull()?.let { prefs[booleanPreferencesKey(name)] = it }
                    "i" -> v.toIntOrNull()?.let { prefs[intPreferencesKey(name)] = it }
                    "l" -> v.toLongOrNull()?.let { prefs[longPreferencesKey(name)] = it }
                    "d" -> v.toDoubleOrNull()?.let { prefs[doublePreferencesKey(name)] = it }
                }
            }
        }
    }

    private fun typeTag(v: Any): String = when (v) {
        is Boolean -> "b"
        is Int -> "i"
        is Long -> "l"
        is Double -> "d"
        else -> "s"
    }
}
