package com.iron.fitness.core.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class StretchingTest {
    private val labels = StretchLabels("левая", "правая", "Смена стороны", "Переход")

    @Test
    fun bothSidesSplitIntoTwoHoldsWithSwitch() {
        val phases = Stretching.phases(listOf(StretchItem("A", 30, bothSides = true)), { "Растяжка" }, labels)
        assertEquals(3, phases.size)
        assertEquals("Растяжка — левая", phases[0].label)
        assertEquals(BlockType.REST, phases[1].type)
        assertEquals(Stretching.SWITCH_SEC, phases[1].seconds)
        assertEquals("Растяжка — правая", phases[2].label)
        assertEquals("A", phases[2].exerciseId)
    }

    @Test
    fun transitionsOnlyBetweenItems() {
        val items = listOf(StretchItem("A", 30), StretchItem("B", 45), StretchItem("C", 0))
        val phases = Stretching.phases(items, { it }, labels)
        assertEquals(listOf(BlockType.WORK, BlockType.REST, BlockType.WORK), phases.map { it.type })
        assertEquals(30 + Stretching.TRANSITION_SEC + 45, phases.sumOf { it.seconds })
        assertEquals(phases.sumOf { it.seconds }, Stretching.totalSeconds(items))
    }

    @Test
    fun totalMatchesPhasesWithSides() {
        val items = listOf(StretchItem("A", 20, true), StretchItem("B", 40))
        assertEquals(Stretching.phases(items, { it }, labels).sumOf { it.seconds }, Stretching.totalSeconds(items))
        assertEquals(0, Stretching.totalSeconds(emptyList()))
    }
}
