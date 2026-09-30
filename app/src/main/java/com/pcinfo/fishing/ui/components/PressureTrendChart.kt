package com.pcinfo.fishing.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.pcinfo.fishing.domain.model.HourlyWeather
import com.pcinfo.fishing.ui.util.formatClock
import com.pcinfo.fishing.ui.util.formatDayLabel
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 可拖动的气压趋势图。
 *
 * 交互：在图上左右拖动即可平移视口查看过去与未来的气压变化，
 * 拖动同时会更新游标，实时读出该时刻的时间与气压值。
 *
 * @param visibleHours 视口内显示的小时数（拖动可浏览完整序列）
 */
@Composable
fun PressureTrendChart(
    points: List<HourlyWeather>,
    currentEpochMs: Long,
    modifier: Modifier = Modifier,
    visibleHours: Int = 12
) {
    if (points.size < 2) return

    val window = visibleHours.coerceIn(2, points.size)
    val maxStart = (points.size - window).coerceAtLeast(0)
    val currentIndex = points.indexOfFirst { it.epochMs >= currentEpochMs }
        .let { if (it < 0) points.lastIndex else it }

    // 初始视口：当前时刻放在左侧约 1/4 处，默认展示以未来为主
    var startIndex by remember(points, window) {
        mutableIntStateOf((currentIndex - window / 4).coerceIn(0, maxStart))
    }
    var cursorIndex by remember(points) { mutableIntStateOf(currentIndex) }

    val endIndex = (startIndex + window).coerceAtMost(points.size)
    val visible = points.subList(startIndex, endIndex)
    val cursorPoint = points.getOrNull(cursorIndex.coerceIn(0, points.lastIndex))

    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val markerColor = MaterialTheme.colorScheme.error
    val cursorColor = MaterialTheme.colorScheme.secondary

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .pointerInput(points, window) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val pxPerPoint = size.width / (window - 1).toFloat()
                            cursorIndex = (startIndex + (offset.x / pxPerPoint).roundToInt())
                                .coerceIn(0, points.lastIndex)
                        },
                        onDragEnd = { },
                        onDragCancel = { },
                        onDrag = { change, dragAmount ->
                            val pxPerPoint = size.width / (window - 1).toFloat()
                            // 手指右移 → 视口左移（查看更早的时段）
                            val delta = -(dragAmount.x / pxPerPoint).roundToInt()
                            startIndex = (startIndex + delta).coerceIn(0, maxStart)
                            cursorIndex = (startIndex + (change.position.x / pxPerPoint).roundToInt())
                                .coerceIn(0, points.lastIndex)
                        }
                    )
                }
        ) {
            val values = visible.map { it.pressureHpa.toFloat() }
            val min = values.min()
            val max = values.max()
            val range = (max - min).let { if (abs(it) < 0.5f) 1f else it }
            val paddingX = 14.dp.toPx()
            val paddingY = 16.dp.toPx()
            val chartWidth = size.width - paddingX * 2
            val chartHeight = size.height - paddingY * 2

            for (i in 0..4) {
                val y = paddingY + chartHeight * (i / 4f)
                drawLine(
                    color = gridColor,
                    start = Offset(paddingX, y),
                    end = Offset(size.width - paddingX, y),
                    strokeWidth = 1f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                )
            }

            val stepX = chartWidth / (visible.size - 1).coerceAtLeast(1)
            val offsets = visible.mapIndexed { index, point ->
                val x = paddingX + stepX * index
                val y = paddingY + chartHeight * (1f - ((point.pressureHpa.toFloat() - min) / range))
                x to y
            }

            drawPath(
                path = Path().apply {
                    moveTo(offsets.first().first, offsets.first().second)
                    offsets.drop(1).forEach { (x, y) -> lineTo(x, y) }
                },
                color = lineColor,
                style = Stroke(width = 3.dp.toPx())
            )

            // 当前时刻竖线
            val currentVisibleIndex = currentIndex - startIndex
            if (currentVisibleIndex in offsets.indices) {
                val (x, y) = offsets[currentVisibleIndex]
                drawLine(
                    color = markerColor,
                    start = Offset(x, paddingY - 6.dp.toPx()),
                    end = Offset(x, size.height - paddingY + 6.dp.toPx()),
                    strokeWidth = 2f
                )
                drawCircle(color = markerColor, radius = 5.dp.toPx(), center = Offset(x, y))
            }

            // 拖动游标
            val cursorVisibleIndex = cursorIndex - startIndex
            if (cursorVisibleIndex in offsets.indices && cursorVisibleIndex != currentVisibleIndex) {
                val (x, y) = offsets[cursorVisibleIndex]
                drawLine(
                    color = cursorColor,
                    start = Offset(x, paddingY - 6.dp.toPx()),
                    end = Offset(x, size.height - paddingY + 6.dp.toPx()),
                    strokeWidth = 2f
                )
                drawCircle(color = cursorColor, radius = 6.dp.toPx(), center = Offset(x, y))
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "可见 ${formatDayLabel(visible.first().epochMs)} → ${formatClock(visible.last().epochMs)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            cursorPoint?.let {
                Text(
                    text = "游标 ${formatDayLabel(it.epochMs)} · %.1f hPa".format(it.pressureHpa),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
        Text(
            text = "左右拖动画布可查看过去与未来时段的气压（红竖线为当前时刻，灰线为游标读数）",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}
