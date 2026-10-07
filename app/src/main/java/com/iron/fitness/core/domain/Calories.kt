package com.iron.fitness.core.domain

/**
 * Оценка калорий по MET: ккал = MET × вес тела (кг) × время (ч).
 * Значения MET — по Compendium of Physical Activities (2011/2024).
 * Это оценка, а не точное измерение.
 */
object Calories {
    const val DEFAULT_BODY_WEIGHT_KG = 75.0

    const val MET_STRENGTH_LIGHT = 3.5
    const val MET_STRENGTH_VIGOROUS = 6.0
    const val MET_STRETCHING = 2.3
    const val MET_CIRCUIT = 8.0
    const val MET_CARDIO_GENERIC = 7.0
    const val MET_PLYOMETRICS = 8.0

    fun kcal(met: Double, bodyWeightKg: Double?, durationSec: Long): Double {
        if (met <= 0 || durationSec <= 0) return 0.0
        val weight = bodyWeightKg?.takeIf { it > 0 } ?: DEFAULT_BODY_WEIGHT_KG
        return met * weight * (durationSec / 3600.0)
    }

    /**
     * MET силовой тренировки по плотности: рабочих подходов на 10 минут.
     * До 2 — лёгкая (3,5), от 4 — интенсивная (6,0), между — линейно.
     */
    fun strengthMet(workingSets: Int, durationSec: Long): Double {
        if (durationSec <= 0 || workingSets <= 0) return MET_STRENGTH_LIGHT
        val density = workingSets / (durationSec / 600.0)
        return when {
            density <= 2.0 -> MET_STRENGTH_LIGHT
            density >= 4.0 -> MET_STRENGTH_VIGOROUS
            else -> MET_STRENGTH_LIGHT + (density - 2.0) / 2.0 * (MET_STRENGTH_VIGOROUS - MET_STRENGTH_LIGHT)
        }
    }

    /** MET для категории упражнения (если у упражнения нет своего значения). */
    fun categoryMet(category: String): Double = when (category) {
        "STRENGTH" -> 5.0
        "CARDIO" -> MET_CARDIO_GENERIC
        "STRETCHING" -> MET_STRETCHING
        else -> 4.0
    }
}
