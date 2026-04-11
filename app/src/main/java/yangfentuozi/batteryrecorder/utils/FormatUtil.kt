package yangfentuozi.batteryrecorder.utils

import yangfentuozi.batteryrecorder.R
import yangfentuozi.batteryrecorder.appString
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

const val POWER_SCALE_DIVISOR = 1_000_000_000_000.0

/**
 * 将毫秒时长格式化为小时分钟字符串
 * @param durationMs 时长（毫秒），如 7200000 表示 2 小时
 * @return 格式化后的字符串，如 "2h0m"；小时为0时只显示分钟，如 "30m"
 */
fun formatDurationHours(durationMs: Long): String {
    val totalMinutes = durationMs / 60000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h${minutes}m" else "${minutes}m"
}

fun formatDetailDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L

    if (hours > 0L) {
        return if (minutes > 0L) "${hours}h${minutes}m" else "${hours}h"
    }
    if (minutes > 0L) {
        return if (seconds > 0L) "${minutes}m${seconds}s" else "${minutes}m"
    }
    return "${seconds}s"
}

/**
 * 将时间戳格式化为 HH:mm 格式
 * @param timestamp Unix 时间戳（毫秒），如 1705900800000
 * @return 格式化后的时间字符串，如 "14:30"
 */
fun formatDateTime(timestamp: Long): String {
    val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    return formatter.format(Date(timestamp))
}

/**
 * 将时间戳格式化为 yyyy/MM/dd HH:mm 格式
 * @param timestamp Unix 时间戳（毫秒），如 1705900800000
 * @return 格式化后的时间字符串，如 "2026/03/11 14:30"
 */
fun formatFullDateTime(timestamp: Long): String {
    val formatter = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
    return formatter.format(Date(timestamp))
}

fun formatRelativeTime(offsetMs: Long): String {
    val totalMinutes = (offsetMs / 60000L).toInt().coerceAtLeast(0)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) {
        if (minutes == 0) {
            "${hours}h"
        } else {
            "${hours}h${minutes}m"
        }
    } else {
        "${minutes}m"
    }
}

fun computePowerW(
    rawPower: Double,
    dualCellEnabled: Boolean,
    calibrationValue: Int
): Double {
    val cellMultiplier = if (dualCellEnabled) 2 else 1
    return cellMultiplier * calibrationValue * (rawPower / POWER_SCALE_DIVISOR)
}

/**
 * 将原始功率值转换为瓦特并格式化（保留1位小数）
 *
 * 计算公式：finalValue = cellMultiplier × calibrationValue × (rawPower / 1000000000000)
 * - 注意：当前实现按 10^12 做缩放转换为瓦特(W)
 * - 双电芯设备需乘以 2（两块电池并联）
 * - calibrationValue 表示“原始电流到实际功率”的换算倍率，负值同时承担方向反转语义
 *
 * @param powerW 原始功率值（记录文件中的原始数值）
 * @param dualCellEnabled 是否为双电芯设备，true 时功率值乘以 2
 * @param calibrationValue 电流校准系数，同时决定方向与倍率
 * @return 格式化后的功率字符串，如 "12.5 W"
 */
fun formatPower(
    powerW: Double,
    dualCellEnabled: Boolean,
    calibrationValue: Int
): String {
    val finalValue = computePowerW(powerW, dualCellEnabled, calibrationValue)
    return String.format(Locale.getDefault(), "%.2f W", finalValue)
}

/**
 * 将原始功率值转换为瓦特并格式化（取整，无小数）
 *
 * 计算逻辑同 [formatPower]，区别在于输出为整数
 *
 * @param powerW 原始功率值（纳瓦 nW）
 * @param dualCellEnabled 是否为双电芯设备
 * @param calibrationValue 电流校准系数
 * @return 格式化后的功率字符串，如 "12 W"
 */
fun formatPowerInt(
    powerW: Double,
    dualCellEnabled: Boolean,
    calibrationValue: Int
): String {
    val finalValue = computePowerW(powerW, dualCellEnabled, calibrationValue)
    return String.format(Locale.getDefault(), "%.0f W", finalValue)
}

fun formatRemainingTime(hours: Double): String {
    if (hours < 0) return "0m"
    val totalMinutes = (hours * 60).toLong()
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return when {
        h > 0 && m > 0 -> appString(R.string.format_about_remaining_hm, h, m)
        h > 0 -> appString(R.string.format_about_remaining_h, h)
        else -> appString(R.string.format_about_remaining_m, m)
    }
}

fun formatFullRemainingTime(hours: Double): String {
    if (hours < 0) return "0m"
    val totalMinutes = (hours * 60).toLong()
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return when {
        h > 0 && m > 0 -> "${h}h${m}m"
        h > 0 -> "${h}h"
        else -> "${m}m"
    }
}
