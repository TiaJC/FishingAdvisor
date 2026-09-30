package com.pcinfo.fishing.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pcinfo.fishing.domain.engine.FishingScoreEngine
import com.pcinfo.fishing.domain.model.HourlyWeather
import com.pcinfo.fishing.domain.model.WeatherSnapshot
import com.pcinfo.fishing.ui.util.formatClock
import com.pcinfo.fishing.ui.util.weatherColor
import kotlin.math.abs
import kotlin.math.roundToInt

private val CHART_HEIGHT = 198.dp
private val GUTTER_WIDTH = 36.dp
private val RIGHT_PAD = 12.dp

private val WEATHER_TOP = 4.dp
private val WEATHER_BOTTOM = 44.dp
private val PRESSURE_TOP = 54.dp
private val PRESSURE_BOTTOM = 128.dp
private val WIND_TOP = 136.dp
private val WIND_BOTTOM = 190.dp
private val ARROW_CENTER_Y = 148.dp
private val BAR_BOTTOM = 188.dp
private val BAR_MAX_HEIGHT = 18.dp

/**
 * 24 小时组合时间轴：气压、风向、天气三条序列共用同一条时间轴。
 *
 * 之所以要把它们画在一起而不是分成三张图：
 * 钓鱼判断本质上是「同一时刻多个变量共同决定鱼口」，
 * 分成三张图用户得自己在脑中对齐时间，很容易错位。
 *
 * 交互：
 * - 左右拖动画布平移视口，可浏览过去与未来；
 * - 点击（或点住拖动）选择某一小时，选中结果会驱动整页建议重算。
 *
 * 实现上刻意不使用 Canvas 的文本绘制（需要 TextMeasurer，涉及实验性 API），
 * 轴标签与带名一律用 Compose Text 叠加，避免版本兼容问题。
 */
