package yangfentuozi.batteryrecorder.server.sampler

import android.annotation.SuppressLint
import android.content.pm.ApplicationInfo
import android.os.Build
import android.system.Os
import androidx.annotation.Keep
import yangfentuozi.batteryrecorder.shared.data.BatteryStatus
import yangfentuozi.batteryrecorder.shared.util.LoggerX
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

@Keep
object SysfsSampler: Sampler {

    private const val TAG = "SysfsSampler"

    @JvmStatic
    external fun nativeInit(): Int

    @JvmStatic
    external fun nativeGetVoltage(): Long

    @JvmStatic
    external fun nativeGetCurrent(): Long

    @JvmStatic
    external fun nativeGetCapacity(): Int

    @JvmStatic
    external fun nativeGetStatus(): Int

    @JvmStatic
    external fun nativeGetTemp(): Int

    /**
     * 将 sysfs `voltage_now` 的返回值统一归一到 uV。
     *
     * 规范实现应直接返回 uV，例如 `4172000`；
     * 但少数 OEM 设备会错误返回 mV，例如 `4172`，这里需要在采样阶段补齐。
     *
     * @param rawVoltage `voltage_now` 原始返回值
     * @return 统一后的 uV 电压；非正数直接原样返回
     */
    private fun normalizeSysfsVoltageToMicroVolt(rawVoltage: Long): Long {
        if (rawVoltage <= 0L) return rawVoltage
        if (rawVoltage >= 100_000L) return rawVoltage

        val normalizedVoltage = rawVoltage * 1_000L
        LoggerX.w(
            TAG,
            "normalizeSysfsVoltageToMicroVolt: voltage_now 口径异常, raw=$rawVoltage normalized=$normalizedVoltage"
        )
        return normalizedVoltage
    }

    @SuppressLint("UnsafeDynamicallyLoadedCode")
    fun init(appInfo: ApplicationInfo): Boolean {
        try {
            val libraryTmpPath = "/data/local/tmp/libbatteryrecorder.so"
            runCatching { Os.remove(libraryTmpPath) }
            val apk = ZipFile(appInfo.sourceDir)
            apk.getInputStream(apk.getEntry("lib/${Build.SUPPORTED_ABIS[0]}/libbatteryrecorder.so"))
                .copyTo(out = FileOutputStream(libraryTmpPath, false))
            File(libraryTmpPath).apply {
                deleteOnExit()
            }
            Os.chmod(libraryTmpPath, "400".toInt(8))
            System.load(libraryTmpPath)
            LoggerX.i(TAG, "init: JNI 库加载成功, path=$libraryTmpPath")
            val initResult = nativeInit() == 1
            if (initResult) {
                LoggerX.i(TAG, "init: nativeInit() 成功")
            } else {
                LoggerX.w(TAG, "init: nativeInit() 返回失败, fallback DumpsysSampler")
            }
            return initResult
        } catch (e: Throwable) {
            LoggerX.w(TAG, "init: 加载 JNI 失败, fallback DumpsysSampler", tr = e)
            return false
        }
    }

    override fun sample(): Sampler.BatteryData {
        return Sampler.BatteryData(
            voltage = normalizeSysfsVoltageToMicroVolt(nativeGetVoltage()),
            current = nativeGetCurrent(),
            capacity = nativeGetCapacity(),
            status = when (nativeGetStatus().toChar()) {
                'C' -> BatteryStatus.Charging
                'D' -> BatteryStatus.Discharging
                'N' -> BatteryStatus.NotCharging
                'F' -> BatteryStatus.Full
                else -> BatteryStatus.Unknown
            },
            temp = nativeGetTemp()
        )
    }
}
