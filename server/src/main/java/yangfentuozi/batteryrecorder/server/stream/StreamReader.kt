package yangfentuozi.batteryrecorder.server.stream

import yangfentuozi.batteryrecorder.server.writer.PowerRecordWriter
import yangfentuozi.batteryrecorder.shared.data.BatteryStatus
import yangfentuozi.batteryrecorder.shared.util.LoggerX
import java.io.Closeable
import java.io.DataInputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream

class StreamReader(
    inputStream: InputStream
): Closeable {
    private val tag = "StreamReader"
    private val input = DataInputStream(inputStream)

    /**
     * 读取下一条记录：
     * - 成功：返回 WriterStatusData
     * - 正常 EOF（还没开始读新帧就到流末尾）：返回 null
     * - 帧读到一半 EOF / 数据损坏 / 协议错误：抛 IOException
     */
    fun readNext(): PowerRecordWriter.WriterStatusData? {
        // 标志位
        val magic = try {
            input.readInt()
        } catch (_: EOFException) {
            // 流正常结束：连新帧的 magic 都没读到
            return null
        }

        if (magic != StreamProtocol.MAGIC) {
            throw IOException(
                "错误标志位: 0x${magic.toUInt().toString(16)}, " +
                        "期望: 0x${StreamProtocol.MAGIC.toUInt().toString(16)}"
            )
        }
        try {
            // flag
            when(val flag = input.readInt()) {
                StreamProtocol.FLAG_DATA -> {}
                else -> throw IOException("无效 flag: $flag")
            }

            return when(val ver = input.readInt()) {
                StreamProtocol.VERSION -> {
                    fun readChildWriterStatusData(): PowerRecordWriter.ChildWriterStatusData {
                        val segmentFile = input.readUTF()
                        val startTime = input.readLong()
                        val lastTime = input.readLong()
                        val lastChangedStatusTime = input.readLong()
                        return PowerRecordWriter.ChildWriterStatusData(segmentFile, startTime, lastTime, lastChangedStatusTime)
                    }
                    val lastStatus = BatteryStatus.fromValue(input.readInt())
                    val chargeDataWriterStatusData = readChildWriterStatusData()
                    val dischargeDataWriterStatusData = readChildWriterStatusData()
                    PowerRecordWriter.WriterStatusData(lastStatus, chargeDataWriterStatusData, dischargeDataWriterStatusData)
                }
                else -> {
                    LoggerX.e(tag, "readNext: 不支持的版本: $ver")
                    null
                }
            }
        } catch (e: EOFException) {
            throw IOException("非预期的 EOF", e)
        }
    }

    override fun close() {
        input.close()
    }
}