package yangfentuozi.batteryrecorder.server

import android.system.Os
import yangfentuozi.batteryrecorder.server.recorder.IRecordListener
import yangfentuozi.batteryrecorder.server.recorder.Monitor
import yangfentuozi.batteryrecorder.server.sampler.Sampler
import yangfentuozi.batteryrecorder.server.writer.PowerRecordWriter
import yangfentuozi.batteryrecorder.server.writer.PowerRecordWriter.WriterStatusData
import yangfentuozi.batteryrecorder.shared.config.dataclass.ServerSettings
import yangfentuozi.batteryrecorder.shared.data.BatteryStatus.Charging
import yangfentuozi.batteryrecorder.shared.data.BatteryStatus.Discharging
import yangfentuozi.batteryrecorder.shared.data.RecordsFile
import yangfentuozi.batteryrecorder.shared.util.Handlers
import yangfentuozi.batteryrecorder.shared.util.LoggerX
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference

private const val TAG = "Server"

abstract class Server(
    powerDir: File,
    writerStatusData: WriterStatusData?,
    fixFileOwner: ((File) -> Unit),
    sampler: Sampler
): IService.Stub() {

    enum class State { NEW, RUNNING, STOPPED }

    private val state = AtomicReference(State.NEW)

    var monitor: Monitor
        private set
    var writer: PowerRecordWriter
        private set

    override fun getVersion(): Int = BuildConfig.VERSION

    override fun getCurrRecordsFile(): RecordsFile? {
        val lastStatus = writer.lastStatus
        val file = when (lastStatus) {
            Charging -> writer.chargeDataWriter.getCurrFile(writer.chargeDataWriter.hasPendingStatusChange)
            Discharging -> writer.dischargeDataWriter.getCurrFile(writer.dischargeDataWriter.hasPendingStatusChange)
            else -> null
        }
        if (file == null) {
            LoggerX.w(
                TAG,
                "getCurrRecordsFile: 当前记录文件为空, lastStatus=%s chargeFile=%s dischargeFile=%s",
                lastStatus,
                writer.chargeDataWriter.segmentFile?.name,
                writer.dischargeDataWriter.segmentFile?.name
            )
            return null
        }
        LoggerX.d(
            TAG,
            "getCurrRecordsFile: $file",
        )
        return RecordsFile.fromFile(file)
    }

    override fun registerRecordListener(listener: IRecordListener) {
        monitor.registerRecordListener(listener)
    }

    override fun unregisterRecordListener(listener: IRecordListener) {
        monitor.unregisterRecordListener(listener)
    }

    open fun syncSettingsBlocking(settings: ServerSettings) {
        LoggerX.d(TAG, "syncSettingsBlocking: 应用配置: $settings")

        monitor.syncSettings(settings)
        writer.syncSettings(settings)
    }

    override fun syncSettings(settings: ServerSettings) {
        Handlers.common.post {
            syncSettingsBlocking(settings)
        }
    }

    fun onStop() {
        if (!state.compareAndSet(State.RUNNING, State.STOPPED)) {
            return
        }

        monitor.onStop()

        try {
            writer.flushBuffer()
        } catch (e: IOException) {
            LoggerX.e(TAG, "onStop: flushBuffer 失败", tr = e)
        }
        writer.close()
        try {
            LoggerX.flushBlocking()
            LoggerX.writer?.close()
        } catch (e: Exception) {
            LoggerX.e(TAG, "onStop: 刷新日志缓冲失败", tr = e, notWrite = true)
        }
        Handlers.interruptAll()
    }

    init {
        LoggerX.i(TAG, "init: Server 初始化开始, uid=${Os.getuid()}")

        try {
            writer = PowerRecordWriter(powerDir, writerStatusData, fixFileOwner)
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
        LoggerX.i(TAG, "init: Writer 初始化完成, targetDir=$powerDir")

        monitor = Monitor(writer, sampler)
        LoggerX.d(TAG, "init: Monitor 初始化完成")

    }

    fun onStart() {
        if (!state.compareAndSet(State.NEW, State.RUNNING)) {
            return
        }

        monitor.start()
        LoggerX.i(TAG, "init: Server 已启动")
    }
}
