package yangfentuozi.batteryrecorder.data.history

import yangfentuozi.batteryrecorder.shared.data.BatteryStatus
import yangfentuozi.batteryrecorder.shared.data.LineRecord
import yangfentuozi.batteryrecorder.shared.util.LoggerX
import kotlin.math.abs

private const val TAG = "RecordDetailPowerStats"
private const val SCREEN_OFF_WH_BASELINE_MIN_PERCENTILE = 0.30
private const val SCREEN_OFF_WH_BASELINE_MAX_PERCENTILE = 0.50
private const val SCREEN_OFF_HIGH_CONFIDENCE_DURATION_MS = 7_200_000L
private const val SCREEN_OFF_HIGH_CONFIDENCE_SAMPLE_COUNT = 1_000

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
 * 统计器只负责提供原始功率均值、时长拆分与电量变化拆分。
 *
 * 约束：
 * 1. `averagePowerRaw`、`screenOnAveragePowerRaw`、`screenOffAveragePowerRaw`
 *    都由对应展示能量除以对应展示时长回推，确保详情页功耗展示与 Wh 展示使用同一口径。
 * 2. `screenOffWhDisplayEnergyRawMs` 专门服务息屏 Wh 展示，
 *    会对长间隔做覆盖率缩放后的弱外推。
 */
