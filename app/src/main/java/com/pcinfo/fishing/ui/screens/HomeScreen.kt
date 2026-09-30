package com.pcinfo.fishing.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pcinfo.fishing.domain.engine.beaufortLabel
import com.pcinfo.fishing.domain.engine.windDirectionLabel
import com.pcinfo.fishing.domain.model.AppSettings
import com.pcinfo.fishing.domain.model.FavoriteSpot
import com.pcinfo.fishing.domain.model.FishSpecies
import com.pcinfo.fishing.domain.model.HourlyWeather
import com.pcinfo.fishing.domain.model.TimelineSpan
import com.pcinfo.fishing.domain.model.WeatherSnapshot
import com.pcinfo.fishing.domain.usecase.FishingReport
import com.pcinfo.fishing.ui.ErrorReason
import com.pcinfo.fishing.ui.FishingUiState
import com.pcinfo.fishing.ui.components.CombinedTimelineChart
import com.pcinfo.fishing.ui.components.FactorRow
import com.pcinfo.fishing.ui.components.FishSpeciesSelector
import com.pcinfo.fishing.ui.components.HourlyForecastCard
import com.pcinfo.fishing.ui.components.ScoreRing
import com.pcinfo.fishing.ui.components.SpeciesAdviceCard
import com.pcinfo.fishing.ui.components.levelColor
import com.pcinfo.fishing.ui.util.accuracyLabel
import com.pcinfo.fishing.ui.util.formatClock
import com.pcinfo.fishing.ui.util.formatDayLabel
import com.pcinfo.fishing.ui.util.formatUpdatedAt
import com.pcinfo.fishing.ui.util.providerLabel
import com.pcinfo.fishing.ui.util.weatherCodeLabel
import kotlin.math.abs

/**
 * 首页：定位 + 24 小时时间轴 + 按选中时刻计算的钓鱼指数与建议。
 *
 * 核心交互是**选时**：时间轴上选中的小时决定了评分、钓位、水深与饵料建议，
 * 因此「明天早上六点值不值得去」可以直接查，而不只能看当前。
 */
@Composable
fun HomeScreen(
    state: FishingUiState,
    settings: AppSettings,
    selectedEpochMs: Long?,
    favorites: List<FavoriteSpot>,
    activeFavoriteId: String?,
    onSpeciesSelect: (FishSpecies) -> Unit,
    onTimeSelect: (Long) -> Unit,
    onSpanChange: (Int) -> Unit,
    onRefresh: () -> Unit,
    onAction: () -> Unit,
    onToggleFavorite: () -> Unit,
    onSelectFavorite: (FavoriteSpot) -> Unit,
    onUseCurrentLocation: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopBar(
            canRefresh = state !is FishingUiState.Loading,
            onRefresh = onRefresh
        )
        when (state) {
            is FishingUiState.Idle -> IdleView(onAction = onAction)
            is FishingUiState.Loading -> LoadingView(hint = state.hint)
            is FishingUiState.Success -> HomeContent(
                report = state.report,
                updatedAtMs = state.updatedAtMs,
                settings = settings,
                selectedEpochMs = selectedEpochMs ?: state.report.assessment.evaluatedAtEpochMs,
                favorites = favorites,
                activeFavoriteId = activeFavoriteId,
                onSpeciesSelect = onSpeciesSelect,
                onTimeSelect = onTimeSelect,
                onSpanChange = onSpanChange,
                onToggleFavorite = onToggleFavorite,
                onSelectFavorite = onSelectFavorite,
                onUseCurrentLocation = onUseCurrentLocation
            )

            is FishingUiState.Error -> ErrorView(
                message = state.message,
                reason = state.reason,
                onAction = onAction
            )
        }
    }
}

@Composable
private fun TopBar(canRefresh: Boolean, onRefresh: () -> Unit) {
    // Surface 不会自动撑满父容器宽度：不显式 fillMaxWidth 时它的背景会随标题文字宽度变化
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "钓鱼气象助手",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "选一个时刻，看那时值不值得下竿",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onRefresh, enabled = canRefresh) {
                Text("刷新")
            }
        }
    }
}

@Composable
private fun IdleView(onAction: () -> Unit) {
    CenteredMessage(
        title = "开始分析你所在位置的钓鱼条件",
        description = "将使用定位获取当地逐小时气压、风向、气温与降水，综合计算垂钓指数。" +
            "位置数据仅用于天气查询，不会上传。",
        actionText = "开始分析",
        onAction = onAction
    )
}

@Composable
private fun LoadingView(hint: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = hint, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ErrorView(message: String, reason: ErrorReason, onAction: () -> Unit) {
    CenteredMessage(
        title = when (reason) {
            ErrorReason.PERMISSION -> "需要定位权限"
            ErrorReason.LOCATION -> "无法获取位置"
            ErrorReason.NETWORK -> "气象数据获取失败"
            ErrorReason.UNKNOWN -> "分析失败"
        },
        description = message,
        actionText = when (reason) {
            ErrorReason.PERMISSION -> "授予定位权限"
            else -> "重试"
        },
        onAction = onAction
    )
}

@Composable
private fun CenteredMessage(
    title: String,
    description: String,
    actionText: String,
    onAction: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onAction) { Text(actionText) }
            }
        }
    }
}

