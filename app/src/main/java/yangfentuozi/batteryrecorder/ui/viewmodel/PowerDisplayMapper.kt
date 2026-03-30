package yangfentuozi.batteryrecorder.ui.viewmodel

import android.content.Context
import yangfentuozi.batteryrecorder.data.history.HistoryRecord
import yangfentuozi.batteryrecorder.data.history.HistorySummary
import yangfentuozi.batteryrecorder.data.model.ChartPoint
import yangfentuozi.batteryrecorder.shared.config.SharedSettings
import yangfentuozi.batteryrecorder.shared.data.BatteryStatus

internal fun getDischargeDisplayPositive(context: Context): Boolean {
    return SharedSettings.readAppSettings(context).dischargeDisplayPositive
}

internal fun mapHistoryRecordForDisplay(
    record: HistoryRecord,
    dischargeDisplayPositive: Boolean
): HistoryRecord {
    if (record.type != BatteryStatus.Discharging) return record
    val multiplier = if (dischargeDisplayPositive) -1.0 else 1.0
    return record.copy(
        stats = record.stats.copy(averagePower = record.stats.averagePower * multiplier)
    )
}

internal fun mapHistorySummaryForDisplay(
    summary: HistorySummary,
    dischargeDisplayPositive: Boolean
): HistorySummary {
    if (summary.type != BatteryStatus.Discharging) return summary
    val multiplier = if (dischargeDisplayPositive) -1.0 else 1.0
    return summary.copy(averagePower = summary.averagePower * multiplier)
}

internal fun mapChartPointsForDisplay(
    points: List<ChartPoint>,
    batteryStatus: BatteryStatus,
    dischargeDisplayPositive: Boolean
): List<ChartPoint> {
    if (batteryStatus != BatteryStatus.Discharging) return points
    val multiplier = if (dischargeDisplayPositive) -1.0 else 1.0
    return points.map { point -> point.copy(power = point.power * multiplier) }
}
