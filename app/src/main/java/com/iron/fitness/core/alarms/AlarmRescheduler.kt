package com.iron.fitness.core.alarms

import com.iron.fitness.feature.daily.data.ChallengeReminders
import javax.inject.Inject
import javax.inject.Singleton

/** Переустановка всех будильников приложения (после загрузки, при старте). */
@Singleton
class AlarmRescheduler @Inject constructor(
    private val challenges: ChallengeReminders,
) {
    suspend fun rescheduleAll() {
        runCatching { challenges.rescheduleAll() }
    }
}
