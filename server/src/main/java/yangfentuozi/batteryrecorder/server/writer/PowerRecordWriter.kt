package yangfentuozi.batteryrecorder.server.writer

import android.os.Handler
import yangfentuozi.batteryrecorder.shared.data.BatteryStatus
import yangfentuozi.batteryrecorder.shared.data.BatteryStatus.Charging
import yangfentuozi.batteryrecorder.shared.data.BatteryStatus.Discharging
import yangfentuozi.batteryrecorder.shared.data.LineRecord
import yangfentuozi.batteryrecorder.shared.data.RecordsFile
import yangfentuozi.batteryrecorder.shared.util.Handlers
import yangfentuozi.batteryrecorder.shared.util.LoggerX
import yangfentuozi.batteryrecorder.shared.writer.AdvancedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream

class PowerRecordWriter(
    powerDir: File,
    writerStatusData: WriterStatusData?,
    private val fixFileOwner: ((File) -> Unit)
) {
    private val tag = "PowerRecordWriter"

    /**
     * 单条采样写入结果。
     *
     * 结果空间只保留真实存在的三种状态：
     * [Accepted] 表示样本进入当前记录文件但未切换文件；
     * [Rejected] 表示样本被过滤，不应继续向上游分发；
     * [Changed] 表示样本进入新当前文件，且文件已在本次写入后切换。
     *
     * 其中 [Changed] 隐含“该样本已 accepted”，调用方不应再额外维护布尔组合状态。
     */
    sealed interface WriteResult {
        data object Accepted : WriteResult
        data object Rejected : WriteResult
        data class Changed(val recordsFile: RecordsFile) : WriteResult
    }

    private val chargeDir = File(powerDir, "charge")
    private val dischargeDir = File(powerDir, "discharge")

    @Volatile
    var lastStatus: BatteryStatus = writerStatusData?.lastStatus ?: BatteryStatus.Unknown
        private set

    val chargeDataWriter = ChargeDataWriter(chargeDir, writerStatusData?.chargeDataWriterStatusData)
    val dischargeDataWriter = DischargeDataWriter(dischargeDir, writerStatusData?.dischargeDataWriterStatusData)

    @Volatile
    var batchSize = 200

    @Volatile
    var flushIntervalMs = 30 * 1000L

    @Volatile
    var maxSegmentDurationMs = 24 * 60 * 60 * 1000L

    init {
        fun makeSureExists(file: File) {
            if (!file.exists()) {
                if (!file.mkdirs()) {
                    LoggerX.e(tag, "makeSureExists: 创建功率数据目录失败, dir=${file.absolutePath}")
                    throw IOException("makeSureExists: 创建功率数据文件夹: ${file.absolutePath} 失败")
                }
                LoggerX.d(tag, "makeSureExists: 创建功率数据目录, dir=${file.absolutePath}")
            } else if (!file.isDirectory()) {
                LoggerX.e(tag, "makeSureExists: 功率数据路径不是目录, path=${file.absolutePath}")
                throw IOException("makeSureExists: 功率数据文件夹: ${file.absolutePath} 不是一个文件夹")
            }
            fixFileOwner(file)
        }
        makeSureExists(powerDir)
        makeSureExists(chargeDir)
        makeSureExists(dischargeDir)
    }

    fun write(record: LineRecord): WriteResult {
        val statusChanged = lastStatus != record.status
        if (statusChanged) {
            LoggerX.d(tag, "write: 电池状态切换, $lastStatus -> ${record.status}")
        }

        val result = when (record.status) {
            Charging -> {
                if (statusChanged) {
                    chargeDataWriter.hasPendingStatusChange = true
                    chargeDataWriter.lastChangedStatusTime = record.timestamp
                }
                chargeDataWriter.write(record)
            }

            Discharging -> {
                if (statusChanged) {
                    dischargeDataWriter.hasPendingStatusChange = true
                    dischargeDataWriter.lastChangedStatusTime = record.timestamp
                }
                dischargeDataWriter.write(record)
            }

            else -> WriteResult.Rejected
        }
        lastStatus = record.status
        return result
    }

    fun close() {
        chargeDataWriter.closeCurrentSegment()
        dischargeDataWriter.closeCurrentSegment()
    }

    fun flushBuffer() {
        chargeDataWriter.flushBuffer()
        dischargeDataWriter.flushBuffer()
    }

    fun flushBufferBlocking() {
        chargeDataWriter.flushBufferBlocking()
        dischargeDataWriter.flushBufferBlocking()
    }

    inner class ChargeDataWriter(dir: File, statusData: ChildWriterStatusData?) : BaseDelayedRecordWriter(dir, statusData) {
        override fun needStartNewSegment(justChangedStatus: Boolean, nowTime: Long): Boolean {
            // case1 记录超过最大分段时间（0 表示不按时间分段）
            return (maxSegmentDurationMs > 0 && nowTime - startTime > maxSegmentDurationMs) ||
                    // case2 允许短时间内续接之前记录
                    (justChangedStatus && nowTime - lastTime > 30 * 1000)
        }

        override fun needDeleteSegment(nowTime: Long): Boolean {
            return nowTime - startTime < 1 * 60 * 1000 // 1min
        }
    }

    inner class DischargeDataWriter(dir: File, statusData: ChildWriterStatusData?) : BaseDelayedRecordWriter(dir, statusData) {
        override fun needStartNewSegment(justChangedStatus: Boolean, nowTime: Long): Boolean {
            // case1 记录超过最大分段时间（0 表示不按时间分段）
            return (maxSegmentDurationMs > 0 && nowTime - startTime > maxSegmentDurationMs) || justChangedStatus
                    // case2 允许短时间内续接之前记录 (暂时禁用)
                    // (justChangedStatus && nowTime - lastTime > 10 * 60 * 1000)
        }

        override fun needDeleteSegment(nowTime: Long): Boolean {
            return nowTime - startTime < 10 * 60 * 1000 // 10min
        }
    }

    abstract inner class BaseDelayedRecordWriter(val dir: File, statusData: ChildWriterStatusData?) {
        private val tag = "BaseDelayedRecordWriter"

        @Volatile
        var segmentFile: File? = null
            private set
        private var writer: AdvancedWriter? = null

        @Volatile
        var hasPendingStatusChange: Boolean = false

        protected var startTime: Long = 0L
        protected var lastTime: Long = 0L
        var lastChangedStatusTime = 0L

        protected val handler: Handler = Handlers.getHandler("RecorderWritingThread")

        @Volatile
        private var isClosed = false

        init {
            if (statusData != null) {
                LoggerX.d(tag, "init: 续接之前状态 $statusData")
                val file = File(statusData.segmentFile)
                segmentFile = file
                startTime = statusData.startTime
                lastTime = statusData.lastTime
                lastChangedStatusTime = statusData.lastChangedStatusTime

                val openOutputStream: (() -> OutputStream) = {
                    if (!file.exists() && !file.createNewFile()) {
                        throw IOException("@openOutputStream: 创建分段文件: ${file.absolutePath} 失败")
                    }
                    fixFileOwner(file)
                    FileOutputStream(file, true)
                }
                writer = AdvancedWriter(
                    handler = handler,
                    batchSize = { batchSize },
                    flushIntervalMs = { flushIntervalMs },
                    outputStream = openOutputStream(),
                    retryTimes = 3,
                    retryIntervalMs = 1000,
                    reopenOutputStream = openOutputStream
                )
            }
        }

        fun write(record: LineRecord): WriteResult {
            if (isClosed) return WriteResult.Rejected
            val justChangedStatus = hasPendingStatusChange

            // 选择性丢弃一些干扰数据
            if (record.timestamp - lastChangedStatusTime < 2 * 1000L) {
                if ((record.power > 0) != (record.status == Discharging)) {
                    LoggerX.v(this@BaseDelayedRecordWriter.tag, "write: 跳过状态切换瞬时干扰数据, dir=${dir.name}")
                    return WriteResult.Rejected
                }
            }

            val startedNewSegment = startNewSegmentIfNeed(justChangedStatus)
            lastTime = record.timestamp

            writer!!.appendLine(record)
            hasPendingStatusChange = false

            if (startedNewSegment) {
                LoggerX.d(this@BaseDelayedRecordWriter.tag, "write: 新分段已创建, 立即落盘, file=${segmentFile?.name}")
                writer!!.flushNowBlocking()
                val currentFile = segmentFile
                if (currentFile != null) {
                    LoggerX.d(
                        this@BaseDelayedRecordWriter.tag,
                        "write: 当前记录文件已切换, reason=新分段已就绪 file=${currentFile.name}"
                    )
                    return WriteResult.Changed(RecordsFile.fromFile(currentFile))
                }
                // TODO: 这里理论上不应为空。先保留错误日志观察实际触发路径，如果没问他就不管
                // 后续再收紧 writer 状态只由采样线程维护，避免在此处直接中断采样线程。
                LoggerX.e(
                    this@BaseDelayedRecordWriter.tag,
                    "write: 当前记录文件切换后 segmentFile 为空, reason=新分段已就绪 dir=${dir.name}"
                )
                return WriteResult.Accepted
            }

            writer!!.onEnqueued()
            if (justChangedStatus) {
                writer!!.flushNowBlocking()
                val currentFile = segmentFile
                if (currentFile != null) {
                    LoggerX.d(
                        this@BaseDelayedRecordWriter.tag,
                        "write: 当前记录文件已切换, reason=状态切换后续写当前分段 file=${currentFile.name}"
                    )
                    return WriteResult.Changed(RecordsFile.fromFile(currentFile))
                }
                // TODO: 这里理论上不应为空。先保留错误日志观察实际触发路径，如果没问他就不管
                // 后续再收紧 writer 状态只由采样线程维护，避免在此处直接中断采样线程。
                LoggerX.e(
                    this@BaseDelayedRecordWriter.tag,
                    "write: 当前记录文件切换后 segmentFile 为空, reason=状态切换后续写当前分段 dir=${dir.name}"
                )
                return WriteResult.Accepted
            }
            return WriteResult.Accepted
        }

        private fun startNewSegmentIfNeed(
            justChangedStatus: Boolean
        ): Boolean {
            val nowTime = System.currentTimeMillis()
            if (needStartNewSegment(justChangedStatus, nowTime) ||
                // case 还没记录过
                writer == null
            ) {
                // 关闭之前的记录，打开新的
                closeCurrentSegment()
                startTime = nowTime
                val fileName = "$nowTime.txt"
                val file = File(dir, fileName)
                segmentFile = file
                LoggerX.d(
                    this@BaseDelayedRecordWriter.tag,
                    "startNewSegmentIfNeed: 创建新分段, dir=${dir.name} file=${file.name} justChangedStatus=$justChangedStatus"
                )

                val openOutputStream: (() -> OutputStream) = {
                    if (!file.exists() && !file.createNewFile()) {
                        throw IOException("@openOutputStream: 创建分段文件: ${file.absolutePath} 失败")
                    }
                    fixFileOwner(file)
                    FileOutputStream(file, true)
                }
                writer = AdvancedWriter(
                    handler = handler,
                    batchSize = { batchSize },
                    flushIntervalMs = { flushIntervalMs },
                    outputStream = openOutputStream(),
                    retryTimes = 3,
                    retryIntervalMs = 1000,
                    reopenOutputStream = openOutputStream
                )
                return true
            }
            return false
        }

        abstract fun needStartNewSegment(
            justChangedStatus: Boolean,
            nowTime: Long = System.currentTimeMillis()
        ): Boolean

        abstract fun needDeleteSegment(
            nowTime: Long = System.currentTimeMillis()
        ): Boolean

        fun flushBuffer() {
            writer?.flushNow()
        }

        fun flushBufferBlocking() {
            writer?.flushNowBlocking()
        }

        fun closeCurrentSegment() {
            flushBuffer()
            if (writer != null) {
                try {
                    writer!!.close()
                } catch (e: IOException) {
                    LoggerX.e(this@BaseDelayedRecordWriter.tag, "closeCurrentSegment: 关闭分段文件失败", tr = e)
                }
                writer = null
                if (needDeleteSegment(System.currentTimeMillis())) {
                    LoggerX.v(this@BaseDelayedRecordWriter.tag, "closeCurrentSegment: 删除短分段, file=${segmentFile?.name}")
                    segmentFile!!.delete()
                }
                segmentFile = null
            }
        }

        fun getCurrFile(
            justChangedStatus: Boolean
        ): File? {
            if (needStartNewSegment(justChangedStatus)) closeCurrentSegment()
            return segmentFile
        }

        fun currWriterStatusAndClose(): ChildWriterStatusData? {
            val result = segmentFile?.let {
                ChildWriterStatusData(
                    segmentFile = it.absolutePath,
                    startTime = startTime,
                    lastTime = lastTime,
                    lastChangedStatusTime = lastChangedStatusTime
                )
            }
            isClosed = true
            writer?.close()
            writer = null
            return result
        }
    }

    fun currWriterStatusAndClose(): WriterStatusData {
        return WriterStatusData(
            lastStatus = lastStatus,
            chargeDataWriterStatusData = chargeDataWriter.currWriterStatusAndClose(),
            dischargeDataWriterStatusData = dischargeDataWriter.currWriterStatusAndClose()
        )
    }

    data class ChildWriterStatusData(
        val segmentFile: String,
        val startTime: Long = 0L,
        val lastTime: Long = 0L,
        val lastChangedStatusTime: Long = 0L
    )

    data class WriterStatusData(
        val lastStatus: BatteryStatus,
        val chargeDataWriterStatusData: ChildWriterStatusData?,
        val dischargeDataWriterStatusData: ChildWriterStatusData?,
    )
}
