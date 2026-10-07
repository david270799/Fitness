package com.iron.fitness.feature.timer

import android.app.Notification
import android.content.Context
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Точка расширения сервиса таймеров для интервальных программ (этап 4).
 * Пока программ нет — уведомления нет, действия игнорируются.
 */
@Singleton
class ProgramRunnerHost @Inject constructor() {
    fun notification(service: TimerService): Notification? = null
    fun handleAction(service: TimerService, action: String?) = Unit
}
