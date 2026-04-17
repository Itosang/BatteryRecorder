package yangfentuozi.batteryrecorder.shared.sync

import android.os.ParcelFileDescriptor
import yangfentuozi.batteryrecorder.shared.util.LoggerX
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Path

object PfdFileSender {
    private const val TAG = "PfdFileSender"

    fun sendFile(
        writePfd: ParcelFileDescriptor,
        file: File,
        callback: ((File) -> Unit)? = null
    ) {
        val basePath = file.toPath()
        LoggerX.i(TAG, "sendFile: 开始发送文件, base=${file.absolutePath}")
        var sentCount = 0
        var sentBytes = 0L
        ParcelFileDescriptor.AutoCloseOutputStream(writePfd).use { raw ->
            BufferedOutputStream(raw, SyncConstants.BUF_SIZE).use { out ->
                sendFileInner(out, file, basePath, callback) { size ->
                    sentCount += 1
                    sentBytes += size
                }
                // 结束码
                out.write(SyncConstants.CODE_FINISHED)
                out.flush()
            }
        }
        LoggerX.i(TAG, "sendFile: 文件发送完成, count=$sentCount bytes=$sentBytes")
    }

    private fun sendSingleFile(
        out: OutputStream,
        file: File,
        basePath: Path,
        callback: ((File) -> Unit)?,
        onSent: (Long) -> Unit
    ) {
        if (!file.exists() || !file.isFile) return
        val size = file.length()
        if (size < 0) throw IOException("Invalid file size: $size")

        out.write(SyncConstants.CODE_FILE)
        out.write(basePath.relativize(file.toPath()).toString().toByteArray(Charsets.UTF_8))
        out.write(SyncConstants.CODE_DELIM)
        out.write(size.toString().toByteArray(Charsets.US_ASCII))
        out.write(SyncConstants.CODE_DELIM)

        BufferedInputStream(FileInputStream(file), SyncConstants.BUF_SIZE).use { fis ->
            val buf = ByteArray(SyncConstants.BUF_SIZE)
            var remaining = size
            while (remaining > 0) {
                val toRead = minOf(remaining, buf.size.toLong()).toInt()
                val n = fis.read(buf, 0, toRead)
                if (n < 0) throw EOFException("Unexpected EOF reading file: ${file.absolutePath}")
                out.write(buf, 0, n)
                remaining -= n.toLong()
            }
        }
        out.flush()

        LoggerX.d(TAG, "sendSingleFile: 发送文件, relative=${basePath.relativize(file.toPath())} size=$size")
        onSent(size)
        callback?.invoke(file)
    }

    private fun sendFileInner(
        out: OutputStream,
        file: File,
        basePath: Path,
        callback: ((File) -> Unit)?,
        onSent: (Long) -> Unit
    ) {
        if (!file.exists()) return
        if (file.isDirectory) {
            file.listFiles()?.forEach {
                sendFileInner(out, it, basePath, callback, onSent)
            }
        } else {
            sendSingleFile(out, file, basePath, callback, onSent)
        }
    }
}
