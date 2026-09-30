package com.pcinfo.fishing.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import com.pcinfo.fishing.domain.model.ThemeMode
import com.pcinfo.fishing.ui.screens.AppRoot
import com.pcinfo.fishing.ui.theme.FishingAdvisorTheme

class MainActivity : ComponentActivity() {

    private val viewModel: FishingViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            viewModel.load(forceRefresh = true)
        } else {
            viewModel.onPermissionDenied()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val settings by viewModel.settings.collectAsState()
            val darkTheme = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            FishingAdvisorTheme(darkTheme = darkTheme) {
                val state by viewModel.state.collectAsState()
                val selectedEpochMs by viewModel.selectedEpochMs.collectAsState()
                val tab by viewModel.tab.collectAsState()
                val favorites by viewModel.favorites.collectAsState()
                val activeFavoriteId by viewModel.activeFavoriteId.collectAsState()
                AppRoot(
                    state = state,
                    settings = settings,
                    selectedEpochMs = selectedEpochMs,
                    currentTab = tab,
                    onTabSelect = viewModel::selectTab,
                    onSpeciesSelect = viewModel::setSpecies,
                    onTimeSelect = viewModel::setSelectedTime,
                    onSpanChange = viewModel::setTimelineSpanHours,
                    onRefresh = { viewModel.load(forceRefresh = true) },
                    onAction = { startAnalysis() },
                    onWeightChange = viewModel::setWeight,
                    onApplyPreset = viewModel::applyWeightPreset,
                    onResetWeights = viewModel::resetWeights,
                    onUseEstimatedWaterTemp = viewModel::setUseEstimatedWaterTemp,
                    onUseWindDirectionScoring = viewModel::setUseWindDirectionScoring,
                    onUseOxygenToleranceAdjust = viewModel::setUseOxygenToleranceAdjust,
                    onThemeMode = viewModel::setThemeMode,
                    onAutoRefresh = viewModel::setAutoRefreshOnStart,
                    onTimelineSpan = viewModel::setTimelineSpanHours,
                    onShowRawLocation = viewModel::setShowRawLocation,
                    onShowHourlyTable = viewModel::setShowHourlyTable,
                    favorites = favorites,
                    activeFavoriteId = activeFavoriteId,
                    onToggleFavorite = viewModel::toggleFavorite,
                    onSelectFavorite = viewModel::selectFavorite,
                    onUseCurrentLocation = viewModel::useCurrentLocation,
                    onDeleteFavorite = viewModel::deleteFavorite,
                    onRestoreDefaults = viewModel::restoreDefaults
                )
            }
        }
        // 已授权则进入即自动分析；是否自动刷新由设置控制
        if (hasLocationPermission() && viewModel.shouldAutoRefresh()) {
            viewModel.load()
        }
    }

    private fun startAnalysis() {
        if (hasLocationPermission()) {
            viewModel.load(forceRefresh = true)
        } else {
            permissionLauncher.launch(REQUIRED_PERMISSIONS)
        }
    }

    private fun hasLocationPermission(): Boolean = REQUIRED_PERMISSIONS.any {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }
}
