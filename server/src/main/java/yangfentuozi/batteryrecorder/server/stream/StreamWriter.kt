package yangfentuozi.batteryrecorder.server.stream

import yangfentuozi.batteryrecorder.server.writer.PowerRecordWriter
import java.io.Closeable
import java.io.DataOutputStream
import java.io.OutputStream

class StreamWriter(
    outputStream: OutputStream
): Closeable {
    private val out = DataOutputStream(outputStream)

    fun write(statusData: PowerRecordWriter.WriterStatusData) {
        // 标志位
        out.writeInt(StreamProtocol.MAGIC)
        // flag
        out.writeInt(StreamProtocol.FLAG_DATA)
        // version
        out.writeInt(StreamProtocol.VERSION)

        fun writeChildWriterStatusData(childWriterStatusData: PowerRecordWriter.ChildWriterStatusData) {
            out.writeUTF(childWriterStatusData.segmentFile)
            out.writeLong(childWriterStatusData.startTime)
            out.writeLong(childWriterStatusData.lastTime)
            out.writeLong(childWriterStatusData.lastChangedStatusTime)
        }

        out.writeInt(statusData.lastStatus.value)
        writeChildWriterStatusData(statusData.chargeDataWriterStatusData)
        writeChildWriterStatusData(statusData.dischargeDataWriterStatusData)

        // 刷新一下
        out.flush()
    }

    override fun close() {
        out.close()
    }
}