package yangfentuozi.batteryrecorder.data.history

import yangfentuozi.batteryrecorder.shared.data.BatteryStatus
import yangfentuozi.batteryrecorder.shared.data.LineRecord
import yangfentuozi.batteryrecorder.shared.util.LoggerX
import kotlin.math.abs

private const val TAG = "RecordDetailPowerStats"
private const val SCREEN_OFF_BASELINE_PERCENTILE = 0.30

/**
 * 记录详情页电量变化拆分结果。
 *
 * @param totalPercent 当前记录的总电量变化百分比。
 * @param screenOffPercent 息屏区间累计的电量变化百分比。
 * @param screenOnPercent 亮屏区间累计的电量变化百分比。
 */
data class CapacityChange(
    val totalPercent: Int,
    val screenOffPercent: Int,
    val screenOnPercent: Int
)

/**
 * 详情页功耗统计结果。
 *
 * 统计器只负责提供原始功率均值、时长拆分与电量变化拆分；
 * 最终展示层再统一把原始功率换算为 Wh。
 */
data class RecordDetailPowerStats(
    val averagePowerRaw: Double,
    val screenOnAveragePowerRaw: Double?,
    val screenOffAveragePowerRaw: Double?,
    val totalConfidentEnergyRawMs: Double,
    val screenOnConfidentEnergyRawMs: Double,
    val screenOffConfidentEnergyRawMs: Double,
    val screenOffDisplayEnergyRawMs: Double,
    val totalDurationMs: Long,
    val screenOnDurationMs: Long,
    val screenOffDurationMs: Long,
    val capacityChange: CapacityChange
)

object RecordDetailPowerStatsComputer {

    /**
     * 按记录文件的真实采样区间计算详情页功耗统计。
     *
     * @param detailType 当前详情页记录类型，只接受充电和放电。
     * @param recordIntervalMs 当前详情页采样间隔配置，超过 `30x` 的息屏长区间不再直接积分，
     * 而是使用同条记录内息屏短区间的稳健基线功率补算，避免高活跃端点把整段长间隔抬高。
     * @param records 已通过解析得到的有效记录点列表，要求时间戳按文件原始顺序传入
     * @return 返回总平均、亮屏平均、息屏平均三项原始功率，以及总/亮屏/息屏时长和电量变化拆分；若有效区间不足则返回 null
     */
    fun compute(
        detailType: BatteryStatus,
        recordIntervalMs: Long,
        records: List<LineRecord>
    ): RecordDetailPowerStats? {
        if (records.size < 2) return null

        val confidenceThresholdMs = recordIntervalMs * 30L
        var totalDurationMs = 0L
        var totalEnergyRawMs = 0.0
        var totalConfidentEnergyRawMs = 0.0
        var screenOnDurationMs = 0L
        var screenOnEnergyRawMs = 0.0
        var screenOnConfidentEnergyRawMs = 0.0
        var screenOnCapacityDropPercent = 0
        var screenOffDurationMs = 0L
        var screenOffEnergyRawMs = 0.0
        var screenOffConfidentEnergyRawMs = 0.0
        var screenOffConfidentDurationMs = 0L
        var screenOffCapacityDropPercent = 0
        val screenOffShortIntervalPowers = mutableListOf<WeightedPowerSample>()

        var previous: LineRecord? = null
        records.forEach { current ->
            val previousRecord = previous
            previous = current
            if (previousRecord == null) return@forEach

            val durationMs = current.timestamp - previousRecord.timestamp
            if (durationMs <= 0L) return@forEach

            val energyRawMs =
                (previousRecord.power.toDouble() + current.power.toDouble()) * 0.5 * durationMs
            val capacityDelta = computeCapacityDelta(
                detailType = detailType,
                previousCapacity = previousRecord.capacity,
                currentCapacity = current.capacity
            )
            totalDurationMs += durationMs
            totalEnergyRawMs += energyRawMs

            if (previousRecord.isDisplayOn == 1) {
                screenOnDurationMs += durationMs
                screenOnEnergyRawMs += energyRawMs
                if (durationMs <= confidenceThresholdMs) {
                    totalConfidentEnergyRawMs += energyRawMs
                    screenOnConfidentEnergyRawMs += energyRawMs
                }
                screenOnCapacityDropPercent += capacityDelta
                return@forEach
            }

            screenOffDurationMs += durationMs
            screenOffEnergyRawMs += energyRawMs
            if (durationMs <= confidenceThresholdMs) {
                totalConfidentEnergyRawMs += energyRawMs
                screenOffConfidentEnergyRawMs += energyRawMs
                screenOffConfidentDurationMs += durationMs
                screenOffShortIntervalPowers += WeightedPowerSample(
                    powerMagnitudeRaw = abs(energyRawMs / durationMs.toDouble()),
                    durationMs = durationMs
                )
            }
            screenOffCapacityDropPercent += capacityDelta
        }

        if (totalDurationMs <= 0L) return null

        val screenOffDisplayEnergyRawMs = computeScreenOffDisplayEnergyRawMs(
            detailType = detailType,
            screenOffDurationMs = screenOffDurationMs,
            screenOffEnergyRawMs = screenOffEnergyRawMs,
            screenOffConfidentDurationMs = screenOffConfidentDurationMs,
            screenOffConfidentEnergyRawMs = screenOffConfidentEnergyRawMs,
            screenOffShortIntervalPowers = screenOffShortIntervalPowers
        )

        val capacityChange = CapacityChange(
            totalPercent = screenOffCapacityDropPercent + screenOnCapacityDropPercent,
            screenOffPercent = screenOffCapacityDropPercent,
            screenOnPercent = screenOnCapacityDropPercent
        )
        val stats = RecordDetailPowerStats(
            averagePowerRaw = totalEnergyRawMs / totalDurationMs.toDouble(),
            screenOnAveragePowerRaw = screenOnDurationMs.takeIf { it > 0L }?.let {
                screenOnEnergyRawMs / it.toDouble()
            },
            screenOffAveragePowerRaw = screenOffDurationMs.takeIf { it > 0L }?.let {
                screenOffDisplayEnergyRawMs / it.toDouble()
            },
            totalConfidentEnergyRawMs = totalConfidentEnergyRawMs,
            screenOnConfidentEnergyRawMs = screenOnConfidentEnergyRawMs,
            screenOffConfidentEnergyRawMs = screenOffConfidentEnergyRawMs,
            screenOffDisplayEnergyRawMs = screenOffDisplayEnergyRawMs,
            totalDurationMs = totalDurationMs,
            screenOnDurationMs = screenOnDurationMs,
            screenOffDurationMs = screenOffDurationMs,
            capacityChange = capacityChange
        )
        LoggerX.d(
            TAG,
            "[记录详情] 统计完成: totalDurationMs=${stats.totalDurationMs} screenOnDurationMs=${stats.screenOnDurationMs} screenOffDurationMs=${stats.screenOffDurationMs} totalCapacity=${stats.capacityChange.totalPercent} screenOnCapacity=${stats.capacityChange.screenOnPercent} screenOffCapacity=${stats.capacityChange.screenOffPercent} thresholdMs=$confidenceThresholdMs confidentOffDurationMs=$screenOffConfidentDurationMs displayOffEnergyRawMs=${stats.screenOffDisplayEnergyRawMs}"
        )
        return stats
    }

