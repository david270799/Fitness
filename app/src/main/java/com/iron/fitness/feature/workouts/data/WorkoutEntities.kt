package com.iron.fitness.feature.workouts.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.iron.fitness.core.domain.SetType

/** Вид тренировки в журнале. */
enum class WorkoutType { STRENGTH, CARDIO, INTERVAL, STRETCHING }

/** Шаблон силовой тренировки. */
@Entity(tableName = "routines")
data class RoutineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val note: String? = null,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "routine_exercises",
    foreignKeys = [ForeignKey(entity = RoutineEntity::class, parentColumns = ["id"], childColumns = ["routineId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("routineId")],
)
data class RoutineExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routineId: Long,
    val exerciseId: String,
    val position: Int,
    val sets: Int = 3,
    /** Целевые повторы: «8-12», «5» и т. п. */
    val targetReps: String? = null,
    val targetWeightKg: Double? = null,
    val targetSeconds: Int? = null,
    val restSeconds: Int? = null,
    /** Номер группы суперсета; соседние упражнения с одним номером — суперсет. */
    val supersetGroup: Int? = null,
    val note: String? = null,
)

data class RoutineWithExercises(
    @Embedded val routine: RoutineEntity,
    @Relation(parentColumn = "id", entityColumn = "routineId")
    val exercises: List<RoutineExerciseEntity>,
)

@Entity(tableName = "workouts", indices = [Index("startedAt"), Index("endedAt"), Index("type")])
data class WorkoutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: WorkoutType = WorkoutType.STRENGTH,
    val name: String,
    val routineId: Long? = null,
    val startedAt: Long,
    /** null — тренировка ещё идёт. */
    val endedAt: Long? = null,
    val durationSec: Long = 0,
    val volumeKg: Double = 0.0,
    val caloriesKcal: Double? = null,
    val note: String? = null,
    // Кардио
    val cardioType: String? = null,
    val distanceKm: Double? = null,
    /** Интенсивность кардио (LIGHT/MODERATE/VIGOROUS). */
    val intensity: String? = null,
    // Интервалы и растяжка
    val programId: Long? = null,
    val stretchPhase: String? = null,
    val linkedWorkoutId: Long? = null,
)

@Entity(
    tableName = "workout_exercises",
    foreignKeys = [ForeignKey(entity = WorkoutEntity::class, parentColumns = ["id"], childColumns = ["workoutId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("workoutId"), Index("exerciseId")],
)
data class WorkoutExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutId: Long,
    val exerciseId: String,
    val position: Int,
    val supersetGroup: Int? = null,
    val note: String? = null,
    val restSeconds: Int? = null,
    val targetReps: String? = null,
)

@Entity(
    tableName = "workout_sets",
    foreignKeys = [ForeignKey(entity = WorkoutExerciseEntity::class, parentColumns = ["id"], childColumns = ["workoutExerciseId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("workoutExerciseId"), Index("workoutId"), Index("exerciseId")],
)
data class WorkoutSetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutExerciseId: Long,
    val workoutId: Long,
    val exerciseId: String,
    val position: Int,
    val setType: SetType = SetType.NORMAL,
    val weightKg: Double? = null,
    val reps: Int? = null,
    val durationSec: Int? = null,
    val completed: Boolean = false,
    val completedAt: Long? = null,
    val isWeightPr: Boolean = false,
    val isRepsPr: Boolean = false,
    val isOneRmPr: Boolean = false,
    val isDurationPr: Boolean = false,
)

val WorkoutSetEntity.isRecord: Boolean
    get() = isWeightPr || isRepsPr || isOneRmPr || isDurationPr

data class WorkoutExerciseWithSets(
    @Embedded val exercise: WorkoutExerciseEntity,
    @Relation(parentColumn = "id", entityColumn = "workoutExerciseId")
    val sets: List<WorkoutSetEntity>,
)

data class WorkoutWithExercises(
    @Embedded val workout: WorkoutEntity,
    @Relation(entity = WorkoutExerciseEntity::class, parentColumn = "id", entityColumn = "workoutId")
    val exercises: List<WorkoutExerciseWithSets>,
)

/** Подход вместе с датой тренировки (для истории упражнения). */
data class SetWithDate(
    @Embedded val set: WorkoutSetEntity,
    val startedAt: Long,
    val workoutName: String,
)

/** Строка журнала. */
data class WorkoutSummaryRow(
    @Embedded val workout: WorkoutEntity,
    val exerciseCount: Int,
    val setCount: Int,
    val recordCount: Int,
)

/** Когда шаблон выполнялся последний раз. */
data class RoutineLastUse(
    val routineId: Long,
    val lastAt: Long,
)
