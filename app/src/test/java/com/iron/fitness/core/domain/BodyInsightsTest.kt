package com.iron.fitness.core.domain

import org.junit.Assert.assertTrue
import org.junit.Test

class BodyInsightsTest {
    private val base = InsightInput(
        workouts28 = 12, pushSets = 40, pullSets = 38, upperSets = 60, lowerSets = 40,
        records30 = 2, cardio14 = 3, stretch14 = 5,
    )

    private fun keys(i: InsightInput) = BodyInsights.analyze(i).map { it.key }.toSet()

    @Test
    fun balancedRegularTrainingIsAllStrengths() {
        val result = BodyInsights.analyze(base)
        assertTrue(result.all { it.kind == InsightKind.STRENGTH })
        assertTrue(InsightKey.FREQ_GOOD in keys(base))
        assertTrue(InsightKey.PUSH_PULL_BALANCED in keys(base))
        assertTrue(InsightKey.UPPER_LOWER_BALANCED in keys(base))
    }

    @Test
    fun pullAndLegsLagging() {
        val k = keys(base.copy(pushSets = 60, pullSets = 20, upperSets = 90, lowerSets = 10))
        assertTrue(InsightKey.PULL_LAGGING in k)
        assertTrue(InsightKey.LOWER_LAGGING in k)
    }

    @Test
    fun lowFrequencyAndNoCardioOrStretching() {
        val k = keys(base.copy(workouts28 = 4, cardio14 = 0, stretch14 = 0))
        assertTrue(InsightKey.FREQ_LOW in k)
        assertTrue(InsightKey.CARDIO_NONE in k)
        assertTrue(InsightKey.STRETCH_NONE in k)
    }

    @Test
    fun weightTrendRelativeToGoal() {
        val losing = base.copy(weightNow = 82.0, weightMonthAgo = 84.0, weightGoal = 78.0)
        assertTrue(InsightKey.WEIGHT_TOWARDS_GOAL in keys(losing))
        val gaining = base.copy(weightNow = 86.0, weightMonthAgo = 84.0, weightGoal = 78.0)
        assertTrue(InsightKey.WEIGHT_AWAY_FROM_GOAL in keys(gaining))
    }

    @Test
    fun waistChanges() {
        assertTrue(InsightKey.WAIST_DOWN in keys(base.copy(waistNow = 84.0, waistMonthAgo = 86.0)))
        assertTrue(InsightKey.WAIST_UP in keys(base.copy(waistNow = 89.0, waistMonthAgo = 86.0)))
    }

    @Test
    fun noPrWithManyWorkouts() {
        assertTrue(InsightKey.NO_PR in keys(base.copy(records30 = 0)))
    }
}