@Composable
fun CombinedTimelineChart(
    points: List<HourlyWeather>,
    snapshot: WeatherSnapshot,
    selectedEpochMs: Long,
    nowEpochMs: Long,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
    visibleHours: Int = 24
) {
    if (points.size < 2) return

    val sorted = remember(points) { points.sortedBy { it.epochMs } }
    val window = visibleHours.coerceIn(4, sorted.size)
    val maxStart = (sorted.size - window).coerceAtLeast(0)

    val selectedIndex = remember(sorted, selectedEpochMs) { sorted.nearestIndex(selectedEpochMs) }
    val nowIndex = remember(sorted, nowEpochMs) { sorted.nearestIndex(nowEpochMs) }

    var startIndex by remember(sorted, window) { mutableIntStateOf(0) }

    // 选中时刻若不在视口内，自动把视口滚过去（例如点击了预报表中的未来时刻）
    LaunchedEffect(sorted, window, selectedIndex) {
        val s = startIndex
        if (selectedIndex !in s..(s + window - 1)) {
            startIndex = (selectedIndex - window / 4).coerceIn(0, maxStart)
        }
    }

    val endIndex = (startIndex + window).coerceAtMost(sorted.size)
    val visible = sorted.subList(startIndex, endIndex)

    val primary = MaterialTheme.colorScheme.primary
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = onSurfaceVariant.copy(alpha = 0.18f)
    val nightColor = onSurfaceVariant.copy(alpha = 0.10f)
    val nowColor = MaterialTheme.colorScheme.error
    val rainColor = Color(0xFF1E88E5)
    val windColor = Color(0xFF00897B)

    val sunriseSec = snapshot.sunriseEpochMs?.let {
        FishingScoreEngine.localSecondsOfDay(it, snapshot.utcOffsetSeconds)
    }
    val sunsetSec = snapshot.sunsetEpochMs?.let {
        FishingScoreEngine.localSecondsOfDay(it, snapshot.utcOffsetSeconds)
    }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val chartWidthDp = (maxWidth - GUTTER_WIDTH - RIGHT_PAD).coerceAtLeast(48.dp)
        val stepDp: Dp = if (visible.size > 1) chartWidthDp / (visible.size - 1) else chartWidthDp

        Column(modifier = Modifier.fillMaxWidth()) {

            Box(modifier = Modifier.fillMaxWidth().height(CHART_HEIGHT)) {

                Canvas(
                    modifier = Modifier
                        .matchParentSize()
                        .pointerInput(sorted, window, startIndex) {
                            detectTapGestures { offset ->
                                val gut = GUTTER_WIDTH.toPx()
                                val step = stepDp.toPx()
                                if (step <= 0f) return@detectTapGestures
                                val i = ((offset.x - gut) / step).roundToInt()
                                    .coerceIn(0, visible.lastIndex)
                                onSelect(visible[i].epochMs)
                            }
                        }
                        .pointerInput(sorted, window) {
                            detectDragGestures { _, dragAmount ->
                                val step = stepDp.toPx()
                                if (step <= 0f) return@detectDragGestures
                                // 手指右移 → 视口左移（看更早的时段）
                                val delta = -(dragAmount.x / step).roundToInt()
                                startIndex = (startIndex + delta).coerceIn(0, maxStart)
                            }
                        }
                ) {
                    val gut = GUTTER_WIDTH.toPx()
                    val step = stepDp.toPx()
                    fun xOf(index: Int): Float = gut + step * index

                    val wTop = WEATHER_TOP.toPx()
                    val wBottom = WEATHER_BOTTOM.toPx()
                    val pTop = PRESSURE_TOP.toPx()
                    val pBottom = PRESSURE_BOTTOM.toPx()
                    val windTop = WIND_TOP.toPx()
                    val windBottom = WIND_BOTTOM.toPx()
                    val arrowY = ARROW_CENTER_Y.toPx()
                    val barBottom = BAR_BOTTOM.toPx()
                    val barMax = BAR_MAX_HEIGHT.toPx()
                    val half = step / 2f

                    // ---- 夜间底纹：跨三条带对齐，便于看出晨昏位置
                    visible.forEachIndexed { i, point ->
                        if (isNight(point, snapshot.utcOffsetSeconds, sunriseSec, sunsetSec)) {
                            drawRect(
                                color = nightColor,
                                topLeft = Offset(xOf(i) - half, 0f),
                                size = Size(step, size.height)
                            )
                        }
                    }

                    // ---- 选中列高亮
                    val selVisible = selectedIndex - startIndex
                    if (selVisible in visible.indices) {
                        drawRect(
                            color = primary.copy(alpha = 0.12f),
                            topLeft = Offset(xOf(selVisible) - half, 0f),
                            size = Size(step, size.height)
                        )
                    }

                    // ---- 第一条带：天气 + 降水
                    val precipMax = visible.maxOf { it.precipitationMm }.let { if (it < 1.0) 1.0 else it }
                    visible.forEachIndexed { i, point ->
                        drawRect(
                            color = weatherColor(point.weatherCode).copy(alpha = 0.35f),
                            topLeft = Offset(xOf(i) - half, wTop),
                            size = Size(step, wBottom - wTop)
                        )
                        if (point.precipitationMm > 0.0) {
                            val h = (point.precipitationMm / precipMax * (wBottom - wTop)).toFloat()
                            drawRect(
                                color = rainColor.copy(alpha = 0.85f),
                                topLeft = Offset(xOf(i) - step * 0.26f, wBottom - h),
                                size = Size(step * 0.52f, h)
                        )
                        }
                    }

                    // ---- 第二条带：气压曲线
                    val values = visible.map { it.pressureHpa }
                    val min = values.min()
                    val max = values.max()
                    val range = (max - min).let { if (abs(it) < 0.5) 0.5f else it.toFloat() }
                    for (i in 0..3) {
                        val y = pTop + (pBottom - pTop) * (i / 3f)
                        drawLine(
                            color = gridColor,
                            start = Offset(gut, y),
                            end = Offset(size.width - RIGHT_PAD.toPx(), y),
                            strokeWidth = 1f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f)
                        )
                    }
                    val pressurePath = Path().apply {
                        visible.forEachIndexed { i, point ->
                            val px = xOf(i)
                            val py = pTop + (pBottom - pTop) *
                                (1f - ((point.pressureHpa - min).toFloat() / range))
                            if (i == 0) moveTo(px, py) else lineTo(px, py)
                        }
                    }
                    drawPath(pressurePath, color = primary, style = Stroke(width = 2.5.dp.toPx()))
                    visible.forEachIndexed { i, point ->
                        val px = xOf(i)
                        val py = pTop + (pBottom - pTop) *
                            (1f - ((point.pressureHpa - min).toFloat() / range))
                        drawCircle(
                            color = primary,
                            radius = if (i == selVisible) 4.dp.toPx() else 1.8.dp.toPx(),
                            center = Offset(px, py)
                        )
                    }

                    // ---- 第三条带：风向箭头 + 风力条
                    val speedMax = visible.maxOf { it.windSpeedKmh }.let { if (it < 10.0) 10.0 else it }
                    visible.forEachIndexed { i, point ->
                        val px = xOf(i)
                        val barH = (point.windSpeedKmh / speedMax * barMax).toFloat()
                        drawRect(
                            color = windColor.copy(alpha = 0.35f),
                            topLeft = Offset(px - step * 0.22f, barBottom - barH),
                            size = Size(step * 0.44f, barH)
                        )
                        // 气象风向是「风的来向」，箭头改画为去向更直观
                        rotate(
                            degrees = point.windDirectionDeg.toFloat() + 180f,
                            pivot = Offset(px, arrowY)
                        ) {
                            val r = (step * 0.34f).coerceAtMost(7.dp.toPx())
                            drawLine(
                                color = windColor,
                                start = Offset(px - r, arrowY),
                                end = Offset(px + r * 0.35f, arrowY),
                                strokeWidth = 1.6.dp.toPx()
                            )
                            drawPath(
                                path = Path().apply {
                                    moveTo(px + r, arrowY)
                                    lineTo(px + r * 0.25f, arrowY - r * 0.6f)
                                    lineTo(px + r * 0.25f, arrowY + r * 0.6f)
                                    close()
                                },
                                color = windColor
                            )
                        }
                    }

                    // ---- 当前时刻参考线
                    val nowVisible = nowIndex - startIndex
                    if (nowVisible in visible.indices && nowVisible != selVisible) {
                        drawLine(
                            color = nowColor,
                            start = Offset(xOf(nowVisible), 0f),
                            end = Offset(xOf(nowVisible), size.height),
                            strokeWidth = 1.5f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f)
                        )
                    }

                    // ---- 选中时刻游标（贯穿三条带）
                    if (selVisible in visible.indices) {
                        drawLine(
                            color = primary,
                            start = Offset(xOf(selVisible), 0f),
                            end = Offset(xOf(selVisible), size.height),
                            strokeWidth = 2.5f
                        )
                    }
                }

                BandLabel(text = "天气", top = WEATHER_TOP, width = GUTTER_WIDTH)
                BandLabel(text = "气压", top = PRESSURE_TOP + 14.dp, width = GUTTER_WIDTH)
                BandLabel(text = "风向", top = WIND_TOP, width = GUTTER_WIDTH)
                BandLabel(text = "风力", top = BAR_BOTTOM - 30.dp, width = GUTTER_WIDTH)
            }

            // ---- 时间轴刻度：用 Text 叠加，避免 Canvas 文本 API 的兼容问题
            Box(modifier = Modifier.fillMaxWidth().height(22.dp)) {
                val labelStep = (window / 7).coerceAtLeast(1)
                visible.forEachIndexed { i, point ->
                    if (i % labelStep == 0 || i == visible.lastIndex) {
                        val labelX = GUTTER_WIDTH + stepDp * i - 18.dp
                        Text(
                            text = formatClock(point.epochMs),
                            fontSize = 9.sp,
                            lineHeight = 10.sp,
                            color = onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .offset(x = labelX, y = 2.dp)
                                .width(36.dp)
                        )
                    }
                }
            }

            LegendRow()
        }
    }
}

