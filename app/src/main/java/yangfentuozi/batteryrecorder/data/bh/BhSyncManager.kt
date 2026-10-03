package yangfentuozi.batteryrecorder.data.bh

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * .bh 事件库静默同步器。
 *
 * 流程（增量）：
 *   1. 列出真机/导入目录的全部 .bh（root 通道）；
 *   2. 与 `bh_files` 账本比对：新文件或 mtime 变化 → 需要入库；
 *   3. 逐个解析（V3 精确 → 失败回退 v2 近似），换算挂钟时间，事务化替换该文件事件；
 *   4. 未变化的文件直接跳过（秒级完成）。
 *
 * 由 App 启动时静默调用；也支持手动触发（全量记录页刷新按钮）。
 */
object BhSyncManager {

    data class SyncResult(
        val scanned: Int,
        val imported: Int,
        val failed: Int,
        val skipped: Int,
        val totalEvents: Int
    )

    /** 距上次成功同步 60 秒内直接复用结果，避免每次进页面都跑一轮 su + 扫描。 */
    private const val FRESH_WINDOW_MS = 60_000L
    @Volatile private var lastSyncAt = 0L
    @Volatile private var lastResult: SyncResult? = null

    /**
     * 增量同步。
     *
     * @param force 跳过"60 秒内结果复用"的短路（用于手动刷新），
     *              但仍走账本比对：只有新增或 mtime 变化的文件才会重新解析。
     */
    suspend fun sync(context: Context, force: Boolean = false): SyncResult = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!force) {
            lastResult?.takeIf { now - lastSyncAt < FRESH_WINDOW_MS }?.let { return@withContext it }
        }
        val db = BhEventDb.get(context)
        val known = db.importedFiles()
        val dir = resolveSourceDir(context)
            ?: return@withContext SyncResult(0, 0, 0, 0, db.totalCount())
        val entries = BhRootSource.listEntries(dir)
            ?: return@withContext SyncResult(0, 0, 0, 0, db.totalCount())

        var imported = 0
        var failed = 0
        var skipped = 0
        for (entry in entries) {
            val prevMtime = known[entry.name]
            if (prevMtime != null && prevMtime == entry.mtimeMs) {
                skipped++
                continue
            }
            val rows = parseIntoRows(entry)
            if (rows == null) {
                failed++
                continue
            }
            db.replaceFileEvents(entry.name, entry.mtimeMs, rows.first, rows.second)
            imported++
        }
        SyncResult(
            scanned = entries.size,
            imported = imported,
            failed = failed,
            skipped = skipped,
            totalEvents = db.totalCount()
        ).also {
            lastResult = it
            lastSyncAt = System.currentTimeMillis()
        }
    }

    /**
     * 解析当前可用的 battery-history 数据源目录。
     *
     * 候选优先级：真机 `/data/system/battery-history`（直接可读或 su 可用）→
     * 应用外部目录 `files/battery-history` → `/sdcard/battery-history` → `/data/local/tmp/battery-history`。
     *
     * @return 可用目录；无任何可用数据源时返回 null。
     */
    fun resolveSourceDir(context: Context): String? {
        val candidates = buildList {
            add(BhRootSource.REAL_DEVICE_DIR)
            context.getExternalFilesDir(null)?.let { add(File(it, "battery-history").absolutePath) }
            add("/sdcard/battery-history")
            add("/data/local/tmp/battery-history")
        }
        for (dir in candidates) {
            if (BhRootSource.readableDir(dir)) return dir
            if (dir == BhRootSource.REAL_DEVICE_DIR && BhRootSource.suAvailable()) return dir
        }
        return null
    }

    /**
     * 解析单个文件为待入库行。
     *
     * @return (rows, exact) ；无法读取/解析返回 null。
     */
    private fun parseIntoRows(entry: BhRootSource.Entry): Pair<List<BhEventDb.Row>, Boolean>? {
        val bytes = BhRootSource.readBytes(entry) ?: return null
        val file = File(entry.path)
        val session = BhHistoryParser.parseBytes(file, bytes, endEpochMsOverride = entry.mtimeMs)
            ?: return null
        val anchor = session.endEpochMs - session.endUptimeMs

        // 优先精确解码
        val v3 = try {
            BhParserV3.decode(bytes)
        } catch (_: Throwable) {
            null
        }
        val rows = ArrayList<BhEventDb.Row>(session.events.size)
        if (v3 != null && v3.isComplete() && v3.events.isNotEmpty()) {
            for (e in v3.events) {
                val text = e.text ?: continue
                rows.add(
                    BhEventDb.Row(
                        wallMs = e.timeMs + anchor,
                        code = e.code,
                        uid = e.uid,
                        level = e.level,
                        text = text,
                        category = e.category.name,
                        approx = false,
                        file = entry.name,
                        fileMtime = entry.mtimeMs
                    )
                )
            }
            if (rows.isNotEmpty()) return rows to true
        }
        // 回退：会话级时间 + 顺序估算（approx）
        val events = session.events
        if (events.isEmpty()) return emptyList<BhEventDb.Row>() to false
        val n = events.size
        val span = session.durationMs.coerceAtLeast(1000L)
        events.forEachIndexed { i, ev ->
            rows.add(
                BhEventDb.Row(
                    wallMs = session.startEpochMs + span * (i + 1) / (n + 1),
                    code = 0,
                    uid = -1,
                    level = null,
                    text = ev.text,
                    category = ev.category.name,
                    approx = true,
                    file = entry.name,
                    fileMtime = entry.mtimeMs
                )
            )
        }
        return rows to false
    }
}
