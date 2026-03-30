package yangfentuozi.batteryrecorder.shared.util

import android.util.Log
import yangfentuozi.batteryrecorder.shared.BuildConfig
import yangfentuozi.batteryrecorder.shared.config.SettingsConstants
import yangfentuozi.batteryrecorder.shared.writer.AdvancedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object LoggerX {

    @Volatile
    private var writer: LogWriter? = null

    // 只允许传入
    var logDir: File?
        get() = null
        set(value) {
            writer?.close()
            writer = if (value == null) null else try {
                LogWriter(value)
            } catch (e: IOException) {
                Log.e("LoggerX", "logDir: 初始化 LogWriter 失败", e)
                null
            }
        }

    // 只允许传入
    var logDirPath: String?
        get() = null
        set(value) {
            logDir = if (value == null) null else File(value)
        }

    @Volatile
    var fixFileOwner: ((File) -> Unit)? = null

    @Volatile
    var maxHistoryDays: Long = SettingsConstants.logMaxHistoryDays.def

    @Volatile
    var logLevel: LogLevel = SettingsConstants.logLevel.def

    fun isLoggable(level: LogLevel): Boolean {
        val allowedPriority =
            if (BuildConfig.DEBUG) logLevel.coerceAtMost(LogLevel.Debug)
            else logLevel
        return level.priority >= allowedPriority.priority
    }

    fun v(
        tag: String,
        msg: String?,
        vararg args: Any?,
        tr: Throwable? = null,
        notWrite: Boolean = false
    ) {
        log(tag, LogLevel.Verbose, msg, *args, tr = tr, notWrite = notWrite)
    }

    fun d(
        tag: String,
        msg: String?,
        vararg args: Any?,
        tr: Throwable? = null,
        notWrite: Boolean = false
    ) {
        log(tag, LogLevel.Debug, msg, *args, tr = tr, notWrite = notWrite)
    }

    fun i(
        tag: String,
        msg: String?,
        vararg args: Any?,
        tr: Throwable? = null,
        notWrite: Boolean = false
    ) {
        log(tag, LogLevel.Info, msg, *args, tr = tr, notWrite = notWrite)
    }

    fun w(
        tag: String,
        msg: String?,
        vararg args: Any?,
        tr: Throwable? = null,
        notWrite: Boolean = false
    ) {
        log(tag, LogLevel.Warning, msg, *args, tr = tr, notWrite = notWrite)
    }

    fun e(
        tag: String,
        msg: String?,
        vararg args: Any?,
        tr: Throwable? = null,
        notWrite: Boolean = false
    ) {
        log(tag, LogLevel.Error, msg, *args, tr = tr, notWrite = notWrite)
    }

    fun a(
        tag: String,
        msg: String?,
        vararg args: Any?,
        tr: Throwable? = null,
        notWrite: Boolean = false
    ) {
        log(tag, LogLevel.Assert, msg, *args, tr = tr, notWrite = notWrite)
    }

    fun log(
        tag: String,
        level: LogLevel,
        msg: String?,
        vararg args: Any?,
        tr: Throwable?,
        notWrite: Boolean = false
    ) {
        if (!isLoggable(level)) return
        val base = if (args.isEmpty()) msg.toString() else String.format(
            Locale.ENGLISH,
            msg ?: "null",
            args
        )
        val content = if (tr == null) base else "$base\n${Log.getStackTraceString(tr)}"
        println(tag, level, content, notWrite)
    }

    fun println(tag: String, priority: LogLevel, msg: String, notWrite: Boolean = false): Int {
        if (!notWrite) writer?.write(tag, priority, msg)
        return Log.println(priority.priority, tag, msg)
    }

    enum class LogLevel(val priority: Int, val shortName: String) {
        Verbose(Log.VERBOSE, "V"),
        Debug(Log.DEBUG, "D"),
        Info(Log.INFO, "I"),
        Warning(Log.WARN, "W"),
        Error(Log.ERROR, "E"),
        Assert(Log.ASSERT, "A"),
        Disabled(Int.MAX_VALUE, "null");

        companion object {
            private val priorityMap = entries.associateBy { it.priority }
            fun fromPriority(priority: Int): LogLevel =
                priorityMap[priority] ?: SettingsConstants.logLevel.def
        }

        fun coerceAtMost(maximumPriority: LogLevel): LogLevel {
            return fromPriority(priority.coerceAtMost(maximumPriority.priority))
        }
    }

    private class LogWriter(private val logDir: File) {

        private val fileNameRegex = Regex("""^(\d{4}-\d{2}-\d{2})(?:_(\d+))?\.log$""")
        private val dateFileFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        private val lineTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
        private val handler = Handlers.getHandler("LoggingThread")
        private var activeDate = ""
        private var writer: AdvancedWriter? = null

        @Volatile
        private var closed = false

        private val cleanupRunnable = object : Runnable {
            override fun run() {
                if (closed) return
                try {
                    val files = logDir.listFiles() ?: return
                    val cutoff = LocalDate.now().minusDays(maxHistoryDays)
                    for (file in files) {
                        val match = fileNameRegex.matchEntire(file.name) ?: run {
                            file.delete()
                            continue
                        }
                        val fileDate = try {
                            LocalDate.parse(match.groupValues[1], dateFileFormatter)
                        } catch (_: Exception) {
                            file.delete()
                            continue
                        }
                        if (fileDate.isBefore(cutoff)) {
                            file.delete()
                        }
                    }
                } catch (e: Exception) {
                    e(
                        "LogWriter",
                        "@cleanupRunnable: 清理过期日志文件失败",
                        tr = e,
                        notWrite = true
                    )
                }
                if (!closed) {
                    handler.postDelayed(this, 8 * 60 * 60 * 1000L)
                }
            }
        }

        init {
            if (!logDir.exists() && !logDir.mkdirs()) throw IOException("logDir.mkdirs() failed: ${logDir.absolutePath}")
            if (!logDir.isDirectory) throw IOException("logDir is not a directory: ${logDir.absolutePath}")
            fixFileOwner?.invoke(logDir)
            handler.postDelayed(cleanupRunnable, 10 * 60 * 1000L)
        }

        fun write(tag: String, level: LogLevel, message: String) {
            handler.post {
                if (closed) return@post
                try {
                    val todayKey = LocalDate.now().format(dateFileFormatter)
                    if (activeDate != todayKey) {
                        closeWriter()
                        activeDate = todayKey
                        openWriter()
                    }
                    val timestamp = Instant.ofEpochMilli(System.currentTimeMillis())
                        .atZone(ZoneId.systemDefault())
                        .toLocalDateTime()
                        .format(lineTimeFormatter)
                    writer?.appendLine(
                        StringBuilder()
                            .append(timestamp)
                            .append(" [")
                            .append(level.shortName)
                            .append("] [")
                            .append(tag)
                            .append("] ")
                            .append(message)
                    )
                    writer?.onEnqueued()
                    // 尽量避免关键日志丢失：错误级别直接触发一次 flush（写入仍在异步线程执行）
                    if (level.priority >= Log.ERROR) {
                        writer?.flushNow()
                    }
                } catch (e: Exception) {
                    e("LogWriter", "write: 写入日志失败", tr = e, notWrite = true)
                }
            }
        }

        fun close() {
            closed = true
            handler.removeCallbacks(cleanupRunnable)
            handler.post { closeWriter() }
        }

        private fun openWriter() {
            val logFile = File(logDir, "$activeDate.log")
            if (!logFile.exists() && !logFile.createNewFile()) throw IOException("logFile.createNewFile() failed: ${logFile.absolutePath}")
            if (logFile.isDirectory) throw IOException("logFile is a directory: ${logFile.absolutePath}")
            fixFileOwner?.invoke(logFile)
            val openOutputStream: (() -> OutputStream) = {
                if (!logFile.exists() && !logFile.createNewFile()) {
                    throw IOException("@openOutputStream: 创建日志文件失败: ${logFile.absolutePath}")
                }
                if (logFile.isDirectory) throw IOException("@openOutputStream: logFile is a directory: ${logFile.absolutePath}")
                fixFileOwner?.invoke(logFile)
                FileOutputStream(logFile, true)
            }
            writer = AdvancedWriter(
                handler = handler,
                batchSize = { 10 },
                flushIntervalMs = { 5_000 },
                outputStream = openOutputStream(),
                reopenOutputStream = openOutputStream,
                retryTimes = 3,
                retryIntervalMs = 1_000,
                useAndroidLog = true
            )
        }

        private fun closeWriter() {
            writer?.let {
                try {
                    it.flushNow()
                    it.close()
                } catch (e: Exception) {
                    e("LogWriter", "closeWriter: 关闭 AdvancedWriter 失败", tr = e, notWrite = true)
                } finally {
                    writer = null
                }
            }
        }
    }
}
