package com.iron.fitness.feature.exercises.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Запись из assets/exercises_ru.json (сгенерирован tools/translate_exercises.py). */
@Serializable
data class ExerciseJson(
    val id: String,
    val name: String,
    @SerialName("name_en") val nameEn: String,
    val force: String? = null,
    val level: String? = null,
    val mechanic: String? = null,
    val equipment: String? = null,
    val primaryMuscles: List<String> = emptyList(),
    val secondaryMuscles: List<String> = emptyList(),
    val instructions: List<String> = emptyList(),
    val category: String,
    val images: List<String> = emptyList(),
)
