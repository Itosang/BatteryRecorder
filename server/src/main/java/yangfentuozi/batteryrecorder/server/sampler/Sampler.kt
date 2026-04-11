package yangfentuozi.batteryrecorder.server.sampler

import yangfentuozi.batteryrecorder.shared.data.BatteryStatus
import yangfentuozi.batteryrecorder.shared.util.LoggerX

abstract class Sampler {

    private val tag = "Sampler"

    abstract fun sample(): BatteryData

    private var printedWarning = false

    /**
     * 将 `voltage_now` 的返回值统一归一到 uV。
     *
     * 规范实现应直接返回 uV，例如 `4172000`；
     * 但少数 OEM 设备会错误返回 mV，例如 `4172`，这里需要在采样阶段补齐。
     *
     * @param rawVoltage `voltage_now` 原始返回值
     * @return 统一后的 uV 电压；非正数直接原样返回
     */
    fun normalizeVoltageToMicroVolt(rawVoltage: Long): Long {
        if (rawVoltage <= 0L) return rawVoltage
        if (rawVoltage >= 100_000L) return rawVoltage

        val normalizedVoltage = rawVoltage * 1_000L
        if (!printedWarning) {
            // 只打印一次 log
            LoggerX.w(
                tag,
                "normalizeSysfsVoltageToMicroVolt: voltage_now 口径异常, raw=$rawVoltage normalized=$normalizedVoltage"
            )
            printedWarning = true
        }
        return normalizedVoltage
    }

    data class BatteryData(
        val voltage: Long,
        val current: Long,
        val capacity: Int,
        val status: BatteryStatus,
        val temp: Int
    )
}