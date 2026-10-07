package com.iron.fitness.core.util

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/** Приложение всегда на русском: правильные падежи в plurals и формат дат. */
val RU: Locale = Locale.forLanguageTag("ru-RU")

fun Context.withRussianLocale(): Context {
    val config = Configuration(resources.configuration)
    config.setLocale(RU)
    return createConfigurationContext(config)
}
