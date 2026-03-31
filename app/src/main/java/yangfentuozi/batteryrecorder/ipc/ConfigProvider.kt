package yangfentuozi.batteryrecorder.ipc

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import yangfentuozi.batteryrecorder.shared.config.SharedSettings
import yangfentuozi.batteryrecorder.shared.util.LoggerX

private const val TAG = "ConfigProvider"

/**
 * 向 shell 侧 Server 暴露当前 `ServerSettings` 的只读 Provider。
 *
 * shell 进程无法直接读取 App 私有 SharedPreferences 时，会通过这里拿到同一份服务端配置。
 */
class ConfigProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (method == "requestConfig") {
            LoggerX.i(TAG, "[配置] 收到 requestConfig 请求")

            // Provider 侧只负责转发当前 ServerSettings，不在这里重复定义设置语义。
            val serverSettings = SharedSettings.readServerSettings(context!!)
            return Bundle().apply {
                putParcelable("config", serverSettings)
            }
        }
        return null
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0
}
