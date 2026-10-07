package com.iron.fitness.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OneRepMaxTest {
    @Test fun epleySingleRepIsWeight() = assertEquals(100.0, OneRepMax.epley(100.0, 1), 1e-9)

    @Test fun epleyFormula() {
        // 80 × (1 + 8/30) = 101,33
        assertEquals(101.333, OneRepMax.epley(80.0, 8), 1e-3)
        assertEquals(120.0, OneRepMax.epley(100.0, 6), 1e-9)
    }

    @Test fun epleyInvalidInputs() {
        assertEquals(0.0, OneRepMax.epley(0.0, 5), 0.0)
        assertEquals(0.0, OneRepMax.epley(50.0, 0), 0.0)
    }

    @Test fun inverseFormulaRoundTrip() {
        val oneRm = OneRepMax.epley(90.0, 5)
        assertEquals(90.0, OneRepMax.weightForReps(oneRm, 5), 1e-9)
        assertEquals(5, OneRepMax.repsForWeight(oneRm, 90.0))
    }

    @Test fun percentTable() {
        val table = OneRepMax.table(100.0)
        assertEquals(11, table.size)
        assertEquals(100, table.first().percent)
        assertEquals(1, table.first().reps)
        assertEquals(50.0, table.last().weightKg, 1e-9)
        assertEquals(30, table.last().reps)
    }
}

class RecordsTest {
    private fun s(w: Double, r: Int, t: SetType = SetType.NORMAL) = SetPerformance(w, r, null, t)

    @Test fun firstEverSetIsNotRecord() {
        val (flags, bests) = Records.evaluate(s(60.0, 10), Bests(), weighted = true)
        assertFalse(flags.any)
        assertEquals(60.0, bests.maxWeight, 0.0)
    }

    @Test fun heavierWeightIsWeightRecord() {
        val before = Records.bestsOf(listOf(s(80.0, 5)), true)
        val (flags, _) = Records.evaluate(s(82.5, 3), before, true)
        assertTrue(flags.weight)
        assertFalse(flags.oneRm) // 82,5×3 = 90,75 < 80×5 = 93,3
    }

    @Test fun moreRepsAtSameWeightIsRepsRecord() {
        val before = Records.bestsOf(listOf(s(80.0, 5), s(70.0, 10)), true)
        val (flags, _) = Records.evaluate(s(80.0, 6), before, true)
        assertTrue(flags.reps)
        assertTrue(flags.oneRm)
        assertFalse(flags.weight)
        // 10 повторов с 70 не делают рекорд «повторы» для 75 кг недостижимым:
        val (flags75, _) = Records.evaluate(s(75.0, 6), before, true)
        assertFalse(flags75.reps)
    }

    @Test fun warmupNeverRecord() {
        val before = Records.bestsOf(listOf(s(60.0, 5)), true)
        val (flags, after) = Records.evaluate(s(100.0, 5, SetType.WARMUP), before, true)
        assertFalse(flags.any)
        assertEquals(60.0, after.maxWeight, 0.0)
    }

    @Test fun flagsInOrderMarksOnlyImprovements() {
        val flags = Records.flagsInOrder(listOf(s(60.0, 5), s(65.0, 5), s(65.0, 5), s(70.0, 3)), true)
        assertEquals(listOf(false, true, false, true), flags.map { it.weight })
    }

    @Test fun bodyweightReps() {
        val before = Records.bestsOf(listOf(SetPerformance(null, 20, null)), weighted = false)
        assertTrue(Records.evaluate(SetPerformance(null, 21, null), before, false).first.reps)
        assertFalse(Records.evaluate(SetPerformance(null, 20, null), before, false).first.reps)
    }
}

class ProgressionTest {
    private fun s(w: Double?, r: Int) = SetPerformance(w, r, null)

    @Test fun noHistory() {
        val sug = Progression.suggest(emptyList())
        assertNull(sug.weightKg)
        assertEquals(Progression.Reason.NO_HISTORY, sug.reason)
    }

    @Test fun allSetsAtTopIncreaseWeight() {
        val sug = Progression.suggest(listOf(s(60.0, 12), s(60.0, 12), s(60.0, 12)), 8..12, 2.5)
        assertEquals(Progression.Reason.INCREASE_WEIGHT, sug.reason)
        assertEquals(62.5, sug.weightKg!!, 1e-9)
        assertEquals(8, sug.reps)
    }

