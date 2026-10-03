package yangfentuozi.batteryrecorder.data.bh

/**
 * 任意时间范围的统计与明细转换（数据库行 → 统计 / 时间线条目）。
 */
object BhRangeStats {

    /** 排行条目（名称 + 次数）。 */
    data class Rank(val name: String, val count: Int)

    data class Stats(
        val total: Int,
        val exact: Int,
        val approx: Int,
        val screenOn: Int,
        val screenOff: Int,
        val dozeOn: Int,
        val abortTotal: Int,
        val abortRank: List<Rank>,
        val wakeTotal: Int,
        val wakeRank: List<Rank>,
        val appRank: List<Rank>,
        val sensorCount: Int
    )

    /** 基于 SQL 聚合结果统计：只翻译"不同文本"（通常几百条），不再逐行 humanize。 */
    fun stats(
        groups: List<BhEventDb.TextGroup>,
        labelOf: (String) -> String? = { null }
    ): Stats {
        var total = 0
        var exact = 0
        var on = 0
        var off = 0
        var doze = 0
        var sensor = 0
        val aborts = HashMap<String, Int>()
        val wakes = HashMap<String, Int>()
        val apps = HashMap<String, Int>()
        for (g in groups) {
            total += g.count
            exact += g.count - g.approxCount
            val h = BhEventHumanizer.humanize(g.text, labelOf)
            when (g.category) {
                BhHistoryParser.BhCategory.SCREEN.name -> when {
                    h.title.contains("亮起") -> on += g.count
                    h.title.contains("熄灭") -> off += g.count
                    h.title.contains("息屏显示") -> doze += g.count
                }
                BhHistoryParser.BhCategory.SUSPEND.name -> {
                    val k = h.title.replace(Regex(" ×\\d+$"), "")
                    aborts[k] = (aborts[k] ?: 0) + g.count
                }
                BhHistoryParser.BhCategory.WAKEUP.name -> {
                    val k = h.title.replace(Regex(" ×\\d+$"), "")
                    wakes[k] = (wakes[k] ?: 0) + g.count
                }
                BhHistoryParser.BhCategory.APP.name -> {
                    val k = h.title.removePrefix("应用活动：")
                    apps[k] = (apps[k] ?: 0) + g.count
                }
                BhHistoryParser.BhCategory.SENSOR.name -> sensor += g.count
            }
        }
        fun rank(m: HashMap<String, Int>) =
            m.entries.sortedByDescending { it.value }.map { Rank(it.key, it.value) }
        return Stats(
            total = total,
            exact = exact,
            approx = total - exact,
            screenOn = on,
            screenOff = off,
            dozeOn = doze,
            abortTotal = aborts.values.sum(),
            abortRank = rank(aborts),
            wakeTotal = wakes.values.sum(),
            wakeRank = rank(wakes),
            appRank = rank(apps),
            sensorCount = sensor
        )
    }

    /** 数据库行 → 时间线条目（保留原始文本，翻译推迟到真正显示时）。 */
    fun toItems(rows: List<BhEventDb.Row>): List<BhTimeline.Item> =
        rows.map { r ->
            BhTimeline.Item(
                wallMs = r.wallMs,
                approximate = r.approx,
                rawText = r.text,
                category = BhHistoryParser.BhCategory.valueOf(r.category),
                sessionName = r.file
            )
        }
}