data class RecordDetailPowerStats(
    val averagePowerRaw: Double,
    val screenOnAveragePowerRaw: Double?,
    val screenOffAveragePowerRaw: Double?,
    val totalDisplayEnergyRawMs: Double,
    val totalConfidentEnergyRawMs: Double,
    val screenOnConfidentEnergyRawMs: Double,
    val screenOffConfidentEnergyRawMs: Double,
    val screenOffWhDisplayEnergyRawMs: Double,
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
     * @param recordIntervalMs 当前详情页采样间隔配置，超过 `30x` 的区间视为长间隔；
     * 息屏 Wh 会基于 confident 样本数量、时长和覆盖率在 `P30~P50` 之间选取弱外推基线。
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
        val screenOffLongIntervals = mutableListOf<LongIntervalEnergySample>()

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
            } else {
                screenOffLongIntervals += LongIntervalEnergySample(
                    signedEnergyRawMs = energyRawMs,
                    durationMs = durationMs
                )
            }
            screenOffCapacityDropPercent += capacityDelta
        }

        if (totalDurationMs <= 0L) return null

        val screenOffWhDisplayEnergyRawMs = computeScreenOffWhDisplayEnergyRawMs(
            screenOffDurationMs = screenOffDurationMs,
            screenOffEnergyRawMs = screenOffEnergyRawMs,
            screenOffConfidentDurationMs = screenOffConfidentDurationMs,
            screenOffConfidentEnergyRawMs = screenOffConfidentEnergyRawMs,
            screenOffShortIntervalPowers = screenOffShortIntervalPowers,
            screenOffLongIntervals = screenOffLongIntervals
        )
        val screenOnDisplayEnergyRawMs = screenOnConfidentEnergyRawMs
        val totalDisplayEnergyRawMs = screenOnDisplayEnergyRawMs + screenOffWhDisplayEnergyRawMs
        val screenOnAveragePowerRaw = screenOnDurationMs.takeIf { it > 0L }?.let {
            screenOnDisplayEnergyRawMs / it.toDouble()
        }
        val screenOffAveragePowerRaw = screenOffDurationMs.takeIf { it > 0L }?.let {
            screenOffWhDisplayEnergyRawMs / it.toDouble()
        }
        val averagePowerRaw = totalDurationMs.takeIf { it > 0L }?.let {
            totalDisplayEnergyRawMs / it.toDouble()
        } ?: 0.0
        val capacityChange = CapacityChange(
            totalPercent = screenOffCapacityDropPercent + screenOnCapacityDropPercent,
            screenOffPercent = screenOffCapacityDropPercent,
            screenOnPercent = screenOnCapacityDropPercent
        )
        val stats = RecordDetailPowerStats(
            averagePowerRaw = averagePowerRaw,
            screenOnAveragePowerRaw = screenOnAveragePowerRaw,
            screenOffAveragePowerRaw = screenOffAveragePowerRaw,
            totalDisplayEnergyRawMs = totalDisplayEnergyRawMs,
            totalConfidentEnergyRawMs = totalConfidentEnergyRawMs,
            screenOnConfidentEnergyRawMs = screenOnConfidentEnergyRawMs,
            screenOffConfidentEnergyRawMs = screenOffConfidentEnergyRawMs,
            screenOffWhDisplayEnergyRawMs = screenOffWhDisplayEnergyRawMs,
            totalDurationMs = totalDurationMs,
            screenOnDurationMs = screenOnDurationMs,
            screenOffDurationMs = screenOffDurationMs,
            capacityChange = capacityChange
        )
        LoggerX.d(
            TAG,
            "[记录详情] 统计完成: totalDurationMs=${stats.totalDurationMs} screenOnDurationMs=${stats.screenOnDurationMs} screenOffDurationMs=${stats.screenOffDurationMs} totalCapacity=${stats.capacityChange.totalPercent} screenOnCapacity=${stats.capacityChange.screenOnPercent} screenOffCapacity=${stats.capacityChange.screenOffPercent} thresholdMs=$confidenceThresholdMs confidentOffDurationMs=$screenOffConfidentDurationMs averagePowerRaw=${stats.averagePowerRaw} totalDisplayEnergyRawMs=${stats.totalDisplayEnergyRawMs} screenOffAveragePowerRaw=${stats.screenOffAveragePowerRaw} screenOffWhDisplayEnergyRawMs=${stats.screenOffWhDisplayEnergyRawMs}"
        )
        return stats
    }

    private data class WeightedPowerSample(
        val powerMagnitudeRaw: Double,
        val durationMs: Long
    )

    private data class LongIntervalEnergySample(
        val signedEnergyRawMs: Double,
        val durationMs: Long
    )

    /**
     * 计算息屏 Wh 展示使用的原始能量。
     *
     * 设计依据：
     * 纯 confident 积分会系统性低估长间隔记录，而旧版整段补算又会系统性高估。
     * 这里对每个长间隔按自身方向做覆盖率缩放后的弱外推，且基线分位会随 confident 质量
     * 在 `P30~P50` 之间自适应变化。覆盖率越低，允许使用的基线分位越保守：
     * `confidentEnergy + Σ(intervalDirection * baselinePower * intervalDuration * confidentCoverage)`。
     *
     * @param screenOffDurationMs 当前记录的总息屏时长。
     * @param screenOffEnergyRawMs 当前记录的总息屏积分能量，作为无 confident 区间时的回退值。
     * @param screenOffConfidentDurationMs 当前记录中落在 confident 阈值内的息屏时长。
     * @param screenOffConfidentEnergyRawMs 当前记录中落在 confident 阈值内的息屏积分能量。
     * @param screenOffShortIntervalPowers 当前记录 confident 息屏区间的功率样本，用于提取稳健基线。
     * @return 返回用于详情页息屏 Wh 展示的原始能量值。
     */
    private fun computeScreenOffWhDisplayEnergyRawMs(
        screenOffDurationMs: Long,
        screenOffEnergyRawMs: Double,
        screenOffConfidentDurationMs: Long,
        screenOffConfidentEnergyRawMs: Double,
        screenOffShortIntervalPowers: List<WeightedPowerSample>,
        screenOffLongIntervals: List<LongIntervalEnergySample>
    ): Double {
        if (screenOffDurationMs <= 0L) return 0.0
        if (screenOffConfidentDurationMs <= 0L) return screenOffEnergyRawMs

        if (screenOffLongIntervals.isEmpty()) return screenOffConfidentEnergyRawMs

        val confidenceScore = computeScreenOffConfidenceScore(
            screenOffConfidentDurationMs = screenOffConfidentDurationMs,
            screenOffConfidentSampleCount = screenOffShortIntervalPowers.size,
            screenOffConfidentCoverage = screenOffConfidentDurationMs.toDouble() /
                screenOffDurationMs.toDouble()
        )
        val baselinePercentile = interpolatePercentile(
            minPercentile = SCREEN_OFF_WH_BASELINE_MIN_PERCENTILE,
            maxPercentile = SCREEN_OFF_WH_BASELINE_MAX_PERCENTILE,
            confidenceScore = confidenceScore
        )
        val baselinePowerRaw = weightedPercentile(
            samples = screenOffShortIntervalPowers,
            percentile = baselinePercentile
        ) ?: return screenOffEnergyRawMs

        val confidentCoverage =
            screenOffConfidentDurationMs.toDouble() / screenOffDurationMs.toDouble()
        var extrapolatedLongGapEnergyRawMs = 0.0
        screenOffLongIntervals.forEach { interval ->
            extrapolatedLongGapEnergyRawMs += observedEnergyDirection(
                primaryEnergy = interval.signedEnergyRawMs,
                fallbackEnergy = screenOffConfidentEnergyRawMs
            ) * baselinePowerRaw * interval.durationMs.toDouble() * confidentCoverage
        }
        return screenOffConfidentEnergyRawMs + extrapolatedLongGapEnergyRawMs
    }

    /**
     * 计算息屏 confident 样本的综合置信度。
     *
     * 设计依据：
     * 单纯依赖一条记录的高基线很容易被短样本带偏，因此这里同时要求：
     * 1. confident 息屏时长足够长；
     * 2. confident 息屏样本数量足够多。
     * 3. confident 息屏覆盖率足够高。
     * 三者都足够时才允许息屏 Wh 的长间隔弱外推基线向更高分位靠近。
     *
     * @param screenOffConfidentDurationMs 当前记录中落在 confident 阈值内的息屏时长。
     * @param screenOffConfidentSampleCount 当前记录中落在 confident 阈值内的息屏样本数量。
     * @param screenOffConfidentCoverage 当前记录中 confident 息屏时长占总息屏时长的比例。
     * @return 返回 `0.0~1.0` 的置信度分数；越接近 `1.0` 代表越可以信任更高的弱外推基线。
     */
    private fun computeScreenOffConfidenceScore(
        screenOffConfidentDurationMs: Long,
        screenOffConfidentSampleCount: Int,
        screenOffConfidentCoverage: Double
    ): Double {
        if (screenOffConfidentDurationMs <= 0L || screenOffConfidentSampleCount <= 0) return 0.0
        val durationScore = (
            screenOffConfidentDurationMs.toDouble() /
                SCREEN_OFF_HIGH_CONFIDENCE_DURATION_MS.toDouble()
            ).coerceIn(0.0, 1.0)
        val sampleScore = (
            screenOffConfidentSampleCount.toDouble() /
                SCREEN_OFF_HIGH_CONFIDENCE_SAMPLE_COUNT.toDouble()
            ).coerceIn(0.0, 1.0)
        val coverageScore = screenOffConfidentCoverage.coerceIn(0.0, 1.0)
        return kotlin.math.sqrt(durationScore * sampleScore) * kotlin.math.sqrt(coverageScore)
    }

    /**
     * 按置信度在线性区间内插值得到目标分位。
     *
     * @param minPercentile 当前口径允许的最小分位。
     * @param maxPercentile 当前口径允许的最大分位。
     * @param confidenceScore `computeScreenOffConfidenceScore()` 产生的综合置信度。
     * @return 返回插值后的目标分位，范围始终落在 `[minPercentile, maxPercentile]`。
     */
    private fun interpolatePercentile(
        minPercentile: Double,
        maxPercentile: Double,
        confidenceScore: Double
    ): Double {
        return minPercentile + (maxPercentile - minPercentile) * confidenceScore.coerceIn(0.0, 1.0)
    }

    /**
     * 推导当前记录观测到的能量方向。
     *
     * @param primaryEnergy 优先使用的观测能量。
     * @param fallbackEnergy 主观测能量为 0 时的回退能量。
     * @return 返回 `1.0`、`-1.0` 或 `0.0`。
     */
    private fun observedEnergyDirection(
        primaryEnergy: Double,
        fallbackEnergy: Double
    ): Double {
        if (primaryEnergy > 0.0) return 1.0
        if (primaryEnergy < 0.0) return -1.0
        if (fallbackEnergy > 0.0) return 1.0
        if (fallbackEnergy < 0.0) return -1.0
        return 0.0
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
