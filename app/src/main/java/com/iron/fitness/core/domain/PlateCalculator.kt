package com.iron.fitness.core.domain

/** Калькулятор блинов: какие диски повесить на каждую сторону грифа. */
object PlateCalculator {
    data class Result(
        /** Диски на одну сторону, от тяжёлого к лёгкому. */
        val perSide: List<Double>,
        /** Вес, который реально получится. */
        val achievedKg: Double,
        /** Сколько не хватило до цели (если набор дисков не позволяет точно). */
        val remainderKg: Double,
    )

    /**
     * @param plates доступные диски (вес одного диска); у каждого веса считаем пары неограниченными,
     * если не задано [limits] — максимум дисков этого веса на одну сторону.
     */
    fun calculate(
        targetKg: Double,
        barKg: Double,
        plates: List<Double>,
        limits: Map<Double, Int> = emptyMap(),
    ): Result {
        if (targetKg <= barKg) return Result(emptyList(), barKg, 0.0)
        var remaining = (targetKg - barKg) / 2.0
        val result = mutableListOf<Double>()
        for (plate in plates.filter { it > 0 }.distinct().sortedDescending()) {
            var used = 0
            val limit = limits[plate] ?: Int.MAX_VALUE
            while (remaining + 1e-9 >= plate && used < limit) {
                result += plate
                remaining -= plate
                used++
            }
        }
        val achieved = barKg + result.sum() * 2
        return Result(result, achieved, targetKg - achieved)
    }
}