@Composable
private fun BandLabel(text: String, top: Dp, width: Dp) {
    Text(
        text = text,
        fontSize = 9.sp,
        lineHeight = 10.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.End,
        modifier = Modifier
            .offset(x = 0.dp, y = top)
            .width(width - 4.dp)
            .padding(end = 4.dp)
    )
}

@Composable
private fun LegendRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LegendItem(color = MaterialTheme.colorScheme.primary, text = "气压曲线")
        Spacer(modifier = Modifier.width(10.dp))
        LegendItem(color = Color(0xFF00897B), text = "风向箭头（指向风的去向）")
        Spacer(modifier = Modifier.width(10.dp))
        LegendItem(color = Color(0xFF1E88E5), text = "降水柱")
    }
    Text(
        text = "底色为天气现象，灰底为夜间；左右拖动可查看过去与未来，点击选中某一小时",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun LegendItem(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.Canvas(modifier = Modifier.width(10.dp).height(10.dp)) {
            drawRect(color = color)
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

/** 判断该小时属于夜间（用于底纹） */
private fun isNight(
    point: HourlyWeather,
    utcOffsetSeconds: Int,
    sunriseSec: Int?,
    sunsetSec: Int?
): Boolean {
    val s = FishingScoreEngine.localSecondsOfDay(point.epochMs, utcOffsetSeconds)
    if (sunriseSec == null || sunsetSec == null) return s < 5 * 3600 || s >= 19 * 3600
    return s < sunriseSec - 1800 || s > sunsetSec + 1800
}

private fun List<HourlyWeather>.nearestIndex(epochMs: Long): Int {
    var bestIndex = 0
    var bestDiff = Long.MAX_VALUE
    forEachIndexed { index, point ->
        val diff = abs(point.epochMs - epochMs)
        if (diff < bestDiff) {
            bestDiff = diff
            bestIndex = index
        }
    }
    return bestIndex
}
