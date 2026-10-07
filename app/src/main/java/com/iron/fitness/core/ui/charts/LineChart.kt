package com.iron.fitness.core.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.iron.fitness.core.ui.theme.Iron

/** Точка графика: x — произвольная монотонная величина (например, день), y — значение. */
data class ChartPoint(val x: Double, val y: Double, val label: String? = null)

/**
 * Простой линейный график на Canvas: сетка, линия, точки, подписи мин/макс и крайних дат.
 * Без градиентов и теней — в стиле приложения.
 */
@Composable
fun LineChart(
    points: List<ChartPoint>,
    modifier: Modifier = Modifier,
    height: Dp = 180.dp,
    lineColor: Color = Iron.colors.accent,
    formatY: (Double) -> String = { it.toString() },
    goal: Double? = null,
    goalColor: Color = Iron.colors.success,
) {
    val textMeasurer = rememberTextMeasurer()
    val gridColor = Iron.colors.border
    val labelColor = Iron.colors.textSecondary
    val pointFill = Iron.colors.surface
    val labelStyle = Iron.numbers.tiny.copy(color = labelColor)
    val sorted = remember(points) { points.sortedBy { it.x } }

    Canvas(modifier.fillMaxWidth().height(height)) {
        if (sorted.isEmpty()) return@Canvas
        val values = sorted.map { it.y } + listOfNotNull(goal)
        var minY = values.min()
        var maxY = values.max()
        if (maxY - minY < 1e-6) {
            minY -= 1.0
            maxY += 1.0
        }
        val pad = (maxY - minY) * 0.12
        minY -= pad
        maxY += pad
        val minX = sorted.first().x
        val maxX = sorted.last().x
        val spanX = (maxX - minX).takeIf { it > 1e-9 } ?: 1.0

        val maxLabel = textMeasurer.measure(formatY(maxY - pad), labelStyle)
        val minLabel = textMeasurer.measure(formatY(minY + pad), labelStyle)
        val leftPad = maxOf(maxLabel.size.width, minLabel.size.width) + 8.dp.toPx()
        val bottomPad = 20.dp.toPx()
        val topPad = 8.dp.toPx()
        val chartW = size.width - leftPad - 6.dp.toPx()
        val chartH = size.height - bottomPad - topPad

        fun px(x: Double) = leftPad + ((x - minX) / spanX * chartW).toFloat()
        fun py(y: Double) = topPad + ((maxY - y) / (maxY - minY) * chartH).toFloat()

        // Сетка: 4 горизонтальные линии.
        for (i in 0..3) {
            val y = topPad + chartH * i / 3f
            drawLine(gridColor, Offset(leftPad, y), Offset(leftPad + chartW, y), strokeWidth = 1f)
        }
        // Подписи макс/мин.
        drawText(maxLabel, topLeft = Offset(0f, py(maxY - pad) - maxLabel.size.height / 2f))
        drawText(minLabel, topLeft = Offset(0f, py(minY + pad) - minLabel.size.height / 2f))

        if (goal != null) {
            drawLine(
                goalColor,
                Offset(leftPad, py(goal)),
                Offset(leftPad + chartW, py(goal)),
                strokeWidth = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
            )
        }

        if (sorted.size == 1) {
            val p = sorted.first()
            drawCircle(lineColor, radius = 5.dp.toPx(), center = Offset(leftPad + chartW / 2, py(p.y)))
        } else {
            val path = Path()
            sorted.forEachIndexed { i, p ->
                val o = Offset(px(p.x), py(p.y))
                if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
            }
            drawPath(path, lineColor, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            if (sorted.size <= 40) {
                sorted.forEach { p ->
                    val o = Offset(px(p.x), py(p.y))
                    drawCircle(lineColor, radius = 4.dp.toPx(), center = o)
                    drawCircle(pointFill, radius = 2.dp.toPx(), center = o)
                }
            }
        }

        // Подписи первой и последней точки по оси X.
        val first = sorted.first().label
        val last = sorted.last().label
        val baseY = size.height - bottomPad + 4.dp.toPx()
        if (first != null) {
            val m = textMeasurer.measure(first, labelStyle)
            drawText(m, topLeft = Offset(leftPad, baseY))
        }
        if (last != null && sorted.size > 1) {
            val m = textMeasurer.measure(last, labelStyle)
            drawText(m, topLeft = Offset(leftPad + chartW - m.size.width, baseY))
        }
    }
}
