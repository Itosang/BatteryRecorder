package yangfentuozi.batteryrecorder.shared.config

import android.os.Build
import android.os.RemoteException
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import yangfentuozi.batteryrecorder.shared.config.dataclass.ServerSettings
import yangfentuozi.batteryrecorder.shared.util.LoggerX
import yangfentuozi.hiddenapi.compat.ActivityManagerCompat
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException

/**
 * ServerSettings 的来源适配层。
 *
 * 这里不定义设置语义，只负责把不同来源的原始数据读成 `ServerSettings`：
 * 1. root 场景直接读取 SharedPreferences XML。
 * 2. shell 场景通过 ConfigProvider 取回 `ServerSettings`。
 */
object ConfigUtil {
    private const val TAG = "ConfigUtil"

    /** 来源适配层只负责读取配置来源并组装 ServerSettings。 */
    fun getServerSettingsByContentProvider(): ServerSettings? {
        return try {
            LoggerX.i(TAG, "getServerSettingsByContentProvider: 通过 ContentProvider 请求配置")
            val settings = readServerSettingsByContentProvider()
            logServerSettings("getServerSettingsByContentProvider", settings)
            settings
        } catch (e: RemoteException) {
            LoggerX.e(TAG, "getServerSettingsByContentProvider: 请求配置失败", tr = e)
            null
        } catch (e: NullPointerException) {
            LoggerX.e(TAG, "getServerSettingsByContentProvider: 请求配置失败", tr = e)
            null
        }
    }

    /** XML 原始值只做解析与缺字段默认值回退，不做额外合法化。 */
    fun readServerSettingsByReading(configFile: File): ServerSettings? {
        if (!configFile.exists()) {
            LoggerX.e(TAG, "readServerSettingsByReading: 配置文件不存在, path=${configFile.absolutePath}")
            return null
        }

        return try {
            LoggerX.i(TAG, "readServerSettingsByReading: 开始读取配置文件, path=${configFile.absolutePath}")
            FileInputStream(configFile).use { fis ->
                val parser = Xml.newPullParser()
                parser.setInput(fis, "UTF-8")
                val settings = ServerSettingsCodec.readFromStringValues(readXmlStringValues(parser))
                logServerSettings("readServerSettingsByReading", settings)
                settings
            }
        } catch (e: FileNotFoundException) {
            LoggerX.e(TAG, "readServerSettingsByReading: 配置文件不存在", tr = e)
            null
        } catch (e: IOException) {
            LoggerX.e(TAG, "readServerSettingsByReading: 读取配置文件失败", tr = e)
            null
        } catch (e: XmlPullParserException) {
            LoggerX.e(TAG, "readServerSettingsByReading: 解析配置文件失败", tr = e)
            null
        }
    }

    private fun readXmlStringValues(parser: XmlPullParser): Map<String, String> {
        val values = LinkedHashMap<String, String>()
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                val nameAttr = parser.getAttributeValue(null, "name")
                val valueAttr = parser.getAttributeValue(null, "value")
                if (nameAttr != null && valueAttr != null) {
                    values[nameAttr] = valueAttr.trim()
                }
            }
            eventType = parser.next()
        }
        return values
    }

    /**
     * 通过 ConfigProvider 读取当前 App 进程导出的 ServerSettings。
     *
     * @return Provider 返回的 ServerSettings；缺失 reply 或 parcelable 时直接抛错给上层处理。
     */
    private fun readServerSettingsByContentProvider(): ServerSettings {
        val reply = ActivityManagerCompat.contentProviderCall(
            "yangfentuozi.batteryrecorder.configProvider",
            "requestConfig",
            null,
            null
        )
        if (reply == null) throw NullPointerException("reply is null")
        reply.classLoader = ServerSettings::class.java.classLoader
        val serverSettings = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            reply.getParcelable("config", ServerSettings::class.java)
        } else {
            @Suppress("DEPRECATION")
            reply.getParcelable("config")
        }
        return serverSettings ?: throw NullPointerException("config is null")
    }

    /**
     * 打印来源适配后的关键服务端配置，便于排查 root/shell 两条读取链是否一致。
     *
     * @param source 当前配置来源标识。
     * @param settings 已解析出的服务端配置。
     * @return 无，仅输出调试日志。
     */
    private fun logServerSettings(source: String, settings: ServerSettings) {
        LoggerX.d(
            TAG,
            "$source: notification=${settings.notificationEnabled} compatMode=${settings.notificationCompatModeEnabled} dualCell=${settings.dualCellEnabled} calibration=${settings.calibrationValue} intervalMs=${settings.recordIntervalMs} batchSize=${settings.batchSize} writeLatencyMs=${settings.writeLatencyMs} screenOffRecord=${settings.screenOffRecordEnabled} preciseScreenOffRecord=${settings.preciseScreenOffRecordEnabled} polling=${settings.alwaysPollingScreenStatusEnabled} logLevel=${settings.logLevel}"
        )
    }
}
