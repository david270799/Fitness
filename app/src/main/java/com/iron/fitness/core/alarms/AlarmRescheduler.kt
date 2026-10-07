package com.iron.fitness.core.alarms

import com.iron.fitness.feature.daily.data.ChallengeReminders
import com.iron.fitness.feature.reminders.data.ReminderAlarms
import javax.inject.Inject
import javax.inject.Singleton

/** Переустановка всех будильников приложения (после загрузки, при старте). */
@Singleton
class AlarmRescheduler @Inject constructor(
    private val challenges: ChallengeReminders,
    private val reminders: ReminderAlarms,
) {
    suspend fun rescheduleAll() {
        runCatching { reminders.rescheduleAll() }
        runCatching { challenges.rescheduleAll() }
    }
}
