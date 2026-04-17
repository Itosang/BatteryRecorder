package yangfentuozi.batteryrecorder.shared.data

import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.util.zip.GZIPInputStream

/**
 * 统一封装记录文件读取入口，避免上层分散判断 plain / gzip。
 */
object RecordFileIO {
    private const val BUFFER_SIZE = 64 * 1024

    /**
     * 打开记录文件输入流。
     *
     * @param file 记录文件，支持 `.txt` 与 `.txt.gz`。
     * @return 返回按文件格式适配后的输入流。
     */
    fun openInputStream(file: File): InputStream {
        val rawInput = BufferedInputStream(FileInputStream(file), BUFFER_SIZE)
        return if (RecordFileNames.isCompressedFileName(file.name)) {
            GZIPInputStream(rawInput, BUFFER_SIZE)
        } else {
            rawInput
        }
    }

    /**
     * 打开统一的文本读取器。
     *
     * @param file 记录文件，支持 `.txt` 与 `.txt.gz`。
     * @return 返回 UTF-8 文本读取器。
     */
    fun openBufferedReader(file: File): BufferedReader =
        BufferedReader(InputStreamReader(openInputStream(file), Charsets.UTF_8), BUFFER_SIZE)

    /**
     * 以明文文本语义导出记录内容。
     *
     * @param file 源记录文件。
     * @param output 导出目标输出流。
     * @return 无。
     */
    fun copyAsPlainText(
        file: File,
        output: OutputStream
    ) {
        openInputStream(file).use { input ->
            input.copyTo(output, BUFFER_SIZE)
        }
    }
}
