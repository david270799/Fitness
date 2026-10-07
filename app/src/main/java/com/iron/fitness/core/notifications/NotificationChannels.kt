package com.iron.fitness.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.iron.fitness.R
import com.iron.fitness.core.util.withRussianLocale

object NotificationChannels {
    const val DOWNLOADS = "downloads"

    fun createAll(context: Context) {
        val ru = context.withRussianLocale()
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channels = listOf(
            NotificationChannel(DOWNLOADS, ru.getString(R.string.notif_channel_downloads), NotificationManager.IMPORTANCE_LOW),
        ) + extraChannels(ru)
        manager.createNotificationChannels(channels)
    }

    /** Каналы следующих этапов добавляются сюда. */
    private fun extraChannels(context: Context): List<NotificationChannel> = emptyList()
}
