package com.iron.fitness.feature.inbody

import androidx.annotation.StringRes
import com.iron.fitness.R
import com.iron.fitness.feature.assistant.InBodyField
import com.iron.fitness.feature.assistant.InBodyReading

/** Поле InBody: подпись, единица, чтение из результата распознавания и из сохранённой записи. */
enum class InBodyKey(
    @StringRes val label: Int,
    @StringRes val unit: Int?,
    val fromReading: (InBodyReading) -> InBodyField,
    val fromEntity: (InBodyEntity) -> Double?,
    /** Больше — лучше (для цвета динамики); null — нейтрально. */
    val higherIsBetter: Boolean?,
) {
    WEIGHT(R.string.ib_weight, R.string.unit_kg, { it.weightKg }, { it.weightKg }, null),
    SMM(R.string.ib_smm, R.string.unit_kg, { it.skeletalMuscleKg }, { it.skeletalMuscleKg }, true),
    FAT_MASS(R.string.ib_fat_mass, R.string.unit_kg, { it.bodyFatKg }, { it.bodyFatKg }, false),
    PBF(R.string.ib_pbf, R.string.unit_percent, { it.bodyFatPct }, { it.bodyFatPct }, false),
    BMI(R.string.ib_bmi, null, { it.bmi }, { it.bmi }, null),
    VISCERAL(R.string.ib_visceral, null, { it.visceralFatLevel }, { it.visceralFatLevel }, false),
    BMR(R.string.ib_bmr, R.string.unit_kcal, { it.bmrKcal }, { it.bmrKcal }, true),
    TBW(R.string.ib_tbw, R.string.unit_liter, { it.totalBodyWaterL }, { it.totalBodyWaterL }, null),
    LEAN(R.string.ib_lean, R.string.unit_kg, { it.leanMassKg }, { it.leanMassKg }, true),
    SCORE(R.string.ib_score, null, { it.inbodyScore }, { it.inbodyScore }, true),
    ECW(R.string.ib_ecw, null, { it.ecwRatio }, { it.ecwRatio }, null),
    ARM_L(R.string.ib_arm_l, R.string.unit_kg, { it.armLeftLeanKg }, { it.armLeftLeanKg }, true),
    ARM_R(R.string.ib_arm_r, R.string.unit_kg, { it.armRightLeanKg }, { it.armRightLeanKg }, true),
    TRUNK(R.string.ib_trunk, R.string.unit_kg, { it.trunkLeanKg }, { it.trunkLeanKg }, true),
    LEG_L(R.string.ib_leg_l, R.string.unit_kg, { it.legLeftLeanKg }, { it.legLeftLeanKg }, true),
    LEG_R(R.string.ib_leg_r, R.string.unit_kg, { it.legRightLeanKg }, { it.legRightLeanKg }, true),
}

/** Собрать запись из проверенных значений. */
fun buildEntity(day: Long, photoPath: String?, values: Map<InBodyKey, Double?>): InBodyEntity = InBodyEntity(
    day = day,
    photoPath = photoPath,
    weightKg = values[InBodyKey.WEIGHT],
    skeletalMuscleKg = values[InBodyKey.SMM],
    bodyFatKg = values[InBodyKey.FAT_MASS],
    bodyFatPct = values[InBodyKey.PBF],
    bmi = values[InBodyKey.BMI],
    visceralFatLevel = values[InBodyKey.VISCERAL],
    bmrKcal = values[InBodyKey.BMR],
    totalBodyWaterL = values[InBodyKey.TBW],
    leanMassKg = values[InBodyKey.LEAN],
    inbodyScore = values[InBodyKey.SCORE],
    ecwRatio = values[InBodyKey.ECW],
    armLeftLeanKg = values[InBodyKey.ARM_L],
    armRightLeanKg = values[InBodyKey.ARM_R],
    trunkLeanKg = values[InBodyKey.TRUNK],
    legLeftLeanKg = values[InBodyKey.LEG_L],
    legRightLeanKg = values[InBodyKey.LEG_R],
)
