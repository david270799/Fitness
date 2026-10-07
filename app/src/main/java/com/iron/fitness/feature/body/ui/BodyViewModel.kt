package com.iron.fitness.feature.body.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iron.fitness.core.domain.BodyInsights
import com.iron.fitness.core.domain.Insight
import com.iron.fitness.core.domain.InsightInput
import com.iron.fitness.core.domain.SetType
import com.iron.fitness.core.settings.SettingsRepository
import com.iron.fitness.core.util.Fmt
import com.iron.fitness.feature.body.data.BodyMetric
import com.iron.fitness.feature.body.data.BodyRepository
import com.iron.fitness.feature.body.data.MeasurementEntity
import com.iron.fitness.feature.body.data.ProgressPhotoEntity
import com.iron.fitness.feature.body.data.latestAndMonthAgo
import com.iron.fitness.feature.exercises.data.ExerciseRepository
import com.iron.fitness.feature.workouts.data.WorkoutRepository
import com.iron.fitness.feature.workouts.data.WorkoutType
import com.iron.fitness.feature.workouts.data.isRecord
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class BodyState(
    val loading: Boolean = true,
    val measurements: List<MeasurementEntity> = emptyList(),
    val photos: List<ProgressPhotoEntity> = emptyList(),
    val weightGoal: Double? = null,
    val insights: List<Insight> = emptyList(),
)

@HiltViewModel
class BodyViewModel @Inject constructor(
    private val body: BodyRepository,
    private val settings: SettingsRepository,
    workouts: WorkoutRepository,
    exercises: ExerciseRepository,
) : ViewModel() {
    private val zone = ZoneId.systemDefault()
    private val since = LocalDate.now().minusDays(30).atStartOfDay(zone).toInstant().toEpochMilli()

    private val training = combine(
        workouts.observeFinishedSince(since),
        workouts.observeCompletedSetsSince(since),
        exercises.observeAll(),
    ) { ws, sets, ex -> Triple(ws, sets, ex.associateBy { it.id }) }

    val state: StateFlow<BodyState> = combine(
        body.observeMeasurements(),
        body.observePhotos(),
        settings.settings,
        training,
    ) { ms, photos, s, (ws, sets, exMap) ->
        val today = LocalDate.now()
        val d28 = today.minusDays(27)
        val d14 = today.minusDays(13)
        val w28 = ws.filter { !Fmt.toLocalDate(it.startedAt).isBefore(d28) }
        val w14 = ws.filter { !Fmt.toLocalDate(it.startedAt).isBefore(d14) }
        val working = sets.filter { it.set.setType != SetType.WARMUP && !Fmt.toLocalDate(it.startedAt).isBefore(d28) }
        var push = 0
        var pull = 0
        var upper = 0
        var lower = 0
        for (row in working) {
            val e = exMap[row.set.exerciseId] ?: continue
            when (e.force) {
                "push" -> push++
                "pull" -> pull++
            }
            val muscle = e.primaryMuscles.firstOrNull() ?: continue
            if (muscle in BodyInsights.UPPER) upper++
            if (muscle in BodyInsights.LOWER) lower++
        }
        val (weightNow, weightBefore) = latestAndMonthAgo(ms, BodyMetric.WEIGHT, today.toEpochDay())
        val (waistNow, waistBefore) = latestAndMonthAgo(ms, BodyMetric.WAIST, today.toEpochDay())
        val input = InsightInput(
            workouts28 = w28.size,
            pushSets = push,
            pullSets = pull,
            upperSets = upper,
            lowerSets = lower,
            records30 = sets.count { it.set.isRecord },
            cardio14 = w14.count { it.type == WorkoutType.CARDIO || it.type == WorkoutType.INTERVAL },
            stretch14 = w14.count { it.type == WorkoutType.STRETCHING },
            weightNow = weightNow,
            weightMonthAgo = weightBefore,
            weightGoal = s.weightGoalKg,
            waistNow = waistNow,
            waistMonthAgo = waistBefore,
        )
        BodyState(
            loading = false,
            measurements = ms,
            photos = photos,
            weightGoal = s.weightGoalKg,
            insights = BodyInsights.analyze(input),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BodyState())

    fun setGoal(kg: Double?) = viewModelScope.launch { settings.setWeightGoal(kg?.takeIf { it > 0 }) }
}
