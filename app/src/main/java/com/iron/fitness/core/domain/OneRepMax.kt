package com.iron.fitness.core.domain

import kotlin.math.roundToInt

/** Расчётный максимум на одно повторение. */
object OneRepMax {
    /** Формула Эпли: 1ПМ = вес × (1 + повторы / 30). Для одного повтора — сам вес. */
    fun epley(weightKg: Double, reps: Int): Double = when {
        weightKg <= 0.0 || reps <= 0 -> 0.0
        reps == 1 -> weightKg
        else -> weightKg * (1.0 + reps / 30.0)
    }

    /** Вес на заданное число повторов при известном 1ПМ (обратная формула Эпли). */
    fun weightForReps(oneRm: Double, reps: Int): Double = when {
        oneRm <= 0.0 || reps <= 0 -> 0.0
        reps == 1 -> oneRm
        else -> oneRm / (1.0 + reps / 30.0)
    }

    /** Сколько повторов можно сделать с весом при известном 1ПМ. */
    fun repsForWeight(oneRm: Double, weightKg: Double): Int {
        if (oneRm <= 0.0 || weightKg <= 0.0 || weightKg > oneRm) return 0
        if (weightKg == oneRm) return 1
        return ((oneRm / weightKg - 1.0) * 30.0).roundToInt().coerceAtLeast(1)
    }

    data class PercentRow(val percent: Int, val weightKg: Double, val reps: Int)

    /** Таблица 100 % … 50 % от 1ПМ с примерным числом повторов. */
    fun table(oneRm: Double): List<PercentRow> = (100 downTo 50 step 5).map { p ->
        val w = oneRm * p / 100.0
        PercentRow(p, w, if (p == 100) 1 else repsForWeight(oneRm, w))
    }
}
