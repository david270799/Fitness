package com.iron.fitness.feature.exercises.model

import androidx.annotation.StringRes
import com.iron.fitness.R
import com.iron.fitness.feature.exercises.data.ExerciseCategory
import com.iron.fitness.feature.exercises.data.RecordType

/** Группы мышц: ключ как в Free Exercise DB, подпись — из strings.xml. */
enum class Muscle(val key: String, @StringRes val label: Int) {
    CHEST("chest", R.string.muscle_chest),
    SHOULDERS("shoulders", R.string.muscle_shoulders),
    TRICEPS("triceps", R.string.muscle_triceps),
    BICEPS("biceps", R.string.muscle_biceps),
    FOREARMS("forearms", R.string.muscle_forearms),
    LATS("lats", R.string.muscle_lats),
    MIDDLE_BACK("middle back", R.string.muscle_middle_back),
    LOWER_BACK("lower back", R.string.muscle_lower_back),
    TRAPS("traps", R.string.muscle_traps),
    NECK("neck", R.string.muscle_neck),
    ABDOMINALS("abdominals", R.string.muscle_abdominals),
    QUADRICEPS("quadriceps", R.string.muscle_quadriceps),
    HAMSTRINGS("hamstrings", R.string.muscle_hamstrings),
    GLUTES("glutes", R.string.muscle_glutes),
    CALVES("calves", R.string.muscle_calves),
    ADDUCTORS("adductors", R.string.muscle_adductors),
    ABDUCTORS("abductors", R.string.muscle_abductors),
    ;

    companion object {
        private val byKey = entries.associateBy { it.key }
        fun of(key: String?): Muscle? = key?.let { byKey[it] }
    }
}

enum class Equipment(val key: String, @StringRes val label: Int) {
    BODY_ONLY("body only", R.string.equipment_body_only),
    BARBELL("barbell", R.string.equipment_barbell),
    DUMBBELL("dumbbell", R.string.equipment_dumbbell),
    KETTLEBELLS("kettlebells", R.string.equipment_kettlebells),
    CABLE("cable", R.string.equipment_cable),
    MACHINE("machine", R.string.equipment_machine),
    EZ_BAR("e-z curl bar", R.string.equipment_ez_bar),
    BANDS("bands", R.string.equipment_bands),
    MEDICINE_BALL("medicine ball", R.string.equipment_medicine_ball),
    EXERCISE_BALL("exercise ball", R.string.equipment_exercise_ball),
    FOAM_ROLL("foam roll", R.string.equipment_foam_roll),
    OTHER("other", R.string.equipment_other),
    ;

    companion object {
        private val byKey = entries.associateBy { it.key }
        fun of(key: String?): Equipment? = key?.let { byKey[it] }
    }
}

enum class Level(val key: String, @StringRes val label: Int) {
    BEGINNER("beginner", R.string.level_beginner),
    INTERMEDIATE("intermediate", R.string.level_intermediate),
    EXPERT("expert", R.string.level_expert),
    ;

    companion object {
        fun of(key: String?): Level? = entries.firstOrNull { it.key == key }
    }
}

@StringRes
fun mechanicLabel(key: String?): Int? = when (key) {
    "compound" -> R.string.mechanic_compound
    "isolation" -> R.string.mechanic_isolation
    else -> null
}

@StringRes
fun forceLabel(key: String?): Int? = when (key) {
    "pull" -> R.string.force_pull
    "push" -> R.string.force_push
    "static" -> R.string.force_static
    else -> null
}

@get:StringRes
val ExerciseCategory.label: Int
    get() = when (this) {
        ExerciseCategory.STRENGTH -> R.string.category_strength
        ExerciseCategory.CARDIO -> R.string.category_cardio
        ExerciseCategory.STRETCHING -> R.string.category_stretching
    }

@get:StringRes
val RecordType.label: Int
    get() = when (this) {
        RecordType.WEIGHT_REPS -> R.string.record_weight_reps
        RecordType.REPS -> R.string.record_reps
        RecordType.TIME -> R.string.record_time
    }

/** Тип записи по умолчанию для упражнения из библиотеки. */
fun defaultRecordType(sourceCategory: String, force: String?, equipment: String?): RecordType = when {
    sourceCategory == "stretching" -> RecordType.TIME
    sourceCategory == "cardio" -> RecordType.TIME
    force == "static" -> RecordType.TIME
    sourceCategory == "plyometrics" && equipment in setOf(null, "body only", "other") -> RecordType.REPS
    equipment in setOf(null, "body only", "bands", "foam roll", "exercise ball") -> RecordType.REPS
    else -> RecordType.WEIGHT_REPS
}

fun appCategory(sourceCategory: String): ExerciseCategory = when (sourceCategory) {
    "cardio", "plyometrics" -> ExerciseCategory.CARDIO
    "stretching" -> ExerciseCategory.STRETCHING
    else -> ExerciseCategory.STRENGTH
}

/** Нормализация для поиска: регистр, ё→е, лишние пробелы. */
fun normalizeForSearch(text: String): String =
    text.lowercase().replace('ё', 'е').replace(Regex("[^\\p{L}\\p{Nd}]+"), " ").trim()
