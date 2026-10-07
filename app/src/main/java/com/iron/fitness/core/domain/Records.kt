package com.iron.fitness.core.domain

/** Типы подходов. */
enum class SetType { NORMAL, WARMUP, DROP, FAILURE }

/** Минимальные данные подхода для расчёта рекордов. */
data class SetPerformance(
    val weightKg: Double?,
    val reps: Int?,
    val durationSec: Int?,
    val setType: SetType = SetType.NORMAL,
)

data class RecordFlags(
    val weight: Boolean = false,
    val reps: Boolean = false,
    val oneRm: Boolean = false,
    val duration: Boolean = false,
) {
    val any: Boolean get() = weight || reps || oneRm || duration
}

/** Лучшие результаты по упражнению. */
data class Bests(
    val maxWeight: Double = 0.0,
    val maxReps: Int = 0,
    val maxOneRm: Double = 0.0,
    val maxDuration: Int = 0,
    /** Максимум повторов для каждого веса (для рекорда «повторы при этом весе»). */
    val repsAtWeight: Map<Double, Int> = emptyMap(),
) {
    fun maxRepsAtLeast(weight: Double): Int =
        repsAtWeight.filterKeys { it >= weight - 1e-9 }.values.maxOrNull() ?: 0
}

object Records {
    /**
     * Проверяет подход на рекорд относительно [before] и возвращает флаги и обновлённые лучшие значения.
     * Разминочные подходы рекордами не считаются.
     */
    fun evaluate(set: SetPerformance, before: Bests, weighted: Boolean): Pair<RecordFlags, Bests> {
        if (set.setType == SetType.WARMUP) return RecordFlags() to before
        val w = set.weightKg ?: 0.0
        val r = set.reps ?: 0
        val d = set.durationSec ?: 0
        var flags = RecordFlags()
        var bests = before
        if (weighted && w > 0 && r > 0) {
            val oneRm = OneRepMax.epley(w, r)
            val isFirst = before.maxWeight <= 0.0
            flags = flags.copy(
                weight = !isFirst && w > before.maxWeight + 1e-9,
                oneRm = !isFirst && oneRm > before.maxOneRm + 1e-9,
                reps = !isFirst && (before.repsAtWeight[w]?.let { r > it } ?: false),
            )
            val prevAtW = before.repsAtWeight[w] ?: 0
            bests = bests.copy(
                maxWeight = maxOf(before.maxWeight, w),
                maxOneRm = maxOf(before.maxOneRm, oneRm),
                maxReps = maxOf(before.maxReps, r),
                repsAtWeight = before.repsAtWeight + (w to maxOf(prevAtW, r)),
            )
        } else if (!weighted && r > 0) {
            flags = flags.copy(reps = before.maxReps > 0 && r > before.maxReps)
            bests = bests.copy(maxReps = maxOf(before.maxReps, r))
        }
        if (d > 0) {
            flags = flags.copy(duration = before.maxDuration > 0 && d > before.maxDuration)
            bests = bests.copy(maxDuration = maxOf(before.maxDuration, d))
        }
        return flags to bests
    }

    /** Лучшие значения по списку подходов. */
    fun bestsOf(sets: List<SetPerformance>, weighted: Boolean): Bests =
        sets.fold(Bests()) { acc, s -> evaluate(s, acc, weighted).second }

    /** Флаги рекордов для подходов в хронологическом порядке. */
    fun flagsInOrder(sets: List<SetPerformance>, weighted: Boolean): List<RecordFlags> {
        var bests = Bests()
        return sets.map { s ->
            val (flags, next) = evaluate(s, bests, weighted)
            bests = next
            flags
        }
    }
}
