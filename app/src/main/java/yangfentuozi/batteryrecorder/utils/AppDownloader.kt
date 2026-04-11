package yangfentuozi.batteryrecorder.utils

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import yangfentuozi.batteryrecorder.BuildConfig
import yangfentuozi.batteryrecorder.R
import yangfentuozi.batteryrecorder.shared.util.LoggerX
import java.io.File

private const val TAG = "AppDownloader"
private const val PREFS_NAME = "download_prefs"
private const val KEY_DOWNLOAD_ID = "download_id"

object AppDownloader {

    fun downloadApk(context: Context, downloadUrl: String, versionName: String) {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

        val uri = Uri.parse(downloadUrl)
        val request = DownloadManager.Request(uri)

        request.setTitle("BatteryRecorder $versionName")
        request.setDescription(context.getString(R.string.update_download))
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        request.setDestinationInExternalPublicDir(
            Environment.DIRECTORY_DOWNLOADS,
            "batteryrecorder-v$versionName.apk"
        )
        request.setMimeType("application/vnd.android.package-archive")

        try {
            val downloadId = downloadManager.enqueue(request)
            saveDownloadId(context, downloadId)
            Toast.makeText(context, R.string.update_download_started, Toast.LENGTH_SHORT).show()
            LoggerX.i(TAG, "[更新] 开始下载 APK, downloadId=$downloadId, url=$downloadUrl")
        } catch (e: Exception) {
            LoggerX.e(TAG, "[更新] 启动下载失败", tr = e)
            Toast.makeText(context, R.string.update_download_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveDownloadId(context: Context, downloadId: Long) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_DOWNLOAD_ID, downloadId)
            .apply()
    }

    fun getDownloadId(context: Context): Long {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_DOWNLOAD_ID, -1L)
    }

    fun installApk(context: Context, apkUri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(intent)
            LoggerX.i(TAG, "[更新] 启动安装 APK, uri=$apkUri")
        } catch (e: Exception) {
            LoggerX.e(TAG, "[更新] 启动安装失败", tr = e)
            Toast.makeText(context, R.string.update_install_failed, Toast.LENGTH_SHORT).show()
        }
    }
}
