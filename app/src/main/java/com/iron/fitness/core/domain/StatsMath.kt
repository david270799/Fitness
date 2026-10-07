package com.iron.fitness.core.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class Granularity { DAY, WEEK, MONTH }

/** Период статистики и шаг столбиков. */
enum class StatsPeriod(val granularity: Granularity, val buckets: Int) {
    WEEK(Granularity.DAY, 7),
    MONTH(Granularity.WEEK, 5),
    QUARTER(Granularity.WEEK, 13),
    YEAR(Granularity.MONTH, 12),
}

object StatsMath {
    /** Начало интервала, в который попадает дата. */
    fun bucketOf(date: LocalDate, g: Granularity): LocalDate = when (g) {
        Granularity.DAY -> date
        Granularity.WEEK -> Streaks.weekStart(date)
        Granularity.MONTH -> date.withDayOfMonth(1)
    }

    /** Начала интервалов периода, от старых к новым; последний содержит [today]. */
    fun bucketStarts(period: StatsPeriod, today: LocalDate): List<LocalDate> {
        val last = bucketOf(today, period.granularity)
        return (period.buckets - 1 downTo 0).map { back ->
            when (period.granularity) {
                Granularity.DAY -> last.minusDays(back.toLong())
                Granularity.WEEK -> last.minusWeeks(back.toLong())
                Granularity.MONTH -> last.minusMonths(back.toLong())
            }
        }
    }

    fun periodStart(period: StatsPeriod, today: LocalDate): LocalDate = bucketStarts(period, today).first()

    /** Сумма значений по интервалам периода (пустые интервалы — 0). */
    fun <T> aggregate(
        items: List<T>,
        dateOf: (T) -> LocalDate,
        valueOf: (T) -> Double,
        period: StatsPeriod,
        today: LocalDate,
    ): List<Pair<LocalDate, Double>> {
        val starts = bucketStarts(period, today)
        val sums = items.groupBy { bucketOf(dateOf(it), period.granularity) }
            .mapValues { (_, list) -> list.sumOf(valueOf) }
        return starts.map { it to (sums[it] ?: 0.0) }
    }

    /** Дней в периоде до сегодняшнего включительно (для средних «в неделю»). */
    fun daysCovered(period: StatsPeriod, today: LocalDate): Long =
        ChronoUnit.DAYS.between(periodStart(period, today), today) + 1
}
