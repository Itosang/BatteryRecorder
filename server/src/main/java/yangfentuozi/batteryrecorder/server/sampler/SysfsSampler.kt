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
object SysfsSampler: Sampler() {

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

    override fun sample(): BatteryData {
        return BatteryData(
            voltage = normalizeVoltageToMicroVolt(nativeGetVoltage()),
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
