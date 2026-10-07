package com.iron.fitness.core.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** Серии: дни и недели подряд с выполненной активностью. */
object Streaks {
    /**
     * Текущая серия дней подряд. Если сегодня ещё не выполнено, серия не обрывается —
     * считаем от вчерашнего дня.
     */
    fun daysInRow(done: Set<LocalDate>, today: LocalDate): Int {
        var day = if (today in done) today else today.minusDays(1)
        var count = 0
        while (day in done) {
            count++
            day = day.minusDays(1)
        }
        return count
    }

    /** Самая длинная серия дней подряд за всё время. */
    fun bestDaysInRow(done: Set<LocalDate>): Int {
        if (done.isEmpty()) return 0
        var best = 0
        for (d in done) {
            // Начало серии — день, перед которым ничего не было.
            if (d.minusDays(1) in done) continue
            var len = 0
            var cur = d
            while (cur in done) {
                len++
                cur = cur.plusDays(1)
            }
            if (len > best) best = len
        }
        return best
    }

    /**
     * Недели подряд (с понедельника) с хотя бы одной активностью. Текущая неделя без активности
     * серию не обрывает.
     */
    fun weeksInRow(dates: Collection<LocalDate>, today: LocalDate): Int {
        val weeks = dates.map { weekStart(it) }.toSet()
        var week = weekStart(today)
        if (week !in weeks) week = week.minusWeeks(1)
        var count = 0
        while (week in weeks) {
            count++
            week = week.minusWeeks(1)
        }
        return count
    }

    fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
}
