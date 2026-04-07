package yangfentuozi.batteryrecorder.data.log

import android.content.Context
import android.net.Uri
import yangfentuozi.batteryrecorder.ipc.Service
import yangfentuozi.batteryrecorder.shared.Constants
import yangfentuozi.batteryrecorder.shared.sync.PfdFileReceiver
import yangfentuozi.batteryrecorder.shared.util.LoggerX
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private const val TAG = "LogRepository"

data class LogExportResult(
    val appFileCount: Int,
    val serverFileCount: Int,
    val serverExportFailed: Boolean,
    val serverFailureMessage: String? = null
)

object LogRepository {
    private const val SERVER_EXPORT_TEMP_DIR_PREFIX = "export_server_logs_"

    /**
     * 导出首页日志 ZIP。
     *
     * App 日志是主导出对象，缺失时直接失败；
     * Server 日志按 best-effort 处理，失败时仍然导出 App 日志，并把部分成功结果返回给上层。
     *
     * @param context 应用上下文。
     * @param destinationUri SAF 目标 URI。
     * @return 导出结果，包含 App/Server 两侧的导出状态。
     */
    fun exportLogsZip(
        context: Context,
        destinationUri: Uri
    ): LogExportResult {
        val appLogsDir = File(context.cacheDir, Constants.APP_LOG_DIR_PATH)
        LoggerX.i(TAG, "exportLogsZip: 开始导出日志压缩包", notWrite = true)
        LoggerX.flushBlocking()
        val appFileCount = requireLogFiles(appLogsDir, "App")
        LoggerX.d(
            TAG,
            "exportLogsZip: App 日志目录检查完成 dir=${appLogsDir.absolutePath} count=$appFileCount"
        )

        val serverTempDir = File(
            context.cacheDir,
            "$SERVER_EXPORT_TEMP_DIR_PREFIX${System.currentTimeMillis()}"
        )
        var serverFileCount = 0
        var serverExportFailed = false
        var serverFailureMessage: String? = null

        try {
            try {
                serverFileCount = tryReceiveServerLogs(serverTempDir)
            } catch (e: Exception) {
                serverExportFailed = true
                serverFailureMessage = e.message ?: e::class.java.simpleName
                LoggerX.w(TAG, "exportLogsZip: Server 日志导出失败, 将仅导出 App 日志", tr = e)
            }

            // 在真正遍历日志目录打包前同步刷新一次，尽量把本次导出相关日志也包含进 ZIP。
            LoggerX.flushBlocking()

            val outputStream = context.contentResolver.openOutputStream(destinationUri, "w")
                ?: throw IOException("打开导出目标失败: $destinationUri")
            outputStream.use { rawOutput ->
                ZipOutputStream(rawOutput).use { zipOutput ->
                    LoggerX.i(
                        TAG,
                        "exportLogsZip: 开始写入 ZIP appCount=$appFileCount serverCount=$serverFileCount"
                    )
                    val zippedAppCount = zipDirectory(zipOutput, appLogsDir, "app")
                    if (zippedAppCount == 0) {
                        throw FileNotFoundException("App 日志目录没有可导出的文件")
                    }
                    if (serverFileCount > 0) {
                        val zippedServerCount = zipDirectory(zipOutput, serverTempDir, "server")
                        LoggerX.d(
                            TAG,
                            "exportLogsZip: Server 日志写入 ZIP 完成 zippedServerCount=$zippedServerCount"
                        )
                    }
                }
            }

            if (serverExportFailed) {
                LoggerX.i(
                    TAG,
                    "exportLogsZip: 导出完成, Server 日志缺失 appCount=$appFileCount reason=$serverFailureMessage"
                )
            } else {
                LoggerX.i(
                    TAG,
                    "exportLogsZip: 导出完成 appCount=$appFileCount serverCount=$serverFileCount"
                )
            }
            return LogExportResult(
                appFileCount = appFileCount,
                serverFileCount = serverFileCount,
                serverExportFailed = serverExportFailed,
                serverFailureMessage = serverFailureMessage
            )
        } finally {
            cleanupTempDir(serverTempDir)
        }
    }

