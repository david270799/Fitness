package com.iron.fitness.core.domain

import kotlin.math.max

/**
 * Двойная прогрессия: держим вес, пока во всех рабочих подходах не достигнут верх
 * диапазона повторов; затем повышаем вес на минимальный шаг и возвращаемся к низу диапазона.
 */
object Progression {
    data class Suggestion(
        val weightKg: Double?,
        val reps: Int,
        val reason: Reason,
    )

    enum class Reason {
        /** Истории нет — начните с комфортного веса. */
        NO_HISTORY,
        /** Все подходы на верхней границе — добавляем вес. */
        INCREASE_WEIGHT,
        /** Добавляем повторы с тем же весом. */
        ADD_REPS,
        /** Подходы сильно не дотянули до диапазона — снизить вес. */
        DELOAD,
        /** Упражнение без веса — добавляем повторы. */
        BODYWEIGHT_REPS,
    }

    /**
     * @param lastSession рабочие подходы прошлой тренировки (без разминки)
     * @param repRange целевой диапазон повторов
     * @param increment шаг увеличения веса (кг)
     */
    fun suggest(
        lastSession: List<SetPerformance>,
        repRange: IntRange = 8..12,
        increment: Double = 2.5,
    ): Suggestion {
        val working = lastSession.filter { it.setType != SetType.WARMUP && (it.reps ?: 0) > 0 }
        if (working.isEmpty()) return Suggestion(null, repRange.first, Reason.NO_HISTORY)
        val weighted = working.any { (it.weightKg ?: 0.0) > 0.0 }
        if (!weighted) {
            val best = working.maxOf { it.reps ?: 0 }
            return Suggestion(null, best + 1, Reason.BODYWEIGHT_REPS)
        }
        // Ориентируемся на самый тяжёлый вес прошлой тренировки.
        val topWeight = working.maxOf { it.weightKg ?: 0.0 }
        val topSets = working.filter { (it.weightKg ?: 0.0) >= topWeight - 1e-9 }
        val minReps = topSets.minOf { it.reps ?: 0 }
        val maxReps = topSets.maxOf { it.reps ?: 0 }
        return when {
            minReps >= repRange.last -> Suggestion(roundToStep(topWeight + increment, increment), repRange.first, Reason.INCREASE_WEIGHT)
            maxReps < max(1, repRange.first - 2) -> Suggestion(
                roundToStep(max(increment, topWeight - increment), increment),
                repRange.first,
                Reason.DELOAD,
            )
            else -> Suggestion(topWeight, (minReps + 1).coerceIn(repRange.first, repRange.last), Reason.ADD_REPS)
        }
    }

    /** Разбор диапазона повторов из строки «8-12», «8–12», «10». */
    fun parseRange(text: String?, fallback: IntRange = 8..12): IntRange {
        if (text.isNullOrBlank()) return fallback
        val nums = Regex("\\d+").findAll(text).map { it.value.toInt() }.toList()
        return when (nums.size) {
            0 -> fallback
            1 -> nums[0]..nums[0]
            else -> minOf(nums[0], nums[1])..maxOf(nums[0], nums[1])
        }
    }

    /** Шаг веса по оборудованию: штанга — 2,5 кг (по 1,25 на сторону), гантели и гири — 2 кг, остальное — 2,5. */
    fun incrementFor(equipment: String?): Double = when (equipment) {
        "dumbbell", "kettlebells" -> 2.0
        "cable", "machine" -> 2.5
        else -> 2.5
    }

    private fun roundToStep(value: Double, step: Double): Double {
        if (step <= 0) return value
        return Math.round(value / step) * step
    }
}
