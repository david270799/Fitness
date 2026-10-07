package com.iron.fitness.feature.exercises.data

import android.content.Context
import androidx.room.withTransaction
import com.iron.fitness.core.db.IronDatabase
import com.iron.fitness.core.settings.SettingsRepository
import com.iron.fitness.feature.exercises.model.appCategory
import com.iron.fitness.feature.exercises.model.defaultRecordType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExerciseRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: IronDatabase,
    private val dao: ExerciseDao,
    private val settings: SettingsRepository,
    private val json: Json,
) {
    private val importMutex = Mutex()
    private val _libraryReady = MutableStateFlow(false)
    val libraryReady: StateFlow<Boolean> = _libraryReady.asStateFlow()

    fun observeAll(): Flow<List<ExerciseEntity>> = dao.observeAll()
    fun observe(id: String): Flow<ExerciseEntity?> = dao.observe(id)
    suspend fun get(id: String): ExerciseEntity? = dao.get(id)
    suspend fun getAll(ids: List<String>): List<ExerciseEntity> = if (ids.isEmpty()) emptyList() else dao.getAll(ids)
    suspend fun getAllOnce(): List<ExerciseEntity> = dao.getAllOnce()

    /**
     * Импорт библиотеки из assets при первом запуске или после обновления файла.
     * Пользовательские упражнения не трогаются; у библиотечных сохраняются
     * личные настройки (избранное, тип записи, отдых, заметка).
     */
    suspend fun ensureLibraryImported() = withContext(Dispatchers.IO) {
        importMutex.withLock {
            val current = settings.current()
            if (current.libraryVersion >= LIBRARY_VERSION && dao.libraryCount() > 0) {
                _libraryReady.value = true
                return@withLock
            }
            val text = context.assets.open(ASSET).bufferedReader(Charsets.UTF_8).use { it.readText() }
            val items = json.decodeFromString(ListSerializer(ExerciseJson.serializer()), text)
            val existing = dao.getLibrary().associateBy { it.id }
            val now = System.currentTimeMillis()
            val entities = items.map { j ->
                val old = existing[j.id]
                ExerciseEntity(
                    id = j.id,
                    name = j.name,
                    nameEn = j.nameEn,
                    category = appCategory(j.category),
                    sourceCategory = j.category,
                    force = j.force,
                    level = j.level,
                    mechanic = j.mechanic,
                    equipment = j.equipment,
                    primaryMuscles = j.primaryMuscles,
                    secondaryMuscles = j.secondaryMuscles,
                    instructions = j.instructions,
                    images = j.images,
                    isCustom = false,
                    recordType = old?.recordType ?: defaultRecordType(j.category, j.force, j.equipment),
                    note = old?.note,
                    restSeconds = old?.restSeconds,
                    met = old?.met,
                    isFavorite = old?.isFavorite ?: false,
                    createdAt = old?.createdAt ?: now,
                )
            }
            db.withTransaction { dao.insertAll(entities) }
            settings.setLibraryVersion(LIBRARY_VERSION)
            _libraryReady.value = true
        }
    }

    suspend fun setFavorite(id: String, favorite: Boolean) = dao.setFavorite(id, favorite)

    suspend fun updateUserSettings(id: String, recordType: RecordType, restSeconds: Int?, note: String?) =
        dao.updateUserSettings(id, recordType, restSeconds, note?.takeIf { it.isNotBlank() })

    suspend fun saveCustom(exercise: ExerciseEntity): String {
        val id = exercise.id.ifBlank { CUSTOM_PREFIX + UUID.randomUUID().toString() }
        dao.upsert(
            exercise.copy(
                id = id,
                isCustom = true,
                createdAt = if (exercise.createdAt == 0L) System.currentTimeMillis() else exercise.createdAt,
            ),
        )
        return id
    }

    suspend fun deleteCustom(id: String) = dao.deleteCustom(id)

    companion object {
        const val ASSET = "exercises_ru.json"
        /** Увеличивайте при обновлении assets/exercises_ru.json. */
        const val LIBRARY_VERSION = 1
        const val CUSTOM_PREFIX = "custom_"
        const val IMAGE_BASE_URL = "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/"

        fun imageUrl(path: String): String = IMAGE_BASE_URL + path
    }
}

/** Модель для картинки упражнения: своё фото (файл) или первая картинка библиотеки (URL). */
fun ExerciseEntity.thumbnailModel(): Any? =
    customImagePath?.let { java.io.File(it) } ?: images.firstOrNull()?.let { ExerciseRepository.imageUrl(it) }

fun ExerciseEntity.imageModels(): List<Any> {
    val list = mutableListOf<Any>()
    customImagePath?.let { list += java.io.File(it) }
    images.forEach { list += ExerciseRepository.imageUrl(it) }
    return list
}
