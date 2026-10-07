package com.iron.fitness.core.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.iron.fitness.core.ui.theme.Iron

data class BarPoint(val label: String, val value: Double)

/**
 * Столбчатая диаграмма на Canvas: подписи снизу (через одну, если тесно), значение максимума слева.
 */
@Composable
fun BarChart(
    bars: List<BarPoint>,
    modifier: Modifier = Modifier,
    height: Dp = 160.dp,
    color: Color = Iron.colors.accent,
    formatValue: (Double) -> String = { it.toInt().toString() },
) {
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = Iron.numbers.tiny.copy(color = Iron.colors.textSecondary)
    val grid = Iron.colors.border
    val empty = Iron.colors.surfaceHigh
    Canvas(modifier.fillMaxWidth().height(height)) {
        if (bars.isEmpty()) return@Canvas
        val maxV = bars.maxOf { it.value }.takeIf { it > 0 } ?: 1.0
        val maxLabel = textMeasurer.measure(formatValue(maxV), labelStyle)
        val leftPad = maxLabel.size.width + 6.dp.toPx()
        val bottomPad = 18.dp.toPx()
        val topPad = maxLabel.size.height / 2f
        val chartW = size.width - leftPad
        val chartH = size.height - bottomPad - topPad
        drawLine(grid, Offset(leftPad, topPad), Offset(size.width, topPad), 1f)
        drawLine(grid, Offset(leftPad, topPad + chartH), Offset(size.width, topPad + chartH), 1f)
        drawText(maxLabel, topLeft = Offset(0f, 0f))
        val slot = chartW / bars.size
        val barW = (slot * 0.7f).coerceAtMost(28.dp.toPx())
        // Подписи: не чаще, чем помещаются.
        val maxLabels = (chartW / 34.dp.toPx()).toInt().coerceAtLeast(1)
        val step = ((bars.size + maxLabels - 1) / maxLabels).coerceAtLeast(1)
        bars.forEachIndexed { i, b ->
            val x = leftPad + slot * i + (slot - barW) / 2
            val h = (b.value / maxV * chartH).toFloat()
            if (b.value > 0) {
                drawRoundRect(color, Offset(x, topPad + chartH - h), Size(barW, h), CornerRadius(2.dp.toPx()))
            } else {
                drawRoundRect(empty, Offset(x, topPad + chartH - 2.dp.toPx()), Size(barW, 2.dp.toPx()))
            }
            if (i % step == 0 || i == bars.lastIndex) {
                val m = textMeasurer.measure(b.label, labelStyle)
                val lx = (x + barW / 2 - m.size.width / 2).coerceIn(leftPad, size.width - m.size.width)
                drawText(m, topLeft = Offset(lx, topPad + chartH + 3.dp.toPx()))
            }
        }
    }
}
