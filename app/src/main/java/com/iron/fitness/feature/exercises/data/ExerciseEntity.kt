package com.iron.fitness.feature.exercises.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Категория в приложении (сопоставлена с категориями Free Exercise DB). */
enum class ExerciseCategory { STRENGTH, CARDIO, STRETCHING }

/** Как записывается подход. */
enum class RecordType { WEIGHT_REPS, REPS, TIME }

@Entity(
    tableName = "exercises",
    indices = [Index("category"), Index("name"), Index("isCustom")],
)
data class ExerciseEntity(
    @PrimaryKey val id: String,
    val name: String,
    val nameEn: String,
    val category: ExerciseCategory,
    val sourceCategory: String?,
    val force: String?,
    val level: String?,
    val mechanic: String?,
    val equipment: String?,
    val primaryMuscles: List<String>,
    val secondaryMuscles: List<String>,
    val instructions: List<String>,
    val images: List<String>,
    val isCustom: Boolean,
    val recordType: RecordType,
    val note: String? = null,
    val customImagePath: String? = null,
    val restSeconds: Int? = null,
    val met: Double? = null,
    val isFavorite: Boolean = false,
    val createdAt: Long = 0,
)
