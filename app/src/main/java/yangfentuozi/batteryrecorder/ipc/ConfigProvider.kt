package yangfentuozi.batteryrecorder.ipc

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import yangfentuozi.batteryrecorder.shared.config.ConfigConstants
import yangfentuozi.batteryrecorder.shared.config.ServerSettingsMapper
import yangfentuozi.batteryrecorder.shared.config.SharedSettings
import yangfentuozi.batteryrecorder.shared.util.LoggerX

private const val TAG = "ConfigProvider"

class ConfigProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (method == "requestConfig") {
            LoggerX.i(TAG, "[配置] 收到 requestConfig 请求")

            val prefs = requireContext().getSharedPreferences(
                ConfigConstants.PREFS_NAME,
                Context.MODE_PRIVATE
            )
            return Bundle().apply {
                // 这里或许后续可以继续优化实现
                putParcelable(
                    "config",
                    ServerSettingsMapper.toConfig(SharedSettings.readServerSettings(prefs))
                )
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
