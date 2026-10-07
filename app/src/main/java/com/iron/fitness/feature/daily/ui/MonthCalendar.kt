package com.iron.fitness.feature.daily.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.iron.fitness.R
import com.iron.fitness.core.domain.Challenges
import com.iron.fitness.core.domain.GoalChange
import com.iron.fitness.core.ui.IronIcons
import com.iron.fitness.core.ui.components.IronIconButton
import com.iron.fitness.core.ui.theme.Iron
import com.iron.fitness.core.util.Fmt
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/** Календарь месяца: выполненные дни — акцентом, частично — приглушённо, сегодня — рамкой. */
@Composable
fun MonthCalendar(
    month: YearMonth,
    totals: Map<Long, Int>,
    goals: List<GoalChange>,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    today: LocalDate = LocalDate.now(),
) {
    val accent = Iron.colors.accent
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IronIconButton(IronIcons.ChevronLeft, stringResource(R.string.action_back), onPrev)
            Text(
                Fmt.monthYear(month.atDay(1)).uppercase(),
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            IronIconButton(IronIcons.ChevronRight, stringResource(R.string.action_more), onNext, enabled = month < YearMonth.from(today))
        }
        Row(Modifier.fillMaxWidth()) {
            DayOfWeek.entries.forEach { d ->
                Text(
                    Fmt.dayOfWeekShort(d),
                    style = MaterialTheme.typography.labelSmall,
                    color = Iron.colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        val first = month.atDay(1)
        val offset = first.dayOfWeek.value - 1
        val cells = offset + month.lengthOfMonth()
        val rows = (cells + 6) / 7
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (c in 0 until 7) {
                    val index = r * 7 + c
                    val dayNum = index - offset + 1
                    Box(Modifier.weight(1f).aspectRatio(1f)) {
                        if (dayNum in 1..month.lengthOfMonth()) {
                            val date = month.atDay(dayNum)
                            val epoch = date.toEpochDay()
                            val total = totals[epoch] ?: 0
                            val goal = Challenges.goalOn(epoch, goals)
                            val done = goal in 1..total
                            val future = date.isAfter(today)
                            val bg = when {
                                done -> accent
                                total > 0 -> accent.copy(alpha = 0.3f)
                                future -> Color.Transparent
                                else -> Iron.colors.surfaceHigh
                            }
                            val shape = RoundedCornerShape(3.dp)
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight()
                                    .clip(shape)
                                    .background(bg)
                                    .then(if (date == today) Modifier.border(2.dp, Iron.colors.text, shape) else Modifier),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    dayNum.toString(),
                                    style = Iron.numbers.tiny,
                                    color = when {
                                        done -> Iron.colors.onAccent
                                        future -> Iron.colors.textSecondary
                                        else -> Iron.colors.text
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Столбики за 7 дней с линией цели. */
@Composable
fun WeekBars(week: List<Pair<LocalDate, Int>>, goals: List<GoalChange>) {
    val maxValue = maxOf(week.maxOfOrNull { it.second } ?: 0, week.maxOfOrNull { Challenges.goalOn(it.first.toEpochDay(), goals) } ?: 0, 1)
    Row(
        Modifier.fillMaxWidth().height(120.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        week.forEach { (date, value) ->
            val goal = Challenges.goalOn(date.toEpochDay(), goals)
            val done = goal in 1..value
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (value > 0) value.toString() else "", style = Iron.numbers.tiny, color = Iron.colors.textSecondary, maxLines = 1)
                Spacer(Modifier.height(2.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height((84f * value / maxValue).coerceAtLeast(2f).dp)
                        .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                        .background(if (done) Iron.colors.accent else if (value > 0) Iron.colors.accent.copy(alpha = 0.4f) else Iron.colors.surfaceHigh),
                )
                Text(
                    Fmt.dayOfWeekShort(date.dayOfWeek),
                    style = MaterialTheme.typography.labelSmall,
                    color = Iron.colors.textSecondary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
