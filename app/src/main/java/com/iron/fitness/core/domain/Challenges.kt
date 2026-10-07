package com.iron.fitness.core.domain

import java.time.LocalDate

/** Изменение дневной цели: с даты [fromEpochDay] цель — [goal]. История не теряется. */
data class GoalChange(val fromEpochDay: Long, val goal: Int)

/** Сводка челленджа на сегодня. */
data class ChallengeStats(
    val todayTotal: Int,
    val todayGoal: Int,
    val streak: Int,
    val best: Int,
    /** Лучший результат за день. */
    val bestDay: Int,
    val totalAllTime: Long,
    val daysDone: Int,
)

object Challenges {
    /** Цель, действовавшая в этот день: последняя смена цели не позже дня; до первой — первая цель. */
    fun goalOn(epochDay: Long, changes: List<GoalChange>): Int {
        if (changes.isEmpty()) return 0
        val sorted = changes.sortedBy { it.fromEpochDay }
        return sorted.lastOrNull { it.fromEpochDay <= epochDay }?.goal ?: sorted.first().goal
    }

    /** Дни, когда цель выполнена (сумма за день ≥ цели того дня). */
    fun doneDays(totals: Map<Long, Int>, changes: List<GoalChange>): Set<LocalDate> =
        totals.filter { (day, total) ->
            val goal = goalOn(day, changes)
            goal > 0 && total >= goal
        }.keys.map { LocalDate.ofEpochDay(it) }.toSet()

    fun stats(totals: Map<Long, Int>, changes: List<GoalChange>, today: LocalDate): ChallengeStats {
        val done = doneDays(totals, changes)
        val todayDay = today.toEpochDay()
        return ChallengeStats(
            todayTotal = totals[todayDay] ?: 0,
            todayGoal = goalOn(todayDay, changes),
            streak = Streaks.daysInRow(done, today),
            best = Streaks.bestDaysInRow(done),
            bestDay = totals.values.maxOrNull() ?: 0,
            totalAllTime = totals.values.sumOf { it.toLong() },
            daysDone = done.size,
        )
    }

    /** Суммы за 7 дней, заканчивая [today] (слева — самый старый). */
    fun lastWeek(totals: Map<Long, Int>, today: LocalDate): List<Pair<LocalDate, Int>> =
        (6 downTo 0).map { back ->
            val d = today.minusDays(back.toLong())
            d to (totals[d.toEpochDay()] ?: 0)
        }
}
