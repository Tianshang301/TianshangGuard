package com.tianshang.guard.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Simple line chart using Compose Canvas.
 */
@Composable
fun TrendLineChart(
    data: List<Float>,
    modifier: Modifier = Modifier,
    lineColor: Color = Color(0xFFFF4444),
    maxValue: Float = data.maxOrNull()?.coerceAtLeast(1f) ?: 1f
) {
    if (data.isEmpty()) return

    Canvas(modifier = modifier.fillMaxWidth().height(120.dp)) {
        val width = size.width
        val height = size.height
        val padding = 16f
        val chartWidth = width - padding * 2
        val chartHeight = height - padding * 2

        // Draw grid lines
        val gridColor = Color.White.copy(alpha = 0.1f)
        for (i in 0..4) {
            val y = padding + chartHeight * i / 4
            drawLine(
                color = gridColor,
                start = Offset(padding, y),
                end = Offset(width - padding, y),
                strokeWidth = 1f
            )
        }

        // Draw data points and line
        if (data.size >= 2) {
            val path = Path()
            val stepX = chartWidth / (data.size - 1)

            data.forEachIndexed { index, value ->
                val x = padding + index * stepX
                val y = padding + chartHeight * (1 - value / maxValue)

                if (index == 0) {
                    path.moveTo(x, y)
                } else {
                    path.lineTo(x, y)
                }
            }

            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(width = 3f, cap = StrokeCap.Round)
            )
        }

        // Draw data points
        val stepX = if (data.size > 1) chartWidth / (data.size - 1) else 0f
        data.forEachIndexed { index, value ->
            val x = padding + index * stepX
            val y = padding + chartHeight * (1 - value / maxValue)

            drawCircle(
                color = lineColor,
                radius = 4f,
                center = Offset(x, y)
            )
        }
    }
}

/**
 * Simple bar chart using Compose Canvas.
 */
@Composable
fun BarChart(
    data: List<Pair<String, Float>>,
    modifier: Modifier = Modifier,
    barColor: Color = Color(0xFFFF4444),
    maxValue: Float = data.maxOfOrNull { it.second }?.coerceAtLeast(1f) ?: 1f
) {
    if (data.isEmpty()) return

    Canvas(modifier = modifier.fillMaxWidth().height(120.dp)) {
        val width = size.width
        val height = size.height
        val padding = 16f
        val chartWidth = width - padding * 2
        val chartHeight = height - padding * 2

        // Draw grid lines
        val gridColor = Color.White.copy(alpha = 0.1f)
        for (i in 0..4) {
            val y = padding + chartHeight * i / 4
            drawLine(
                color = gridColor,
                start = Offset(padding, y),
                end = Offset(width - padding, y),
                strokeWidth = 1f
            )
        }

        // Draw bars
        val barWidth = chartWidth / data.size * 0.7f
        val gap = chartWidth / data.size * 0.3f

        data.forEachIndexed { index, (_, value) ->
            val x = padding + index * (barWidth + gap) + gap / 2
            val barHeight = chartHeight * value / maxValue
            val y = padding + chartHeight - barHeight

            drawRect(
                color = barColor,
                topLeft = Offset(x, y),
                size = androidx.compose.ui.geometry.Size(barWidth, barHeight)
            )
        }
    }
}

/**
 * Pie chart using Compose Canvas.
 */
@Composable
fun PieChart(
    data: List<Pair<String, Float>>,
    modifier: Modifier = Modifier,
    colors: List<Color> = listOf(
        Color(0xFFFF4444),  // Red
        Color(0xFFFFAA00),  // Orange
        Color(0xFF4CAF50),  // Green
        Color(0xFF2196F3),  // Blue
    )
) {
    if (data.isEmpty()) return

    val total = data.sumOf { it.second.toDouble() }.toFloat()
    if (total == 0f) return

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val radius = minOf(width, height) / 2 * 0.8f
        val center = Offset(width / 2, height / 2)

        var startAngle = -90f

        data.forEachIndexed { index, (_, value) ->
            val sweepAngle = 360f * value / total
            val color = colors[index % colors.size]

            drawArc(
                color = color,
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = true,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2)
            )

            startAngle += sweepAngle
        }
    }
}
