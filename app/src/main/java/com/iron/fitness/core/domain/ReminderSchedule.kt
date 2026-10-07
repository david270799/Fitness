package com.iron.fitness.core.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Статус приёма. */
enum class DoseStatus { PENDING, TAKEN, MISSED, SKIPPED }

/**
 * Расписание напоминания: времена (минуты от полуночи) и дни недели битовой маской
 * (бит 0 — понедельник … бит 6 — воскресенье; 127 — каждый день).
 */
object ReminderSchedule {
    const val EVERY_DAY = 0b1111111
    const val WEEKDAYS = 0b0011111

    fun isDayEnabled(mask: Int, day: DayOfWeek): Boolean = mask and (1 shl (day.value - 1)) != 0

    fun toggleDay(mask: Int, day: DayOfWeek): Int = mask xor (1 shl (day.value - 1))

    /** Ближайший приём строго после [after]; null — если нет ни времени, ни дней. */
    fun next(times: List<Int>, mask: Int, after: LocalDateTime): LocalDateTime? {
        if (times.isEmpty() || mask and EVERY_DAY == 0) return null
        val sorted = times.distinct().sorted()
        for (dayOffset in 0..7) {
            val date = after.toLocalDate().plusDays(dayOffset.toLong())
            if (!isDayEnabled(mask, date.dayOfWeek)) continue
            for (m in sorted) {
                val at = LocalDateTime.of(date, LocalTime.of(m / 60, m % 60))
                if (at.isAfter(after)) return at
            }
        }
        return null
    }

    /** Все приёмы в этот день по расписанию. */
    fun dosesOn(times: List<Int>, mask: Int, date: LocalDate): List<LocalDateTime> =
        if (!isDayEnabled(mask, date.dayOfWeek)) emptyList()
        else times.distinct().sorted().map { LocalDateTime.of(date, LocalTime.of(it / 60, it % 60)) }

    /**
     * Соблюдение режима, %: принятые / все учтённые. Неотмеченные приёмы старше [graceMinutes]
     * считаются пропущенными; пропуски по решению пользователя (SKIPPED) не учитываются.
     */
    fun adherencePercent(
        logs: List<Pair<LocalDateTime, DoseStatus>>,
        now: LocalDateTime,
        graceMinutes: Long = 180,
    ): Int? {
        var taken = 0
        var counted = 0
        for ((at, status) in logs) {
            when (status) {
                DoseStatus.TAKEN -> { taken++; counted++ }
                DoseStatus.MISSED -> counted++
                DoseStatus.PENDING -> if (at.plusMinutes(graceMinutes).isBefore(now)) counted++
                DoseStatus.SKIPPED -> Unit
            }
        }
        if (counted == 0) return null
        return Math.round(taken * 100.0 / counted).toInt()
    }

    /** «Пн, Ср, Пт» / «Каждый день» / «Будни» — ключи для подписи. */
    fun isWeekdays(mask: Int) = mask and EVERY_DAY == WEEKDAYS
    fun isEveryDay(mask: Int) = mask and EVERY_DAY == EVERY_DAY
}
