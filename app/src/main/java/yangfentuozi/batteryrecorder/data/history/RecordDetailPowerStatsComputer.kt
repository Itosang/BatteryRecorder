package yangfentuozi.batteryrecorder.data.history

import yangfentuozi.batteryrecorder.shared.data.BatteryStatus
import yangfentuozi.batteryrecorder.shared.data.LineRecord
import yangfentuozi.batteryrecorder.shared.util.LoggerX

private const val TAG = "RecordDetailPowerStats"

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
     * @param recordIntervalMs 当前详情页采样间隔配置，超过 `20x` 的区间只参与原始统计，不参与 Wh 积分。
     * @param records 已通过解析得到的有效记录点列表，要求时间戳按文件原始顺序传入
     * @return 返回总平均、亮屏平均、息屏平均三项原始功率，以及总/亮屏/息屏时长和电量变化拆分；若有效区间不足则返回 null
     */
    fun compute(
        detailType: BatteryStatus,
        recordIntervalMs: Long,
        records: List<LineRecord>
    ): RecordDetailPowerStats? {
        if (records.size < 2) return null

        val confidenceThresholdMs = recordIntervalMs * 20L
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
        var screenOffCapacityDropPercent = 0

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
            }
            screenOffCapacityDropPercent += capacityDelta
        }

        if (totalDurationMs <= 0L) return null

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
                screenOffEnergyRawMs / it.toDouble()
            },
            totalConfidentEnergyRawMs = totalConfidentEnergyRawMs,
            screenOnConfidentEnergyRawMs = screenOnConfidentEnergyRawMs,
            screenOffConfidentEnergyRawMs = screenOffConfidentEnergyRawMs,
            totalDurationMs = totalDurationMs,
            screenOnDurationMs = screenOnDurationMs,
            screenOffDurationMs = screenOffDurationMs,
            capacityChange = capacityChange
        )
        LoggerX.d(
            TAG,
            "[记录详情] 统计完成: totalDurationMs=${stats.totalDurationMs} screenOnDurationMs=${stats.screenOnDurationMs} screenOffDurationMs=${stats.screenOffDurationMs} totalCapacity=${stats.capacityChange.totalPercent} screenOnCapacity=${stats.capacityChange.screenOnPercent} screenOffCapacity=${stats.capacityChange.screenOffPercent} thresholdMs=$confidenceThresholdMs"
        )
        return stats
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
