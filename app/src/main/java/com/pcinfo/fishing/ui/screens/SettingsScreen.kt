package com.pcinfo.fishing.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pcinfo.fishing.domain.model.AppSettings
import com.pcinfo.fishing.domain.model.FavoriteSpot
import com.pcinfo.fishing.domain.model.ScoreWeights
import com.pcinfo.fishing.domain.model.ThemeMode
import com.pcinfo.fishing.domain.model.TimelineSpan
import kotlin.math.roundToInt

/**
 * 设置页：权重、算法开关与显示偏好。
 *
 * 权重用相对值存储、计分时归一化，所以滑块怎么拖都不会让总分失真；
 * 界面实时显示「归一化后占比」，让用户的直觉与实际计算对得上。
 */
@Composable
fun SettingsScreen(
    settings: AppSettings,
    favorites: List<FavoriteSpot>,
    onWeightChange: (String, Int) -> Unit,
    onApplyPreset: (ScoreWeights) -> Unit,
    onResetWeights: () -> Unit,
    onUseEstimatedWaterTemp: (Boolean) -> Unit,
    onUseWindDirectionScoring: (Boolean) -> Unit,
    onUseOxygenToleranceAdjust: (Boolean) -> Unit,
    onThemeMode: (ThemeMode) -> Unit,
    onAutoRefresh: (Boolean) -> Unit,
    onTimelineSpan: (Int) -> Unit,
    onShowRawLocation: (Boolean) -> Unit,
    onShowHourlyTable: (Boolean) -> Unit,
    onDeleteFavorite: (String) -> Unit,
    onRestoreDefaults: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 显式 fillMaxWidth：否则 Surface 会按文字宽度收缩，背景不能铺满整行
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 2.dp
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    text = "设置",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "权重、算法开关与显示偏好",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            WeightSection(
                weights = settings.weights,
                onWeightChange = onWeightChange,
                onApplyPreset = onApplyPreset,
                onReset = onResetWeights
            )

            EngineSection(
                settings = settings,
                onUseEstimatedWaterTemp = onUseEstimatedWaterTemp,
                onUseWindDirectionScoring = onUseWindDirectionScoring,
                onUseOxygenToleranceAdjust = onUseOxygenToleranceAdjust
            )

            DisplaySection(
                settings = settings,
                onThemeMode = onThemeMode,
                onAutoRefresh = onAutoRefresh,
                onTimelineSpan = onTimelineSpan,
                onShowRawLocation = onShowRawLocation,
                onShowHourlyTable = onShowHourlyTable
            )

            FavoritesSection(
                favorites = favorites,
                onDeleteFavorite = onDeleteFavorite
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "恢复默认",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "把权重、开关与显示偏好全部还原为出厂设置，不影响已缓存的天气数据。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(onClick = onRestoreDefaults, modifier = Modifier.fillMaxWidth()) {
                        Text("全部恢复默认")
                    }
                }
            }

            Text(
                text = "所有设置保存在本机，不会上传。改变任一设置都会立即用已缓存的天气数据重算，" +
                    "无需重新定位或联网。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

// ------------------------------------------------------------------ 权重

@Composable
private fun WeightSection(
    weights: ScoreWeights,
    onWeightChange: (String, Int) -> Unit,
    onApplyPreset: (ScoreWeights) -> Unit,
    onReset: () -> Unit
) {
    val percentages = weights.percentages()
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "因子权重",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "拖动调整各因子的相对权重；实际计分按占比归一化，合计恒为 100%。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "快速方案",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            // FlowRow：五个预设在窄屏上一排放不下，换行比把文字挤成竖排可读得多
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ScoreWeights.PRESETS.forEach { preset ->
                    PresetChip(
                        text = preset.name,
                        selected = preset.weights == weights,
                        onClick = { onApplyPreset(preset.weights) }
                    )
                }
            }
            ScoreWeights.PRESETS.firstOrNull { it.weights == weights }?.let {
                Text(
                    text = it.summary,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))

            ScoreWeights.KEYS.forEach { key ->
                WeightSlider(
                    label = ScoreWeights.LABELS[key] ?: key,
                    rawValue = weights.get(key),
                    percent = percentages[key] ?: 0.0,
                    onValueChange = { onWeightChange(key, it) }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "权重合计 ${weights.total}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = onReset) { Text("恢复默认权重") }
            }
        }
    }
}

@Composable
private fun WeightSlider(
    label: String,
    rawValue: Int,
    percent: Double,
    onValueChange: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "$rawValue",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = " → %.1f%%".format(percent),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(56.dp)
            )
        }
        Slider(
            value = rawValue.toFloat(),
            onValueChange = { onValueChange(it.roundToInt()) },
            valueRange = ScoreWeights.MIN_SINGLE.toFloat()..ScoreWeights.MAX_SINGLE.toFloat(),
            steps = ScoreWeights.MAX_SINGLE - 1
        )
    }
}

