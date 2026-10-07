package com.iron.fitness.core.backup

import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.iron.fitness.MainActivity
import com.iron.fitness.R
import com.iron.fitness.core.notifications.NotificationChannels
import com.iron.fitness.core.settings.SettingsRepository
import com.iron.fitness.core.util.withRussianLocale
import com.iron.fitness.navigation.Routes
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Ежедневный бэкап на Google Диск (только по Wi-Fi, при нормальном заряде). */
@HiltWorker
class DriveBackupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val drive: DriveBackup,
    private val settings: SettingsRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val s = settings.current()
        if (!s.autoBackupEnabled || s.driveAccount == null) return Result.success()
        return try {
            when (val auth = drive.authorize()) {
                is DriveBackup.Auth.Granted -> {
                    drive.backupNow(auth.token)
                    Result.success()
                }
                is DriveBackup.Auth.NeedsConsent -> {
                    // Без окна входа продолжить нельзя — просим открыть приложение.
                    notifySignIn()
                    Result.success()
                }
            }
        } catch (e: DriveException) {
            when (e.kind) {
                DriveException.Kind.NETWORK, DriveException.Kind.OTHER ->
                    if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
                DriveException.Kind.AUTH -> {
                    notifySignIn()
                    Result.failure()
                }
                else -> Result.failure()
            }
        } catch (e: BackupException) {
            Result.failure()
        }
    }

    private fun notifySignIn() {
        val ctx = applicationContext
        val res = ctx.withRussianLocale().resources
        val open = PendingIntent.getActivity(
            ctx,
            NOTIFICATION_ID,
            MainActivity.routeIntent(ctx, Routes.BACKUP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(ctx, NotificationChannels.BACKUP)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(res.getString(R.string.backup_notif_signin_title))
            .setContentText(res.getString(R.string.backup_notif_signin_text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(NOTIFICATION_ID, notification) }
    }

    private companion object {
        const val MAX_RETRIES = 3
        const val NOTIFICATION_ID = 7301
    }
}
