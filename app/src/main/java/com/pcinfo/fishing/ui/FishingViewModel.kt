package com.pcinfo.fishing.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pcinfo.fishing.core.AppError
import com.pcinfo.fishing.core.Logger
import com.pcinfo.fishing.core.Outcome
import com.pcinfo.fishing.data.location.AndroidLocationRepository
import com.pcinfo.fishing.data.location.AndroidPlaceNameRepository
import com.pcinfo.fishing.data.settings.FavoritesStore
import com.pcinfo.fishing.data.settings.SettingsStore
import com.pcinfo.fishing.data.weather.WeatherRepository
import com.pcinfo.fishing.domain.model.AppSettings
import com.pcinfo.fishing.domain.model.EngineOptions
import com.pcinfo.fishing.domain.model.FavoriteSpot
import com.pcinfo.fishing.domain.model.FishSpecies
import com.pcinfo.fishing.domain.model.GeoPoint
import com.pcinfo.fishing.domain.model.ScoreWeights
import com.pcinfo.fishing.domain.model.ThemeMode
import com.pcinfo.fishing.domain.usecase.AnalyzeFishingConditionsUseCase
import com.pcinfo.fishing.ui.navigation.AppTab
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 主界面 ViewModel：持有唯一的界面状态，所有副作用（定位、网络、持久化）在这里发起。
 *
 * 状态分三组：
 * - [state] 分析结果（空闲 / 加载 / 成功 / 失败）
 * - [settings] 用户可调的权重、开关与显示偏好
 * - [selectedEpochMs] 时间轴上选中的时刻，决定「按哪个小时给建议」
 *
 * 三者中任意一个变化都会触发本地重算（不重新定位、不联网）。
 */
class FishingViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsStore = SettingsStore(application.applicationContext)
    private val favoritesStore = FavoritesStore(application.applicationContext)

    private val useCase = AnalyzeFishingConditionsUseCase(
        locationRepository = AndroidLocationRepository(application.applicationContext),
        weatherRepository = WeatherRepository(),
        placeNameRepository = AndroidPlaceNameRepository(application.applicationContext)
    )

    private val _settings = MutableStateFlow(settingsStore.load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _state = MutableStateFlow<FishingUiState>(FishingUiState.Idle)
    val state: StateFlow<FishingUiState> = _state.asStateFlow()

    /** 时间轴上选中的时刻；null 表示尚未选择（此时按观测时刻评估） */
    private val _selectedEpochMs = MutableStateFlow<Long?>(null)
    val selectedEpochMs: StateFlow<Long?> = _selectedEpochMs.asStateFlow()

    private val _tab = MutableStateFlow(AppTab.HOME)
    val tab: StateFlow<AppTab> = _tab.asStateFlow()

    /** 收藏的钓点列表 */
    private val _favorites = MutableStateFlow(favoritesStore.load())
    val favorites: StateFlow<List<FavoriteSpot>> = _favorites.asStateFlow()

    /** 当前正在查看的收藏钓点；null 表示看的是实时定位 */
    private val _activeFavoriteId = MutableStateFlow<String?>(null)
    val activeFavoriteId: StateFlow<String?> = _activeFavoriteId.asStateFlow()

    private var loadJob: Job? = null

    fun selectTab(tab: AppTab) {
        _tab.value = tab
    }

    /** 是否应在启动后自动拉取一次 */
    fun shouldAutoRefresh(): Boolean = _settings.value.autoRefreshOnStart

    /** 触发一次分析；forceRefresh=true 时绕过天气缓存 */
    fun load(forceRefresh: Boolean = false) {
        runAnalysis(forceRefresh = forceRefresh, point = null)
    }

    /** 切换到某个收藏钓点：跳过定位，直接按该坐标拉气象 */
    fun selectFavorite(spot: FavoriteSpot) {
        if (_activeFavoriteId.value == spot.id) return
        _activeFavoriteId.value = spot.id
        runAnalysis(forceRefresh = false, point = spot.toGeoPoint(), hint = "正在获取「${spot.name}」的气象数据…")
    }

    /** 回到实时定位 */
    fun useCurrentLocation() {
        if (_activeFavoriteId.value == null && _state.value is FishingUiState.Success) return
        _activeFavoriteId.value = null
        runAnalysis(forceRefresh = true, point = null, hint = "正在重新定位…")
    }

    /** 收藏/取消收藏当前正在查看的钓点 */
    fun toggleFavorite() {
        val report = (_state.value as? FishingUiState.Success)?.report ?: return
        val point = report.point
        val existing = _favorites.value.firstOrNull {
            it.distanceMetersTo(point.latitude, point.longitude) <= FavoriteSpot.SAME_SPOT_METERS
        }
        if (existing != null) {
            _favorites.value = favoritesStore.remove(existing.id)
            if (_activeFavoriteId.value == existing.id) _activeFavoriteId.value = null
            Logger.i("favorite removed: ${existing.name}")
        } else {
            val name = report.placeName?.takeIf { it.isNotBlank() }
                ?: _activeFavoriteId.value?.let { id -> _favorites.value.firstOrNull { it.id == id }?.name }
                ?: "%.4f, %.4f".format(point.latitude, point.longitude)
            val spot = FavoriteSpot(
                id = System.currentTimeMillis().toString(),
                name = name,
                latitude = point.latitude,
                longitude = point.longitude,
                createdAtMs = System.currentTimeMillis()
            )
            _favorites.value = favoritesStore.add(spot)
            Logger.i("favorite added: $name")
        }
    }

    /** 删除收藏；若删的正是当前查看的钓点，则回到实时定位 */
    fun deleteFavorite(id: String) {
        _favorites.value = favoritesStore.remove(id)
        if (_activeFavoriteId.value == id) {
            _activeFavoriteId.value = null
            runAnalysis(forceRefresh = true, point = null, hint = "已删除该钓点，正在回到当前位置…")
        }
    }

    private fun runAnalysis(
        forceRefresh: Boolean,
        point: GeoPoint?,
        hint: String? = null
    ) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val s = _settings.value
            _state.value = FishingUiState.Loading(
                hint ?: if (forceRefresh) "正在刷新气象数据…" else "正在获取位置与气象数据…"
            )
            when (val result = useCase(
                forceRefresh = forceRefresh,
                species = s.species,
                atEpochMs = _selectedEpochMs.value,
                weights = s.weights,
                options = s.options,
                point = point
            )) {
                is Outcome.Ok -> {
                    _state.value = FishingUiState.Success(result.value, System.currentTimeMillis())
                    _selectedEpochMs.value = result.value.assessment.evaluatedAtEpochMs
                    Logger.i("load done at ${result.value.assessment.evaluatedAtEpochMs}")
                }
                is Outcome.Fail -> _state.value = result.error.toUiState()
            }
        }
    }

    /** 切换目标鱼种 */
    fun setSpecies(species: FishSpecies) {
        updateSettings(_settings.value.copy(species = species))
        Logger.i("species switched to ${species.profile.name}")
    }

    /** 在时间轴上选中某个时刻，全部建议按该时刻重算 */
    fun setSelectedTime(epochMs: Long) {
        _selectedEpochMs.value = epochMs
        recompute()
    }

    /** 回到「此刻」 */
    fun resetToNow() {
        val current = _state.value
        if (current is FishingUiState.Success) {
            setSelectedTime(current.report.weather.observedAtEpochMs)
        }
    }

    // ------------------------------------------------------------ 设置项

    fun setWeight(key: String, value: Int) {
        val clamped = value.coerceIn(ScoreWeights.MIN_SINGLE, ScoreWeights.MAX_SINGLE)
        updateSettings(_settings.value.copy(weights = _settings.value.weights.with(key, clamped)))
    }

    fun applyWeightPreset(weights: ScoreWeights) {
        updateSettings(_settings.value.copy(weights = weights))
    }

    fun resetWeights() {
        updateSettings(_settings.value.copy(weights = ScoreWeights.DEFAULT))
    }

    fun setUseEstimatedWaterTemp(enabled: Boolean) {
        setOptions(_settings.value.options.copy(useEstimatedWaterTemp = enabled))
    }

    fun setUseWindDirectionScoring(enabled: Boolean) {
        setOptions(_settings.value.options.copy(useWindDirectionScoring = enabled))
    }

    fun setUseOxygenToleranceAdjust(enabled: Boolean) {
        setOptions(_settings.value.options.copy(useOxygenToleranceAdjust = enabled))
    }

    fun setThemeMode(mode: ThemeMode) {
        updateSettings(_settings.value.copy(themeMode = mode))
    }

    fun setAutoRefreshOnStart(enabled: Boolean) {
        updateSettings(_settings.value.copy(autoRefreshOnStart = enabled))
    }

    fun setTimelineSpanHours(hours: Int) {
        updateSettings(_settings.value.copy(timelineSpanHours = hours))
    }

    fun setShowRawLocation(enabled: Boolean) {
        updateSettings(_settings.value.copy(showRawLocation = enabled))
    }

    fun setShowHourlyTable(enabled: Boolean) {
        updateSettings(_settings.value.copy(showHourlyTable = enabled))
    }

    fun restoreDefaults() {
        updateSettings(AppSettings())
    }

    private fun setOptions(options: EngineOptions) {
        updateSettings(_settings.value.copy(options = options))
    }

    // ------------------------------------------------------------ 内部

    private fun updateSettings(newSettings: AppSettings) {
        _settings.value = newSettings
        settingsStore.save(newSettings)
        recompute()
    }

    /**
     * 用内存中已有的天气数据重算评估结果。
     * 切换鱼种、拖动时间轴、调整权重都走这条路，因此是零延迟、零流量的。
     */
    private fun recompute() {
        val current = _state.value
        if (current !is FishingUiState.Success) return
        val s = _settings.value
        val epoch = _selectedEpochMs.value ?: current.report.assessment.evaluatedAtEpochMs
        _state.value = current.copy(
            report = useCase.reEvaluate(current.report, s.species, epoch, s.weights, s.options)
        )
    }

    /** 权限被拒绝时的展示态 */
    fun onPermissionDenied() {
        Logger.w("location permission denied by user")
        _state.value = FishingUiState.Error(
            message = AppError.PermissionDenied.message ?: "未获得定位权限",
            reason = ErrorReason.PERMISSION
        )
    }

    private fun AppError.toUiState(): FishingUiState {
        val message = this.message ?: "出现未知错误"
        val reason = when (this) {
            is AppError.PermissionDenied -> ErrorReason.PERMISSION
            is AppError.LocationUnavailable -> ErrorReason.LOCATION
            is AppError.NetworkUnavailable,
            is AppError.ServerError -> ErrorReason.NETWORK
            else -> ErrorReason.UNKNOWN
        }
        return FishingUiState.Error(message, reason)
    }
}
