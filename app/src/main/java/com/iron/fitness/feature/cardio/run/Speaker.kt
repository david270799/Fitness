package com.iron.fitness.feature.cardio.run

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import com.iron.fitness.core.settings.SettingsRepository
import com.iron.fitness.core.util.RU
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Голосовые подсказки («Следующее: …») через системный синтез речи. */
@Singleton
class Speaker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
) {
    private var tts: TextToSpeech? = null
    @Volatile private var ready = false
    private var pending: String? = null

    fun prepare() {
        if (tts != null) return
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = RU
                tts?.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                ready = true
                pending?.let { speakNow(it) }
                pending = null
            }
        }
    }

    suspend fun say(text: String) {
        if (!settings.current().voiceHintsEnabled) return
        prepare()
        if (ready) speakNow(text) else pending = text
    }

    private fun speakNow(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "iron")
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
        pending = null
    }
}
