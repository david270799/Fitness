package com.iron.fitness.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ChallengesTest {
    private val today = LocalDate.of(2025, 5, 20)
    private fun d(back: Long) = today.minusDays(back).toEpochDay()

    @Test
    fun goalChangeKeepsHistory() {
        // До 10 дней назад цель была 30, потом подняли до 50.
        val changes = listOf(GoalChange(d(30), 30), GoalChange(d(10), 50))
        assertEquals(30, Challenges.goalOn(d(15), changes))
        assertEquals(50, Challenges.goalOn(d(10), changes))
        assertEquals(50, Challenges.goalOn(d(0), changes))
        // Дни до первой цели оцениваются по первой цели.
        assertEquals(30, Challenges.goalOn(d(100), changes))
    }

    @Test
    fun doneDaysUseGoalOfThatDay() {
        val changes = listOf(GoalChange(d(30), 30), GoalChange(d(2), 50))
        val totals = mapOf(d(3) to 40, d(2) to 40, d(1) to 55)
        val done = Challenges.doneDays(totals, changes)
        assertTrue(today.minusDays(3) in done) // 40 ≥ 30
        assertTrue(today.minusDays(2) !in done) // 40 < 50
        assertTrue(today.minusDays(1) in done)
    }

    @Test
    fun statsStreakAndBest() {
        val changes = listOf(GoalChange(d(60), 10))
        val totals = mapOf(
            d(0) to 5, // сегодня ещё не выполнено — серия не рвётся
            d(1) to 10, d(2) to 12, d(3) to 20,
            d(5) to 10, d(6) to 10, d(7) to 10, d(8) to 10,
        )
        val s = Challenges.stats(totals, changes, today)
        assertEquals(3, s.streak)
        assertEquals(4, s.best)
        assertEquals(5, s.todayTotal)
        assertEquals(10, s.todayGoal)
        assertEquals(20, s.bestDay)
        assertEquals(87L, s.totalAllTime)
        assertEquals(7, s.daysDone)
    }

    @Test
    fun lastWeekHasSevenDaysEndingToday() {
        val week = Challenges.lastWeek(mapOf(d(0) to 3, d(6) to 1), today)
        assertEquals(7, week.size)
        assertEquals(today, week.last().first)
        assertEquals(3, week.last().second)
        assertEquals(1, week.first().second)
    }
}
