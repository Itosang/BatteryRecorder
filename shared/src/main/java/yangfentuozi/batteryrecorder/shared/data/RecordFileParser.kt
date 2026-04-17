package yangfentuozi.batteryrecorder.shared.data

import yangfentuozi.batteryrecorder.shared.util.LoggerX
import java.io.File

object RecordFileParser {
    private const val TAG = "RecordFileParser"

    fun parseToList(file: File): List<LineRecord> {
        val records = mutableListOf<LineRecord>()
        var firstTimestamp: Long? = null
        var lastTimestamp: Long? = null
        forEachValidRecord(file) { record ->
            if (firstTimestamp == null) {
                firstTimestamp = record.timestamp
            }
            lastTimestamp = record.timestamp
            records += record
        }
        LoggerX.d(TAG, 
            "parseToList: 解析完成, file=${file.name} count=${records.size} firstTs=$firstTimestamp lastTs=$lastTimestamp"
        )
        return records
    }

    fun forEachValidRecord(
        file: File,
        onRecord: (LineRecord) -> Unit
    ) {
        LoggerX.d(TAG, "forEachValidRecord: 开始解析, file=${file.absolutePath}")
        var lineNumber = 0
        var previousParsedTimestamp: Long? = null

        RecordFileIO.openBufferedReader(file).useLines { lines ->
            lines.forEach { raw ->
                lineNumber++
                val line = raw.trim()
                if (line.isEmpty()) return@forEach

                val parts = line.split(",")
                if (parts.size != LineRecord.PERSISTED_COLUMN_COUNT) {
                    logInvalidLine(
                        file = file,
                        lineNumber = lineNumber,
                        reason = "字段数量不匹配: ${parts.size}"
                    )
                    return@forEach
                }

                val timestampValue = parts[0]
                if (timestampValue.length != LineRecord.PERSISTED_TIMESTAMP_LENGTH) {
                    logInvalidLine(
                        file = file,
                        lineNumber = lineNumber,
                        reason = "时间戳长度异常"
                    )
                    return@forEach
                }

                val record = LineRecord.fromParts(parts)
                if (record == null) {
                    logInvalidLine(
                        file = file,
                        lineNumber = lineNumber,
                        reason = "字段解析失败"
                    )
                    return@forEach
                }

                val previousTimestamp = previousParsedTimestamp
                if (previousTimestamp != null && record.timestamp <= previousTimestamp) {
                    logInvalidLine(
                        file = file,
                        lineNumber = lineNumber,
                        reason = "时间戳未严格递增"
                    )
                    return@forEach
                }

                previousParsedTimestamp = record.timestamp
                onRecord(record)
            }
        }
    }

    private fun logInvalidLine(
        file: File,
        lineNumber: Int,
        reason: String
    ) {
        LoggerX.w(TAG, 
            "logInvalidLine: 跳过损坏记录, file=${file.absolutePath} line=$lineNumber reason=$reason"
        )
    }
}
