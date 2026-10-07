package com.iron.fitness.core.ui.charts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.coerceAtMost
import androidx.compose.ui.unit.dp
import com.iron.fitness.R
import com.iron.fitness.core.ui.theme.Iron
import com.iron.fitness.core.util.Fmt
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Тепловая карта активности: колонки — недели (слева старые), строки — дни недели (Пн…Вс).
 * [levels] — значение 0…[maxLevel] на дату.
 */
@Composable
fun ActivityHeatmap(
    levels: Map<LocalDate, Int>,
    modifier: Modifier = Modifier,
    weeks: Int = 16,
    today: LocalDate = LocalDate.now(),
    maxLevel: Int = 3,
) {
    val accent = Iron.colors.accent
    val empty = Iron.colors.surfaceHigh
    val start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks((weeks - 1).toLong())
    val gap = 3.dp
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val labelWidth = 24.dp
            val cell = ((maxWidth - labelWidth - gap * (weeks - 1)) / weeks).coerceAtMost(22.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                Column(verticalArrangement = Arrangement.spacedBy(gap), modifier = Modifier.width(labelWidth - gap)) {
                    DayOfWeek.entries.forEach { d ->
                        Box(Modifier.height(cell), contentAlignment = Alignment.CenterStart) {
                            if (d == DayOfWeek.MONDAY || d == DayOfWeek.WEDNESDAY || d == DayOfWeek.FRIDAY) {
                                Text(Fmt.dayOfWeekShort(d), style = MaterialTheme.typography.labelSmall, color = Iron.colors.textSecondary, maxLines = 1)
                            }
                        }
                    }
                }
                for (w in 0 until weeks) {
                    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                        for (d in 0 until 7) {
                            val date = start.plusWeeks(w.toLong()).plusDays(d.toLong())
                            val future = date.isAfter(today)
                            val level = (levels[date] ?: 0).coerceIn(0, maxLevel)
                            val color = when {
                                future -> Color.Transparent
                                level == 0 -> empty
                                else -> accent.copy(alpha = 0.35f + 0.65f * level / maxLevel)
                            }
                            Box(
                                Modifier
                                    .size(cell)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(color),
                            )
                        }
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text(stringResource(R.string.heatmap_less), style = MaterialTheme.typography.labelSmall, color = Iron.colors.textSecondary)
            Spacer(Modifier.width(6.dp))
            for (l in 0..maxLevel) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (l == 0) empty else accent.copy(alpha = 0.35f + 0.65f * l / maxLevel)),
                )
                Spacer(Modifier.width(3.dp))
            }
            Spacer(Modifier.width(3.dp))
            Text(stringResource(R.string.heatmap_more), style = MaterialTheme.typography.labelSmall, color = Iron.colors.textSecondary)
        }
    }
}
