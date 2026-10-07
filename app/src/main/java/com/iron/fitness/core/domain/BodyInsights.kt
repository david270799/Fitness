package com.iron.fitness.core.domain

/** Сильная сторона или зона роста. */
enum class InsightKind { STRENGTH, WEAKNESS }

/** Что именно заметили (подпись — в ресурсах). */
enum class InsightKey {
    FREQ_GOOD, FREQ_LOW,
    PULL_LAGGING, PUSH_PULL_BALANCED,
    LOWER_LAGGING, UPPER_LOWER_BALANCED,
    PR_PROGRESS, NO_PR,
    CARDIO_GOOD, CARDIO_NONE,
    STRETCH_GOOD, STRETCH_NONE,
    WEIGHT_TOWARDS_GOAL, WEIGHT_AWAY_FROM_GOAL,
    WAIST_DOWN, WAIST_UP,
}

data class Insight(val kind: InsightKind, val key: InsightKey, val value: Double? = null)

/** Данные для анализа (собираются из журнала и замеров). */
data class InsightInput(
    /** Тренировок любого вида за 28 дней. */
    val workouts28: Int,
    /** Рабочих подходов «жим» и «тяга» за 28 дней (по полю force упражнения). */
    val pushSets: Int,
    val pullSets: Int,
    /** Рабочих подходов на верх и низ тела за 28 дней. */
    val upperSets: Int,
    val lowerSets: Int,
    /** Рекордов за 30 дней. */
    val records30: Int,
    /** Кардио/интервалов и растяжек за 14 дней. */
    val cardio14: Int,
    val stretch14: Int,
    /** Текущий вес, вес месяц назад и цель. */
    val weightNow: Double? = null,
    val weightMonthAgo: Double? = null,
    val weightGoal: Double? = null,
    /** Талия сейчас и месяц назад. */
    val waistNow: Double? = null,
    val waistMonthAgo: Double? = null,
)

/**
 * Правила без ИИ: простые пороги по частоте, балансу и динамике.
 * Это подсказки для самоконтроля, а не медицинская оценка.
 */
object BodyInsights {
    val UPPER = setOf("chest", "shoulders", "triceps", "biceps", "forearms", "lats", "middle back", "traps", "neck")
    val LOWER = setOf("quadriceps", "hamstrings", "glutes", "calves", "adductors", "abductors")

    fun analyze(i: InsightInput): List<Insight> {
        val out = mutableListOf<Insight>()
        val perWeek = i.workouts28 / 4.0
        when {
            perWeek >= 3 -> out += Insight(InsightKind.STRENGTH, InsightKey.FREQ_GOOD, perWeek)
            perWeek < 2 -> out += Insight(InsightKind.WEAKNESS, InsightKey.FREQ_LOW, perWeek)
        }
        val strengthSets = i.pushSets + i.pullSets
        if (strengthSets >= 20) {
            val ratio = i.pushSets.toDouble() / i.pullSets.coerceAtLeast(1)
            if (ratio > 1.5) {
                out += Insight(InsightKind.WEAKNESS, InsightKey.PULL_LAGGING, ratio)
            } else if (ratio in 0.67..1.5) {
                out += Insight(InsightKind.STRENGTH, InsightKey.PUSH_PULL_BALANCED, ratio)
            }
        }
        if (i.upperSets + i.lowerSets >= 20) {
            val lowerShare = i.lowerSets.toDouble() / (i.upperSets + i.lowerSets)
            if (lowerShare < 0.25) {
                out += Insight(InsightKind.WEAKNESS, InsightKey.LOWER_LAGGING, lowerShare * 100)
            } else if (lowerShare in 0.3..0.6) {
                out += Insight(InsightKind.STRENGTH, InsightKey.UPPER_LOWER_BALANCED, lowerShare * 100)
            }
        }
        if (strengthSets > 0) {
            if (i.records30 > 0) {
                out += Insight(InsightKind.STRENGTH, InsightKey.PR_PROGRESS, i.records30.toDouble())
            } else if (i.workouts28 >= 6) {
                out += Insight(InsightKind.WEAKNESS, InsightKey.NO_PR)
            }
        }
        when {
            i.cardio14 >= 3 -> out += Insight(InsightKind.STRENGTH, InsightKey.CARDIO_GOOD, i.cardio14.toDouble())
            i.cardio14 == 0 -> out += Insight(InsightKind.WEAKNESS, InsightKey.CARDIO_NONE)
        }
        when {
            i.stretch14 >= 4 -> out += Insight(InsightKind.STRENGTH, InsightKey.STRETCH_GOOD, i.stretch14.toDouble())
            i.stretch14 == 0 -> out += Insight(InsightKind.WEAKNESS, InsightKey.STRETCH_NONE)
        }
        val now = i.weightNow
        val before = i.weightMonthAgo
        val goal = i.weightGoal
        if (now != null && before != null && goal != null && kotlin.math.abs(goal - before) > 0.3) {
            val change = now - before
            val needed = goal - before
            if (kotlin.math.abs(change) >= 0.3) {
                if (change * needed > 0) {
                    out += Insight(InsightKind.STRENGTH, InsightKey.WEIGHT_TOWARDS_GOAL, change)
                } else {
                    out += Insight(InsightKind.WEAKNESS, InsightKey.WEIGHT_AWAY_FROM_GOAL, change)
                }
            }
        }
        val wNow = i.waistNow
        val wBefore = i.waistMonthAgo
        if (wNow != null && wBefore != null) {
            val d = wNow - wBefore
            if (d <= -1.0) out += Insight(InsightKind.STRENGTH, InsightKey.WAIST_DOWN, d)
            if (d >= 2.0) out += Insight(InsightKind.WEAKNESS, InsightKey.WAIST_UP, d)
        }
        return out
    }
}
