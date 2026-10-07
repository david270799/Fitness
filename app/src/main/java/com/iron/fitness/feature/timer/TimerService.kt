package com.iron.fitness.feature.timer

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.iron.fitness.MainActivity
import com.iron.fitness.R
import com.iron.fitness.core.notifications.NotificationChannels
import com.iron.fitness.core.util.withRussianLocale
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground Service таймеров: отдых между подходами и интервальные программы / кардио ([ProgramRunnerHost]).
 * Держит частичный WakeLock, пока идёт отсчёт, поэтому сигнал приходит и при выключенном экране.
 */
@AndroidEntryPoint
class TimerService : LifecycleService() {

    @Inject lateinit var restTimer: RestTimer
    @Inject lateinit var alerts: Alerts
    @Inject lateinit var programHost: ProgramRunnerHost

    private var restJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var inForeground = false

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.withRussianLocale())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        // Запущены через startForegroundService — обязаны сразу показать уведомление,
        // даже если таймер уже успели остановить (иначе система завершит приложение).
        if (intent?.getBooleanExtra(EXTRA_FOREGROUND, false) == true && !inForeground) {
            goForeground(placeholderNotification())
        }
        when (intent?.action) {
            ACTION_REST_ADD -> restTimer.add(intent.getIntExtra(EXTRA_SECONDS, 15))
            ACTION_REST_SKIP -> restTimer.stop()
            ACTION_REST_START -> restTimer.start(intent.getIntExtra(EXTRA_SECONDS, 90), null)
            else -> programHost.handleAction(this, intent?.action)
        }
        refresh()
        return START_NOT_STICKY
    }

    /** Пересобрать уведомление и решить, нужен ли сервис дальше. */
    fun refresh() {
        val program = programHost.notification(this)
        val rest = restTimer.state.value
        if (program != null) {
            goForeground(program)
            acquireWakeLock(3 * 60 * 60 * 1000L)
            programHost.ensureLoop(this)
        } else if (rest != null) {
            goForeground(restNotification(rest))
            acquireWakeLock(rest.remainingMs() + 60_000)
        }
        if (rest != null) startRestLoop()
        if (program == null && rest == null) {
            restJob?.cancel()
            programHost.onIdle()
            releaseWakeLock()
            if (inForeground) {
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                inForeground = false
            }
            stopSelf()
        }
    }

    private fun startRestLoop() {
        if (restJob?.isActive == true) return
        restJob = lifecycleScope.launch {
            var lastBeepSec = -1
            while (isActive) {
                val s = restTimer.state.value ?: break
                val remaining = s.remainingMs()
                val sec = s.remainingSec()
                if (remaining <= 0) {
                    restTimer.onFinished()
                    alerts.finish()
                    break
                }
                if (sec in 1..3 && sec != lastBeepSec) {
                    lastBeepSec = sec
                    alerts.tick()
                }
                delay(minOf(250L, remaining))
            }
            restJob = null
            refresh()
        }
    }

    private fun goForeground(notification: Notification) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
        inForeground = true
    }

    private fun placeholderNotification(): Notification =
        NotificationCompat.Builder(this, NotificationChannels.TIMER)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.timer_rest_title))
            .setSilent(true)
            .setOngoing(true)
            .setContentIntent(openAppIntent(this))
            .build()

    private fun restNotification(state: RestState): Notification {
        val endWallClock = System.currentTimeMillis() + state.remainingMs()
        return NotificationCompat.Builder(this, NotificationChannels.TIMER)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.timer_rest_title))
            .setContentText(state.label ?: getString(R.string.timer_rest_text))
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(endWallClock)
            .setShowWhen(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openAppIntent(this))
            .addAction(0, getString(R.string.timer_minus_15), actionIntent(this, ACTION_REST_ADD, -15, 1))
            .addAction(0, getString(R.string.timer_plus_15), actionIntent(this, ACTION_REST_ADD, 15, 2))
            .addAction(0, getString(R.string.timer_skip), actionIntent(this, ACTION_REST_SKIP, 0, 3))
            .build()
    }

    private fun acquireWakeLock(timeoutMs: Long) {
        val pm = getSystemService(PowerManager::class.java) ?: return
        val lock = wakeLock ?: pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "IRON:timer").also {
            it.setReferenceCounted(false)
            wakeLock = it
        }
        lock.acquire(timeoutMs.coerceAtLeast(10_000))
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
    }

    override fun onDestroy() {
        releaseWakeLock()
        alerts.release()
        super.onDestroy()
    }

    companion object {
        const val NOTIFICATION_ID = 4201
        const val ACTION_REFRESH = "com.iron.fitness.timer.REFRESH"
        const val ACTION_REST_START = "com.iron.fitness.timer.REST_START"
        const val ACTION_REST_ADD = "com.iron.fitness.timer.REST_ADD"
        const val ACTION_REST_SKIP = "com.iron.fitness.timer.REST_SKIP"
        const val ACTION_PROGRAM_TOGGLE = "com.iron.fitness.timer.PROGRAM_TOGGLE"
        const val ACTION_PROGRAM_NEXT = "com.iron.fitness.timer.PROGRAM_NEXT"
        const val ACTION_PROGRAM_FINISH = "com.iron.fitness.timer.PROGRAM_FINISH"
        const val EXTRA_SECONDS = "seconds"
        const val EXTRA_FOREGROUND = "foreground"

        fun openAppIntent(context: Context, route: String? = null): PendingIntent {
            val intent = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            if (route != null) intent.putExtra(MainActivity.EXTRA_ROUTE, route)
            return PendingIntent.getActivity(
                context,
                route?.hashCode() ?: 0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        fun actionIntent(context: Context, action: String, seconds: Int, requestCode: Int): PendingIntent =
            PendingIntent.getService(
                context,
                requestCode,
                Intent(context, TimerService::class.java).setAction(action).putExtra(EXTRA_SECONDS, seconds),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        /** Быстрый старт отдыха (например, из виджета). */
        fun startRestIntent(context: Context, seconds: Int, requestCode: Int): PendingIntent =
            PendingIntent.getForegroundService(
                context,
                requestCode,
                Intent(context, TimerService::class.java)
                    .setAction(ACTION_REST_START)
                    .putExtra(EXTRA_SECONDS, seconds)
                    .putExtra(EXTRA_FOREGROUND, true),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
    }
}