    @Test fun notAllAtTopAddReps() {
        val sug = Progression.suggest(listOf(s(60.0, 12), s(60.0, 10), s(60.0, 9)), 8..12, 2.5)
        assertEquals(Progression.Reason.ADD_REPS, sug.reason)
        assertEquals(60.0, sug.weightKg!!, 1e-9)
        assertEquals(10, sug.reps)
    }

    @Test fun farBelowRangeDeload() {
        val sug = Progression.suggest(listOf(s(100.0, 4), s(100.0, 3)), 8..12, 2.5)
        assertEquals(Progression.Reason.DELOAD, sug.reason)
        assertEquals(97.5, sug.weightKg!!, 1e-9)
    }

    @Test fun bodyweightAddsRep() {
        val sug = Progression.suggest(listOf(s(null, 15), s(null, 12)))
        assertEquals(Progression.Reason.BODYWEIGHT_REPS, sug.reason)
        assertEquals(16, sug.reps)
    }

    @Test fun warmupsIgnored() {
        val sug = Progression.suggest(
            listOf(SetPerformance(40.0, 12, null, SetType.WARMUP), s(60.0, 12), s(60.0, 12)),
            8..12,
        )
        assertEquals(62.5, sug.weightKg!!, 1e-9)
    }

    @Test fun parseRange() {
        assertEquals(8..12, Progression.parseRange("8-12"))
        assertEquals(6..10, Progression.parseRange("10–6"))
        assertEquals(5..5, Progression.parseRange("5"))
        assertEquals(8..12, Progression.parseRange(null))
    }
}

class PlateCalculatorTest {
    private val plates = listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25)

    @Test fun exactWeight() {
        val r = PlateCalculator.calculate(100.0, 20.0, plates)
        assertEquals(listOf(25.0, 15.0), r.perSide)
        assertEquals(100.0, r.achievedKg, 1e-9)
        assertEquals(0.0, r.remainderKg, 1e-9)
    }

    @Test fun smallIncrements() {
        val r = PlateCalculator.calculate(62.5, 20.0, plates)
        assertEquals(listOf(20.0, 1.25), r.perSide)
    }

    @Test fun belowBar() {
        val r = PlateCalculator.calculate(15.0, 20.0, plates)
        assertTrue(r.perSide.isEmpty())
        assertEquals(20.0, r.achievedKg, 0.0)
    }

    @Test fun remainderWhenImpossible() {
        val r = PlateCalculator.calculate(61.0, 20.0, plates)
        assertEquals(60.0, r.achievedKg, 1e-9)
        assertEquals(1.0, r.remainderKg, 1e-9)
    }

    @Test fun respectsLimits() {
        val r = PlateCalculator.calculate(120.0, 20.0, listOf(20.0, 10.0), mapOf(20.0 to 1))
        assertEquals(listOf(20.0, 10.0, 10.0, 10.0), r.perSide)
    }
}

class CaloriesTest {
    @Test fun metFormula() {
        // 6 MET × 80 кг × 1 ч = 480 ккал
        assertEquals(480.0, Calories.kcal(6.0, 80.0, 3600), 1e-9)
        // 30 минут растяжки при 75 кг: 2,3 × 75 × 0,5 = 86,25
        assertEquals(86.25, Calories.kcal(Calories.MET_STRETCHING, 75.0, 1800), 1e-9)
    }

    @Test fun defaultWeightWhenUnknown() {
        assertEquals(Calories.kcal(4.0, 75.0, 600), Calories.kcal(4.0, null, 600), 1e-9)
        assertEquals(Calories.kcal(4.0, 75.0, 600), Calories.kcal(4.0, 0.0, 600), 1e-9)
    }

    @Test fun zeroDuration() = assertEquals(0.0, Calories.kcal(5.0, 80.0, 0), 0.0)

    @Test fun strengthDensity() {
        assertEquals(3.5, Calories.strengthMet(10, 3600), 1e-9) // 1,7 подхода / 10 мин
        assertEquals(6.0, Calories.strengthMet(30, 3600), 1e-9) // 5 / 10 мин
        assertEquals(4.75, Calories.strengthMet(18, 3600), 1e-9) // 3 / 10 мин
    }
}
