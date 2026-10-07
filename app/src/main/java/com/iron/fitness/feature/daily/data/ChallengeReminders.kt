package com.iron.fitness.feature.daily.data

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.iron.fitness.MainActivity
import com.iron.fitness.R
import com.iron.fitness.core.alarms.AlarmScheduler
import com.iron.fitness.core.notifications.NotificationChannels
import com.iron.fitness.core.util.withRussianLocale
import com.iron.fitness.navigation.Routes
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Ежедневные напоминания челленджей. */
@Singleton
class ChallengeReminders @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scheduler: AlarmScheduler,
    private val repo: ChallengeRepository,
) {
    private fun pending(id: Long, flags: Int = PendingIntent.FLAG_UPDATE_CURRENT): PendingIntent? =
        PendingIntent.getBroadcast(
            context,
            REQUEST_BASE + id.toInt(),
            Intent(context, ChallengeReminderReceiver::class.java).setAction(ACTION_REMIND).putExtra(EXTRA_ID, id),
            flags or PendingIntent.FLAG_IMMUTABLE,
        )

    fun schedule(c: ChallengeEntity, afterFire: Boolean = false) {
        val pi = pending(c.id) ?: return
        if (!c.reminderEnabled || c.archived) {
            scheduler.cancel(pi)
            return
        }
        val at = if (afterFire) AlarmScheduler.tomorrowAt(c.reminderMinutes) else AlarmScheduler.nextDaily(c.reminderMinutes)
        // Напоминание челленджа не критично по минутам — достаточно неточного будильника.
        scheduler.schedule(at, pi, exact = false)
    }

    fun cancel(id: Long) {
        pending(id)?.let { scheduler.cancel(it) }
    }

    suspend fun rescheduleAll() {
        repo.getActive().forEach { schedule(it) }
    }

    /** Показать напоминание, если цель на сегодня ещё не выполнена. */
    suspend fun fire(id: Long) {
        val c = repo.get(id) ?: return
        schedule(c, afterFire = true)
        if (!c.reminderEnabled || c.archived) return
        val total = repo.totalToday(id)
        if (total >= c.goal) return
        notify(c, total)
    }

    fun notify(c: ChallengeEntity, total: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val res = context.withRussianLocale().resources
        val unit = if (c.unit == ChallengeUnit.SECONDS) res.getString(R.string.unit_sec) else ""
        val text = res.getString(R.string.challenge_notif_text, total, c.goal, (c.goal - total).coerceAtLeast(0)) +
            if (unit.isNotEmpty()) " $unit" else ""
        val open = PendingIntent.getActivity(
            context,
            REQUEST_BASE + c.id.toInt(),
            MainActivity.routeIntent(context, Routes.challenge(c.id)),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, NotificationChannels.CHALLENGES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(c.name)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .addAction(0, "+${c.step1}", actionIntent(c.id, c.step1, 1))
            .addAction(0, "+${c.step2}", actionIntent(c.id, c.step2, 2))
        NotificationManagerCompat.from(context).notify(NOTIFICATION_BASE + c.id.toInt(), builder.build())
    }

    private fun actionIntent(id: Long, amount: Int, slot: Int): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_BASE + id.toInt() * 10 + slot,
        Intent(context, ChallengeReminderReceiver::class.java)
            .setAction(ACTION_ADD)
            .putExtra(EXTRA_ID, id)
            .putExtra(EXTRA_AMOUNT, amount),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** «+N» из уведомления: записать и обновить или убрать уведомление. */
    suspend fun addFromNotification(id: Long, amount: Int) {
        repo.add(id, amount)
        val c = repo.get(id) ?: return
        val total = repo.totalToday(id)
        if (total >= c.goal) {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_BASE + id.toInt())
        } else {
            notify(c, total)
        }
    }

    companion object {
        const val ACTION_REMIND = "com.iron.fitness.challenge.REMIND"
        const val ACTION_ADD = "com.iron.fitness.challenge.ADD"
        const val EXTRA_ID = "challenge_id"
        const val EXTRA_AMOUNT = "amount"
        private const val REQUEST_BASE = 30_000
        private const val NOTIFICATION_BASE = 6_000
    }
}

@AndroidEntryPoint
class ChallengeReminderReceiver : BroadcastReceiver() {
    @Inject lateinit var reminders: ChallengeReminders

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(ChallengeReminders.EXTRA_ID, -1)
        if (id < 0) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ChallengeReminders.ACTION_REMIND -> reminders.fire(id)
                    ChallengeReminders.ACTION_ADD -> reminders.addFromNotification(id, intent.getIntExtra(ChallengeReminders.EXTRA_AMOUNT, 0))
                }
            } finally {
                pending.finish()
            }
        }
    }
}
