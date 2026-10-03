package yangfentuozi.batteryrecorder.data.bh

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 合并时间线：把所有 .bh 会话拼接成一条连续事件流，并支持自定义分割。
 *
 * 时间基准：
 * - 会话若"完整解码"（V3），每个事件带精确 uptime → 用会话 mtime 锚点换算为挂钟时间；
 * - 未完整解码的会话，事件只有顺序 → 按会话时长把事件均匀铺开（标记为"近似"）。
 */
object BhTimeline {

    data class Item(
        val wallMs: Long,
        val approximate: Boolean,
        /** 原始事件串；显示时再交给 `BhEventHumanizer` 翻译（避免整表预翻译）。 */
        val rawText: String,
        val category: BhHistoryParser.BhCategory,
        val sessionName: String,
    )

    /** 自定义分割模式。 */
    enum class SegmentMode(val label: String, val minutes: Int?) {
        SCREEN_OFF("息屏", null),
        HOURLY("1小时", null),
        EVERY_30("30分钟", 30),
        EVERY_10("10分钟", 10),
    }

    data class Segment(
        val title: String,
        val startMs: Long,
        val endMs: Long,
        val items: List<Item>
    ) {
        val abortCount: Int get() = items.count { it.category == BhHistoryParser.BhCategory.SUSPEND }
        val wakeCount: Int get() = items.count { it.category == BhHistoryParser.BhCategory.WAKEUP }
        val approximateCount: Int get() = items.count { it.approximate }
    }

    private val timeFmt = ThreadLocal.withInitial { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    private val rangeFmt = ThreadLocal.withInitial { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }

    fun formatTime(ms: Long): String = timeFmt.get()!!.format(Date(ms))
    fun formatRange(startMs: Long, endMs: Long): String =
        "${rangeFmt.get()!!.format(Date(startMs))} ~ ${rangeFmt.get()!!.format(Date(endMs))}"

    /** 按模式分割事件流。 */
    fun segment(items: List<Item>, mode: SegmentMode): List<Segment> {
        if (items.isEmpty()) return emptyList()
        return when (mode) {
            SegmentMode.SCREEN_OFF -> segmentByScreenOff(items)
            SegmentMode.HOURLY -> segmentByBucket(items, 3600_000L)
            else -> segmentByBucket(items, mode.minutes!! * 60_000L)
        }
    }

    private fun segmentByBucket(items: List<Item>, bucketMs: Long): List<Segment> {
        val first = items.first().wallMs
        val groups = LinkedHashMap<Long, MutableList<Item>>()
        for (it in items) {
            val key = (it.wallMs - first) / bucketMs
            groups.getOrPut(key) { ArrayList() }.add(it)
        }
        return groups.entries.map { (k, list) ->
            val start = first + k * bucketMs
            val end = list.last().wallMs
            Segment(
                title = formatRange(start, end.coerceAtLeast(start + bucketMs)),
                startMs = start,
                endMs = end,
                items = list
            )
        }
    }

    private fun segmentByScreenOff(items: List<Item>): List<Segment> {
        val segs = ArrayList<Segment>()
        var current = ArrayList<Item>()
        var segStart = items.first().wallMs
        for (it in items) {
            current.add(it)
            val isScreenOff = it.category == BhHistoryParser.BhCategory.SCREEN &&
                    (it.rawText.contains("state=OFF") || it.rawText.contains("state=DOZE_SUSPEND"))
            if (isScreenOff) {
                segs.add(
                    Segment(
                        title = formatRange(segStart, it.wallMs),
                        startMs = segStart,
                        endMs = it.wallMs,
                        items = ArrayList(current)
                    )
                )
                current = ArrayList()
                segStart = it.wallMs
            }
        }
        if (current.isNotEmpty()) {
            segs.add(
                Segment(
                    title = formatRange(segStart, current.last().wallMs),
                    startMs = segStart,
                    endMs = current.last().wallMs,
                    items = current
                )
            )
        }
        return segs
    }
}
