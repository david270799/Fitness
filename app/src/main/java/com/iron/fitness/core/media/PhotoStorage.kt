package com.iron.fitness.core.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/** Папки для фото внутри приватного хранилища приложения. */
enum class PhotoKind(val dir: String) {
    EXERCISE("exercises"),
    REMINDER("reminders"),
    PROGRESS("progress"),
    INBODY("inbody"),
}

/**
 * Хранение пользовательских фото в приватной папке (filesDir/photos).
 * Фото уменьшаются до разумного размера и поворачиваются по EXIF.
 */
@Singleton
class PhotoStorage @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val root: File get() = File(context.filesDir, ROOT_DIR)

    fun dir(kind: PhotoKind): File = File(root, kind.dir).apply { mkdirs() }

    /** Временный файл + content:// Uri для системной камеры (TakePicture). */
    fun newCameraTarget(): Pair<Uri, File> {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        val file = File(dir, "shot_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        return uri to file
    }

    /** Копирует изображение из Uri в приватную папку. Возвращает абсолютный путь или null. */
    suspend fun import(uri: Uri, kind: PhotoKind, maxSide: Int = DEFAULT_MAX_SIDE): String? = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
            val decoded = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return@runCatching null
            val orientation = resolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
            val rotated = rotate(decoded, orientation)
            val scaled = scaleDown(rotated, maxSide)
            val file = File(dir(kind), "${UUID.randomUUID()}.jpg")
            file.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            if (scaled !== rotated) scaled.recycle()
            if (rotated !== decoded) rotated.recycle()
            decoded.recycle()
            file.absolutePath
        }.getOrNull()
    }

    suspend fun importFile(file: File, kind: PhotoKind, maxSide: Int = DEFAULT_MAX_SIDE): String? {
        val result = import(Uri.fromFile(file), kind, maxSide)
        file.delete()
        return result
    }

    fun delete(path: String?) {
        if (path.isNullOrBlank()) return
        val file = File(path)
        if (file.absolutePath.startsWith(root.absolutePath)) file.delete()
    }

    private fun rotate(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun scaleDown(bitmap: Bitmap, maxSide: Int): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= maxSide) return bitmap
        val ratio = maxSide.toFloat() / longest
        return Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true)
    }

    companion object {
        const val ROOT_DIR = "photos"
        const val DEFAULT_MAX_SIDE = 1600
        const val JPEG_QUALITY = 85
    }
}
