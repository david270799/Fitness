package com.iron.fitness.feature.assistant

import com.iron.fitness.core.ai.GeminiClient
import com.iron.fitness.core.ai.InlineImage
import com.iron.fitness.core.ai.Schema
import com.iron.fitness.core.domain.DoseStatus
import com.iron.fitness.core.domain.NameMatch
import com.iron.fitness.core.domain.Progression
import com.iron.fitness.core.domain.SetType
import com.iron.fitness.core.settings.SettingsRepository
import com.iron.fitness.core.util.Fmt
import com.iron.fitness.feature.body.data.BodyMetric
import com.iron.fitness.feature.body.data.BodyRepository
import com.iron.fitness.feature.body.data.latestAndMonthAgo
import com.iron.fitness.feature.daily.data.ChallengeRepository
import com.iron.fitness.feature.exercises.data.ExerciseCategory
import com.iron.fitness.feature.exercises.data.ExerciseEntity
import com.iron.fitness.feature.exercises.data.ExerciseRepository
import com.iron.fitness.feature.reminders.data.ReminderRepository
import com.iron.fitness.feature.workouts.data.RoutineEntity
import com.iron.fitness.feature.workouts.data.RoutineExerciseEntity
import com.iron.fitness.feature.workouts.data.WorkoutRepository
import com.iron.fitness.feature.workouts.data.WorkoutType
import com.iron.fitness.feature.workouts.data.isRecord
import com.iron.fitness.feature.workouts.data.toPerformance
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class AiRoutine(val name: String = "", val notes: String? = null, val exercises: List<AiRoutineExercise> = emptyList())

@Serializable
data class AiRoutineExercise(
    val nameEn: String? = null,
    val nameRu: String = "",
    val sets: Int = 3,
    val reps: String? = null,
    val restSec: Int? = null,
    val note: String? = null,
)

@Serializable
data class AiLog(val title: String? = null, val durationMin: Int? = null, val exercises: List<AiLogExercise> = emptyList())

@Serializable
data class AiLogExercise(val nameEn: String? = null, val nameRu: String = "", val sets: List<AiSet> = emptyList())

@Serializable
data class AiSet(val weightKg: Double? = null, val reps: Int? = null, val seconds: Int? = null)

@Serializable
data class AiAlternatives(val alternatives: List<AiAlt> = emptyList())

@Serializable
data class AiAlt(val nameEn: String? = null, val nameRu: String = "", val reason: String? = null)

@Serializable
data class AiStretchPlan(val items: List<AiStretchItem> = emptyList())

@Serializable
data class AiStretchItem(val nameEn: String? = null, val nameRu: String = "", val seconds: Int = 30, val bothSides: Boolean = false)

/** Позиция ответа ассистента, сопоставленная с библиотекой (null — не нашли). */
data class Matched<T>(val ai: T, val exercise: ExerciseEntity?)

/** Объяснение прогрессии без ИИ. */
data class ProgressionExplanation(
    val exercise: ExerciseEntity,
    val lastSession: String?,
    val suggestion: Progression.Suggestion?,
    val range: IntRange,
    val increment: Double,
)

/**
 * Функции ассистента. Всё, что меняет данные, сначала возвращается пользователю на проверку —
 * сохранение только по кнопке на экране.
 */
