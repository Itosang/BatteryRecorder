package yangfentuozi.batteryrecorder.ui.model

import yangfentuozi.batteryrecorder.data.history.CapacityChange
import yangfentuozi.batteryrecorder.data.model.RecordDetailChartPoint

/**
 * 记录详情页图表状态。
 *
 * points 保留原始展示点；trendPoints 保留基于过滤后点序列生成的趋势点。
 */
data class RecordDetailChartUiState(
    val points: List<RecordDetailChartPoint> = emptyList(),
    val trendPoints: List<RecordDetailChartPoint> = emptyList(),
    val minChartTime: Long? = null,
    val maxChartTime: Long? = null,
    val maxViewportStartTime: Long? = null,
    val viewportDurationMs: Long = 0L
)

/**
 * 记录详情页应用维度单行展示数据。
 *
 * averagePowerRaw 保持原始口径，正负值映射交给 Screen 层根据设置处理。
 */
data class RecordAppDetailUiEntry(
    val packageName: String,
    val appLabel: String,
    val averagePowerRaw: Double,
    val averageTempCelsius: Double?,
    val maxTempCelsius: Double?,
    val durationMs: Long
)

/**
 * 记录详情页顶部功耗摘要状态。
 */
data class RecordDetailPowerUiState(
    val averagePower: Double,
    val screenOnAveragePower: Double?,
    val screenOffAveragePower: Double?,
    val totalTransferredWh: Double,
    val screenOnConsumedWh: Double,
    val screenOffConsumedWh: Double,
    val capacityChange: CapacityChange,
    val appSwitchCount: Int
)
