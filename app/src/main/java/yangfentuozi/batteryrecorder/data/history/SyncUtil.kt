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
        LoggerX.i(TAG, "[SYNC] 开始从服务端拉取记录文件")
        val readPfd = service.sync() ?: run {
            LoggerX.w(TAG, "[SYNC] 服务端未返回同步管道")
            return
        }
        val outDir = File(context.dataDir, Constants.APP_POWER_DATA_PATH)

        try {
            PfdFileReceiver.receiveToDir(readPfd, outDir) { savedFile, _ ->
                val logicalName = RecordFileNames.logicalNameOrNull(savedFile.name) ?: return@receiveToDir
                val cacheFile = getPowerStatsCacheFile(context.cacheDir, logicalName)
                runCatching {
                    RecordsStats.getCachedStats(
                        cacheFile = cacheFile,
                        sourceFile = savedFile,
                        needCaching = true
                    )
                }.onFailure { error ->
                    LoggerX.w(TAG, "[SYNC] 预热记录统计缓存失败: file=${savedFile.absolutePath}", tr = error)
                }
            }
            LoggerX.i(TAG, "[SYNC] 客户端接收完成: ${outDir.absolutePath}")
        } catch (e: Exception) {
            LoggerX.e(TAG, "[SYNC] 客户端接收失败", tr = e)
        }
    }

}
