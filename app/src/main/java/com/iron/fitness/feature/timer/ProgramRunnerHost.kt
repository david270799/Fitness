package com.iron.fitness.feature.timer

import android.app.Notification
import android.content.Context
import android.content.res.Resources
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.lifecycle.lifecycleScope
import com.iron.fitness.R
import com.iron.fitness.core.domain.BlockType
import com.iron.fitness.core.domain.Phase
import com.iron.fitness.core.notifications.NotificationChannels
import com.iron.fitness.core.util.Fmt
import com.iron.fitness.core.util.withRussianLocale
import com.iron.fitness.feature.cardio.run.ProgramRunner
import com.iron.fitness.feature.cardio.run.RunKind
import com.iron.fitness.feature.cardio.run.RunnerEvent
import com.iron.fitness.feature.cardio.run.Speaker
import com.iron.fitness.navigation.Routes
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Подпись типа отрезка: «Работа», «Отдых»… */
fun phaseTypeName(res: Resources, type: BlockType): String = res.getString(
    when (type) {
        BlockType.WARMUP -> R.string.block_warmup
        BlockType.WORK -> R.string.block_work
        BlockType.REST -> R.string.block_rest
        BlockType.COOLDOWN -> R.string.block_cooldown
        BlockType.REPEAT -> R.string.block_repeat
    },
)

/** Как назвать отрезок голосом и в уведомлении: подпись или тип. */
fun phaseName(res: Resources, phase: Phase): String = phase.label?.takeIf { it.isNotBlank() } ?: phaseTypeName(res, phase.type)

/**
 * Связка сервиса таймеров и [ProgramRunner]: отсчёт отрезков, сигналы за 3 секунды,
 * голосовая подсказка «Следующее: …», уведомление с кнопками «Пауза» и «Далее».
 */
@Singleton
class ProgramRunnerHost @Inject constructor(
    @ApplicationContext private val context: Context,
    private val runner: ProgramRunner,
    private val alerts: Alerts,
    private val speaker: Speaker,
) {
    private var loop: Job? = null
    private val res: Resources get() = context.withRussianLocale().resources

    val isActive: Boolean get() = runner.isRunning

    fun notification(service: TimerService): Notification? {
        val s = runner.state.value ?: return null
        if (s.finished) return null
        val phase = s.current ?: return null
        val r = service.resources
        val now = SystemClock.elapsedRealtime()
        val title = if (s.kind == RunKind.CARDIO) {
            s.title.uppercase()
        } else {
            val type = phaseTypeName(r, phase.type).uppercase()
            if (phase.label.isNullOrBlank()) type else "$type · ${phase.label}"
        }
        val parts = buildList {
            if (s.paused) add(r.getString(R.string.run_paused))
            if (phase.rounds > 0) add(r.getString(R.string.run_round, phase.round, phase.rounds))
            s.next?.let { add(r.getString(R.string.run_next, phaseName(r, it))) }
            if (s.kind == RunKind.INTERVAL && s.next == null) add(r.getString(R.string.run_last_block))
        }
        val remaining = s.remainingMs(now)
        val builder = NotificationCompat.Builder(service, NotificationChannels.TIMER)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(parts.joinToString(" · ").ifEmpty { s.title })
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(TimerService.openAppIntent(service, Routes.INTERVAL_RUN))
        if (s.paused) {
            builder.setUsesChronometer(false).setShowWhen(false)
            remaining?.let { builder.setSubText(Fmt.duration(it / 1000)) }
        } else if (remaining != null) {
            builder.setUsesChronometer(true).setChronometerCountDown(true).setShowWhen(true)
                .setWhen(System.currentTimeMillis() + remaining)
        } else {
            builder.setUsesChronometer(true).setShowWhen(true)
                .setWhen(System.currentTimeMillis() - s.phaseRunMs(now))
        }
        builder.addAction(
            0,
            r.getString(if (s.paused) R.string.run_resume else R.string.run_pause),
            TimerService.actionIntent(service, TimerService.ACTION_PROGRAM_TOGGLE, 0, 11),
        )
        if (s.kind == RunKind.CARDIO) {
            builder.addAction(0, r.getString(R.string.run_finish), TimerService.actionIntent(service, TimerService.ACTION_PROGRAM_FINISH, 0, 12))
        } else {
            builder.addAction(0, r.getString(R.string.run_skip), TimerService.actionIntent(service, TimerService.ACTION_PROGRAM_NEXT, 0, 13))
        }
        return builder.build()
    }

    fun handleAction(service: TimerService, action: String?) {
        when (action) {
            TimerService.ACTION_PROGRAM_TOGGLE -> runner.togglePause()
            TimerService.ACTION_PROGRAM_FINISH -> runner.stop(save = true)
            TimerService.ACTION_PROGRAM_NEXT -> service.lifecycleScope.launch {
                when (runner.advance()) {
                    RunnerEvent.NEXT_PHASE -> alerts.blockChange()
                    RunnerEvent.FINISHED -> alerts.finish()
                    null -> Unit
                }
            }
        }
    }

    /** Запустить цикл отсчёта в сервисе, если ещё не запущен. */
    fun ensureLoop(service: TimerService) {
        if (loop?.isActive == true) return
        // Обычный Main (не immediate): корутина стартует после присваивания loop, без рекурсии через refresh().
        loop = service.lifecycleScope.launch(Dispatchers.Main) { runLoop(service) }
    }

    /** Сервису больше нечего показывать. */
    fun onIdle() {
        loop?.cancel()
        loop = null
        speaker.shutdown()
    }

    private suspend fun runLoop(service: TimerService) {
        var lastIndex = -1
        var lastPaused: Boolean? = null
        var lastBeepKey = ""
        var announcedIndex = -1
        runner.state.value?.current?.let { speaker.say(phaseName(res, it)) }
        while (currentCoroutineContext().isActive) {
            val s = runner.state.value ?: break
            if (s.finished) break
            if (s.index != lastIndex || s.paused != lastPaused) {
                lastIndex = s.index
                lastPaused = s.paused
                service.refresh()
            }
            if (!s.paused) {
                val now = SystemClock.elapsedRealtime()
                val rem = s.remainingMs(now)
                val cur = s.current
                if (rem != null && cur != null) {
                    val sec = ((rem + 999) / 1000).toInt()
                    val next = s.next
                    if (next != null && cur.seconds >= 8 && sec <= 5 && announcedIndex != s.index) {
                        announcedIndex = s.index
                        speaker.say(res.getString(R.string.run_tts_next, phaseName(res, next)))
                    }
                    val key = "${s.index}:$sec"
                    if (sec in 1..3 && key != lastBeepKey) {
                        lastBeepKey = key
                        alerts.tick()
                    }
                    if (rem <= 0) {
                        when (runner.advance()) {
                            RunnerEvent.NEXT_PHASE -> alerts.blockChange()
                            RunnerEvent.FINISHED -> {
                                alerts.finish()
                                speaker.say(res.getString(R.string.run_tts_done))
                                // Даём договорить, прежде чем сервис остановится.
                                delay(3_000)
                            }
                            null -> Unit
                        }
                        continue
                    }
                }
            }
            delay(100)
        }
        loop = null
        service.refresh()
    }
}
