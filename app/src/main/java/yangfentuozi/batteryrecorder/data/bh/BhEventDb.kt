package yangfentuozi.batteryrecorder.data.bh

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.Calendar

/**
 * .bh 事件数据库（App 内部存储，SQLite）。
 *
 * 设计：
 * - 所有 .bh 文件的事件统一并入 `bh_events` 一张表，按挂钟时间（wall_ms）索引，
 *   不再保留"原始文件分段"作为浏览维度；
 * - `bh_files` 记录每个来源文件（名 + mtime）作为增量同步账本：
 *   文件未变化则跳过，变化则删除旧行重新入库；
 * - 未完整解码的会话以 approx=1 标记（时间由会话内顺序估算）。
 */
class BhEventDb(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    companion object {
        private const val DB_NAME = "bh_events.db"
        private const val DB_VERSION = 1

        const val TABLE_EVENTS = "bh_events"
        const val TABLE_FILES = "bh_files"

        const val DAY_MS = 24L * 3600 * 1000

        @Volatile private var instance: BhEventDb? = null

        /** 进程内单例：避免每个页面各开一条 SQLite 连接。 */
        fun get(context: Context): BhEventDb =
            instance ?: synchronized(this) {
                instance ?: BhEventDb(context.applicationContext).also { instance = it }
            }

        /** 向下取整到本地自然日 00:00。 */
        fun floorDay(ms: Long): Long {
            val cal = Calendar.getInstance()
            cal.timeInMillis = ms
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            return cal.timeInMillis
        }
    }

    /** 库中的一条事件。 */
    data class Row(
        val wallMs: Long,
        val code: Int,
        val uid: Int,
        val level: Int?,
        val text: String,
        val category: String,
        val approx: Boolean,
        val file: String,
        val fileMtime: Long
    )

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_EVENTS(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                file TEXT NOT NULL,
                file_mtime INTEGER NOT NULL,
                wall_ms INTEGER NOT NULL,
                uptime_ms INTEGER NOT NULL,
                code INTEGER NOT NULL,
                uid INTEGER NOT NULL,
                level INTEGER NOT NULL,
                text TEXT NOT NULL,
                category TEXT NOT NULL,
                approx INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_events_wall ON $TABLE_EVENTS(wall_ms)")
        db.execSQL("CREATE INDEX idx_events_file ON $TABLE_EVENTS(file)")
        db.execSQL(
            """
            CREATE TABLE $TABLE_FILES(
                file TEXT PRIMARY KEY,
                file_mtime INTEGER NOT NULL,
                imported_at INTEGER NOT NULL,
                exact INTEGER NOT NULL,
                event_count INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_EVENTS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_FILES")
        onCreate(db)
    }

    // ---- 同步账本 ----

    /** 已入库文件的 mtime 映射（name → mtime）。 */
    fun importedFiles(): Map<String, Long> {
        val out = HashMap<String, Long>()
        readableDatabase.rawQuery("SELECT file, file_mtime FROM $TABLE_FILES", null).use { c ->
            while (c.moveToNext()) out[c.getString(0)] = c.getLong(1)
        }
        return out
    }

    /** 事务化替换某个文件的所有事件（增量更新的原子步骤）。 */
    fun replaceFileEvents(
        file: String,
        fileMtime: Long,
        events: List<Row>,
        exact: Boolean
    ) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete(TABLE_EVENTS, "file = ?", arrayOf(file))
            val cv = ContentValues()
            for (e in events) {
                cv.clear()
                cv.put("file", file)
                cv.put("file_mtime", fileMtime)
                cv.put("wall_ms", e.wallMs)
                cv.put("uptime_ms", 0L)
                cv.put("code", e.code)
                cv.put("uid", e.uid)
                cv.put("level", e.level ?: -1)
                cv.put("text", e.text)
                cv.put("category", e.category)
                cv.put("approx", if (e.approx) 1 else 0)
                db.insert(TABLE_EVENTS, null, cv)
            }
            cv.clear()
            cv.put("file", file)
            cv.put("file_mtime", fileMtime)
            cv.put("imported_at", System.currentTimeMillis())
            cv.put("exact", if (exact) 1 else 0)
            cv.put("event_count", events.size)
            db.insertWithOnConflict(TABLE_FILES, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    // ---- 查询 ----

    /** 时间范围查询（wall_ms 闭区间），升序。 */
    fun queryRange(startMs: Long, endMs: Long, limit: Int = 200_000): List<Row> {
        val out = ArrayList<Row>(1024)
        readableDatabase.rawQuery(
            "SELECT wall_ms, code, uid, level, text, category, approx, file, file_mtime " +
                    "FROM $TABLE_EVENTS WHERE wall_ms >= ? AND wall_ms <= ? ORDER BY wall_ms ASC LIMIT ?",
            arrayOf(startMs.toString(), endMs.toString(), limit.toString())
        ).use { c ->
            while (c.moveToNext()) {
                out.add(
                    Row(
                        wallMs = c.getLong(0),
                        code = c.getInt(1),
                        uid = c.getInt(2),
                        level = c.getInt(3).takeIf { it >= 0 },
                        text = c.getString(4),
                        category = c.getString(5),
                        approx = c.getInt(6) != 0,
                        file = c.getString(7),
                        fileMtime = c.getLong(8)
                    )
                )
            }
        }
        return out
    }

    /** 数据整体时间边界（无数据返回 null）。 */
    fun overallBounds(): Pair<Long, Long>? {
        readableDatabase.rawQuery(
            "SELECT MIN(wall_ms), MAX(wall_ms), COUNT(*) FROM $TABLE_EVENTS", null
        ).use { c ->
            if (c.moveToFirst() && c.getLong(2) > 0) return c.getLong(0) to c.getLong(1)
        }
        return null
    }

    fun totalCount(): Int {
        readableDatabase.rawQuery("SELECT COUNT(*) FROM $TABLE_EVENTS", null).use { c ->
            if (c.moveToFirst()) return c.getInt(0)
        }
        return 0
    }

    /** 按（分类, 原始文本）聚合的计数：统计卡片不必逐行加载。 */
    data class TextGroup(val category: String, val text: String, val count: Int, val approxCount: Int)

    fun textGroups(startMs: Long, endMs: Long): List<TextGroup> {
        val out = ArrayList<TextGroup>(512)
        readableDatabase.rawQuery(
            "SELECT category, text, COUNT(*), SUM(CASE WHEN approx = 0 THEN 0 ELSE 1 END) " +
                    "FROM $TABLE_EVENTS WHERE wall_ms >= ? AND wall_ms <= ? GROUP BY category, text",
            arrayOf(startMs.toString(), endMs.toString())
        ).use { c ->
            while (c.moveToNext()) {
                out.add(
                    TextGroup(
                        category = c.getString(0),
                        text = c.getString(1),
                        count = c.getInt(2),
                        approxCount = c.getInt(3)
                    )
                )
            }
        }
        return out
    }

    /** 按本地日期分组的每日事件数与起止时间（用于"按日期分类"）。 */
    data class DayStat(val dayStartMs: Long, val count: Int, val firstMs: Long, val lastMs: Long)

    fun dayStats(): List<DayStat> {
        val bounds = overallBounds() ?: return emptyList()
        val out = ArrayList<DayStat>()
        val cal = Calendar.getInstance()
        var dayStart = floorDay(bounds.first)
        while (dayStart <= bounds.second) {
            val dayEnd = dayStart + DAY_MS
            val count = countRange(dayStart, dayEnd)
            if (count > 0) {
                readableDatabase.rawQuery(
                    "SELECT MIN(wall_ms), MAX(wall_ms) FROM $TABLE_EVENTS WHERE wall_ms >= ? AND wall_ms < ?",
                    arrayOf(dayStart.toString(), dayEnd.toString())
                ).use { c ->
                    if (c.moveToFirst()) out.add(DayStat(dayStart, count, c.getLong(0), c.getLong(1)))
                }
            }
            dayStart = dayEnd
        }
        return out
    }

    /** 左闭右开区间 [startMs, endMs) 内的事件数。 */
    fun countRange(startMs: Long, endMs: Long): Int {
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM $TABLE_EVENTS WHERE wall_ms >= ? AND wall_ms < ?",
            arrayOf(startMs.toString(), endMs.toString())
        ).use { c ->
            if (c.moveToFirst()) return c.getInt(0)
        }
        return 0
    }
}
