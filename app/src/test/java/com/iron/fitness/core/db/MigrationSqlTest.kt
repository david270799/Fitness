package com.iron.fitness.core.db

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Сверяет SQL ручных миграций со схемой, которую Room экспортирует при сборке (app/schemas).
 * Если SQL разойдётся с сущностями, приложение упало бы при обновлении — тест ловит это заранее.
 */
class MigrationSqlTest {
    private fun database(version: Int): JsonObject {
        val file = File("schemas/com.iron.fitness.core.db.IronDatabase/$version.json")
        assertTrue("Нет схемы ${file.absolutePath}", file.exists())
        return Json.parseToJsonElement(file.readText()).jsonObject["database"]!!.jsonObject
    }

    private fun entity(db: JsonObject, table: String): JsonObject =
        db["entities"]!!.jsonArray.map { it.jsonObject }.first { it["tableName"]!!.jsonPrimitive.content == table }

    private fun createSql(db: JsonObject, table: String): String =
        entity(db, table)["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table)

    private fun indexSql(db: JsonObject, table: String): List<String> =
        entity(db, table)["indices"]?.jsonArray.orEmpty().map {
            it.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table)
        }

    /** Схема текущей версии: таблицы, созданные миграциями, с тех пор не менялись. */
    private fun current() = database(IronDatabase.VERSION)

    @Test
    fun intervalProgramsTableMatchesRoom() {
        val db = current()
        assertEquals(createSql(db, "interval_programs"), IronMigrations.CREATE_INTERVAL_PROGRAMS)
        val indices = entity(db, "interval_programs")["indices"]?.jsonArray
        assertTrue(indices == null || indices.isEmpty())
    }

    @Test
    fun workoutsIntensityColumnMatchesRoom() {
        val fields = entity(current(), "workouts")["fields"]!!.jsonArray.map { it.jsonObject }
        val f = fields.first { it["columnName"]!!.jsonPrimitive.content == "intensity" }
        assertEquals("TEXT", f["affinity"]!!.jsonPrimitive.content)
        // Room не записывает notNull = false (значение по умолчанию).
        assertEquals(false, f["notNull"]?.jsonPrimitive?.boolean ?: false)
        assertEquals("ALTER TABLE `workouts` ADD COLUMN `intensity` TEXT", IronMigrations.ADD_WORKOUT_INTENSITY)
    }

    @Test
    fun stretchRoutinesTableMatchesRoom() {
        val db = current()
        assertEquals(createSql(db, "stretch_routines"), IronMigrations.CREATE_STRETCH_ROUTINES)
    }

    @Test
    fun challengeTablesMatchRoom() {
        val db = current()
        assertEquals(createSql(db, "challenges"), IronMigrations.CREATE_CHALLENGES)
        assertEquals(createSql(db, "challenge_goals"), IronMigrations.CREATE_CHALLENGE_GOALS)
        assertEquals(createSql(db, "challenge_logs"), IronMigrations.CREATE_CHALLENGE_LOGS)
        assertEquals(listOf(IronMigrations.INDEX_CHALLENGE_GOALS), indexSql(db, "challenge_goals"))
        assertEquals(listOf(IronMigrations.INDEX_CHALLENGE_LOGS), indexSql(db, "challenge_logs"))
    }

    @Test
    fun migrationsCoverEveryVersionStep() {
        val steps = IronMigrations.ALL.map { it.startVersion to it.endVersion }
        for (v in 2 until IronDatabase.VERSION) {
            assertTrue("Нет миграции $v → ${v + 1}", (v to v + 1) in steps)
        }
    }
}
