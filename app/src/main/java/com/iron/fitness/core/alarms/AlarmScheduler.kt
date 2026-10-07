package com.iron.fitness.core.alarms

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Будильники через AlarmManager. Точные (setExactAndAllowWhileIdle) срабатывают и в режиме Doze;
 * если точные будильники запрещены, используется неточный вариант, который тоже работает в Doze.
 */
@Singleton
class AlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val alarmManager: AlarmManager? get() = context.getSystemService(AlarmManager::class.java)

    fun canScheduleExact(): Boolean {
        val am = alarmManager ?: return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
    }

    fun schedule(triggerAtMillis: Long, operation: PendingIntent, exact: Boolean = true) {
        val am = alarmManager ?: return
        try {
            if (exact && canScheduleExact()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
        }
    }

    fun cancel(operation: PendingIntent) {
        alarmManager?.cancel(operation)
    }

    companion object {
        /** Ближайший момент времени [minutesOfDay] (сегодня, если ещё не прошёл, иначе завтра). */
        fun nextDaily(minutesOfDay: Int, now: LocalDateTime = LocalDateTime.now(), zone: ZoneId = ZoneId.systemDefault()): Long {
            val time = LocalTime.of((minutesOfDay / 60).coerceIn(0, 23), (minutesOfDay % 60).coerceIn(0, 59))
            var at = LocalDateTime.of(now.toLocalDate(), time)
            if (!at.isAfter(now)) at = at.plusDays(1)
            return at.atZone(zone).toInstant().toEpochMilli()
        }

        /** Тот же момент, но не раньше завтрашнего дня (после срабатывания). */
        fun tomorrowAt(minutesOfDay: Int, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): Long {
            val time = LocalTime.of((minutesOfDay / 60).coerceIn(0, 23), (minutesOfDay % 60).coerceIn(0, 59))
            return LocalDateTime.of(today.plusDays(1), time).atZone(zone).toInstant().toEpochMilli()
        }
    }
}
