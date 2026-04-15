package yangfentuozi.batteryrecorder.utils

import android.content.Context
import kotlin.math.roundToInt

private const val BATTERY_INFO_NOMINAL_VOLTAGE = 3.86
/**
 * 读取设备设计电池容量。
 *
 * 这里直接复用系统 `PowerProfile#getBatteryCapacity()` 的口径，避免把充电记录详情和
 * 当前记录统计链路混在一起。
 *
 * @param context 任意可用的应用上下文。
 * @return 设备设计容量，单位 mAh。
 */
fun readDeviceBatteryCapacityMah(context: Context): Int {
    val powerProfileClass = Class.forName("com.android.internal.os.PowerProfile")
    val powerProfile = powerProfileClass.getConstructor(Context::class.java)
        .newInstance(context.applicationContext)
    val capacityMah = (powerProfileClass.getMethod("getBatteryCapacity").invoke(powerProfile) as Number)
        .toDouble()
    check(capacityMah > 0.0) { "PowerProfile.getBatteryCapacity() 返回非法容量: $capacityMah" }
    return capacityMah.roundToInt()
}

/**
 * 按目标应用充电页口径把设计容量从 mAh 换算为 Wh。
 *
 * @param capacityMah 设备设计容量，单位 mAh。
 * @param referenceVoltageV 详情记录里的参考电压，单位 V。
 * @return 换算后的设计容量，单位 Wh。
 */
fun computeDesignCapacityWh(
    capacityMah: Int,
    referenceVoltageV: Double?
): Double {
    val cellMultiplier = if (
        capacityMah <= 2510 &&
        (referenceVoltageV ?: 0.0) >= 5.0
    ) {
        2
    } else {
        1
    }
    return capacityMah * BATTERY_INFO_NOMINAL_VOLTAGE * cellMultiplier / 1000.0
}

/**
 * 格式化充电记录详情中的设备电池信息文本。
 *
 * @param locale 当前界面使用的区域设置。
 * @param capacityMah 设备设计容量，单位 mAh。
 * @param referenceVoltageV 详情记录里的参考电压，单位 V。
 * @return 形如 `5000mAh(≈19.3Wh)` 的展示文本。
 */
fun formatChargeDetailBatteryInfo(
    locale: java.util.Locale,
    capacityMah: Int,
    referenceVoltageV: Double?
): String {
    val capacityWh = computeDesignCapacityWh(
        capacityMah = capacityMah,
        referenceVoltageV = referenceVoltageV
    )
    return String.format(locale, "%dmAh(≈%.1fWh)", capacityMah, capacityWh)
}
