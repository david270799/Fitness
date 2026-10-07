package com.iron.fitness.feature.daily.ui

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.iron.fitness.R
import com.iron.fitness.core.ui.IronIcons
import com.iron.fitness.core.ui.components.FlatProgressBar
import com.iron.fitness.core.ui.components.GhostButton
import com.iron.fitness.core.ui.components.IronCard
import com.iron.fitness.core.ui.components.IronIcon
import com.iron.fitness.core.ui.components.PrimaryButton
import com.iron.fitness.core.ui.components.SecondaryButton
import com.iron.fitness.core.ui.components.TextInputDialog
import com.iron.fitness.core.ui.theme.Iron
import com.iron.fitness.core.util.Fmt
import com.iron.fitness.feature.daily.data.ChallengeUi
import com.iron.fitness.feature.daily.data.ChallengeUnit
import kotlinx.coroutines.delay

/** Значение челленджа текстом: повторы — числом, секунды — «1:40». */
fun challengeValue(value: Int, unit: ChallengeUnit): String =
    if (unit == ChallengeUnit.SECONDS) Fmt.duration(value) else value.toString()

/** Шаг быстрой кнопки: «+5» или «+15 с». */
fun stepLabel(step: Int, unit: ChallengeUnit, secShort: String): String =
    if (unit == ChallengeUnit.SECONDS) "+$step $secShort" else "+$step"

/**
 * Карточка челленджа: название, серия, крупный счётчик «30 / 50», прогресс и быстрые кнопки.
 * [compact] — для экрана «Сегодня».
 */
@Composable
fun ChallengeCard(
    ui: ChallengeUi,
    onAdd: (Int) -> Unit,
    onOpen: () -> Unit,
    compact: Boolean = false,
) {
    val c = ui.challenge
    val s = ui.stats
    var customDialog by remember { mutableStateOf(false) }
    var stopwatch by remember { mutableStateOf(false) }
    val secShort = stringResource(R.string.unit_sec)
    IronCard(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        borderColor = if (ui.doneToday) Iron.colors.success else Iron.colors.border,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (ui.doneToday) {
                    IronIcon(IronIcons.CircleCheck, null, tint = Iron.colors.success, size = 20.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    c.name.uppercase(),
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(R.string.challenge_streak_best, s.streak, s.best),
                    style = MaterialTheme.typography.labelSmall,
                    color = Iron.colors.textSecondary,
                )
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    challengeValue(s.todayTotal, c.unit),
                    style = if (compact) Iron.numbers.medium else Iron.numbers.large,
                    color = if (ui.doneToday) Iron.colors.success else Iron.colors.text,
                )
                Text(
                    " / " + challengeValue(s.todayGoal, c.unit),
                    style = if (compact) Iron.numbers.small else Iron.numbers.medium,
                    color = Iron.colors.textSecondary,
                    modifier = Modifier.padding(bottom = if (compact) 4.dp else 6.dp),
                )
            }
            FlatProgressBar(ui.progress, height = if (compact) 6.dp else 10.dp, color = if (ui.doneToday) Iron.colors.success else Iron.colors.accent)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton(stepLabel(c.step1, c.unit, secShort), { onAdd(c.step1) }, modifier = Modifier.weight(1f).height(48.dp))
                PrimaryButton(stepLabel(c.step2, c.unit, secShort), { onAdd(c.step2) }, modifier = Modifier.weight(1f).height(48.dp))
                if (c.unit == ChallengeUnit.SECONDS) {
                    SecondaryButton(stringResource(R.string.challenge_timer), { stopwatch = true }, modifier = Modifier.weight(1f).height(48.dp))
                } else {
                    SecondaryButton(stringResource(R.string.challenge_custom), { customDialog = true }, modifier = Modifier.weight(1f).height(48.dp))
                }
            }
        }
    }
    if (customDialog) {
        TextInputDialog(
            title = stringResource(R.string.challenge_custom_title),
            initial = "",
            label = stringResource(R.string.challenge_custom_label),
            keyboardType = KeyboardType.Number,
            supportingText = stringResource(R.string.challenge_custom_hint),
            validate = { it.trim().toIntOrNull()?.let { v -> v != 0 } == true },
            onConfirm = { v -> v.trim().toIntOrNull()?.let(onAdd); customDialog = false },
            onDismiss = { customDialog = false },
        )
    }
    if (stopwatch) {
        StopwatchDialog(
            title = c.name,
            onSave = { sec -> if (sec > 0) onAdd(sec); stopwatch = false },
            onDismiss = { stopwatch = false },
        )
    }
}

/** Секундомер для статичных челленджей (планка): результат добавляется в секундах. */
@Composable
fun StopwatchDialog(title: String, onSave: (Int) -> Unit, onDismiss: () -> Unit) {
    var startedAt by remember { mutableStateOf<Long?>(null) }
    var accumulated by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val running = startedAt != null
    LaunchedEffect(running) {
        while (running) {
            now = SystemClock.elapsedRealtime()
            delay(100)
        }
    }
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    val elapsedMs = accumulated + (startedAt?.let { now - it } ?: 0L)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Iron.colors.surface,
        shape = RoundedCornerShape(8.dp),
        title = { Text(title.uppercase(), style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    Fmt.duration(elapsedMs / 1000),
                    style = Iron.numbers.huge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                PrimaryButton(
                    stringResource(if (running) R.string.run_pause else if (elapsedMs > 0) R.string.run_resume else R.string.action_start),
                    {
                        val t = SystemClock.elapsedRealtime()
                        val s = startedAt
                        if (s != null) {
                            accumulated += t - s
                            startedAt = null
                        } else {
                            startedAt = t
                            now = t
                        }
                    },
                    icon = if (running) IronIcons.Pause else IronIcons.Play,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            GhostButton(stringResource(R.string.action_save), {
                val total = accumulated + (startedAt?.let { SystemClock.elapsedRealtime() - it } ?: 0L)
                onSave((total / 1000).toInt())
            }, enabled = elapsedMs >= 1000)
        },
        dismissButton = { GhostButton(stringResource(R.string.action_cancel), onDismiss, color = Iron.colors.textSecondary) },
    )
}
