package yangfentuozi.batteryrecorder.data.bh

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.GZIPInputStream

/**
 * 小米 HyperOS 系统电池历史（/data/system/battery-history/ 下的 .bh 文件）解析器。
 *
 * 文件结构（逆向自 Redmi K90 / HyperOS 4.0.0.34 实测样本）：
 * ```
 * [8 字节文件头]   "GZIP" + 版本(2) + 压缩流偏移
 * [gzip 流]        解压后为 BatteryStats 历史事件流
 *   解压后头部 32 字节：
 *     u32 @0  记录数
 *     u32 @4  会话起始 uptime(毫秒, 自首次开机累计)
 *     u32 @8  保留 0
 *     u32 @12 会话结束 uptime(毫秒)
 *     u32 @16 保留 0
 *     u32 @20 常量(样本恒为 65965816)
 *     u32 @24 保留 0
 *     u32 @28 数据区长度
 *   正文：事件流，事件名以 UTF-16LE 存储，包含：
 *     - display=0 state=ON/OFF/DOZE reason=...   屏幕状态机
 *     - ALARM_ACTION(10000)/u0/0x1055            应用定时器唤醒
 *     - xxx com.xiaomi.push.PING_TIMER/u0/0x2055 带哈希前缀的定时器
 *     - MSF:WakeLock:MsgPush                     唤醒锁
 *     - Abort: Pending Wakeup Sources: [...]     内核挂起失败原因
 *     - sensor:0x... / NR LOW|MID|HIGH / dt-*    传感器与网络
 * 文件名 = 会话起始 uptime 毫秒（如 357953908.bh）
 * ```
 */
object BhHistoryParser {

    private const val HEADER_SIZE = 32

    /** 单个 .bh 会话。 */
    data class BhSession(
        val file: File,
        /** 会话起始 uptime（毫秒，自首次开机累计）。 */
        val startUptimeMs: Long,
        /** 会话结束 uptime（毫秒）。 */
        val endUptimeMs: Long,
        /** 头部声明的记录数。 */
        val recordCount: Int,
        val events: List<BhEvent>,
        /**
         * 会话结束时的挂钟时间（epoch 毫秒）。
         *
         * .bh 文件在会话结束时写入，文件 mtime 即会话结束时刻。
         * root 读取时 mtime 由调用方（stat 列表）传入，避免 File 不可读导致为 0。
         */
        val endEpochMsOverride: Long = 0L
    ) {
        val durationMs: Long get() = (endUptimeMs - startUptimeMs).coerceAtLeast(0)

        /** 会话结束挂钟时间；优先使用显式传入的 mtime。 */
        val endEpochMs: Long
            get() = if (endEpochMsOverride > 0) endEpochMsOverride else file.lastModified()

        /** 会话起始挂钟时间（epoch 毫秒）。 */
        val startEpochMs: Long get() = endEpochMs - durationMs
    }

    /** 单条历史事件。 */
    data class BhEvent(
        /** 事件在解压数据中的字节偏移（用于排序与调试）。 */
        val offset: Int,
        /** 事件原文（UTF-16LE 解码后的可读文本）。 */
        val text: String,
        val category: BhCategory
    )

    /** 事件分类。 */
    enum class BhCategory(val label: String) {
        SCREEN("屏幕"),
        WAKEUP("唤醒"),
        SUSPEND("挂起失败"),
        NETWORK("网络"),
        SENSOR("传感器"),
        APP("应用"),
        OTHER("其它");
    }

    /** 从字节数组解析（root 通道读取 .bh 原始字节后调用）。 */
    fun parseBytes(file: File, bytes: ByteArray, endEpochMsOverride: Long = 0L): BhSession? {
        if (bytes.size < HEADER_SIZE + 2) return null
        // 定位 gzip 流（\x1f\x8b）
        var gzOffset = -1
        val probe = minOf(bytes.size - 1, 16)
        for (i in 0 until probe) {
            if (bytes[i] == 0x1f.toByte() && bytes[i + 1] == 0x8b.toByte()) {
                gzOffset = i
                break
            }
        }
        if (gzOffset < 0) return null

        val raw = GZIPInputStream(bytes.inputStream(gzOffset, bytes.size - gzOffset)).use { input ->
            val out = ByteArrayOutputStream(1 shl 18)
            val buf = ByteArray(1 shl 16)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
            }
            out.toByteArray()
        }
        if (raw.size < HEADER_SIZE) return null

