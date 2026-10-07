package com.iron.fitness.core.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class StreaksTest {
    private val today = LocalDate.of(2025, 3, 12) // среда

    private fun days(vararg offsets: Long) = offsets.map { today.minusDays(it) }.toSet()

    @Test
    fun emptyHistoryGivesZero() {
        assertEquals(0, Streaks.daysInRow(emptySet(), today))
        assertEquals(0, Streaks.bestDaysInRow(emptySet()))
        assertEquals(0, Streaks.weeksInRow(emptyList(), today))
    }

    @Test
    fun streakIncludesToday() {
        assertEquals(3, Streaks.daysInRow(days(0, 1, 2, 4), today))
    }

    @Test
    fun streakNotBrokenWhenTodayNotDoneYet() {
        assertEquals(2, Streaks.daysInRow(days(1, 2), today))
    }

    @Test
    fun streakBrokenByMissedYesterday() {
        assertEquals(0, Streaks.daysInRow(days(2, 3, 4), today))
    }

    @Test
    fun bestStreakIsLongestRun() {
        assertEquals(4, Streaks.bestDaysInRow(days(0, 1, 5, 6, 7, 8, 10)))
    }

    @Test
    fun weeksInRowCountsCalendarWeeks() {
        // Тренировки: на этой неделе (пн), прошлой (пт) и позапрошлой (вс). Неделя до этого — пропуск.
        val dates = listOf(
            LocalDate.of(2025, 3, 10),
            LocalDate.of(2025, 3, 7),
            LocalDate.of(2025, 3, 2),
            LocalDate.of(2025, 2, 10),
        )
        assertEquals(3, Streaks.weeksInRow(dates, today))
    }

    @Test
    fun currentWeekWithoutWorkoutDoesNotBreakSeries() {
        val dates = listOf(LocalDate.of(2025, 3, 7), LocalDate.of(2025, 2, 26))
        assertEquals(2, Streaks.weeksInRow(dates, today))
    }
}
