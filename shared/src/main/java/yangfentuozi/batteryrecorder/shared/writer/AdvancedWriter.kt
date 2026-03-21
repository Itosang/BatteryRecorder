package yangfentuozi.batteryrecorder.shared.writer

import android.os.Handler
import yangfentuozi.batteryrecorder.shared.util.LoggerX
import java.io.IOException
import java.io.OutputStream

/**
 * 字符串批量写入缓冲：
 * - 达到 batchSize 时立即 flush
 * - 否则延迟 flush（flushIntervalMs）
 *
 * 附带能力：
 * - flush 时自动异步写入 OutputStream
 * - 写入失败自动重试；超过次数后重建 OutputStream 再继续重试
 */
class AdvancedWriter(
    private val handler: Handler,
    private val batchSize: (() -> Int),
    private val flushIntervalMs: (() -> Long),
    private var outputStream: OutputStream,
    private val reopenOutputStream: (() -> OutputStream),
    private val retryTimes: Int,
    private var retryIntervalMs: Long,
    initialCapacity: Int = 4096,
    private val useAndroidLog: Boolean = false
) {
    private val autoRetryWriter = AutoRetryStringWriter()

    private val buffer = StringBuilder(initialCapacity)
    private var batchCount: Int = 0

    private val flushRunnable = Runnable {
        flushInternal("延迟写入")
        // 防止异步写完被忽略掉：flush 执行过程中可能有新数据入队
        if (batchCount > 0) {
            scheduleDelayedFlush()
        }
    }

    fun appendLine(value: Any) {
        buffer.append(value).append("\n")
        batchCount++
    }

    fun onEnqueued() {
        if (batchCount >= batchSize()) {
            flushInternal("数据量足够")
            handler.removeCallbacks(flushRunnable)
        } else {
            if (!handler.hasCallbacks(flushRunnable)) {
                scheduleDelayedFlush()
            }
        }
    }

    fun flushNow() {
        flushInternal("flushNow")
    }

    fun close() {
        // 关闭前先取消延迟 flush，避免 close 后仍有回调触发写入
        handler.removeCallbacks(flushRunnable)
        // close 是强语义：尽量把主缓冲里的数据也写出去
        flushInternal("close")
        autoRetryWriter.close()
        try {
            outputStream.close()
        } catch (e: Exception) {
            LoggerX.e<AdvancedWriter>("close: outputStream 关闭失败", e, notWrite = useAndroidLog)
        }
    }

    private fun scheduleDelayedFlush() {
        handler.postDelayed(flushRunnable, flushIntervalMs())
    }

    private fun flushInternal(reason: String) {
        if (batchCount == 0) return
        LoggerX.d<AdvancedWriter>("flushInternal: $reason，触发写入，数据大小: $batchCount", notWrite = useAndroidLog)
        autoRetryWriter.write(buffer)
        buffer.setLength(0)
        batchCount = 0
    }

    private inner class AutoRetryStringWriter {
        private val bufferLock = Any()
        private var retryCount = -1
        @Volatile
        private var closing = false

        private val thread = android.os.HandlerThread("RetryThread")
        private val handler: Handler
        private val retryRunnable = object : Runnable {
            override fun run() {
                if (++retryCount > retryTimes) {
                    try {
                        outputStream.close()
                    } catch (e: IOException) {
                        LoggerX.e<AutoRetryStringWriter>(
                            "retryRunnable: 关闭 OutputStream 失败",
                            tr = e,
                            notWrite = useAndroidLog
                        )
                    }

                    try {
                        outputStream = reopenOutputStream()
                    } catch (e: IOException) {
                        LoggerX.e<AutoRetryStringWriter>(
                            "retryRunnable: 重新打开 OutputStream 失败，多次重试失败，强行终止",
                            tr = e,
                            notWrite = useAndroidLog
                        )
                        throw RuntimeException()
                    }
                    LoggerX.i<AutoRetryStringWriter>(
                        "retryRunnable: OutputStream 已重建，继续写入重试",
                        notWrite = useAndroidLog
                    )
                }
                synchronized(bufferLock) {
                    try {
                        outputStream.write(buffer.toString().toByteArray())
                        outputStream.flush()
                        buffer.setLength(0)
                        retryCount = -1
                    } catch (e: IOException) {
                        if (++retryCount > retryTimes) {
                            LoggerX.e<AutoRetryStringWriter>(
                                "retryRunnable: 写入 OutputStream 失败，多次重试失败，强行终止",
                                tr = e,
                                notWrite = useAndroidLog
                            )
                            throw RuntimeException()
                        }
                        LoggerX.w<AutoRetryStringWriter>(
                            "retryRunnable: 写入 OutputStream 失败，准备重试: retryCount=$retryCount/$retryTimes",
                            tr = e,
                            notWrite = useAndroidLog
                        )
                        if (!closing) {
                            handler.postDelayed(this, retryIntervalMs)
                        }
                    }
                }
            }
        }

        private val buffer = StringBuilder(4096)

        init {
            thread.start()
            handler = Handler(thread.looper)
        }

        fun write(stringBuilder: StringBuilder) {
            synchronized(bufferLock) {
                buffer.append(stringBuilder)
                LoggerX.v<AutoRetryStringWriter>(
                    "write: 缓冲入队: appendedChars=${stringBuilder.length} pendingChars=${buffer.length}",
                    notWrite = useAndroidLog
                )
                if (retryCount == -1) {
                    handler.post(retryRunnable)
                }
            }
        }

        fun close() {
            closing = true
            handler.removeCallbacks(retryRunnable)
            // close 时同步尝试清空缓冲，避免「刚入队还没来得及写」就被关闭导致丢数据
            drainBufferBlocking()
            try {
                thread.quitSafely()
                thread.join(1_000)
            } catch (_: Exception) {
                // ignore
            }
        }

        private fun drainBufferBlocking() {
            synchronized(bufferLock) {
                if (buffer.isEmpty()) return
                retryIntervalMs = 100
                var localRetryCount = 0
                while (buffer.isNotEmpty()) {
                    try {
                        outputStream.write(buffer.toString().toByteArray())
                        outputStream.flush()
                        buffer.setLength(0)
                        retryCount = -1
                        return
                    } catch (e: IOException) {
                        if (++localRetryCount > retryTimes) {
                            try {
                                outputStream.close()
                            } catch (closeErr: IOException) {
                                LoggerX.e<AutoRetryStringWriter>(
                                    "drainBufferBlocking: 关闭 OutputStream 失败",
                                    tr = closeErr,
                                    notWrite = useAndroidLog
                                )
                            }
                            outputStream = try {
                                reopenOutputStream()
                            } catch (reopenErr: IOException) {
                                LoggerX.e<AutoRetryStringWriter>(
                                    "drainBufferBlocking: 重新打开 OutputStream 失败，多次重试失败，强行终止",
                                    tr = reopenErr,
                                    notWrite = useAndroidLog
                                )
                                throw RuntimeException()
                            }
                            LoggerX.i<AutoRetryStringWriter>(
                                "drainBufferBlocking: OutputStream 已重建，继续写入重试",
                                notWrite = useAndroidLog
                            )
                            localRetryCount = 0
                            continue
                        }
                        LoggerX.w<AutoRetryStringWriter>(
                            "drainBufferBlocking: 写入 OutputStream 失败，准备重试: retryCount=$localRetryCount/$retryTimes",
                            tr = e,
                            notWrite = useAndroidLog
                        )
                        Thread.sleep(retryIntervalMs)
                    }
                }
            }
        }
    }
}