    private data class WeightedPowerSample(
        val powerMagnitudeRaw: Double,
        val durationMs: Long
    )

    /**
     * 息屏单值显示优先走“短区间真实积分 + 长区间基线补算”。
     *
     * 当存在 `> 30x` 的息屏长间隔时，直接用端点积分会把短暂唤醒的高功耗扩散到整段长间隔；
     * 这里改为使用息屏短区间的加权 P30 作为稳健基线功率补算长间隔。
     */
    private fun computeScreenOffDisplayEnergyRawMs(
        detailType: BatteryStatus,
        screenOffDurationMs: Long,
        screenOffEnergyRawMs: Double,
        screenOffConfidentDurationMs: Long,
        screenOffConfidentEnergyRawMs: Double,
        screenOffShortIntervalPowers: List<WeightedPowerSample>
    ): Double {
        if (screenOffDurationMs <= 0L) return 0.0
        if (screenOffConfidentDurationMs <= 0L) return screenOffEnergyRawMs

        val longGapDurationMs = screenOffDurationMs - screenOffConfidentDurationMs
        if (longGapDurationMs <= 0L) return screenOffConfidentEnergyRawMs

        val baselinePowerRaw = weightedPercentile(
            samples = screenOffShortIntervalPowers,
            percentile = SCREEN_OFF_BASELINE_PERCENTILE
        ) ?: return screenOffEnergyRawMs

        val expectedSign = when (detailType) {
            BatteryStatus.Discharging -> -1.0
            BatteryStatus.Charging -> 1.0
            else -> return screenOffEnergyRawMs
        }
        return screenOffConfidentEnergyRawMs + expectedSign * baselinePowerRaw * longGapDurationMs
    }

    /**
     * 按区间时长加权计算功率分位数。
     *
     * 这里使用加权分位而不是均值，目的是降低短暂唤醒峰值对息屏基线的污染。
     */
    private fun weightedPercentile(
        samples: List<WeightedPowerSample>,
        percentile: Double
    ): Double? {
        if (samples.isEmpty()) return null

        val sorted = samples.sortedBy { it.powerMagnitudeRaw }
        val totalWeight = sorted.sumOf { it.durationMs.toDouble() }
        if (totalWeight <= 0.0) return null

        val targetWeight = totalWeight * percentile.coerceIn(0.0, 1.0)
        var accumulatedWeight = 0.0
        sorted.forEach { sample ->
            accumulatedWeight += sample.durationMs.toDouble()
            if (accumulatedWeight >= targetWeight) {
                return sample.powerMagnitudeRaw
            }
        }
        return sorted.last().powerMagnitudeRaw
    }

    /**
     * 按记录类型计算当前区间的电量变化百分比。
     *
     * @param detailType 当前详情页记录类型，只接受充电和放电。
     * @param previousCapacity 区间起点电量百分比。
     * @param currentCapacity 区间终点电量百分比。
     * @return 返回当前区间在正确语义下的正向电量变化值；方向不一致时返回 0。
     */
    private fun computeCapacityDelta(
        detailType: BatteryStatus,
        previousCapacity: Int,
        currentCapacity: Int
    ): Int {
        val rawDelta = when (detailType) {
            BatteryStatus.Discharging -> previousCapacity - currentCapacity
            BatteryStatus.Charging -> currentCapacity - previousCapacity
            else -> throw IllegalArgumentException("Unsupported detail type: $detailType")
        }
        return rawDelta.coerceAtLeast(0)
    }
}