@Singleton
class AssistantService @Inject constructor(
    private val gemini: GeminiClient,
    private val exercises: ExerciseRepository,
    private val workouts: WorkoutRepository,
    private val challenges: ChallengeRepository,
    private val reminders: ReminderRepository,
    private val body: BodyRepository,
    private val settings: SettingsRepository,
) {
    private val system = """
        Ты — тренер-ассистент приложения IRON для одного человека. Пиши по-русски, коротко и по делу.
        Единицы: кг, см, ккал. Не ставь диагнозов и не давай медицинских назначений; при боли советуй обратиться к врачу.
        Названия упражнений по возможности бери из открытой базы Free Exercise DB (поле nameEn — точно как в базе), nameRu — по-русски.
    """.trimIndent()

    private suspend fun candidates(category: ExerciseCategory? = null): List<Pair<Triple<String, String, String>, ExerciseEntity>> =
        exercises.getAllOnce()
            .filter { category == null || it.category == category }
            .map { Triple(it.id, it.nameEn, it.name) to it }

    private suspend fun <T> match(items: List<T>, en: (T) -> String?, ru: (T) -> String, category: ExerciseCategory? = null): List<Matched<T>> {
        val list = candidates(category)
        val triples = list.map { it.first }
        val byId = list.associate { it.first.first to it.second }
        return items.map { item -> Matched(item, NameMatch.best(en(item), ru(item), triples)?.let { byId[it] }) }
    }

    // ---------------- Тренировка по запросу ----------------

    suspend fun generateRoutine(goal: String, minutes: Int, equipment: List<String>, level: String): Pair<AiRoutine, List<Matched<AiRoutineExercise>>> {
        val schema = Schema.obj(
            "name" to Schema.str("Короткое название на русском"),
            "notes" to Schema.str("1–2 предложения о тренировке"),
            "exercises" to Schema.arr(
                Schema.obj(
                    "nameEn" to Schema.str("Название как в Free Exercise DB"),
                    "nameRu" to Schema.str(),
                    "sets" to Schema.int(nullable = false),
                    "reps" to Schema.str("Диапазон повторов, например 8-12"),
                    "restSec" to Schema.int(),
                    "note" to Schema.str(),
                    required = listOf("nameRu", "sets"),
                ),
            ),
            required = listOf("name", "exercises"),
        )
        val prompt = buildString {
            appendLine("Составь силовую тренировку.")
            appendLine("Цель: $goal")
            appendLine("Время: около $minutes минут, уровень: $level.")
            if (equipment.isNotEmpty()) appendLine("Доступное оборудование: ${equipment.joinToString()}.")
            appendLine("Дай 4–8 упражнений в порядке выполнения, с подходами, диапазоном повторов и отдыхом.")
        }
        val routine = gemini.generateJson(prompt, AiRoutine.serializer(), schema, system)
        return routine to match(routine.exercises, { it.nameEn }, { it.nameRu })
    }

    suspend fun saveRoutine(routine: AiRoutine, matched: List<Matched<AiRoutineExercise>>, defaultName: String): Long {
        val items = matched.filter { it.exercise != null }.mapIndexed { i, m ->
            RoutineExerciseEntity(
                routineId = 0,
                exerciseId = m.exercise!!.id,
                position = i,
                sets = m.ai.sets.coerceIn(1, 10),
                targetReps = m.ai.reps,
                restSeconds = m.ai.restSec?.coerceIn(15, 600),
                note = m.ai.note,
            )
        }
        return workouts.saveRoutine(RoutineEntity(name = routine.name.ifBlank { defaultName }, note = routine.notes), items)
    }

    // ---------------- Запись тренировки текстом или голосом ----------------

    suspend fun parseLog(text: String): Pair<AiLog, List<Matched<AiLogExercise>>> {
        val set = Schema.obj("weightKg" to Schema.num(), "reps" to Schema.int(), "seconds" to Schema.int())
        val schema = Schema.obj(
            "title" to Schema.str(),
            "durationMin" to Schema.int(),
            "exercises" to Schema.arr(
                Schema.obj(
                    "nameEn" to Schema.str(),
                    "nameRu" to Schema.str(),
                    "sets" to Schema.arr(set),
                    required = listOf("nameRu", "sets"),
                ),
            ),
            required = listOf("exercises"),
        )
        val prompt = """
            Разбери запись тренировки в структуру. Каждый подход — отдельный элемент sets.
            «3 по 8 по 80» = три подхода по 8 повторов с весом 80 кг. Для упражнений на время заполняй seconds.
            Ничего не придумывай: если чего-то нет в тексте, оставь поле пустым.
            Запись: $text
        """.trimIndent()
        val log = gemini.generateJson(prompt, AiLog.serializer(), schema, system, temperature = 0.0)
        return log to match(log.exercises, { it.nameEn }, { it.nameRu })
    }

    suspend fun saveLog(log: AiLog, matched: List<Matched<AiLogExercise>>, startedAt: Long, durationMin: Int, defaultName: String): Long {
        val items = matched.filter { it.exercise != null && it.ai.sets.isNotEmpty() }.map { m ->
            m.exercise!!.id to m.ai.sets.map { WorkoutRepository.LoggedSet(it.weightKg, it.reps, it.seconds) }
        }
        return workouts.saveLogged(
            name = log.title?.takeIf { it.isNotBlank() } ?: defaultName,
            startedAt = startedAt,
            durationSec = durationMin.coerceIn(1, 600) * 60L,
            exercises = items,
        )
    }

    // ---------------- Обзор недели ----------------

    /** Сводка, которая уйдёт ассистенту (показывается пользователю до отправки). */
    suspend fun weeklyContext(): String {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val from = today.minusDays(13).atStartOfDay(zone).toInstant().toEpochMilli()
        val all = workouts.observeFinishedSince(from).first()
        val split = today.minusDays(6)
        val thisWeek = all.filter { !Fmt.toLocalDate(it.startedAt).isBefore(split) }
        val prevWeek = all.filter { Fmt.toLocalDate(it.startedAt).isBefore(split) }
        val sets = workouts.observeCompletedSetsSince(split.atStartOfDay(zone).toInstant().toEpochMilli()).first()
        val exMap = exercises.getAllOnce().associateBy { it.id }
        val muscles = sets.filter { it.set.setType != SetType.WARMUP }
            .flatMap { exMap[it.set.exerciseId]?.primaryMuscles.orEmpty() }
            .groupingBy { it }.eachCount()
        val s = settings.current()
        return buildString {
            appendLine("Последние 7 дней (${Fmt.dateShort(split)}–${Fmt.dateShort(today)}):")
            appendLine("Тренировок: ${thisWeek.size} (неделей раньше: ${prevWeek.size}).")
            thisWeek.forEach { w ->
                append("- ${Fmt.dateShort(Fmt.toLocalDate(w.startedAt))}: ${w.type.label()} «${w.name}», ${w.durationSec / 60} мин")
                if (w.volumeKg > 0) append(", объём ${w.volumeKg.toInt()} кг")
                w.distanceKm?.let { append(", ${Fmt.num(it, 1)} км") }
                appendLine()
            }
            appendLine("Объём силовых: ${thisWeek.sumOf { it.volumeKg }.toInt()} кг (неделей раньше: ${prevWeek.sumOf { it.volumeKg }.toInt()} кг).")
            if (muscles.isNotEmpty()) appendLine("Рабочие подходы по мышцам: " + muscles.entries.sortedByDescending { it.value }.joinToString { "${it.key} ${it.value}" } + ".")
            appendLine("Рекордов: ${sets.count { it.set.isRecord }}.")
            val ch = challenges.observeActive().first()
            ch.forEach { c ->
                val done = (0..6).count { back ->
                    val d = today.minusDays(back.toLong()).toEpochDay()
                    val goal = com.iron.fitness.core.domain.Challenges.goalOn(d, c.goals)
                    goal > 0 && (c.totals[d] ?: 0) >= goal
                }
                appendLine("Челлендж «${c.challenge.name}»: цель выполнена $done из 7 дней, серия ${c.stats.streak}.")
            }
            val logs = reminders.observeLogsSince(split.atStartOfDay(zone).toInstant().toEpochMilli()).first()
            if (logs.isNotEmpty()) {
                appendLine("Приём витаминов/лекарств: принято ${logs.count { it.status == DoseStatus.TAKEN }}, пропущено ${logs.count { it.status == DoseStatus.MISSED }}.")
            }
            if (s.sendBodyDataToAssistant) {
                val ms = body.observeMeasurements().first()
                val (w, wBefore) = latestAndMonthAgo(ms, BodyMetric.WEIGHT, today.toEpochDay())
                w?.let { append("Вес: ${Fmt.num(it, 1)} кг"); wBefore?.let { b -> append(" (месяц назад ${Fmt.num(b, 1)})") }; appendLine(".") }
                s.weightGoalKg?.let { appendLine("Цель по весу: ${Fmt.num(it, 1)} кг.") }
                val (waist, waistBefore) = latestAndMonthAgo(ms, BodyMetric.WAIST, today.toEpochDay())
                waist?.let { append("Талия: ${Fmt.num(it, 1)} см"); waistBefore?.let { b -> append(" (было ${Fmt.num(b, 1)})") }; appendLine(".") }
            }
        }
    }

    suspend fun weeklyReview(context: String): String = gemini.generate(
        prompt = "Сделай обзор моей недели: что получилось, что подтянуть и 3 конкретных шага на следующую неделю. До 150 слов, без вступлений.\n\n$context",
        system = system,
        temperature = 0.6,
    )

    // ---------------- Замена упражнения ----------------

    suspend fun alternatives(exercise: ExerciseEntity, reason: String): List<Matched<AiAlt>> {
        val schema = Schema.obj(
            "alternatives" to Schema.arr(
                Schema.obj("nameEn" to Schema.str(), "nameRu" to Schema.str(), "reason" to Schema.str(), required = listOf("nameRu")),
            ),
            required = listOf("alternatives"),
        )
        val prompt = buildString {
            appendLine("Предложи 4 замены упражнения «${exercise.name}» (${exercise.nameEn}), которые нагружают те же мышцы: ${exercise.primaryMuscles.joinToString()}.")
            appendLine("Причина замены: $reason.")
            appendLine("Для каждой — одно короткое пояснение.")
        }
        val res = gemini.generateJson(prompt, AiAlternatives.serializer(), schema, system)
        return match(res.alternatives, { it.nameEn }, { it.nameRu }).filter { it.exercise?.id != exercise.id }
    }

    // ---------------- Растяжка ----------------

    suspend fun stretchFor(muscles: List<String>, phase: String): List<Matched<AiStretchItem>> {
        val schema = Schema.obj(
            "items" to Schema.arr(
                Schema.obj(
                    "nameEn" to Schema.str(),
                    "nameRu" to Schema.str(),
                    "seconds" to Schema.int(nullable = false),
                    "bothSides" to Schema.bool(),
                    required = listOf("nameRu", "seconds"),
                ),
            ),
            required = listOf("items"),
        )
        val prompt = buildString {
            appendLine("Подбери 5–7 упражнений растяжки из категории stretching Free Exercise DB.")
            appendLine("Когда: $phase.")
            if (muscles.isNotEmpty()) appendLine("Мышцы, которые работали: ${muscles.distinct().joinToString()}.")
            appendLine("Для каждого — время удержания в секундах и нужно ли делать на обе стороны.")
        }
        val res = gemini.generateJson(prompt, AiStretchPlan.serializer(), schema, system)
        return match(res.items, { it.nameEn }, { it.nameRu }, ExerciseCategory.STRETCHING)
    }

    suspend fun musclesOfLastStrength(): List<String> {
        val zone = ZoneId.systemDefault()
        val from = LocalDate.now().minusDays(3).atStartOfDay(zone).toInstant().toEpochMilli()
        val last = workouts.observeFinishedSince(from).first().filter { it.type == WorkoutType.STRENGTH }.maxByOrNull { it.startedAt }
            ?: return emptyList()
        val full = workouts.getFull(last.id) ?: return emptyList()
        val ids = full.exercises.map { it.exercise.exerciseId }
        return exercises.getAll(ids).flatMap { it.primaryMuscles }
    }

    // ---------------- Прогрессия ----------------

    suspend fun explainLocally(exerciseId: String): ProgressionExplanation? {
        val e = exercises.get(exerciseId) ?: return null
        val history = workouts.observeCompletedSetsForExercise(exerciseId).first()
        val lastWorkout = history.maxByOrNull { it.startedAt }?.set?.workoutId
        val last = history.filter { it.set.workoutId == lastWorkout && it.set.setType != SetType.WARMUP }.map { it.set }
        val range = Progression.parseRange(null)
        val inc = Progression.incrementFor(e.equipment)
        val suggestion = if (last.isEmpty()) null else Progression.suggest(last.map { it.toPerformance() }, range, inc)
        val lastText = last.takeIf { it.isNotEmpty() }?.joinToString(", ") { s ->
            val w = s.weightKg
            if (w != null && w > 0) "${Fmt.num(w)}×${s.reps ?: 0}" else "${s.reps ?: s.durationSec ?: 0}"
        }
        return ProgressionExplanation(e, lastText, suggestion, range, inc)
    }

    suspend fun explainWithAi(ex: ProgressionExplanation): String {
        val prompt = buildString {
            appendLine("Объясни простыми словами, как прогрессировать в упражнении «${ex.exercise.name}».")
            appendLine("Правило приложения — двойная прогрессия: диапазон ${ex.range.first}–${ex.range.last} повторов, шаг веса ${Fmt.num(ex.increment)} кг.")
            ex.lastSession?.let { appendLine("Прошлая тренировка: $it.") }
            appendLine("Дай 3–5 практичных советов, до 120 слов.")
        }
        return gemini.generate(prompt, system, temperature = 0.5)
    }

    // ---------------- InBody ----------------

    suspend fun readInBody(image: InlineImage): InBodyReading {
        val f = { Schema.obj("value" to Schema.num(), "uncertain" to Schema.bool(), required = listOf("uncertain")) }
        val schema = Schema.obj(
            "date" to Schema.str("Дата обследования в формате ГГГГ-ММ-ДД, если видна"),
            "weightKg" to f(), "skeletalMuscleKg" to f(), "bodyFatKg" to f(), "bodyFatPct" to f(),
            "bmi" to f(), "visceralFatLevel" to f(), "bmrKcal" to f(), "totalBodyWaterL" to f(),
            "leanMassKg" to f(), "inbodyScore" to f(), "ecwRatio" to f(),
            "armLeftLeanKg" to f(), "armRightLeanKg" to f(), "trunkLeanKg" to f(), "legLeftLeanKg" to f(), "legRightLeanKg" to f(),
            required = listOf("weightKg"),
        )
        val prompt = """
            На фото — распечатка InBody. Перепиши числа в поля. Если значение не видно или ты не уверен в цифре,
            поставь uncertain = true (значение можно оставить пустым). Ничего не придумывай и не пересчитывай.
        """.trimIndent()
        return gemini.generateJson(prompt, InBodyReading.serializer(), schema, system, listOf(image), temperature = 0.0)
    }

    suspend fun analyzeInBody(summary: String): String = gemini.generate(
        prompt = "Вот мои результаты InBody (и предыдущие, если есть). Кратко разбери состав тела и динамику, отметь 2–3 приоритета для тренировок и питания. До 150 слов. Не ставь диагнозов.\n\n$summary",
        system = system,
        temperature = 0.5,
    )
}

