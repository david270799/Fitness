package com.iron.fitness.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.iron.fitness.R
import com.iron.fitness.core.util.withRussianLocale

object NotificationChannels {
    const val DOWNLOADS = "downloads"
    const val TIMER = "timer"
    const val CHALLENGES = "challenges"
    const val REMINDERS = "reminders"

    fun createAll(context: Context) {
        val ru = context.withRussianLocale()
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channels = listOf(
            NotificationChannel(DOWNLOADS, ru.getString(R.string.notif_channel_downloads), NotificationManager.IMPORTANCE_LOW),
            NotificationChannel(TIMER, ru.getString(R.string.notif_channel_timer), NotificationManager.IMPORTANCE_LOW).apply {
                description = ru.getString(R.string.notif_channel_timer_desc)
                setShowBadge(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            },
            NotificationChannel(CHALLENGES, ru.getString(R.string.notif_channel_challenges), NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(REMINDERS, ru.getString(R.string.notif_channel_reminders), NotificationManager.IMPORTANCE_HIGH).apply {
                description = ru.getString(R.string.notif_channel_reminders_desc)
                enableVibration(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
            },
        ) + extraChannels(ru)
        manager.createNotificationChannels(channels)
    }

    /** Каналы следующих этапов добавляются сюда. */
    private fun extraChannels(context: Context): List<NotificationChannel> = emptyList()
}
