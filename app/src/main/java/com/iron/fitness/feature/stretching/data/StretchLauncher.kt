package com.iron.fitness.feature.stretching.data

import android.content.Context
import com.iron.fitness.R
import com.iron.fitness.core.domain.StretchItem
import com.iron.fitness.core.domain.StretchLabels
import com.iron.fitness.core.domain.StretchPhase
import com.iron.fitness.core.domain.Stretching
import com.iron.fitness.core.util.withRussianLocale
import com.iron.fitness.feature.cardio.run.ProgramRunner
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Запуск растяжки в общем таймере программ. */
@Singleton
class StretchLauncher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: StretchRepository,
    private val runner: ProgramRunner,
) {
    private val res get() = context.withRussianLocale().resources

    /** false — уже идёт другая программа. */
    suspend fun start(
        name: String,
        phase: StretchPhase,
        items: List<StretchItem>,
        routineId: Long? = null,
        linkedWorkoutId: Long? = null,
    ): Boolean {
        if (runner.isRunning) return false
        val names = repo.names(items.map { it.exerciseId })
        val r = res
        val labels = StretchLabels(
            left = r.getString(R.string.stretch_left),
            right = r.getString(R.string.stretch_right),
            switchSides = r.getString(R.string.stretch_switch),
            transition = r.getString(R.string.stretch_transition),
        )
        val phases = Stretching.phases(items, { id -> names[id] ?: id.replace('_', ' ') }, labels)
        if (phases.isEmpty()) return true
        val link = linkedWorkoutId ?: repo.linkTarget(phase)
        runner.startStretch(name, phases, phase, link, routineId)
        return true
    }

    suspend fun startTemplate(t: StretchTemplate, linkedWorkoutId: Long? = null): Boolean =
        start(res.getString(t.name), t.phase, t.items, null, linkedWorkoutId)

    suspend fun startRoutine(r: StretchRoutine): Boolean = start(r.name, r.phase, r.items, r.id)

    /** Заминка, подобранная под мышцы силовой тренировки. */
    suspend fun startAfterWorkout(workoutId: Long): Boolean {
        val t = StretchTemplates.suggestAfter(repo.musclesOfWorkout(workoutId))
        return startTemplate(t, linkedWorkoutId = workoutId)
    }

    val isBusy: Boolean get() = runner.isRunning
}
