package com.iron.fitness.core.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class StatsMathTest {
    private val today = LocalDate.of(2025, 3, 12) // среда

    @Test
    fun weekPeriodIsSevenDaysEndingToday() {
        val starts = StatsMath.bucketStarts(StatsPeriod.WEEK, today)
        assertEquals(7, starts.size)
        assertEquals(today, starts.last())
        assertEquals(today.minusDays(6), starts.first())
    }

    @Test
    fun monthPeriodUsesMondays() {
        val starts = StatsMath.bucketStarts(StatsPeriod.MONTH, today)
        assertEquals(5, starts.size)
        assertEquals(LocalDate.of(2025, 3, 10), starts.last())
        assertEquals(LocalDate.of(2025, 2, 10), starts.first())
    }

    @Test
    fun yearPeriodUsesMonthStarts() {
        val starts = StatsMath.bucketStarts(StatsPeriod.YEAR, today)
        assertEquals(12, starts.size)
        assertEquals(LocalDate.of(2025, 3, 1), starts.last())
        assertEquals(LocalDate.of(2024, 4, 1), starts.first())
    }

    @Test
    fun aggregateSumsIntoBuckets() {
        val items = listOf(
            LocalDate.of(2025, 3, 10) to 2.0,
            LocalDate.of(2025, 3, 12) to 3.0,
            LocalDate.of(2025, 3, 3) to 1.0,
            LocalDate.of(2024, 1, 1) to 100.0, // вне периода
        )
        val result = StatsMath.aggregate(items, { it.first }, { it.second }, StatsPeriod.MONTH, today)
        assertEquals(5.0, result.last().second, 1e-9)
        assertEquals(1.0, result[result.size - 2].second, 1e-9)
        assertEquals(6.0, result.sumOf { it.second }, 1e-9)
    }
}
