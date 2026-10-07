package com.iron.fitness.core.domain

import kotlinx.serialization.Serializable

/** Когда выполняется растяжка. */
enum class StretchPhase { BEFORE, AFTER, ANY }

/** Позиция комплекса растяжки: упражнение, удержание, обе ли стороны. */
@Serializable
data class StretchItem(
    val exerciseId: String,
    val seconds: Int = 30,
    val bothSides: Boolean = false,
)

/** Подписи для отрезков растяжки (из ресурсов). */
data class StretchLabels(
    val left: String,
    val right: String,
    val switchSides: String,
    val transition: String,
)

object Stretching {
    /** Пауза на смену стороны и переход к следующему упражнению. */
    const val SWITCH_SEC = 5
    const val TRANSITION_SEC = 8

    /** MET растяжки по Compendium (лёгкая растяжка, йога-хатха ≈ 2,3–2,5). */
    const val MET = 2.3

    /**
     * Комплекс → отрезки таймера: удержание (работа), смена стороны и переход (отдых).
     * Перед первым упражнением перехода нет; после последнего — тоже.
     */
    fun phases(items: List<StretchItem>, nameOf: (String) -> String, labels: StretchLabels): List<Phase> {
        val result = mutableListOf<Phase>()
        val valid = items.filter { it.seconds > 0 }
        valid.forEachIndexed { i, item ->
            val name = nameOf(item.exerciseId)
            if (i > 0) result += Phase(BlockType.REST, TRANSITION_SEC, labels.transition, null)
            if (item.bothSides) {
                result += Phase(BlockType.WORK, item.seconds, "$name — ${labels.left}", item.exerciseId)
                result += Phase(BlockType.REST, SWITCH_SEC, labels.switchSides, null)
                result += Phase(BlockType.WORK, item.seconds, "$name — ${labels.right}", item.exerciseId)
            } else {
                result += Phase(BlockType.WORK, item.seconds, name, item.exerciseId)
            }
        }
        return result
    }

    fun totalSeconds(items: List<StretchItem>): Int {
        val valid = items.filter { it.seconds > 0 }
        if (valid.isEmpty()) return 0
        val holds = valid.sumOf { if (it.bothSides) it.seconds * 2 + SWITCH_SEC else it.seconds }
        return holds + (valid.size - 1) * TRANSITION_SEC
    }
}
