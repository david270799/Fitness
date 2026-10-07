package com.iron.fitness.core.domain

/**
 * Сопоставление названия упражнения из ответа ассистента с библиотекой:
 * точное совпадение после нормализации, иначе — по доле общих слов.
 */
object NameMatch {
    fun normalize(s: String): String =
        s.lowercase()
            .replace('ё', 'е')
            .replace(Regex("[^\\p{L}\\p{Nd}]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")

    private val stop = setOf("the", "a", "with", "on", "and", "of", "на", "с", "со", "в", "и", "для")

    fun tokens(s: String): Set<String> = normalize(s).split(' ').filter { it.length > 1 && it !in stop }.toSet()

    /** Сходство 0…1: доля общих слов (коэффициент Дайса). */
    fun similarity(a: String, b: String): Double {
        val ta = tokens(a)
        val tb = tokens(b)
        if (ta.isEmpty() || tb.isEmpty()) return 0.0
        val common = ta.intersect(tb).size
        return 2.0 * common / (ta.size + tb.size)
    }

    /**
     * Лучший кандидат из [candidates] (id, английское имя, русское имя) или null, если сходство ниже [threshold].
     */
    fun best(
        nameEn: String?,
        nameRu: String?,
        candidates: List<Triple<String, String, String>>,
        threshold: Double = 0.6,
    ): String? {
        val en = nameEn?.let(::normalize).orEmpty()
        val ru = nameRu?.let(::normalize).orEmpty()
        if (en.isNotEmpty()) candidates.firstOrNull { normalize(it.second) == en }?.let { return it.first }
        if (ru.isNotEmpty()) candidates.firstOrNull { normalize(it.third) == ru }?.let { return it.first }
        var bestId: String? = null
        var bestScore = 0.0
        for ((id, cEn, cRu) in candidates) {
            val score = maxOf(
                if (nameEn.isNullOrBlank()) 0.0 else similarity(nameEn, cEn),
                if (nameRu.isNullOrBlank()) 0.0 else similarity(nameRu, cRu),
            )
            if (score > bestScore) {
                bestScore = score
                bestId = id
            }
        }
        return bestId.takeIf { bestScore >= threshold }
    }
}
