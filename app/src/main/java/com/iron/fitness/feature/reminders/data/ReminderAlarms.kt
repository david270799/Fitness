package com.iron.fitness.feature.reminders.data

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.iron.fitness.MainActivity
import com.iron.fitness.R
import com.iron.fitness.core.alarms.AlarmScheduler
import com.iron.fitness.core.domain.DoseStatus
import com.iron.fitness.core.domain.ReminderSchedule
import com.iron.fitness.core.notifications.NotificationChannels
import com.iron.fitness.core.settings.SettingsRepository
import com.iron.fitness.core.util.Fmt
import com.iron.fitness.core.util.withRussianLocale
import com.iron.fitness.navigation.Routes
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Будильники напоминаний о приёме. На каждое напоминание — один будильник ближайшего приёма
 * и один — повтора (если не отметили «Принял»). Точные будильники срабатывают и в режиме Doze.
 */
@Singleton
class ReminderAlarms @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scheduler: AlarmScheduler,
    private val repo: ReminderRepository,
    private val settings: SettingsRepository,
) {
    private val zone: ZoneId get() = ZoneId.systemDefault()

    private fun intent(action: String, id: Long, at: Long, n: Int = 0) =
        Intent(context, ReminderReceiver::class.java)
            .setAction(action)
            .putExtra(EXTRA_ID, id)
            .putExtra(EXTRA_AT, at)
            .putExtra(EXTRA_N, n)

    private fun broadcast(code: Int, intent: Intent, flags: Int = PendingIntent.FLAG_UPDATE_CURRENT): PendingIntent? =
        PendingIntent.getBroadcast(context, code, intent, flags or PendingIntent.FLAG_IMMUTABLE)

    private fun fireCode(id: Long) = 40_000 + id.toInt()
    private fun repeatCode(id: Long) = 50_000 + id.toInt()
    private fun notificationId(id: Long) = 7_000 + id.toInt()

    /** Поставить будильник ближайшего приёма. */
    fun scheduleNext(r: ReminderEntity, after: LocalDateTime = LocalDateTime.now()) {
        val pi = broadcast(fireCode(r.id), intent(ACTION_FIRE, r.id, 0)) ?: return
        if (!r.enabled) {
            scheduler.cancel(pi)
            return
        }
        val next = ReminderSchedule.next(r.timeList, r.weekdays, after) ?: run {
            scheduler.cancel(pi)
            return
        }
        val at = next.atZone(zone).toInstant().toEpochMilli()
        val withTime = broadcast(fireCode(r.id), intent(ACTION_FIRE, r.id, at)) ?: return
        scheduler.schedule(at, withTime, exact = true)
    }

    fun cancel(id: Long) {
        broadcast(fireCode(id), intent(ACTION_FIRE, id, 0))?.let { scheduler.cancel(it) }
        cancelRepeat(id)
        NotificationManagerCompat.from(context).cancel(notificationId(id))
    }

    private fun cancelRepeat(id: Long) {
        broadcast(repeatCode(id), intent(ACTION_REPEAT, id, 0))?.let { scheduler.cancel(it) }
    }

    private fun scheduleRepeat(id: Long, at: Long, n: Int, triggerAt: Long) {
        val pi = broadcast(repeatCode(id), intent(ACTION_REPEAT, id, at, n)) ?: return
        scheduler.schedule(triggerAt, pi, exact = true)
    }

    suspend fun rescheduleAll() {
        // Всё, что не отмечено за полсуток, — пропуск.
        repo.markOldPendingMissed(System.currentTimeMillis() - 12 * 60 * 60 * 1000L)
        repo.getAll().forEach { scheduleNext(it) }
    }

    /** Наступило время приёма. */
    suspend fun onFire(id: Long, at: Long) {
        val r = repo.get(id) ?: return
        val atTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(at.takeIf { it > 0 } ?: System.currentTimeMillis()), zone)
        // Следующий будильник — сразу, чтобы цепочка не прервалась.
        scheduleNext(r, atTime)
        if (!r.enabled || at <= 0) return
        val log = repo.ensureLog(id, at)
        if (log.status != DoseStatus.PENDING) return
        notify(r, at)
        val s = settings.current()
        if (s.reminderRepeatCount > 0) {
            scheduleRepeat(id, at, 1, System.currentTimeMillis() + s.reminderRepeatMinutes * 60_000L)
        }
    }

    /** Повторное напоминание, если не отметили «Принял». */
    suspend fun onRepeat(id: Long, at: Long, n: Int) {
        val r = repo.get(id) ?: return
        val log = repo.getLog(id, at) ?: return
        if (log.status != DoseStatus.PENDING) return
        val s = settings.current()
        if (n > s.reminderRepeatCount) {
            // Повторы закончились — считаем пропуском (позже всё ещё можно отметить «Принял»).
            repo.setStatus(id, at, DoseStatus.MISSED)
            return
        }
        repo.setRepeats(id, at, n)
        notify(r, at)
        scheduleRepeat(id, at, n + 1, System.currentTimeMillis() + s.reminderRepeatMinutes * 60_000L)
    }

    suspend fun onTaken(id: Long, at: Long) {
        repo.setStatus(id, at, DoseStatus.TAKEN)
        cancelRepeat(id)
        NotificationManagerCompat.from(context).cancel(notificationId(id))
    }

    suspend fun onSnooze(id: Long, at: Long, n: Int) {
        NotificationManagerCompat.from(context).cancel(notificationId(id))
        val log = repo.getLog(id, at)
        if (log != null && log.status == DoseStatus.MISSED) repo.setStatus(id, at, DoseStatus.PENDING)
        scheduleRepeat(id, at, n, System.currentTimeMillis() + SNOOZE_MINUTES * 60_000L)
    }

    private fun notify(r: ReminderEntity, at: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val res = context.withRussianLocale().resources
        val text = listOfNotNull(r.dose?.takeIf { it.isNotBlank() }, Fmt.time(at), r.note?.takeIf { it.isNotBlank() }).joinToString(" · ")
        val open = PendingIntent.getActivity(
            context,
            notificationId(r.id),
            MainActivity.routeIntent(context, Routes.reminderDetail(r.id)),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val taken = broadcast(60_000 + r.id.toInt(), intent(ACTION_TAKEN, r.id, at))
        val snooze = broadcast(70_000 + r.id.toInt(), intent(ACTION_SNOOZE, r.id, at))
        val builder = NotificationCompat.Builder(context, NotificationChannels.REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(r.name)
            .setContentText(text)
            .setContentIntent(open)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(false)
            .setOnlyAlertOnce(false)
            .addAction(0, res.getString(R.string.reminder_taken), taken)
            .addAction(0, res.getString(R.string.reminder_snooze), snooze)
        loadBitmap(r.photoPath)?.let { bmp ->
            builder.setLargeIcon(bmp)
                .setStyle(NotificationCompat.BigPictureStyle().bigPicture(bmp).bigLargeIcon(null as Bitmap?))
        }
        NotificationManagerCompat.from(context).notify(notificationId(r.id), builder.build())
    }

    private fun loadBitmap(path: String?): Bitmap? {
        if (path.isNullOrBlank()) return null
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            var sample = 1
            while (bounds.outWidth / sample > 720 || bounds.outHeight / sample > 720) sample *= 2
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
        }.getOrNull()
    }

    companion object {
        const val ACTION_FIRE = "com.iron.fitness.reminder.FIRE"
        const val ACTION_REPEAT = "com.iron.fitness.reminder.REPEAT"
        const val ACTION_TAKEN = "com.iron.fitness.reminder.TAKEN"
        const val ACTION_SNOOZE = "com.iron.fitness.reminder.SNOOZE"
        const val EXTRA_ID = "reminder_id"
        const val EXTRA_AT = "at"
        const val EXTRA_N = "n"
        const val SNOOZE_MINUTES = 10
    }
}

@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {
    @Inject lateinit var alarms: ReminderAlarms

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(ReminderAlarms.EXTRA_ID, -1)
        if (id < 0) return
        val at = intent.getLongExtra(ReminderAlarms.EXTRA_AT, 0)
        val n = intent.getIntExtra(ReminderAlarms.EXTRA_N, 0)
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ReminderAlarms.ACTION_FIRE -> alarms.onFire(id, at)
                    ReminderAlarms.ACTION_REPEAT -> alarms.onRepeat(id, at, n)
                    ReminderAlarms.ACTION_TAKEN -> alarms.onTaken(id, at)
                    ReminderAlarms.ACTION_SNOOZE -> alarms.onSnooze(id, at, n)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
