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

private const val TAG = "ConfigUtil"

/**
 * ServerSettings 的来源适配层。
 *
 * 这里不定义设置语义，只负责把不同来源的原始数据读成 `ServerSettings`：
 * 1. root 场景直接读取 SharedPreferences XML。
 * 2. shell 场景通过 ConfigProvider 取回 `ServerSettings`。
 */
object ConfigUtil {
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

                var eventType = parser.eventType
                var recordIntervalMs: Long? = null
                var batchSize: Int? = null
                var writeLatencyMs: Long? = null
                var screenOffRecordEnabled: Boolean? = null
                var segmentDurationMin: Long? = null
                var maxHistoryDays: Long? = null
                var logLevelPriority: Int? = null
                var alwaysPollingScreenStatusEnabled: Boolean? = null

                while (eventType != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.START_TAG) {
                        val nameAttr = parser.getAttributeValue(null, "name")
                        val valueAttr = parser.getAttributeValue(null, "value")
                        val trimmedValue = valueAttr?.trim()

                        when (nameAttr) {
                            SettingsConstants.recordIntervalMs.key ->
                                recordIntervalMs = trimmedValue?.toLongOrNull()

                            SettingsConstants.batchSize.key ->
                                batchSize = trimmedValue?.toIntOrNull()

                            SettingsConstants.writeLatencyMs.key ->
                                writeLatencyMs = trimmedValue?.toLongOrNull()

                            SettingsConstants.screenOffRecordEnabled.key ->
                                screenOffRecordEnabled = trimmedValue?.toBooleanStrictOrNull()

                            SettingsConstants.segmentDurationMin.key ->
                                segmentDurationMin = trimmedValue?.toLongOrNull()

                            SettingsConstants.logMaxHistoryDays.key ->
                                maxHistoryDays = trimmedValue?.toLongOrNull()

                            SettingsConstants.logLevel.key ->
                                logLevelPriority = trimmedValue?.toIntOrNull()

                            SettingsConstants.alwaysPollingScreenStatusEnabled.key ->
                                alwaysPollingScreenStatusEnabled =
                                    trimmedValue?.toBooleanStrictOrNull()
                        }
                    }
                    eventType = parser.next()
                }

                val settings = ServerSettings(
                    recordIntervalMs = recordIntervalMs ?: SettingsConstants.recordIntervalMs.def,
                    batchSize = batchSize ?: SettingsConstants.batchSize.def,
                    writeLatencyMs = writeLatencyMs ?: SettingsConstants.writeLatencyMs.def,
                    screenOffRecordEnabled =
                        screenOffRecordEnabled ?: SettingsConstants.screenOffRecordEnabled.def,
                    segmentDurationMin =
                        segmentDurationMin ?: SettingsConstants.segmentDurationMin.def,
                    maxHistoryDays = maxHistoryDays ?: SettingsConstants.logMaxHistoryDays.def,
                    logLevel = SharedSettings.decodeLogLevel(
                        logLevelPriority
                            ?: SharedSettings.encodeLogLevel(SettingsConstants.logLevel.def)
                    ),
                    alwaysPollingScreenStatusEnabled =
                        alwaysPollingScreenStatusEnabled
                            ?: SettingsConstants.alwaysPollingScreenStatusEnabled.def
                )
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
            "$source: intervalMs=${settings.recordIntervalMs} batchSize=${settings.batchSize} writeLatencyMs=${settings.writeLatencyMs} screenOffRecord=${settings.screenOffRecordEnabled} polling=${settings.alwaysPollingScreenStatusEnabled} logLevel=${settings.logLevel}"
        )
    }
}
