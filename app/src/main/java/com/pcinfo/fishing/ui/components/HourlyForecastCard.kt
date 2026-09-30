package com.pcinfo.fishing.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pcinfo.fishing.domain.engine.beaufortLevel
import com.pcinfo.fishing.domain.engine.windDirectionLabel
import com.pcinfo.fishing.domain.model.HourlyWeather
import com.pcinfo.fishing.domain.model.TimeWindow
import com.pcinfo.fishing.ui.util.formatClock
import com.pcinfo.fishing.ui.util.formatDayLabel
import com.pcinfo.fishing.ui.util.weatherCodeLabel

/**
 * 未来 24 小时逐小时预报，同时充当时间轴的**精确选择器**。
 *
 * 每行自上而下：时间 · 天气 · 气温(℃) · 气压(hPa) · 风向 · 风力 · 雨量(mm) · 降水概率(%)。
 * - 点击任意一列即把该小时设为评估时刻，整页建议随之重算；
 * - 落在推荐出钓窗口内的列显示绿底，被选中的列显示主色描边。
 */
@Composable
fun HourlyForecastCard(
    hourly: List<HourlyWeather>,
    currentEpochMs: Long,
    selectedEpochMs: Long,
    bestWindow: TimeWindow?,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val points = hourly
        .filter { it.epochMs >= currentEpochMs - 3_600_000L }
        .take(25)
    if (points.isEmpty()) return

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "未来 24 小时预报",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.padding(top = 4.dp))
            Text(
                text = "点击任意一小时即可切换评估时刻；绿底为推荐出钓窗口，蓝框为当前选中。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.padding(top = 10.dp))
            Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                points.forEach { point ->
                    val inWindow = bestWindow?.let {
                        point.epochMs >= it.startEpochMs - 3_600_000L && point.epochMs <= it.endEpochMs
                    } ?: false
                    val isSelected = point.epochMs == selectedEpochMs
                    HourColumn(
                        point = point,
                        highlighted = inWindow,
                        selected = isSelected,
                        onClick = { onSelect(point.epochMs) }
                    )
                }
            }
            Text(
                text = "已选 ${formatDayLabel(selectedEpochMs)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun HourColumn(
    point: HourlyWeather,
    highlighted: Boolean,
    selected: Boolean,
    onClick: () -> Unit
) {
    val container = when {
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
        highlighted -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)
        else -> Color.Transparent
    }
    Column(
        modifier = Modifier
            .width(66.dp)
            .padding(end = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(container)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = formatClock(point.epochMs),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (selected) MaterialTheme.colorScheme.primary else Color.Unspecified
        )
        Text(
            text = weatherCodeLabel(point.weatherCode),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "%.0f°".format(point.temperatureC),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "%.0f".format(point.pressureHpa),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = windDirectionLabel(point.windDirectionDeg).removeSuffix("风"),
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = "${beaufortLevel(point.windSpeedKmh)}级",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "%.1f".format(point.precipitationMm),
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = "${point.precipitationProbabilityPercent}%",
            style = MaterialTheme.typography.bodySmall,
            color = if (point.precipitationProbabilityPercent >= 50) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}
