package yangfentuozi.batteryrecorder.utils

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import yangfentuozi.batteryrecorder.BuildConfig
import yangfentuozi.batteryrecorder.shared.util.LoggerX
import java.io.File

private const val TAG = "DownloadCompleteReceiver"

class DownloadCompleteReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return

        val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        val savedDownloadId = AppDownloader.getDownloadId(context)

        if (downloadId != savedDownloadId) {
            LoggerX.d(TAG, "[更新] 下载完成，但不是我们的下载任务，忽略")
            return
        }

        LoggerX.i(TAG, "[更新] 下载完成，准备安装 APK, downloadId=$downloadId")

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().setFilterById(downloadId)

        downloadManager.query(query)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                if (statusIndex != -1) {
                    val status = cursor.getInt(statusIndex)
                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        val uriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                        if (uriIndex != -1) {
                            val uriString = cursor.getString(uriIndex)
                            if (uriString != null) {
                                val apkUri = Uri.parse(uriString)
                                val contentUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                    FileProvider.getUriForFile(
                                        context,
                                        "${BuildConfig.APPLICATION_ID}.fileprovider",
                                        File(apkUri.path!!)
                                    )
                                } else {
                                    apkUri
                                }
                                AppDownloader.installApk(context, contentUri)
                            }
                        }
                    } else {
                        LoggerX.w(TAG, "[更新] 下载失败，status=$status")
                    }
                }
            }
        }
    }
}
