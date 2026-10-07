package com.iron.fitness.feature.workouts.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.iron.fitness.R
import com.iron.fitness.core.ui.IronIcons
import com.iron.fitness.core.ui.components.IronCard
import com.iron.fitness.core.ui.components.IronIcon
import com.iron.fitness.core.ui.theme.Iron
import com.iron.fitness.core.util.Fmt
import com.iron.fitness.feature.workouts.data.WorkoutSummaryRow
import com.iron.fitness.feature.workouts.data.WorkoutType

/** Строка журнала: дата, название, длительность, объём, рекорды. */
@Composable
fun WorkoutRow(row: WorkoutSummaryRow, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val w = row.workout
    IronCard(onClick = onClick, modifier = modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    Fmt.dateTime(w.startedAt) + " · " + Fmt.dayOfWeek(Fmt.toLocalDate(w.startedAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = Iron.colors.textSecondary,
                )
                Text(w.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val parts = buildList {
                    add(Fmt.durationWords(w.durationSec))
                    if (w.type == WorkoutType.STRENGTH) {
                        add(pluralStringResource(R.plurals.sets, row.setCount, row.setCount))
                        if (w.volumeKg > 0) add(stringResource(R.string.value_kg, Fmt.grouped(w.volumeKg)))
                    }
                    w.caloriesKcal?.takeIf { it > 0 }?.let { add(stringResource(R.string.approx_kcal, it.toInt())) }
                }
                Text(parts.joinToString(" · "), style = Iron.numbers.tiny, color = Iron.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (row.recordCount > 0) {
                Spacer(Modifier.width(8.dp))
                IronIcon(IronIcons.Trophy, null, tint = Iron.colors.accentText, size = 18.dp)
                Spacer(Modifier.width(2.dp))
                Text(row.recordCount.toString(), style = Iron.numbers.small, color = Iron.colors.accentText)
            }
            Spacer(Modifier.width(4.dp))
            IronIcon(IronIcons.ChevronRight, null, tint = Iron.colors.textSecondary, size = 18.dp)
        }
    }
}
