package com.iron.fitness.core.backup

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import com.iron.fitness.core.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Ошибка Google Диска (текст пользователю — по [kind]). */
class DriveException(val kind: Kind, message: String? = null) : Exception(message) {
    enum class Kind { NOT_CONFIGURED, API_DISABLED, AUTH, CANCELLED, NETWORK, STORAGE_FULL, OTHER }
}

/** Файл бэкапа в скрытой папке приложения на Диске. */
@Serializable
data class DriveFile(val id: String, val name: String = "", val size: String? = null, val createdTime: String? = null) {
    val createdMillis: Long get() = createdTime?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: 0L
    val bytes: Long get() = size?.toLongOrNull() ?: 0L
}

@Serializable
private data class DriveFileList(val files: List<DriveFile> = emptyList())

@Serializable
private data class DriveAbout(val user: DriveUser? = null)

@Serializable
private data class DriveUser(val emailAddress: String? = null, val displayName: String? = null)

/**
 * Бэкап в Google Диск (скрытая папка приложения appDataFolder — её видит только IRON).
 * Доступ — через вход в Google (Authorization API), токен не хранится приложением.
 */
@Singleton
class DriveBackup @Inject constructor(
    @ApplicationContext private val context: Context,
    private val http: OkHttpClient,
    private val json: Json,
    private val backup: BackupManager,
    private val settings: SettingsRepository,
) {
    sealed interface Auth {
        data class Granted(val token: String) : Auth
        /** Нужно согласие пользователя: запустить [intent] из экрана. */
        data class NeedsConsent(val intent: PendingIntent) : Auth
    }

    private val client get() = Identity.getAuthorizationClient(context)

    private fun request(): AuthorizationRequest =
        AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(SCOPE_APPDATA))).build()

    /** Получить доступ. Если вход уже разрешён — без окон. */
    suspend fun authorize(): Auth = try {
        val result = client.authorize(request()).await()
        val intent = result.pendingIntent
        when {
            result.hasResolution() && intent != null -> Auth.NeedsConsent(intent)
            result.accessToken != null -> Auth.Granted(result.accessToken!!)
            else -> throw DriveException(DriveException.Kind.AUTH)
        }
    } catch (e: ApiException) {
        throw e.toDrive()
    }

    /** Результат окна согласия. */
    fun tokenFromConsent(data: Intent?): String = try {
        val intent = data ?: throw DriveException(DriveException.Kind.CANCELLED)
        client.getAuthorizationResultFromIntent(intent).accessToken ?: throw DriveException(DriveException.Kind.AUTH)
    } catch (e: ApiException) {
        throw e.toDrive()
    }

    private fun ApiException.toDrive(): DriveException = DriveException(
        when (statusCode) {
            CommonStatusCodes.DEVELOPER_ERROR -> DriveException.Kind.NOT_CONFIGURED
            CommonStatusCodes.CANCELED -> DriveException.Kind.CANCELLED
            CommonStatusCodes.NETWORK_ERROR -> DriveException.Kind.NETWORK
            else -> DriveException.Kind.AUTH
        },
        "ApiException $statusCode",
    )

    // ---------------- REST Drive v3 ----------------

    suspend fun accountEmail(token: String): String? = withContext(Dispatchers.IO) {
        val url = "$API/about".toHttpUrl().newBuilder().addQueryParameter("fields", "user(emailAddress,displayName)").build()
        val text = call(Request.Builder().url(url).auth(token).get().build()) { it.body?.string().orEmpty() }
        json.decodeFromString(DriveAbout.serializer(), text).user?.let { it.emailAddress ?: it.displayName }
    }

    suspend fun list(token: String): List<DriveFile> = withContext(Dispatchers.IO) {
        val url = "$API/files".toHttpUrl().newBuilder()
            .addQueryParameter("spaces", "appDataFolder")
            .addQueryParameter("fields", "files(id,name,size,createdTime)")
            .addQueryParameter("orderBy", "createdTime desc")
            .addQueryParameter("pageSize", "100")
            .build()
        val text = call(Request.Builder().url(url).auth(token).get().build()) { it.body?.string().orEmpty() }
        json.decodeFromString(DriveFileList.serializer(), text).files.sortedByDescending { it.createdMillis }
    }

    /** Загрузка в один запрос по протоколу resumable (без ограничения размера multipart). */
    private suspend fun upload(token: String, file: File, name: String) = withContext(Dispatchers.IO) {
        val meta = buildJsonObject {
            put("name", name)
            putJsonArray("parents") { add("appDataFolder") }
        }
        val start = Request.Builder()
            .url("$UPLOAD/files?uploadType=resumable")
            .auth(token)
            .header("X-Upload-Content-Type", BackupFormat.MIME)
            .header("X-Upload-Content-Length", file.length().toString())
            .post(meta.toString().toRequestBody("application/json; charset=UTF-8".toMediaType()))
            .build()
        val session = call(start) { it.header("Location") } ?: throw DriveException(DriveException.Kind.OTHER, "no upload url")
        call(Request.Builder().url(session).put(file.asRequestBody(BackupFormat.MIME.toMediaType())).build()) { }
    }

    private suspend fun download(token: String, id: String, dest: File) = withContext(Dispatchers.IO) {
        val url = "$API/files/$id".toHttpUrl().newBuilder().addQueryParameter("alt", "media").build()
        call(Request.Builder().url(url).auth(token).get().build()) { r ->
            val body = r.body ?: throw DriveException(DriveException.Kind.OTHER, "empty body")
            dest.outputStream().use { out -> body.byteStream().use { it.copyTo(out) } }
        }
    }

    suspend fun delete(token: String, id: String) = withContext(Dispatchers.IO) {
        call(Request.Builder().url("$API/files/$id").auth(token).delete().build()) { }
    }

    private fun Request.Builder.auth(token: String) = header("Authorization", "Bearer $token")

    private fun <T> call(request: Request, read: (Response) -> T): T {
        val response = try {
            http.newCall(request).execute()
        } catch (e: IOException) {
            throw DriveException(DriveException.Kind.NETWORK, e.message)
        }
        response.use { r ->
            if (r.isSuccessful) return read(r)
            val text = r.body?.string().orEmpty()
            val kind = when {
                r.code == 401 -> DriveException.Kind.AUTH
                text.contains("storageQuotaExceeded") -> DriveException.Kind.STORAGE_FULL
                text.contains("accessNotConfigured") || text.contains("SERVICE_DISABLED") -> DriveException.Kind.API_DISABLED
                r.code == 403 && text.contains("insufficient", ignoreCase = true) -> DriveException.Kind.AUTH
                else -> DriveException.Kind.OTHER
            }
            throw DriveException(kind, "HTTP ${r.code}")
        }
    }

    // ---------------- Сценарии ----------------

    /** Сделать бэкап и загрузить на Диск; старые копии сверх [KEEP] удаляются. */
    suspend fun backupNow(token: String) {
        val tmp = File(context.cacheDir, "drive_upload.zip")
        try {
            withContext(Dispatchers.IO) { tmp.outputStream().use { backup.write(it) } }
            upload(token, tmp, BackupFormat.fileName(LocalDateTime.now()))
            settings.setLastBackup(System.currentTimeMillis())
            runCatching {
                BackupFormat.toPrune(list(token), KEEP) { it.createdMillis }.forEach { delete(token, it.id) }
            }.onFailure { if (it is CancellationException) throw it }
        } finally {
            tmp.delete()
        }
    }

    /** Скачать копию и подготовить восстановление (дальше — перезапуск приложения). */
    suspend fun restore(token: String, id: String): BackupManifest {
        val tmp = File(context.cacheDir, "drive_restore.zip")
        try {
            download(token, id, tmp)
            return withContext(Dispatchers.IO) { tmp.inputStream().use { backup.stageRestore(it) } }
        } finally {
            tmp.delete()
        }
    }

    /** Ежедневный бэкап по Wi-Fi. */
    fun schedule(enabled: Boolean) {
        val wm = WorkManager.getInstance(context)
        if (!enabled) {
            wm.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<DriveBackupWorker>(1, TimeUnit.DAYS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.UNMETERED)
                    .setRequiresBatteryNotLow(true)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()
        wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    companion object {
        const val SCOPE_APPDATA = "https://www.googleapis.com/auth/drive.appdata"
        private const val API = "https://www.googleapis.com/drive/v3"
        private const val UPLOAD = "https://www.googleapis.com/upload/drive/v3"
        private const val WORK_NAME = "drive_auto_backup"
        const val KEEP = 7
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}
