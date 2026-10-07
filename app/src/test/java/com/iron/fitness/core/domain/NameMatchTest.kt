package com.iron.fitness.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NameMatchTest {
    private val lib = listOf(
        Triple("Barbell_Bench_Press_-_Medium_Grip", "Barbell Bench Press - Medium Grip", "Жим штанги лёжа (средний хват)"),
        Triple("Pullups", "Pullups", "Подтягивания"),
        Triple("Barbell_Squat", "Barbell Squat", "Приседания со штангой"),
        Triple("Dumbbell_Bench_Press", "Dumbbell Bench Press", "Жим гантелей лёжа"),
    )

    @Test
    fun exactEnglishNameWins() {
        assertEquals("Pullups", NameMatch.best("pullups", null, lib))
        assertEquals("Barbell_Bench_Press_-_Medium_Grip", NameMatch.best("Barbell Bench Press - Medium Grip", "что угодно", lib))
    }

    @Test
    fun russianNameIgnoresYoAndCase() {
        assertEquals("Barbell_Bench_Press_-_Medium_Grip", NameMatch.best(null, "жим штанги лежа (средний хват)", lib))
    }

    @Test
    fun fuzzyByWords() {
        assertEquals("Barbell_Squat", NameMatch.best("Squat with barbell", null, lib))
        assertEquals("Dumbbell_Bench_Press", NameMatch.best(null, "Жим гантелей лёжа на скамье", lib))
    }

    @Test
    fun unknownReturnsNull() {
        assertNull(NameMatch.best("Underwater basket weaving", "Плетение корзин", lib))
    }
}
