package com.iron.fitness.feature.stretching

import com.iron.fitness.core.domain.StretchPhase
import com.iron.fitness.feature.stretching.data.StretchTemplates
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class StretchTemplatesTest {
    private val libraryIds: Set<String> by lazy {
        val text = File("src/main/assets/exercises_ru.json").readText()
        Json.parseToJsonElement(text).jsonArray.map { it.jsonObject["id"]!!.jsonPrimitive.content }.toSet()
    }

    @Test
    fun allTemplateExercisesExistInLibrary() {
        val missing = StretchTemplates.all.flatMap { t -> t.items.map { it.exerciseId } }.filter { it !in libraryIds }
        assertTrue("Нет в библиотеке: $missing", missing.isEmpty())
    }

    @Test
    fun suggestAfterPicksByMuscles() {
        assertEquals("after_upper", StretchTemplates.suggestAfter(listOf("chest", "triceps", "shoulders")).key)
        assertEquals("after_lower", StretchTemplates.suggestAfter(listOf("quadriceps", "glutes")).key)
        assertEquals(StretchPhase.AFTER, StretchTemplates.suggestAfter(emptyList()).phase)
    }
}
