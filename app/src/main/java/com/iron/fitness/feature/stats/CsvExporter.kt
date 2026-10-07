package com.iron.fitness.feature.stats

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.iron.fitness.R
import com.iron.fitness.core.domain.SetType
import com.iron.fitness.core.util.Fmt
import com.iron.fitness.core.util.withRussianLocale
import com.iron.fitness.feature.exercises.data.ExerciseRepository
import com.iron.fitness.feature.workouts.data.WorkoutRepository
import com.iron.fitness.feature.workouts.data.isRecord
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Экспорт журнала в CSV для Excel/Google Таблиц: разделитель «;», десятичная запятая, UTF-8 с BOM.
 * Два файла: тренировки и подходы.
 */
@Singleton
class CsvExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val workouts: WorkoutRepository,
    private val exercises: ExerciseRepository,
) {
    private fun dec(v: Double?, digits: Int = 2): String = v?.let { Fmt.num(it, digits) } ?: ""

    private fun cell(text: String?): String {
        val t = text.orEmpty()
        return if (t.any { it == ';' || it == '"' || it == '\n' || it == '\r' }) "\"" + t.replace("\"", "\"\"") + "\"" else t
    }

    /** Создаёт файлы в cache/exports и возвращает их content:// адреса. */
    suspend fun export(): List<Uri> = withContext(Dispatchers.IO) {
        val res = context.withRussianLocale().resources
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val stamp = LocalDate.now().toString()
        val names = exercises.getAllOnce().associate { it.id to it.name }

        val all = workouts.allFinished()
        val wFile = File(dir, "iron_workouts_$stamp.csv")
        wFile.bufferedWriter(Charsets.UTF_8).use { w ->
            w.write("﻿")
            w.write(res.getString(R.string.csv_workouts_header))
            w.newLine()
            for (x in all) {
                val dt = Fmt.toLocalDateTime(x.startedAt)
                w.write(
                    listOf(
                        dt.toLocalDate().toString(),
                        Fmt.time(x.startedAt),
                        x.type.name,
                        cell(x.name),
                        dec(x.durationSec / 60.0, 1),
                        dec(x.volumeKg, 1),
                        dec(x.caloriesKcal, 0),
                        dec(x.distanceKm),
                        cell(x.cardioType),
                        cell(x.note),
                    ).joinToString(";"),
                )
                w.newLine()
            }
        }

        val sets = workouts.allCompletedSets()
        val sFile = File(dir, "iron_sets_$stamp.csv")
        sFile.bufferedWriter(Charsets.UTF_8).use { w ->
            w.write("﻿")
            w.write(res.getString(R.string.csv_sets_header))
            w.newLine()
            for (row in sets) {
                val s = row.set
                w.write(
                    listOf(
                        Fmt.toLocalDate(row.startedAt).toString(),
                        cell(row.workoutName),
                        cell(names[s.exerciseId] ?: s.exerciseId),
                        (s.position + 1).toString(),
                        when (s.setType) {
                            SetType.NORMAL -> ""
                            SetType.WARMUP -> res.getString(R.string.session_set_warmup)
                            SetType.DROP -> res.getString(R.string.session_set_drop)
                            SetType.FAILURE -> res.getString(R.string.session_set_failure)
                        },
                        dec(s.weightKg),
                        s.reps?.toString().orEmpty(),
                        s.durationSec?.toString().orEmpty(),
                        if (s.isRecord) "1" else "",
                    ).joinToString(";"),
                )
                w.newLine()
            }
        }
        listOf(wFile, sFile).map { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it) }
    }

    fun shareIntent(uris: List<Uri>): Intent {
        val res = context.withRussianLocale().resources
        val send = Intent(Intent.ACTION_SEND_MULTIPLE)
            .setType("text/csv")
            .putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            .putExtra(Intent.EXTRA_SUBJECT, res.getString(R.string.csv_subject))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(send, res.getString(R.string.csv_share))
    }
}
