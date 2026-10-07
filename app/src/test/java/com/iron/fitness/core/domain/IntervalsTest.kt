package com.iron.fitness.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IntervalsTest {
    private fun work(s: Int, label: String = "Работа") = IntervalBlock(BlockType.WORK, s, label)
    private fun rest(s: Int) = IntervalBlock(BlockType.REST, s)

    @Test
    fun tabataExpandsToSixteenPhasesWithoutLastRest() {
        val tabata = listOf(IntervalBlock(BlockType.REPEAT, rounds = 8, children = listOf(work(20), rest(10))))
        val phases = Intervals.expand(tabata)
        assertEquals(15, phases.size)
        assertEquals(BlockType.WORK, phases.last().type)
        assertEquals(8 * 20 + 7 * 10, Intervals.totalSeconds(tabata))
        assertEquals(8, phases.last().round)
        assertEquals(8, phases.last().rounds)
    }

    @Test
    fun lastRestKeptWhenRequested() {
        val blocks = listOf(IntervalBlock(BlockType.REPEAT, rounds = 3, children = listOf(work(30), rest(30)), skipLastRest = false))
        assertEquals(6, Intervals.expand(blocks).size)
        assertEquals(180, Intervals.totalSeconds(blocks))
    }

    @Test
    fun warmupRepeatCooldownInOrder() {
        val blocks = listOf(
            IntervalBlock(BlockType.WARMUP, 300),
            IntervalBlock(BlockType.REPEAT, rounds = 2, children = listOf(work(60, "Быстро"), rest(120))),
            IntervalBlock(BlockType.COOLDOWN, 300),
        )
        val types = Intervals.expand(blocks).map { it.type }
        assertEquals(
            listOf(BlockType.WARMUP, BlockType.WORK, BlockType.REST, BlockType.WORK, BlockType.COOLDOWN),
            types,
        )
        val rounds = Intervals.expand(blocks).map { it.round }
        assertEquals(listOf(0, 1, 1, 2, 0), rounds)
    }

    @Test
    fun emptyAndNestedRepeatsIgnored() {
        val blocks = listOf(
            IntervalBlock(BlockType.REPEAT, rounds = 5, children = emptyList()),
            IntervalBlock(BlockType.REPEAT, rounds = 2, children = listOf(IntervalBlock(BlockType.REPEAT, rounds = 3), work(0), work(10))),
        )
        val phases = Intervals.expand(blocks)
        assertEquals(2, phases.size)
        assertTrue(phases.all { it.seconds == 10 })
    }

    @Test
    fun roundsClamped() {
        val blocks = listOf(IntervalBlock(BlockType.REPEAT, rounds = 1000, children = listOf(work(1))))
        assertEquals(Intervals.MAX_ROUNDS, Intervals.expand(blocks).size)
    }

    @Test
    fun secondsByTypeAndCalories() {
        val phases = Intervals.expand(
            listOf(IntervalBlock(BlockType.REPEAT, rounds = 2, children = listOf(work(60), rest(60)), skipLastRest = false)),
        )
        val byType = Intervals.secondsByType(phases)
        assertEquals(120, byType[BlockType.WORK])
        assertEquals(120, byType[BlockType.REST])
        // 8 MET × 80 кг × 2 мин + 2,5 MET × 80 кг × 2 мин
        val kcal = Intervals.kcal(byType.mapValues { it.value.toLong() }, 8.0, 80.0)
        assertEquals(8.0 * 80 * (120 / 3600.0) + 2.5 * 80 * (120 / 3600.0), kcal, 1e-9)
    }
}