@Composable
private fun HomeContent(
    report: FishingReport,
    updatedAtMs: Long,
    settings: AppSettings,
    selectedEpochMs: Long,
    favorites: List<FavoriteSpot>,
    activeFavoriteId: String?,
    onSpeciesSelect: (FishSpecies) -> Unit,
    onTimeSelect: (Long) -> Unit,
    onSpanChange: (Int) -> Unit,
    onToggleFavorite: () -> Unit,
    onSelectFavorite: (FavoriteSpot) -> Unit,
    onUseCurrentLocation: () -> Unit
) {
    val weather = report.weather
    val selectedPoint = rememberSelectedPoint(weather.hourly, selectedEpochMs)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        LocationHeaderCard(
            report = report,
            showRaw = settings.showRawLocation,
            favorites = favorites,
            activeFavoriteId = activeFavoriteId,
            onToggleFavorite = onToggleFavorite,
            onSelectFavorite = onSelectFavorite,
            onUseCurrentLocation = onUseCurrentLocation
        )
        // 指数放在鱼种之上：先看「值不值得去」，再决定「钓什么鱼」
        ScoreCard(report = report, selectedEpochMs = selectedEpochMs)
        FishSpeciesSelector(selected = settings.species, onSelect = onSpeciesSelect)
        TimelineCard(
            weather = weather,
            selectedEpochMs = selectedEpochMs,
            selectedPoint = selectedPoint,
            spanHours = settings.timelineSpanHours,
            onTimeSelect = onTimeSelect,
            onSpanChange = onSpanChange
        )
        if (settings.showHourlyTable) {
            HourlyForecastCard(
                hourly = weather.hourly,
                currentEpochMs = weather.observedAtEpochMs,
                selectedEpochMs = selectedEpochMs,
                bestWindow = report.assessment.bestWindow,
                onSelect = onTimeSelect
            )
        }
        WeatherCard(weather = weather, selectedPoint = selectedPoint)
        FactorsCard(report = report)
        report.assessment.speciesAdvice?.let { advice ->
            SpeciesAdviceCard(advice = advice)
        }
        TipsCard(report = report)
        UpdatedAtFooter(updatedAtMs = updatedAtMs)
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun rememberSelectedPoint(hourly: List<HourlyWeather>, epochMs: Long): HourlyWeather? =
    hourly.minByOrNull { abs(it.epochMs - epochMs) }

// ------------------------------------------------------------------ 时间轴

@Composable
private fun TimelineCard(
    weather: WeatherSnapshot,
    selectedEpochMs: Long,
    selectedPoint: HourlyWeather?,
    spanHours: Int,
    onTimeSelect: (Long) -> Unit,
    onSpanChange: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "24 小时时间轴",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "气压 · 风向 · 天气 共用一条时间轴",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = { onTimeSelect(weather.observedAtEpochMs) }) {
                    Text("回到此刻")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TimelineSpan.entries.forEach { span ->
                    SpanChip(
                        text = span.label,
                        selected = span.hours == spanHours,
                        onClick = { onSpanChange(span.hours) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            CombinedTimelineChart(
                points = weather.hourly,
                snapshot = weather,
                selectedEpochMs = selectedEpochMs,
                nowEpochMs = weather.observedAtEpochMs,
                onSelect = onTimeSelect,
                visibleHours = spanHours
            )

            selectedPoint?.let { point ->
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "已选 ${formatDayLabel(point.epochMs)}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            ReadoutCell("天气", weatherCodeLabel(point.weatherCode), Modifier.weight(1f))
                            ReadoutCell("气温", "%.1f℃".format(point.temperatureC), Modifier.weight(1f))
                            ReadoutCell("气压", "%.0f hPa".format(point.pressureHpa), Modifier.weight(1f))
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            ReadoutCell(
                                "风向",
                                windDirectionLabel(point.windDirectionDeg),
                                Modifier.weight(1f)
                            )
                            ReadoutCell(
                                "风力",
                                beaufortLabel(point.windSpeedKmh).substringBefore("（"),
                                Modifier.weight(1f)
                            )
                            ReadoutCell("降水", "%.1f mm".format(point.precipitationMm), Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpanChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val background = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val content = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text = text,
        fontSize = 12.sp,
        color = content,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    )
}

@Composable
private fun ReadoutCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// ------------------------------------------------------------------ 各信息卡

@Composable
private fun LocationHeaderCard(
    report: FishingReport,
    showRaw: Boolean,
    favorites: List<FavoriteSpot>,
    activeFavoriteId: String?,
    onToggleFavorite: () -> Unit,
    onSelectFavorite: (FavoriteSpot) -> Unit,
    onUseCurrentLocation: () -> Unit
) {
    val point = report.point
    // 收藏判定用距离而非精确相等：定位本身有几十米误差，按坐标比对会永远判不上
    val starred = favorites.any {
        it.distanceMetersTo(point.latitude, point.longitude) <= FavoriteSpot.SAME_SPOT_METERS
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = report.placeName ?: "定位成功，地址解析中…",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    if (showRaw) {
                        Text(
                            text = "%.4f, %.4f".format(point.latitude, point.longitude),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "${providerLabel(point.provider)} · ${accuracyLabel(point.accuracyMeters)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                FavoriteToggleButton(starred = starred, onClick = onToggleFavorite)
            }

            if (favorites.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "切换钓点",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = " · 收藏 ${favorites.size} 个",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // 用 FlowRow 而非 Row：钓点名称长度不定，屏幕放不下时换行而不是把文字挤成竖排
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SpotChip(
                        text = "当前位置",
                        selected = activeFavoriteId == null,
                        onClick = onUseCurrentLocation
                    )
                    favorites.forEach { spot ->
                        SpotChip(
                            text = spot.name,
                            selected = spot.id == activeFavoriteId,
                            onClick = { onSelectFavorite(spot) }
                        )
                    }
                }
            }
        }
    }
}

/** 收藏切换按钮：实心星表示已在收藏夹中，再点一次取消收藏 */
@Composable
private fun FavoriteToggleButton(starred: Boolean, onClick: () -> Unit) {
    val background = if (starred) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val content = if (starred) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(background),
            contentAlignment = Alignment.Center
        ) {
            Text(text = if (starred) "★" else "☆", fontSize = 20.sp, color = content)
        }
        Text(
            text = if (starred) "已收藏" else "收藏",
            fontSize = 11.sp,
            color = content,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun SpotChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val background = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val content = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text = text,
        fontSize = 12.sp,
        color = content,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    )
}

@Composable
private fun ScoreCard(report: FishingReport, selectedEpochMs: Long) {
    val assessment = report.assessment
    val isNow = abs(selectedEpochMs - report.weather.observedAtEpochMs) <= 1_800_000L
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "钓鱼指数",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (isNow) {
                    "按当前时刻 ${formatClock(selectedEpochMs)} 评估"
                } else {
                    "按所选时刻 ${formatDayLabel(selectedEpochMs)} 评估"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))
            ScoreRing(score = assessment.totalScore, level = assessment.level)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "观测时间 ${formatClock(report.weather.observedAtEpochMs)} · ${report.weather.timeZoneId}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            assessment.bestWindow?.let { window ->
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    color = levelColor(assessment.level).copy(alpha = 0.12f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "推荐出钓窗口：${formatDayLabel(window.startEpochMs)} - ${formatClock(window.endEpochMs)}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = window.reason,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeatherCard(weather: WeatherSnapshot, selectedPoint: HourlyWeather?) {
    val point = selectedPoint
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "实况气象",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                MetricCell(
                    label = "天气",
                    value = weatherCodeLabel(weather.weatherCode),
                    modifier = Modifier.weight(1f)
                )
                MetricCell(
                    label = "气温",
                    value = "%.1f℃".format(weather.temperatureC),
                    modifier = Modifier.weight(1f)
                )
                MetricCell(
                    label = "体感",
                    value = "%.1f℃".format(weather.apparentTemperatureC),
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                MetricCell(
                    label = "风向",
                    value = "${weather.windDirectionDeg}°",
                    modifier = Modifier.weight(1f)
                )
                MetricCell(
                    label = "风速",
                    value = "%.1f km/h".format(weather.windSpeedKmh),
                    modifier = Modifier.weight(1f)
                )
                MetricCell(
                    label = "阵风",
                    value = "%.1f km/h".format(weather.windGustKmh),
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                MetricCell(
                    label = "湿度",
                    value = "${weather.humidityPercent}%",
                    modifier = Modifier.weight(1f)
                )
                MetricCell(
                    label = "云量",
                    value = "${weather.cloudCoverPercent}%",
                    modifier = Modifier.weight(1f)
                )
                MetricCell(
                    label = "降水",
                    value = "%.1f mm".format(weather.precipitationMm),
                    modifier = Modifier.weight(1f)
                )
            }
            if (point != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "所选时刻云量 ${point.cloudCoverPercent}% · 降水概率 ${point.precipitationProbabilityPercent}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            weather.sunriseEpochMs?.let { sunrise ->
                weather.sunsetEpochMs?.let { sunset ->
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "日出 ${formatClock(sunrise)} · 日落 ${formatClock(sunset)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun FactorsCard(report: FishingReport) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "评分构成",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            report.assessment.factors.forEach { factor ->
                FactorRow(factor = factor)
            }
        }
    }
}

@Composable
private fun TipsCard(report: FishingReport) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "垂钓建议",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            report.assessment.tips.forEach { tip ->
                Row {
                    Text(
                        text = "•",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = tip,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun UpdatedAtFooter(updatedAtMs: Long) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "${formatUpdatedAt(updatedAtMs)} · 气象数据 Open-Meteo · 地址数据 BigDataCloud",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 供预览使用的占位色块 */
@Composable
internal fun PreviewChip(color: Color) {
    Box(
        modifier = Modifier
            .padding(end = 4.dp)
            .width(12.dp)
            .height(12.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(color)
    )
}