/** Подпись типа тренировки для контекста ассистента. */
private fun WorkoutType.label(): String = when (this) {
    WorkoutType.STRENGTH -> "силовая"
    WorkoutType.CARDIO -> "кардио"
    WorkoutType.INTERVAL -> "интервалы"
    WorkoutType.STRETCHING -> "растяжка"
}

@Serializable
data class InBodyField(val value: Double? = null, val uncertain: Boolean = false)

@Serializable
data class InBodyReading(
    val date: String? = null,
    val weightKg: InBodyField = InBodyField(),
    val skeletalMuscleKg: InBodyField = InBodyField(),
    val bodyFatKg: InBodyField = InBodyField(),
    val bodyFatPct: InBodyField = InBodyField(),
    val bmi: InBodyField = InBodyField(),
    val visceralFatLevel: InBodyField = InBodyField(),
    val bmrKcal: InBodyField = InBodyField(),
    val totalBodyWaterL: InBodyField = InBodyField(),
    val leanMassKg: InBodyField = InBodyField(),
    val inbodyScore: InBodyField = InBodyField(),
    val ecwRatio: InBodyField = InBodyField(),
    val armLeftLeanKg: InBodyField = InBodyField(),
    val armRightLeanKg: InBodyField = InBodyField(),
    val trunkLeanKg: InBodyField = InBodyField(),
    val legLeftLeanKg: InBodyField = InBodyField(),
    val legRightLeanKg: InBodyField = InBodyField(),
)
