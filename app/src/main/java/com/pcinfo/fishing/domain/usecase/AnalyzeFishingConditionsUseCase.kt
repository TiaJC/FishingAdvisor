package com.pcinfo.fishing.domain.usecase

import com.pcinfo.fishing.core.Logger
import com.pcinfo.fishing.core.Outcome
import com.pcinfo.fishing.data.location.LocationRepository
import com.pcinfo.fishing.data.location.PlaceNameRepository
import com.pcinfo.fishing.data.weather.WeatherRepository
import com.pcinfo.fishing.domain.engine.FishingScoreEngine
import com.pcinfo.fishing.domain.model.EngineOptions
import com.pcinfo.fishing.domain.model.FishSpecies
import com.pcinfo.fishing.domain.model.FishingAssessment
import com.pcinfo.fishing.domain.model.GeoPoint
import com.pcinfo.fishing.domain.model.ScoreWeights
import com.pcinfo.fishing.domain.model.WeatherSnapshot

/** 一次分析的完整结果：位置 + 地址 + 气象 + 钓鱼评估 */
data class FishingReport(
    val point: GeoPoint,
    val weather: WeatherSnapshot,
    val assessment: FishingAssessment,
    val placeName: String? = null
)

/**
 * 用例层：编排「取定位 → 拉天气 → 计算钓鱼指数」，不含任何 Android/HTTP 细节。
 */
class AnalyzeFishingConditionsUseCase(
    private val locationRepository: LocationRepository,
    private val weatherRepository: WeatherRepository,
    private val placeNameRepository: PlaceNameRepository? = null
) {

    /**
     * @param point 指定坐标；传入时跳过定位，直接分析该点（用于收藏钓点切换）
     */
    suspend operator fun invoke(
        forceRefresh: Boolean = false,
        species: FishSpecies = FishSpecies.ANY,
        atEpochMs: Long? = null,
        weights: ScoreWeights = ScoreWeights(),
        options: EngineOptions = EngineOptions(),
        point: GeoPoint? = null
    ): Outcome<FishingReport> {
        Logger.i("analyze start (forceRefresh=$forceRefresh, species=${species.profile.name})")

        val location = if (point != null) {
            point
        } else {
            when (val result = locationRepository.currentLocation()) {
                is Outcome.Ok -> result.value
                is Outcome.Fail -> {
                    Logger.w("analyze aborted at location: ${result.error.message}")
                    return Outcome.Fail(result.error)
                }
            }
        }

        if (!location.isValid()) {
            return Outcome.Fail(
                com.pcinfo.fishing.core.AppError.LocationUnavailable("定位结果无效")
            )
        }

        val weather = when (val result = weatherRepository.getWeather(location, forceRefresh)) {
            is Outcome.Ok -> result.value
            is Outcome.Fail -> {
                Logger.w("analyze aborted at weather: ${result.error.message}")
                return Outcome.Fail(result.error)
            }
        }

        // 地址解析失败不影响主流程，仅降级为不显示地址
        val placeName = placeNameRepository?.resolve(location)
        val evaluationEpoch = atEpochMs ?: weather.observedAtEpochMs
        val assessment = FishingScoreEngine.evaluate(weather, species, evaluationEpoch, weights, options)
        Logger.i("analyze done: score=${assessment.totalScore}, level=${assessment.level.label}")
        return Outcome.Ok(FishingReport(location, weather, assessment, placeName))
    }

    /**
     * 本地重算：切换鱼种、拖动时间轴选时、调整权重或开关时都走这里。
     * 天气数据已在内存，无需重新定位与联网，因此是零延迟的，也不消耗接口额度。
     *
     * @param atEpochMs 评估时刻；为空时沿用上一次的评估时刻，这样切换鱼种不会把时间跳回当前
     */
    fun reEvaluate(
        report: FishingReport,
        species: FishSpecies = report.assessment.species,
        atEpochMs: Long? = null,
        weights: ScoreWeights = ScoreWeights(),
        options: EngineOptions = EngineOptions()
    ): FishingReport {
        val epoch = atEpochMs
            ?: report.assessment.evaluatedAtEpochMs.takeIf { it > 0 }
            ?: report.weather.observedAtEpochMs
        return report.copy(
            assessment = FishingScoreEngine.evaluate(report.weather, species, epoch, weights, options)
        )
    }
}
