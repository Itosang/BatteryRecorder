package yangfentuozi.batteryrecorder.data.history

import yangfentuozi.batteryrecorder.shared.data.LineRecord
import yangfentuozi.batteryrecorder.shared.util.LoggerX

private const val TAG = "RecordDetailPowerStats"
private const val MICROAMPERE_HOUR_DIVISOR = 3_600_000_000.0

data class RecordDetailPowerStats(
    val averagePowerRaw: Double,
    val screenOnAveragePowerRaw: Double?,
    val screenOffAveragePowerRaw: Double?,
    val totalTransferredMahBaseSigned: Double,
    val screenOnConsumedMahBase: Double,
    val screenOffConsumedMahBase: Double
)

object RecordDetailPowerStatsComputer {

    /**
     * 按记录文件的真实采样区间计算详情页功耗统计。
     *
     * @param records 已通过解析得到的有效记录点列表，要求时间戳按文件原始顺序传入
     * @return 返回总平均、亮屏平均、息屏平均三项原始功率，以及“校准值为 1 时”的净 mAh 变化量和亮屏/息屏耗电量；若有效区间不足则返回 null
     */
    fun compute(records: List<LineRecord>): RecordDetailPowerStats? {
        if (records.size < 2) return null

        var totalDurationMs = 0L
        var totalEnergyRawMs = 0.0
        var totalTransferredMahBaseSigned = 0.0
        var screenOnDurationMs = 0L
        var screenOnEnergyRawMs = 0.0
        var screenOnConsumedMahBase = 0.0
        var screenOffDurationMs = 0L
        var screenOffEnergyRawMs = 0.0
        var screenOffConsumedMahBase = 0.0

        var previous: LineRecord? = null
        records.forEach { current ->
            val previousRecord = previous
            previous = current
            if (previousRecord == null) return@forEach

            val durationMs = current.timestamp - previousRecord.timestamp
            if (durationMs <= 0L) return@forEach

            val energyRawMs =
                (previousRecord.power.toDouble() + current.power.toDouble()) * 0.5 * durationMs
            val consumedMahBase = computeConsumedMahBase(
                previousCurrent = previousRecord.current,
                currentCurrent = current.current,
                durationMs = durationMs
            )
            val transferredMahBaseSigned = computeTransferredMahBaseSigned(
                previousCurrent = previousRecord.current,
                currentCurrent = current.current,
                durationMs = durationMs
            )
            totalDurationMs += durationMs
            totalEnergyRawMs += energyRawMs
            totalTransferredMahBaseSigned += transferredMahBaseSigned

            if (previousRecord.isDisplayOn == 1) {
                screenOnDurationMs += durationMs
                screenOnEnergyRawMs += energyRawMs
                screenOnConsumedMahBase += consumedMahBase
                return@forEach
            }

            screenOffDurationMs += durationMs
            screenOffEnergyRawMs += energyRawMs
            screenOffConsumedMahBase += consumedMahBase
        }

        if (totalDurationMs <= 0L) return null

        val stats = RecordDetailPowerStats(
            averagePowerRaw = totalEnergyRawMs / totalDurationMs.toDouble(),
            screenOnAveragePowerRaw = screenOnDurationMs.takeIf { it > 0L }?.let {
                screenOnEnergyRawMs / it.toDouble()
            },
            screenOffAveragePowerRaw = screenOffDurationMs.takeIf { it > 0L }?.let {
                screenOffEnergyRawMs / it.toDouble()
            },
            totalTransferredMahBaseSigned = totalTransferredMahBaseSigned,
            screenOnConsumedMahBase = screenOnConsumedMahBase,
            screenOffConsumedMahBase = screenOffConsumedMahBase
        )
        LoggerX.d(
            TAG,
            "[记录详情] mAh 统计完成: totalSignedMahBase=${stats.totalTransferredMahBaseSigned} screenOnMahBase=${stats.screenOnConsumedMahBase} screenOffMahBase=${stats.screenOffConsumedMahBase}"
        )
        return stats
    }

    /**
     * 按相邻两点的真实采样区间计算“校准值为 1 时”的基础 mAh 消耗量。
     *
     * 这里不应用双电芯倍率：
     * - 统计器只负责产出单路基础值
     * - 展示层再根据 `dualCellEnabled` 统一决定是否乘 2
     * - 展示层再根据 `calibrationValue` 统一决定是否放大 / 缩小 / 反转方向
     *
     * @param previousCurrent 区间起点电流
     * @param currentCurrent 区间终点电流
     * @param durationMs 区间时长，单位毫秒
     * @return 返回该区间对应的基础 mAh 消耗量
     */
    private fun computeConsumedMahBase(
        previousCurrent: Long,
        currentCurrent: Long,
        durationMs: Long
    ): Double {
        val averageAbsCurrent = (absCurrent(previousCurrent) + absCurrent(currentCurrent)) * 0.5
        return averageAbsCurrent * durationMs / MICROAMPERE_HOUR_DIVISOR
    }

    /**
     * 按相邻两点的真实采样区间计算“校准值为 1 时”的带符号基础 mAh 变化量。
     *
     * 这里保留电流原始符号：
     * - 正值表示该区间净流入电池
     * - 负值表示该区间净流出电池
     *
     * 真实展示值仍由外层统一乘上 `calibrationValue`；
     * 这样切换校准配置时无需重新解析整条记录文件。
     *
     * @param previousCurrent 区间起点电流
     * @param currentCurrent 区间终点电流
     * @param durationMs 区间时长，单位毫秒
     * @return 返回该区间对应的带符号基础 mAh 变化量
     */
    private fun computeTransferredMahBaseSigned(
        previousCurrent: Long,
        currentCurrent: Long,
        durationMs: Long
    ): Double {
        val averageCurrent = (previousCurrent.toDouble() + currentCurrent.toDouble()) * 0.5
        return averageCurrent * durationMs / MICROAMPERE_HOUR_DIVISOR
    }

    /**
     * 返回 Long 电流值的安全绝对值。
     *
     * @param current 原始电流值
     * @return 返回绝对值；遇到 Long.MIN_VALUE 时退回 Long.MAX_VALUE，避免溢出
     */
    private fun absCurrent(current: Long): Long {
        if (current == Long.MIN_VALUE) return Long.MAX_VALUE
        return kotlin.math.abs(current)
    }
}