    /**
     * 将目录中的文件递归写入 ZIP。
     *
     * @param zipOutput ZIP 输出流。
     * @param directory 待打包目录。
     * @param rootEntryName ZIP 内根目录名称。
     * @return 写入的文件数量。
     */
    private fun zipDirectory(
        zipOutput: ZipOutputStream,
        directory: File?,
        rootEntryName: String
    ): Int {
        if (directory == null || !directory.exists() || !directory.isDirectory) {
            return 0
        }
        val basePath = directory.toPath()
        var entryCount = 0
        directory.walkTopDown()
            .filter { it.isFile }
            .forEach { file ->
                val relativePath = basePath.relativize(file.toPath()).toString().replace('\\', '/')
                val entryName = "$rootEntryName/$relativePath"
                zipOutput.putNextEntry(ZipEntry(entryName))
                file.inputStream().use { input ->
                    input.copyTo(zipOutput)
                }
                zipOutput.closeEntry()
                entryCount += 1
                LoggerX.d(TAG, "zipDirectory: 写入 ZIP 条目 entry=$entryName")
            }
        return entryCount
    }

    /**
     * 校验日志目录必须存在且至少包含一个文件。
     *
     * @param directory 待校验目录。
     * @param label 日志归属标识。
     * @return 目录内文件数量。
     */
    private fun requireLogFiles(directory: File, label: String): Int {
        if (!directory.exists() || !directory.isDirectory) {
            throw FileNotFoundException("$label 日志目录不存在: ${directory.absolutePath}")
        }
        val fileCount = countRegularFiles(directory)
        if (fileCount == 0) {
            throw FileNotFoundException("$label 日志目录为空: ${directory.absolutePath}")
        }
        return fileCount
    }

    /**
     * 尝试拉取服务端日志到临时目录。
     *
     * 在请求服务端导出前会先由服务端同步 flush 一次 LoggerX，
     * 尽量把最近的故障日志和导出相关日志一并收进导出包。
     *
     * @param tempDir 接收服务端日志的临时目录。
     * @return 接收到的服务端日志文件数量。
     */
    private fun tryReceiveServerLogs(tempDir: File): Int {
        LoggerX.i(TAG, "tryReceiveServerLogs: 开始尝试拉取 Server 日志")
        val service = Service.service ?: throw IOException("服务未连接，无法导出 Server 日志")
        val readPfd = service.exportLogs()
            ?: throw IOException("服务端未返回日志导出管道")
        LoggerX.d(TAG, "tryReceiveServerLogs: 已获取 Server 日志导出管道")

        if (!tempDir.exists() && !tempDir.mkdirs()) {
            throw IOException("创建 Server 日志临时目录失败: ${tempDir.absolutePath}")
        }
        LoggerX.d(TAG, "tryReceiveServerLogs: Server 日志临时目录已创建 dir=${tempDir.absolutePath}")

        PfdFileReceiver.receiveToDir(readPfd, tempDir)
        val fileCount = countRegularFiles(tempDir)
        if (fileCount == 0) {
            throw FileNotFoundException("Server 日志导出为空")
        }
        LoggerX.i(TAG, "tryReceiveServerLogs: Server 日志接收完成 count=$fileCount")
        return fileCount
    }

    /**
     * 清理接收服务端日志时使用的临时目录。
     *
     * @param tempDir 待清理目录。
     * @return 无返回值。
     */
    private fun cleanupTempDir(tempDir: File) {
        if (!tempDir.exists()) {
            return
        }
        LoggerX.d(TAG, "cleanupTempDir: 开始清理临时目录 dir=${tempDir.absolutePath}")
        if (tempDir.deleteRecursively()) {
            LoggerX.d(TAG, "cleanupTempDir: 临时目录清理完成")
        } else {
            LoggerX.w(TAG, "cleanupTempDir: 临时目录清理失败 dir=${tempDir.absolutePath}")
        }
    }

    /**
     * 统计目录中的常规文件数量。
     *
     * @param directory 待统计目录。
     * @return 常规文件数量。
     */
    private fun countRegularFiles(directory: File): Int {
        return directory.walkTopDown()
            .count { it.isFile }
    }
}
