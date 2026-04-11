package yangfentuozi.batteryrecorder.utils

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.core.net.toUri
import yangfentuozi.batteryrecorder.BuildConfig
import yangfentuozi.batteryrecorder.R
import yangfentuozi.batteryrecorder.shared.util.LoggerX
import java.io.File

object AppDownloader {

    private const val TAG = "AppDownloader"
    private const val PREFS_NAME = "download_prefs"
    private const val KEY_DOWNLOAD_ID = "download_id"

    class DownloadCompleteReceiver : BroadcastReceiver() {
        private val tag = "DownloadCompleteReceiver"

        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
                LoggerX.d(tag, "[更新] 无效action=${intent.action}，忽略")
                return
            }
            LoggerX.d(tag, "[更新] 收到下载完成广播, action=${intent.action}")

            if (intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) == -1L) {
                LoggerX.d(tag, "[更新] 无下载ID，忽略")
                return
            }

            val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            val savedDownloadId = getDownloadId(context)

            if (downloadId != savedDownloadId) {
                LoggerX.d(tag, "[更新] 下载完成，但不是我们的下载任务，忽略: downloadId=$downloadId savedId=$savedDownloadId")
                return
            }

            LoggerX.i(tag, "[更新] 下载完成，准备安装 APK, downloadId=$downloadId")
            checkAndInstallForId(context, downloadId)
        }
    }

    fun canRequestPackageInstalls(context: Context): Boolean {
        return context.packageManager.canRequestPackageInstalls()
    }

    fun createInstallPermissionIntent(): Intent {
        return Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            "package:${BuildConfig.APPLICATION_ID}".toUri()
        )
    }

    fun downloadApk(context: Context, downloadUrl: String, versionName: String) {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

        val uri = downloadUrl.toUri()
        val request = DownloadManager.Request(uri).apply {
            setTitle("BatteryRecorder $versionName")
            setDescription(context.getString(R.string.update_download))
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                "batteryrecorder-v$versionName.apk"
            )
            setMimeType("application/vnd.android.package-archive")
        }

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
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putLong(KEY_DOWNLOAD_ID, downloadId)
        }
    }

    fun getDownloadId(context: Context): Long {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_DOWNLOAD_ID, -1L)
    }

    fun installApk(context: Context, apkUri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        try {
            context.startActivity(intent)
            LoggerX.i(TAG, "[更新] 启动安装 APK, uri=$apkUri")
        } catch (e: Exception) {
            LoggerX.e(TAG, "[更新] 启动安装失败", tr = e)
            Toast.makeText(context, R.string.update_install_failed, Toast.LENGTH_SHORT).show()
        }
    }

    fun checkAndInstallForId(context: Context, downloadId: Long): Boolean {
        if (downloadId == -1L) return false

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().setFilterById(downloadId)

        return try {
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
                                    if (!canRequestPackageInstalls(context)) {
                                        LoggerX.w(TAG, "[更新] 缺少安装权限")
                                        Toast.makeText(
                                            context,
                                            R.string.update_install_permission_required,
                                            Toast.LENGTH_LONG
                                        ).show()
                                        val permissionIntent = createInstallPermissionIntent()
                                        permissionIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        try {
                                            context.startActivity(permissionIntent)
                                        } catch (e: Exception) {
                                            LoggerX.e(TAG, "[更新] 无法启动权限设置", tr = e)
                                        }
                                        return false
                                    }

                                    val apkUri = uriString.toUri()
                                    val contentUri = FileProvider.getUriForFile(
                                            context,
                                            "${BuildConfig.APPLICATION_ID}.fileprovider",
                                            File(apkUri.path!!)
                                        )
                                    installApk(context, contentUri)
                                    return true
                                }
                            }
                        }
                    }
                }
                false
            } ?: false
        } catch (e: Exception) {
            LoggerX.e(TAG, "[更新] 检查下载状态失败", tr = e)
            false
        }
    }
}
