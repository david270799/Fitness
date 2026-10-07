package com.iron.fitness.core.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Тип блока интервальной программы. */
@Serializable
enum class BlockType {
    @SerialName("warmup") WARMUP,
    @SerialName("work") WORK,
    @SerialName("rest") REST,
    @SerialName("cooldown") COOLDOWN,
    /** Повтор вложенных блоков [IntervalBlock.rounds] раз. */
    @SerialName("repeat") REPEAT,
}

/**
 * Блок программы. Для [BlockType.REPEAT] важны [rounds] и [children] (один уровень вложенности),
 * для остальных — [seconds] и подпись. [seconds] = 0 — блок без ограничения времени (до нажатия «Далее»).
 */
@Serializable
data class IntervalBlock(
    val type: BlockType,
    val seconds: Int = 0,
    val label: String? = null,
    val exerciseId: String? = null,
    val rounds: Int = 1,
    val children: List<IntervalBlock> = emptyList(),
    /** Пропускать последний отдых в последнем круге. */
    val skipLastRest: Boolean = true,
)

/** Один отрезок после разворачивания повторов. */
data class Phase(
    val type: BlockType,
    val seconds: Int,
    val label: String?,
    val exerciseId: String?,
    /** Номер круга (с 1) и всего кругов; для блоков вне повтора — 0 и 0. */
    val round: Int = 0,
    val rounds: Int = 0,
)

object Intervals {
    const val MAX_ROUNDS = 99

    /** Разворачивает программу в плоский список отрезков. Пустые (0 с) блоки внутри повторов пропускаются. */
    fun expand(blocks: List<IntervalBlock>): List<Phase> {
        val result = mutableListOf<Phase>()
        for (b in blocks) {
            if (b.type == BlockType.REPEAT) {
                val rounds = b.rounds.coerceIn(1, MAX_ROUNDS)
                val steps = b.children.filter { it.type != BlockType.REPEAT && it.seconds > 0 }
                if (steps.isEmpty()) continue
                for (r in 1..rounds) {
                    steps.forEachIndexed { i, c ->
                        val lastInRound = i == steps.lastIndex
                        if (r == rounds && lastInRound && c.type == BlockType.REST && b.skipLastRest && steps.size > 1) return@forEachIndexed
                        result += Phase(c.type, c.seconds, c.label, c.exerciseId, r, rounds)
                    }
                }
            } else {
                result += Phase(b.type, b.seconds.coerceAtLeast(0), b.label, b.exerciseId)
            }
        }
        return result
    }

    /** Общая длительность программы в секундах (блоки без ограничения времени не учитываются). */
    fun totalSeconds(blocks: List<IntervalBlock>): Int = expand(blocks).sumOf { it.seconds }

    /** Секунды по типам отрезков (для оценки калорий). */
    fun secondsByType(phases: List<Phase>): Map<BlockType, Int> =
        phases.groupBy { it.type }.mapValues { (_, list) -> list.sumOf { it.seconds } }

    /**
     * Оценка калорий интервальной тренировки: работа — [workMet], отдых — 2,5 (лёгкое движение),
     * разминка и заминка — 3,5.
     */
    fun kcal(spentSecByType: Map<BlockType, Long>, workMet: Double, bodyWeightKg: Double?): Double =
        spentSecByType.entries.sumOf { (type, sec) ->
            val met = when (type) {
                BlockType.WORK -> workMet
                BlockType.REST -> MET_REST
                BlockType.WARMUP, BlockType.COOLDOWN -> MET_EASY
                BlockType.REPEAT -> 0.0
            }
            Calories.kcal(met, bodyWeightKg, sec)
        }

    const val MET_REST = 2.5
    const val MET_EASY = 3.5
}