@Composable
private fun PresetChip(text: String, selected: Boolean, onClick: () -> Unit) {
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
        // 单行不换行：芯片空间固定，换行会把「重风浪」这类短词压成竖排
        maxLines = 1,
        softWrap = false,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    )
}

// ------------------------------------------------------------------ 算法开关

@Composable
private fun EngineSection(
    settings: AppSettings,
    onUseEstimatedWaterTemp: (Boolean) -> Unit,
    onUseWindDirectionScoring: (Boolean) -> Unit,
    onUseOxygenToleranceAdjust: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "算法开关",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "这三项都是「经验 vs 证据」存在张力的地方，按你的装备与钓场习惯选择。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            SwitchRow(
                title = "用估算水温评分",
                subtitle = "开启：水温 = 0.6×24h 均温 + 0.4×当前气温 − 1.0℃；" +
                    "关闭：直接用气温评分。有水温和实测条件时建议开启。",
                checked = settings.options.useEstimatedWaterTemp,
                onCheckedChange = onUseEstimatedWaterTemp
            )
            SwitchRow(
                title = "风向方位评分",
                subtitle = "关闭后风向项固定记中性分，只保留风力影响。" +
                    "方位口诀地域性强，若你在非季风区作钓可关闭。",
                checked = settings.options.useWindDirectionScoring,
                onCheckedChange = onUseWindDirectionScoring
            )
            SwitchRow(
                title = "鱼种耐低氧修正",
                subtitle = "关闭后所有鱼种按同一套气压标准评分，不做耐低氧能力的缩放。",
                checked = settings.options.useOxygenToleranceAdjust,
                onCheckedChange = onUseOxygenToleranceAdjust
            )
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

// ------------------------------------------------------------------ 收藏钓点

@Composable
private fun FavoritesSection(
    favorites: List<FavoriteSpot>,
    onDeleteFavorite: (String) -> Unit
) {
    // 删除不可撤销，先确认一次；收藏是用户自己攒的数据，误删成本比多点一下高
    var pendingDelete by remember { mutableStateOf<FavoriteSpot?>(null) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "收藏钓点",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${favorites.size} 个",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "在首页位置卡右侧点星标可收藏／取消当前钓点，点卡内「切换钓点」的标签即可切换。" +
                    "这里只做清理，删除后不可恢复。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            if (favorites.isEmpty()) {
                Text(
                    text = "还没有收藏任何钓点",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp)
                )
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                favorites.forEachIndexed { index, spot ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = spot.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "%.4f, %.4f".format(spot.latitude, spot.longitude),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(onClick = { pendingDelete = spot }) {
                            Text(
                                text = "删除",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { spot ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除收藏钓点？") },
            text = { Text("「${spot.name}」将从收藏列表中移除，此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteFavorite(spot.id)
                    pendingDelete = null
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            }
        )
    }
}

// ------------------------------------------------------------------ 显示

@Composable
private fun DisplaySection(
    settings: AppSettings,
    onThemeMode: (ThemeMode) -> Unit,
    onAutoRefresh: (Boolean) -> Unit,
    onTimelineSpan: (Int) -> Unit,
    onShowRawLocation: (Boolean) -> Unit,
    onShowHourlyTable: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "显示与定位",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "主题",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ThemeMode.entries.forEach { mode ->
                    PresetChip(
                        text = mode.label,
                        selected = settings.themeMode == mode,
                        onClick = { onThemeMode(mode) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "时间轴默认跨度",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TimelineSpan.entries.forEach { span ->
                    PresetChip(
                        text = span.label,
                        selected = settings.timelineSpanHours == span.hours,
                        onClick = { onTimelineSpan(span.hours) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            SwitchRow(
                title = "启动时自动刷新",
                subtitle = "打开应用即定位并拉取最新气象，无需手动点刷新。",
                checked = settings.autoRefreshOnStart,
                onCheckedChange = onAutoRefresh
            )
            SwitchRow(
                title = "显示经纬度",
                subtitle = "关闭后首页只显示地址与定位来源，不展示精确坐标。",
                checked = settings.showRawLocation,
                onCheckedChange = onShowRawLocation
            )
            SwitchRow(
                title = "显示 24 小时明细表",
                subtitle = "关闭后首页只保留组合时间轴，页面更短。",
                checked = settings.showHourlyTable,
                onCheckedChange = onShowHourlyTable
            )
        }
    }
}
