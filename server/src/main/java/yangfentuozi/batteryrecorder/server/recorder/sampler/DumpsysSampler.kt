package yangfentuozi.batteryrecorder.server.recorder.sampler

import android.os.BatteryManager
import android.os.BatteryProperty
import android.os.IBatteryPropertiesRegistrar
import android.os.ParcelFileDescriptor
import android.os.ServiceManager
import androidx.annotation.Keep
import yangfentuozi.batteryrecorder.shared.data.BatteryStatus
import yangfentuozi.batteryrecorder.shared.util.LoggerX

private const val TAG = "DumpsysSampler"

@Keep
class DumpsysSampler : Sampler {
    private val batteryService = ServiceManager.getService("battery")
    private var registrar: IBatteryPropertiesRegistrar =
        IBatteryPropertiesRegistrar.Stub.asInterface(
            ServiceManager.getService("batteryproperties")
        )

    private val prop = BatteryProperty()

    private external fun nativeParseBatteryDumpPfd(pfd: ParcelFileDescriptor): LongArray

    init {
        LoggerX.d(TAG, "init: 启用 Dumpsys 回退采样器")
    }

    override fun sample(): Sampler.BatteryData {
        val pipe = ParcelFileDescriptor.createPipe()

        val readSide = pipe[0]
        val writeSide = pipe[1]

        // 执行 dump
        Thread {
            try {
                batteryService.dump(writeSide.fileDescriptor, arrayOf())
            } catch (e: Exception) {
                LoggerX.e(TAG, "@dumpThread: dump 失败", tr = e)
            } finally {
                writeSide.close()
            }
        }.start()

        var flag = false
        var voltage: Long = 0
        var current: Long = 0
        var capacity = 0
        var status: BatteryStatus = BatteryStatus.Unknown
        var temp = 0
        var readSideAutoClosed = false
        try {
            registrar.getProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW, prop)
            current = prop.long
            registrar.getProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY, prop)
            capacity = prop.long.toInt()
            registrar.getProperty(BatteryManager.BATTERY_PROPERTY_STATUS, prop)
            status = BatteryStatus.fromValue(prop.long.toInt())

            try {
                val result = nativeParseBatteryDumpPfd(readSide)
                voltage = result.getOrNull(0) ?: 0
                temp = (result.getOrNull(1) ?: 0).toInt()
            } catch (e: UnsatisfiedLinkError) {
                LoggerX.d(TAG, "sample: JNI 未加载，回退 Kotlin 解析 dump 输出流", tr = e)
                ParcelFileDescriptor.AutoCloseInputStream(readSide).bufferedReader().use { reader ->
                    var line: String?
                    while ((reader.readLine().also { line = it }) != null) {
                        if (line != null) if (flag) {
                            when {
                                line.contains("voltage:") -> {
                                    line.substringAfter(": ").trim().toLongOrNull().let {
                                        if (it != null) voltage = it
                                    }
                                }

                                line.contains("temperature:") -> {
                                    line.substringAfter(": ").trim().toIntOrNull().let {
                                        if (it != null) temp = it
                                    }
                                }
                            }
                        } else if (line.contains("Current Battery Service state:")) flag = true
                    }
                }
                readSideAutoClosed = true
            }
        } catch (e: Exception) {
            LoggerX.e(TAG, "sample: 读取 dump 输出流失败", tr = e)
        } finally {
            if (!readSideAutoClosed) {
                try {
                    readSide.close()
                } catch (e: Exception) {
                    LoggerX.w(TAG, "sample: 关闭 readSide 失败", tr = e)
                }
            }
        }
        return Sampler.BatteryData(
            voltage = voltage * 1000, // 修正单位与内核数据一致
            current = current,
            capacity = capacity,
            status = status,
            temp = temp
        )
    }
}
