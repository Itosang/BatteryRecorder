package yangfentuozi.batteryrecorder.shared.config

import android.content.SharedPreferences
import android.os.Build
import android.os.RemoteException
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import yangfentuozi.batteryrecorder.shared.util.LoggerX
import yangfentuozi.hiddenapi.compat.ActivityManagerCompat
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException

private const val TAG = "ConfigUtil"

object ConfigUtil {
    // 来源适配器先返回 ServerSettings，让服务端配置合法化统一收敛到领域模型；
    // Config 仅保留为现有 IPC 边界的薄包装。
    fun getConfigByContentProvider(): Config? =
        getServerSettingsByContentProvider()?.let(ServerSettingsMapper::toConfig)

    fun getServerSettingsByContentProvider(): ServerSettings? {
        return try {
            LoggerX.i(TAG, "getServerSettingsByContentProvider: 通过 ContentProvider 请求配置")
            val settings = ServerSettingsMapper.fromConfig(readConfigDtoByContentProvider())
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

    // 兼容仍依赖 Config 的服务端调用方；真正的来源解析与合法化统一先落到 ServerSettings。
    fun getConfigByReading(configFile: File): Config? =
        readServerSettingsByReading(configFile)?.let(ServerSettingsMapper::toConfig)

    fun getServerSettingsBySharedPreferences(prefs: SharedPreferences): ServerSettings {
        val settings = SharedSettings.readServerSettings(prefs)
        logServerSettings("getServerSettingsBySharedPreferences", settings)
        return settings
    }

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
                            ConfigConstants.KEY_RECORD_INTERVAL_MS ->
                                recordIntervalMs = trimmedValue?.toLongOrNull()

                            ConfigConstants.KEY_BATCH_SIZE ->
                                batchSize = trimmedValue?.toIntOrNull()

                            ConfigConstants.KEY_WRITE_LATENCY_MS ->
                                writeLatencyMs = trimmedValue?.toLongOrNull()

                            ConfigConstants.KEY_SCREEN_OFF_RECORD_ENABLED ->
                                screenOffRecordEnabled = trimmedValue?.toBooleanStrictOrNull()

                            ConfigConstants.KEY_SEGMENT_DURATION_MIN ->
                                segmentDurationMin = trimmedValue?.toLongOrNull()

                            ConfigConstants.KEY_LOG_MAX_HISTORY_DAYS ->
                                maxHistoryDays = trimmedValue?.toLongOrNull()

                            ConfigConstants.KEY_LOG_LEVEL ->
                                logLevelPriority = trimmedValue?.toIntOrNull()

                            ConfigConstants.KEY_ALWAYS_POLLING_SCREEN_STATUS_ENABLED ->
                                alwaysPollingScreenStatusEnabled =
                                    trimmedValue?.toBooleanStrictOrNull()
                        }
                    }
                    eventType = parser.next()
                }

                val settings = SharedSettings.serverSettingsFromStoredValues(
                    recordIntervalMs = recordIntervalMs,
                    batchSize = batchSize,
                    writeLatencyMs = writeLatencyMs,
                    screenOffRecordEnabled = screenOffRecordEnabled,
                    segmentDurationMin = segmentDurationMin,
                    maxHistoryDays = maxHistoryDays,
                    logLevelPriority = logLevelPriority,
                    alwaysPollingScreenStatusEnabled = alwaysPollingScreenStatusEnabled
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

    private fun readConfigDtoByContentProvider(): Config {
        val reply = ActivityManagerCompat.contentProviderCall(
            "yangfentuozi.batteryrecorder.configProvider",
            "requestConfig",
            null,
            null
        )
        if (reply == null) throw NullPointerException("reply is null")
        reply.classLoader = Config::class.java.classLoader
        val config = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            reply.getParcelable("config", Config::class.java)
        } else {
            @Suppress("DEPRECATION")
            reply.getParcelable("config")
        }
        return config ?: throw NullPointerException("config is null")
    }

    private fun logServerSettings(source: String, settings: ServerSettings) {
        LoggerX.d(
            TAG,
            "$source: intervalMs=${settings.recordIntervalMs} batchSize=${settings.batchSize} writeLatencyMs=${settings.writeLatencyMs} screenOffRecord=${settings.screenOffRecordEnabled} polling=${settings.alwaysPollingScreenStatusEnabled} logLevel=${settings.logLevel}"
        )
    }
}
