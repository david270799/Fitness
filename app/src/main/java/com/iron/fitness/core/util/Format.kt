package com.iron.fitness.core.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

object Fmt {
    private val dayMonth = DateTimeFormatter.ofPattern("d MMMM", RU)
    private val dayMonthYear = DateTimeFormatter.ofPattern("d MMMM yyyy", RU)
    private val shortDate = DateTimeFormatter.ofPattern("dd.MM.yy", RU)
    private val dayMonthShort = DateTimeFormatter.ofPattern("d MMM", RU)
    private val time = DateTimeFormatter.ofPattern("HH:mm", RU)
    private val monthYear = DateTimeFormatter.ofPattern("LLLL yyyy", RU)

    /** 80 → «80», 82.5 → «82,5», 1.25 → «1,25». */
    fun num(value: Double, maxDecimals: Int = 2): String {
        if (value.isNaN() || value.isInfinite()) return "—"
        val factor = Math.pow(10.0, maxDecimals.toDouble())
        val rounded = (value * factor).roundToLong() / factor
        return if (rounded == rounded.toLong().toDouble()) {
            rounded.toLong().toString()
        } else {
            var s = String.format(RU, "%.${maxDecimals}f", rounded)
            while (s.endsWith("0")) s = s.dropLast(1)
            s.trimEnd(',', '.')
        }
    }

    fun num(value: Float, maxDecimals: Int = 2): String = num(value.toDouble(), maxDecimals)

    fun signed(value: Double, maxDecimals: Int = 1): String {
        val s = num(abs(value), maxDecimals)
        return when {
            value > 0.0001 -> "+$s"
            value < -0.0001 -> "−$s"
            else -> s
        }
    }

    /** Разбор числа, введённого пользователем: принимает и запятую, и точку. */
    fun parse(text: String): Double? = text.trim().replace(',', '.').replace(" ", "").toDoubleOrNull()

    /** 75 → «1:15», 3725 → «1:02:05». */
    fun duration(totalSeconds: Long): String {
        val s = if (totalSeconds < 0) 0 else totalSeconds
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) String.format(RU, "%d:%02d:%02d", h, m, sec) else String.format(RU, "%d:%02d", m, sec)
    }

    fun duration(totalSeconds: Int): String = duration(totalSeconds.toLong())

    /** Компактно: «45 мин», «1 ч 20 мин». */
    fun durationWords(totalSeconds: Long): String {
        val minutes = (totalSeconds / 60.0).roundToInt()
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h > 0 && m > 0 -> "$h ч $m мин"
            h > 0 -> "$h ч"
            totalSeconds in 1..59 -> "$totalSeconds с"
            else -> "$m мин"
        }
    }

    fun date(date: LocalDate): String = date.format(dayMonth)
    fun dateFull(date: LocalDate): String = date.format(dayMonthYear)
    fun dateShort(date: LocalDate): String = date.format(shortDate)
    fun dateCompact(date: LocalDate): String = date.format(dayMonthShort)
    fun monthYear(date: LocalDate): String = date.format(monthYear).replaceFirstChar { it.uppercase(RU) }
    fun time(dateTime: LocalDateTime): String = dateTime.format(time)
    fun time(epochMillis: Long): String = toLocalDateTime(epochMillis).format(time)

    fun dayOfWeek(date: LocalDate): String =
        date.dayOfWeek.getDisplayName(TextStyle.FULL_STANDALONE, RU).replaceFirstChar { it.uppercase(RU) }

    fun dayOfWeekShort(dayOfWeek: java.time.DayOfWeek): String =
        dayOfWeek.getDisplayName(TextStyle.SHORT_STANDALONE, RU).replaceFirstChar { it.uppercase(RU) }

    fun dateTime(epochMillis: Long): String {
        val dt = toLocalDateTime(epochMillis)
        return "${date(dt.toLocalDate())}, ${dt.format(time)}"
    }

    fun toLocalDateTime(epochMillis: Long): LocalDateTime =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault())

    fun toLocalDate(epochMillis: Long): LocalDate = toLocalDateTime(epochMillis).toLocalDate()
}
