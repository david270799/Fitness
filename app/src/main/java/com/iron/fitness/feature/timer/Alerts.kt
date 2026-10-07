package com.iron.fitness.feature.timer

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.iron.fitness.core.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Звук и вибрация таймеров. */
@Singleton
class Alerts @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
) {
    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    @Volatile private var tone: ToneGenerator? = null

    private fun toneGenerator(): ToneGenerator? {
        tone?.let { return it }
        return runCatching { ToneGenerator(AudioManager.STREAM_ALARM, 85) }.getOrNull()?.also { tone = it }
    }

    /** Короткий сигнал «скоро конец» (за 3 секунды). */
    suspend fun tick() {
        val s = settings.current()
        if (s.soundEnabled) toneGenerator()?.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
        if (s.vibrationEnabled) vibrate(longArrayOf(0, 60))
    }

    /** Сигнал окончания отдыха/блока. */
    suspend fun finish() {
        val s = settings.current()
        if (s.soundEnabled) toneGenerator()?.startTone(ToneGenerator.TONE_PROP_BEEP2, 600)
        if (s.vibrationEnabled) vibrate(longArrayOf(0, 400, 150, 400, 150, 400))
    }

    /** Сигнал смены блока в интервальной программе. */
    suspend fun blockChange() {
        val s = settings.current()
        if (s.soundEnabled) toneGenerator()?.startTone(ToneGenerator.TONE_PROP_ACK, 300)
        if (s.vibrationEnabled) vibrate(longArrayOf(0, 250))
    }

    private fun vibrate(pattern: LongArray) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        runCatching { v.vibrate(VibrationEffect.createWaveform(pattern, -1)) }
    }

    fun release() {
        tone?.release()
        tone = null
    }
}
