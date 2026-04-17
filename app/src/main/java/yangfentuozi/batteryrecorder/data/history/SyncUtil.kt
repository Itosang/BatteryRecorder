package yangfentuozi.batteryrecorder.data.history

import android.content.Context
import yangfentuozi.batteryrecorder.ipc.Service
import yangfentuozi.batteryrecorder.shared.Constants
import yangfentuozi.batteryrecorder.shared.data.RecordFileNames
import yangfentuozi.batteryrecorder.shared.data.RecordsStats
import yangfentuozi.batteryrecorder.shared.sync.PfdFileReceiver
import yangfentuozi.batteryrecorder.shared.util.LoggerX
import java.io.File

private const val TAG = "SyncUtil"

object SyncUtil {
    fun sync(context: Context) {
        val service = Service.service ?: run {
            LoggerX.w(TAG, "[SYNC] 服务未连接，跳过同步")
            return
        }
        LoggerX.i(TAG, "[SYNC] 开始刷新本地历史仓库")
        val outDir = File(context.dataDir, Constants.APP_POWER_DATA_PATH)
        val syncedTargets = LinkedHashMap<String, Pair<File, String>>()

        try {
            val readPfd = service.sync()
            if (readPfd == null) {
                LoggerX.i(TAG, "[SYNC] 服务端未返回同步管道，跳过传输，继续整理本地历史")
            } else {
                PfdFileReceiver.receiveToDir(readPfd, outDir) { savedFile, _ ->
                    val logicalName = RecordFileNames.logicalNameOrNull(savedFile.name)
                        ?: return@receiveToDir
                    val parentDir = savedFile.parentFile ?: return@receiveToDir
                    syncedTargets["${parentDir.absolutePath}/$logicalName"] = parentDir to logicalName
                }
                LoggerX.i(TAG, "[SYNC] 客户端接收完成: ${outDir.absolutePath}")
            }

            val activeRecordsFileResult = runCatching { service.currRecordsFile }
                .onFailure { error ->
                    LoggerX.e(TAG, "[SYNC] 读取服务端当前记录失败", tr = error)
                }
            val compressedCount = if (activeRecordsFileResult.isSuccess) {
                HistoryRepository.compressHistoricalRecords(
                    context = context,
                    activeRecordsFile = activeRecordsFileResult.getOrNull()
                )
            } else {
                LoggerX.w(TAG, "[SYNC] 当前记录未知，跳过本地历史压缩")
                0
            }
            syncedTargets.values.forEach { (parentDir, logicalName) ->
                preheatPowerStatsCache(context, parentDir, logicalName)
            }
            LoggerX.i(
                TAG,
                "[SYNC] 本地历史整理完成: received=${syncedTargets.size} compressed=$compressedCount"
            )
        } catch (e: Exception) {
            LoggerX.e(TAG, "[SYNC] 本地历史整理失败", tr = e)
            return
        }
    }

    private fun preheatPowerStatsCache(
        context: Context,
        parentDir: File,
        logicalName: String
    ) {
        val sourceFile = RecordFileNames.resolvePhysicalFile(parentDir, logicalName) ?: return
        val cacheFile = getPowerStatsCacheFile(context.cacheDir, logicalName)
        runCatching {
            RecordsStats.getCachedStats(
                cacheFile = cacheFile,
                sourceFile = sourceFile,
                needCaching = true
            )
        }.onFailure { error ->
            LoggerX.w(TAG, "[SYNC] 预热记录统计缓存失败: file=${sourceFile.absolutePath}", tr = error)
        }
    }
}
