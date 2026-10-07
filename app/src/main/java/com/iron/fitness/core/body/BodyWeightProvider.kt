package com.iron.fitness.core.body

import com.iron.fitness.core.domain.Calories
import javax.inject.Inject
import javax.inject.Singleton

/** Текущий вес тела для расчёта калорий: последний замер из раздела «Тело» или 75 кг. */
@Singleton
class BodyWeightProvider @Inject constructor() {
    suspend fun currentKg(): Double = Calories.DEFAULT_BODY_WEIGHT_KG
}