        val recordCount = readU32(raw, 0).toInt()
        val startMs = readU32(raw, 4)
        val endMs = readU32(raw, 12)

        val events = extractEvents(raw)
        return BhSession(
            file = file,
            startUptimeMs = startMs,
            endUptimeMs = endMs,
            recordCount = recordCount,
            endEpochMsOverride = endEpochMsOverride,
            events = events
        )
    }

    /** 读取小端 u32。 */    private fun readU32(data: ByteArray, offset: Int): Long {
        if (offset + 4 > data.size) return 0L
        return (data[offset].toLong() and 0xFF) or
                ((data[offset + 1].toLong() and 0xFF) shl 8) or
                ((data[offset + 2].toLong() and 0xFF) shl 16) or
                ((data[offset + 3].toLong() and 0xFF) shl 24)
    }

    /**
     * 扫描 UTF-16LE 事件串。
     *
     * 事件名在文件中以 UTF-16LE 存储；同一条文本可能连续出现多次（格式图例与记录重复），
     * 这里合并「相邻重复」，保留事件顺序。
     */
    private fun extractEvents(raw: ByteArray): List<BhEvent> {
        val result = ArrayList<BhEvent>()
        var i = HEADER_SIZE
        var lastText: String? = null
        val sb = StringBuilder()
        while (i + 1 < raw.size) {
            val lo = raw[i].toInt() and 0xFF
            val hi = raw[i + 1].toInt() and 0xFF
            val ch = if (hi == 0 && lo in 0x20..0x7e) lo else -1
            if (ch >= 0) {
                sb.append(ch.toChar())
                i += 2
                continue
            }
            if (sb.length >= 5) { // 至少 5 个字符才算事件名，过滤噪声
                val text = sb.toString()
                if (text != lastText && !isLegend(text)) {
                    result.add(BhEvent(i - sb.length * 2, text, classify(text)))
                    lastText = text
                } else if (isLegend(text)) {
                    // 图例文本参与 lastText 去重但不展示
                    lastText = text
                }
            }
            sb.setLength(0)
            i += 1
        }

        // 兜底：合并完全相同的连续事件（部分记录会重复同一事件名 2-4 次）
        return collapseConsecutive(result)
    }

    /** 合并连续重复（同类别同名连续重复 3 次以内视为同一事件的多副本）。 */
    private fun collapseConsecutive(events: List<BhEvent>): List<BhEvent> {
        if (events.isEmpty()) return events
        val out = ArrayList<BhEvent>(events.size)
        var last: BhEvent? = null
        for (e in events) {
            if (last != null && last.text == e.text) continue
            out.add(e)
            last = e
        }
        return out
    }

    /** 过滤文件自带的字段布局图例（\"rx:0 tx:1 ...\" 之类）。 */
    private fun isLegend(text: String): Boolean {
        if (text.contains("format-")) return true
        // 形如 "sleep:0 idle:1 scan:2" / "brightness-0:1" / 纯数字逗号序列
        if (Regex("^([a-zA-Z-]+:\\d+\\s*)+$").matches(text.trim())) return true
        if (Regex("^([0-9]+,)+[0-9]+$").matches(text.trim())) return true
        return text.contains(":0[") || text.contains("?")
    }

    /** 事件分类。 */
    fun classify(text: String): BhCategory = when {
        text.startsWith("display=") -> BhCategory.SCREEN
        text.contains("Abort:") -> BhCategory.SUSPEND
        text.contains("WakeLock") || text.contains("ALARM_ACTION") ||
                text.contains("walarm") || (text.contains("alarm") && text.length < 64) -> BhCategory.WAKEUP
        text.contains("wakelock-change") -> BhCategory.WAKEUP
        text.startsWith("sensor:") -> BhCategory.SENSOR
        text.startsWith("NR ") || text.startsWith("dt-") || text.startsWith("ut-") ||
                text == "wifi" || text == "gnss" || text == "mobile_radio" ||
                text.startsWith("rx") || text.startsWith("tx") -> BhCategory.NETWORK
        text.contains("/") && text.contains(".") -> BhCategory.APP
        text.contains(".") && !text.contains(" ") && text.firstOrNull()?.isLowerCase() == true -> BhCategory.APP
        else -> BhCategory.OTHER
    }
}
