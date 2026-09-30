package com.pcinfo.fishing.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.pcinfo.fishing.domain.model.AppSettings
import com.pcinfo.fishing.domain.model.FavoriteSpot
import com.pcinfo.fishing.domain.model.FishSpecies
import com.pcinfo.fishing.domain.model.ScoreWeights
import com.pcinfo.fishing.domain.model.ThemeMode
import com.pcinfo.fishing.ui.FishingUiState
import com.pcinfo.fishing.ui.navigation.AppTab

/**
 * 应用外壳：底部三页导航 + 页面内容。
 *
 * 三个页面共享同一份 ViewModel 状态，因此在设置页改权重后回到首页，
 * 评分与建议已经是新的——不需要任何额外的同步逻辑。
 */
@Composable
fun AppRoot(
    state: FishingUiState,
    settings: AppSettings,
    selectedEpochMs: Long?,
    currentTab: AppTab,
    onTabSelect: (AppTab) -> Unit,
    onSpeciesSelect: (FishSpecies) -> Unit,
    onTimeSelect: (Long) -> Unit,
    onSpanChange: (Int) -> Unit,
    onRefresh: () -> Unit,
    onAction: () -> Unit,
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
    favorites: List<FavoriteSpot>,
    activeFavoriteId: String?,
    onToggleFavorite: () -> Unit,
    onSelectFavorite: (FavoriteSpot) -> Unit,
    onUseCurrentLocation: () -> Unit,
    onDeleteFavorite: (String) -> Unit,
    onRestoreDefaults: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                AppTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = tab == currentTab,
                        onClick = { onTabSelect(tab) },
                        icon = { Text(text = tab.glyph, fontSize = 18.sp) },
                        label = { Text(text = tab.label, fontSize = 11.sp) },
                        alwaysShowLabel = true,
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (currentTab) {
                AppTab.HOME -> HomeScreen(
                    state = state,
                    settings = settings,
                    selectedEpochMs = selectedEpochMs,
                    favorites = favorites,
                    activeFavoriteId = activeFavoriteId,
                    onSpeciesSelect = onSpeciesSelect,
                    onTimeSelect = onTimeSelect,
                    onSpanChange = onSpanChange,
                    onRefresh = onRefresh,
                    onAction = onAction,
                    onToggleFavorite = onToggleFavorite,
                    onSelectFavorite = onSelectFavorite,
                    onUseCurrentLocation = onUseCurrentLocation
                )

                AppTab.KNOWLEDGE -> KnowledgeScreen(settings = settings)

                AppTab.SETTINGS -> SettingsScreen(
                    settings = settings,
                    favorites = favorites,
                    onWeightChange = onWeightChange,
                    onApplyPreset = onApplyPreset,
                    onResetWeights = onResetWeights,
                    onUseEstimatedWaterTemp = onUseEstimatedWaterTemp,
                    onUseWindDirectionScoring = onUseWindDirectionScoring,
                    onUseOxygenToleranceAdjust = onUseOxygenToleranceAdjust,
                    onThemeMode = onThemeMode,
                    onAutoRefresh = onAutoRefresh,
                    onTimelineSpan = onTimelineSpan,
                    onShowRawLocation = onShowRawLocation,
                    onShowHourlyTable = onShowHourlyTable,
                    onDeleteFavorite = onDeleteFavorite,
                    onRestoreDefaults = onRestoreDefaults
                )
            }
        }
    }
}
