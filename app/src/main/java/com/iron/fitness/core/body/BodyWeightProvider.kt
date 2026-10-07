package com.iron.fitness.core.body

import com.iron.fitness.core.domain.Calories
import com.iron.fitness.feature.body.data.BodyDao
import javax.inject.Inject
import javax.inject.Singleton

/** Текущий вес тела для расчёта калорий: последний замер из раздела «Тело» или 75 кг. */
@Singleton
class BodyWeightProvider @Inject constructor(
    private val dao: BodyDao,
) {
    suspend fun currentKg(): Double = dao.latestWeight()?.takeIf { it > 0 } ?: Calories.DEFAULT_BODY_WEIGHT_KG
}
