package com.iron.fitness.feature.cardio.data

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.iron.fitness.R
import com.iron.fitness.core.ui.IronIcons

/** Интервальная программа (блоки хранятся в JSON). */
@Entity(tableName = "interval_programs")
data class IntervalProgramEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val note: String? = null,
    val blocksJson: String,
    /** MET рабочих отрезков (интенсивность). */
    val workMet: Double = 8.0,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

/** Интенсивность кардио. */
enum class Intensity(@StringRes val label: Int) {
    LIGHT(R.string.intensity_light),
    MODERATE(R.string.intensity_moderate),
    VIGOROUS(R.string.intensity_vigorous),
}

/**
 * Вид кардио и MET по Compendium of Physical Activities для лёгкой, средней и высокой интенсивности.
 */
enum class CardioType(
    @StringRes val label: Int,
    @DrawableRes val icon: Int,
    private val mets: Triple<Double, Double, Double>,
    val hasDistance: Boolean,
) {
    RUN(R.string.cardio_run, IronIcons.Footprints, Triple(7.0, 9.8, 11.5), true),
    WALK(R.string.cardio_walk, IronIcons.Footprints, Triple(2.8, 3.5, 5.0), true),
    BIKE(R.string.cardio_bike, IronIcons.Bike, Triple(5.8, 7.5, 10.0), true),
    ROW(R.string.cardio_row, IronIcons.Waves, Triple(4.8, 7.0, 8.5), true),
    ELLIPTICAL(R.string.cardio_elliptical, IronIcons.Activity, Triple(4.0, 5.0, 7.5), true),
    SWIM(R.string.cardio_swim, IronIcons.Waves, Triple(5.8, 7.0, 9.8), true),
    ROPE(R.string.cardio_rope, IronIcons.Repeat, Triple(8.8, 11.8, 12.3), false),
    STAIRS(R.string.cardio_stairs, IronIcons.Mountain, Triple(4.0, 8.8, 9.0), false),
    OTHER(R.string.cardio_other, IronIcons.Heart, Triple(4.0, 6.0, 8.0), false),
    ;

    fun met(intensity: Intensity): Double = when (intensity) {
        Intensity.LIGHT -> mets.first
        Intensity.MODERATE -> mets.second
        Intensity.VIGOROUS -> mets.third
    }

    companion object {
        fun of(name: String?): CardioType = entries.firstOrNull { it.name == name } ?: OTHER
    }
}
