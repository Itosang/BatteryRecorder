package yangfentuozi.batteryrecorder.server.sampler

import android.os.BatteryManager
import android.os.BatteryProperty
import android.os.IBatteryPropertiesRegistrar
import android.os.ParcelFileDescriptor
import android.os.ServiceManager
import androidx.annotation.Keep
import yangfentuozi.batteryrecorder.shared.data.BatteryStatus
import yangfentuozi.batteryrecorder.shared.util.LoggerX

@Keep
class DumpsysSampler : Sampler() {

    private val tag = "DumpsysSampler"

    private val batteryService = ServiceManager.getService("battery")
    private var registrar: IBatteryPropertiesRegistrar =
        IBatteryPropertiesRegistrar.Stub.asInterface(
            ServiceManager.getService("batteryproperties")
        )

    private val prop = BatteryProperty()

    private external fun nativeParseBatteryDumpPfd(pfd: ParcelFileDescriptor): LongArray

    init {
        LoggerX.d(tag, "init: 启用 Dumpsys 回退采样器")
    }

    private var printedWarning = false

    override fun sample(): BatteryData {
        val pipe = ParcelFileDescriptor.createPipe()

        val readSide = pipe[0]
        val writeSide = pipe[1]

        // 执行 dump
        Thread {
            try {
                batteryService.dump(writeSide.fileDescriptor, arrayOf())
            } catch (e: Exception) {
                LoggerX.e(tag, "@dumpThread: dump 失败", tr = e)
            } finally {
                writeSide.close()
            }
        }.start()

        var flag = false
        var voltage: Long = 0
        var current: Long = 0
        var capacity = 0
        var status: BatteryStatus = BatteryStatus.Unknown
        var rawStatus = BatteryManager.BATTERY_STATUS_UNKNOWN
        var temp = 0
        var readSideAutoClosed = false
        try {
            registrar.getProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW, prop)
            current = prop.long
            registrar.getProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY, prop)
            capacity = prop.long.toInt()
            registrar.getProperty(BatteryManager.BATTERY_PROPERTY_STATUS, prop)
            rawStatus = prop.long.toInt()
            status = BatteryStatus.fromValue(rawStatus)

            try {
                val result = nativeParseBatteryDumpPfd(readSide)
                voltage = result.getOrNull(0) ?: 0
                temp = (result.getOrNull(1) ?: 0).toInt()
            } catch (e: UnsatisfiedLinkError) {
                if (!printedWarning) {
                    LoggerX.d(tag, "sample: JNI 未加载，回退 Kotlin 解析 dump 输出流", tr = e)
                    printedWarning = true
                }
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
            LoggerX.e(tag, "sample: 读取 dump 输出流失败", tr = e)
        } finally {
            if (!readSideAutoClosed) {
                try {
                    readSide.close()
                } catch (e: Exception) {
                    LoggerX.w(tag, "sample: 关闭 readSide 失败", tr = e)
                }
            }
        }
        return BatteryData(
            // dumpsys 电压一定是 sysfs 电压除以 1000
            voltage = normalizeVoltageToMicroVolt(voltage * 1000),
            current = current,
            capacity = capacity,
            status = status,
            temp = temp
        ).also { data ->
            LoggerX.v(
                tag,
                "sample: 采样诊断 rawStatus=%d status=%s voltage=%d current=%d capacity=%d temp=%d",
                rawStatus,
                status,
                data.voltage,
                current,
                capacity,
                temp
            )
        }
    }
}
