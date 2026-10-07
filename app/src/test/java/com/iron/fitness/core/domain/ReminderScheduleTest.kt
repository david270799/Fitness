package com.iron.fitness.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime

class ReminderScheduleTest {
    // Среда, 14 мая 2025, 10:00.
    private val now = LocalDateTime.of(2025, 5, 14, 10, 0)

    @Test
    fun nextSameDayLaterTime() {
        val next = ReminderSchedule.next(listOf(8 * 60, 20 * 60), ReminderSchedule.EVERY_DAY, now)
        assertEquals(LocalDateTime.of(2025, 5, 14, 20, 0), next)
    }

    @Test
    fun nextTomorrowWhenAllPassed() {
        val next = ReminderSchedule.next(listOf(8 * 60), ReminderSchedule.EVERY_DAY, now)
        assertEquals(LocalDateTime.of(2025, 5, 15, 8, 0), next)
    }

    @Test
    fun weekdaysSkipWeekend() {
        // Пятница 21:00 → следующий приём в понедельник.
        val fri = LocalDateTime.of(2025, 5, 16, 21, 0)
        val next = ReminderSchedule.next(listOf(9 * 60), ReminderSchedule.WEEKDAYS, fri)
        assertEquals(LocalDateTime.of(2025, 5, 19, 9, 0), next)
    }

    @Test
    fun onlySundayAcrossWeek() {
        val mask = 1 shl 6
        val next = ReminderSchedule.next(listOf(12 * 60), mask, now)
        assertEquals(DayOfWeek.SUNDAY, next!!.dayOfWeek)
        assertEquals(LocalDateTime.of(2025, 5, 18, 12, 0), next)
    }

    @Test
    fun exactTimeIsNotNext() {
        val at = LocalDateTime.of(2025, 5, 14, 8, 0)
        val next = ReminderSchedule.next(listOf(8 * 60), ReminderSchedule.EVERY_DAY, at)
        assertEquals(LocalDateTime.of(2025, 5, 15, 8, 0), next)
    }

    @Test
    fun emptyScheduleHasNoNext() {
        assertNull(ReminderSchedule.next(emptyList(), ReminderSchedule.EVERY_DAY, now))
        assertNull(ReminderSchedule.next(listOf(60), 0, now))
    }

    @Test
    fun toggleDays() {
        var mask = 0
        mask = ReminderSchedule.toggleDay(mask, DayOfWeek.MONDAY)
        assertTrue(ReminderSchedule.isDayEnabled(mask, DayOfWeek.MONDAY))
        assertFalse(ReminderSchedule.isDayEnabled(mask, DayOfWeek.TUESDAY))
        mask = ReminderSchedule.toggleDay(mask, DayOfWeek.MONDAY)
        assertEquals(0, mask)
    }

    @Test
    fun adherenceCountsOldPendingAsMissed() {
        val logs = listOf(
            now.minusDays(1) to DoseStatus.TAKEN,
            now.minusDays(2) to DoseStatus.TAKEN,
            now.minusDays(3) to DoseStatus.MISSED,
            now.minusHours(5) to DoseStatus.PENDING, // давно — пропуск
            now.minusMinutes(10) to DoseStatus.PENDING, // ещё можно принять
            now.minusDays(4) to DoseStatus.SKIPPED, // не учитывается
        )
        assertEquals(50, ReminderSchedule.adherencePercent(logs, now))
        assertNull(ReminderSchedule.adherencePercent(emptyList(), now))
    }
}
