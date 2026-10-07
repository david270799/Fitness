package com.iron.fitness.feature.workouts.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {
    // ---------- Шаблоны ----------
    @Transaction
    @Query("SELECT * FROM routines ORDER BY sortOrder, updatedAt DESC")
    fun observeRoutines(): Flow<List<RoutineWithExercises>>

    @Transaction
    @Query("SELECT * FROM routines WHERE id = :id")
    suspend fun getRoutine(id: Long): RoutineWithExercises?

    @Insert
    suspend fun insertRoutine(routine: RoutineEntity): Long

    @Update
    suspend fun updateRoutine(routine: RoutineEntity)

    @Query("DELETE FROM routines WHERE id = :id")
    suspend fun deleteRoutine(id: Long)

    @Insert
    suspend fun insertRoutineExercises(items: List<RoutineExerciseEntity>)

    @Query("DELETE FROM routine_exercises WHERE routineId = :routineId")
    suspend fun deleteRoutineExercises(routineId: Long)

    // ---------- Тренировки ----------
    @Insert
    suspend fun insertWorkout(workout: WorkoutEntity): Long

    @Update
    suspend fun updateWorkout(workout: WorkoutEntity)

    @Query("DELETE FROM workouts WHERE id = :id")
    suspend fun deleteWorkout(id: Long)

    @Query("SELECT * FROM workouts WHERE id = :id")
    suspend fun getWorkout(id: Long): WorkoutEntity?

    @Query("SELECT * FROM workouts WHERE endedAt IS NULL AND type = 'STRENGTH' ORDER BY startedAt DESC LIMIT 1")
    fun observeActiveStrength(): Flow<WorkoutEntity?>

    @Query("SELECT * FROM workouts WHERE endedAt IS NULL AND type = 'STRENGTH' ORDER BY startedAt DESC LIMIT 1")
    suspend fun getActiveStrength(): WorkoutEntity?

    @Transaction
    @Query("SELECT * FROM workouts WHERE id = :id")
    fun observeFull(id: Long): Flow<WorkoutWithExercises?>

    @Transaction
    @Query("SELECT * FROM workouts WHERE id = :id")
    suspend fun getFull(id: Long): WorkoutWithExercises?

    @Query(
        """
        SELECT w.*,
          (SELECT COUNT(*) FROM workout_exercises e WHERE e.workoutId = w.id) AS exerciseCount,
          (SELECT COUNT(*) FROM workout_sets s WHERE s.workoutId = w.id AND s.completed = 1) AS setCount,
          (SELECT COUNT(*) FROM workout_sets s WHERE s.workoutId = w.id AND s.completed = 1
              AND (s.isWeightPr = 1 OR s.isRepsPr = 1 OR s.isOneRmPr = 1 OR s.isDurationPr = 1)) AS recordCount
        FROM workouts w
        WHERE w.endedAt IS NOT NULL
        ORDER BY w.startedAt DESC
        """,
    )
    fun observeHistory(): Flow<List<WorkoutSummaryRow>>

    @Query("SELECT * FROM workouts WHERE endedAt IS NOT NULL AND startedAt >= :from ORDER BY startedAt")
    fun observeFinishedSince(from: Long): Flow<List<WorkoutEntity>>

    @Query("SELECT routineId, MAX(startedAt) AS lastAt FROM workouts WHERE endedAt IS NOT NULL AND routineId IS NOT NULL GROUP BY routineId")
    fun observeRoutineLastUse(): Flow<List<RoutineLastUse>>

    @Query("SELECT * FROM workouts WHERE endedAt IS NOT NULL AND startedAt >= :from AND startedAt < :to ORDER BY startedAt")
    suspend fun finishedBetween(from: Long, to: Long): List<WorkoutEntity>

    // ---------- Упражнения и подходы в тренировке ----------
    @Insert
    suspend fun insertWorkoutExercise(item: WorkoutExerciseEntity): Long

    @Update
    suspend fun updateWorkoutExercise(item: WorkoutExerciseEntity)

    @Update
    suspend fun updateWorkoutExercises(items: List<WorkoutExerciseEntity>)

    @Query("DELETE FROM workout_exercises WHERE id = :id")
    suspend fun deleteWorkoutExercise(id: Long)

    @Query("SELECT * FROM workout_exercises WHERE workoutId = :workoutId ORDER BY position")
    suspend fun getWorkoutExercises(workoutId: Long): List<WorkoutExerciseEntity>

    @Insert
    suspend fun insertSet(set: WorkoutSetEntity): Long

    @Insert
    suspend fun insertSets(sets: List<WorkoutSetEntity>)

    @Update
    suspend fun updateSet(set: WorkoutSetEntity)

    @Update
    suspend fun updateSets(sets: List<WorkoutSetEntity>)

    @Delete
    suspend fun deleteSet(set: WorkoutSetEntity)

    @Query("SELECT * FROM workout_sets WHERE id = :id")
    suspend fun getSet(id: Long): WorkoutSetEntity?

    @Query("SELECT * FROM workout_sets WHERE workoutExerciseId = :workoutExerciseId ORDER BY position")
    suspend fun getSets(workoutExerciseId: Long): List<WorkoutSetEntity>

    @Query("DELETE FROM workout_sets WHERE workoutId = :workoutId AND completed = 0")
    suspend fun deleteIncompleteSets(workoutId: Long)

    @Query("DELETE FROM workout_exercises WHERE workoutId = :workoutId AND id NOT IN (SELECT workoutExerciseId FROM workout_sets WHERE workoutId = :workoutId)")
    suspend fun deleteEmptyExercises(workoutId: Long)

    /** Все выполненные подходы упражнения в завершённых тренировках (по времени). */
    @Query(
        """
        SELECT s.*, w.startedAt AS startedAt, w.name AS workoutName FROM workout_sets s
        JOIN workouts w ON w.id = s.workoutId
        WHERE s.exerciseId = :exerciseId AND s.completed = 1 AND w.endedAt IS NOT NULL
        ORDER BY w.startedAt, s.workoutExerciseId, s.position
        """,
    )
    suspend fun completedSetsForExercise(exerciseId: String): List<SetWithDate>

    @Query(
        """
        SELECT s.*, w.startedAt AS startedAt, w.name AS workoutName FROM workout_sets s
        JOIN workouts w ON w.id = s.workoutId
        WHERE s.exerciseId = :exerciseId AND s.completed = 1 AND w.endedAt IS NOT NULL
        ORDER BY w.startedAt, s.workoutExerciseId, s.position
        """,
    )
    fun observeCompletedSetsForExercise(exerciseId: String): Flow<List<SetWithDate>>

    /** Подходы прошлой (последней завершённой, кроме текущей) тренировки с этим упражнением. */
    @Query(
        """
        SELECT s.* FROM workout_sets s
        WHERE s.exerciseId = :exerciseId AND s.completed = 1 AND s.workoutId = (
            SELECT w.id FROM workouts w JOIN workout_sets s2 ON s2.workoutId = w.id
            WHERE s2.exerciseId = :exerciseId AND s2.completed = 1 AND w.endedAt IS NOT NULL AND w.id != :excludeWorkoutId
            ORDER BY w.startedAt DESC LIMIT 1
        )
        ORDER BY s.workoutExerciseId, s.position
        """,
    )
    suspend fun previousSets(exerciseId: String, excludeWorkoutId: Long): List<WorkoutSetEntity>

    @Query("SELECT DISTINCT exerciseId FROM workout_sets WHERE workoutId = :workoutId")
    suspend fun exerciseIdsInWorkout(workoutId: Long): List<String>

    /** Выполненные подходы по всем завершённым тренировкам за период (для статистики). */
    @Query(
        """
        SELECT s.*, w.startedAt AS startedAt, w.name AS workoutName FROM workout_sets s
        JOIN workouts w ON w.id = s.workoutId
        WHERE s.completed = 1 AND w.endedAt IS NOT NULL AND w.startedAt >= :from
        ORDER BY w.startedAt
        """,
    )
    fun observeCompletedSetsSince(from: Long): Flow<List<SetWithDate>>
}
