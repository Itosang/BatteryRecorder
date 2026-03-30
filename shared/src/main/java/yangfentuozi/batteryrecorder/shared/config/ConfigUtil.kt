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
    fun getConfigByContentProvider(): Config? {
        return try {
            LoggerX.i(TAG, "getConfigByContentProvider: 通过 ContentProvider 请求配置")
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
            if (config == null) throw NullPointerException("config is null")
            val coerced = coerceConfigValue(config)
            LoggerX.d(TAG, 
                "getConfigByContentProvider: 配置已解析, intervalMs=${coerced.recordIntervalMs} batchSize=${coerced.batchSize} writeLatencyMs=${coerced.writeLatencyMs} screenOffRecord=${coerced.screenOffRecordEnabled} polling=${coerced.alwaysPollingScreenStatusEnabled} logLevel=${coerced.logLevel}"
            )
            coerced
        } catch (e: RemoteException) {
            LoggerX.e(TAG, "getConfigByContentProvider: 请求配置失败", tr = e)
            null
        } catch (e: NullPointerException) {
            LoggerX.e(TAG, "getConfigByContentProvider: 请求配置失败", tr = e)
            null
        }
    }

    fun getConfigByReading(configFile: File): Config? {
        return readServerSettingsByReading(configFile)?.let(ServerSettingsMapper::toConfig)
    }

    fun getConfigBySharedPreferences(prefs: SharedPreferences): Config =
        ServerSettingsMapper.toConfig(getServerSettingsBySharedPreferences(prefs))

    fun getServerSettingsBySharedPreferences(prefs: SharedPreferences): ServerSettings {
        val settings = SharedSettings.readServerSettings(prefs)
        LoggerX.d(
            TAG,
            "getServerSettingsBySharedPreferences: intervalMs=${settings.recordIntervalMs} batchSize=${settings.batchSize} writeLatencyMs=${settings.writeLatencyMs} screenOffRecord=${settings.screenOffRecordEnabled} polling=${settings.alwaysPollingScreenStatusEnabled} logLevel=${settings.logLevel}"
        )
        return settings
    }

    fun readServerSettingsByReading(configFile: File): ServerSettings? {
        if (!configFile.exists()) {
            LoggerX.e(TAG, "getConfigByReading: 配置文件不存在, path=${configFile.absolutePath}")
            return null
        }

        return try {
            LoggerX.i(TAG, "getConfigByReading: 开始读取配置文件, path=${configFile.absolutePath}")
            FileInputStream(configFile).use { fis ->
                val parser = Xml.newPullParser()
                parser.setInput(fis, "UTF-8")

                var eventType = parser.eventType
                var recordIntervalMs = ConfigConstants.DEF_RECORD_INTERVAL_MS
                var batchSize = ConfigConstants.DEF_BATCH_SIZE
                var writeLatencyMs = ConfigConstants.DEF_WRITE_LATENCY_MS
                var screenOffRecordEnabled = ConfigConstants.DEF_SCREEN_OFF_RECORD_ENABLED
                var segmentDurationMin = ConfigConstants.DEF_SEGMENT_DURATION_MIN
                var maxHistoryDays = ConfigConstants.DEF_LOG_MAX_HISTORY_DAYS
                var logLevel = ConfigConstants.DEF_LOG_LEVEL
                var alwaysPollingScreenStatusEnabled =
                    ConfigConstants.DEF_ALWAYS_POLLING_SCREEN_STATUS_ENABLED

                while (eventType != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.START_TAG) {
                        val nameAttr = parser.getAttributeValue(null, "name")
                        val valueAttr = parser.getAttributeValue(null, "value")

                        when (nameAttr) {
                            ConfigConstants.KEY_RECORD_INTERVAL_MS ->
                                recordIntervalMs =
                                    valueAttr.toLongOrNull()
                                        ?: ConfigConstants.DEF_RECORD_INTERVAL_MS

                            ConfigConstants.KEY_BATCH_SIZE ->
                                batchSize =
                                    valueAttr.toIntOrNull()
                                        ?: ConfigConstants.DEF_BATCH_SIZE

                            ConfigConstants.KEY_WRITE_LATENCY_MS ->
                                writeLatencyMs =
                                    valueAttr.toLongOrNull()
                                        ?: ConfigConstants.DEF_WRITE_LATENCY_MS

                            ConfigConstants.KEY_SCREEN_OFF_RECORD_ENABLED -> {
                                screenOffRecordEnabled =
                                    valueAttr.toBooleanStrictOrNull()
                                        ?: ConfigConstants.DEF_SCREEN_OFF_RECORD_ENABLED
                            }

                            ConfigConstants.KEY_SEGMENT_DURATION_MIN ->
                                segmentDurationMin =
                                    valueAttr.toLongOrNull()
                                        ?: ConfigConstants.DEF_SEGMENT_DURATION_MIN

                            ConfigConstants.KEY_LOG_MAX_HISTORY_DAYS ->
                                maxHistoryDays = valueAttr.toLongOrNull()
                                    ?: ConfigConstants.DEF_LOG_MAX_HISTORY_DAYS

                            ConfigConstants.KEY_LOG_LEVEL ->
                                logLevel = SharedSettings.decodeLogLevel(
                                    valueAttr?.trim()?.toIntOrNull()
                                        ?: SharedSettings.encodeLogLevel(ConfigConstants.DEF_LOG_LEVEL)
                                )

                            ConfigConstants.KEY_ALWAYS_POLLING_SCREEN_STATUS_ENABLED ->
                                alwaysPollingScreenStatusEnabled =
                                    valueAttr.toBooleanStrictOrNull()
                                        ?: ConfigConstants.DEF_ALWAYS_POLLING_SCREEN_STATUS_ENABLED
                        }
                    }
                    eventType = parser.next()
                }

                val settings = SharedSettings.readServerSettings(
                    XmlBackedSharedPreferences(
                        recordIntervalMs = recordIntervalMs,
                        batchSize = batchSize,
                        writeLatencyMs = writeLatencyMs,
                        screenOffRecordEnabled = screenOffRecordEnabled,
                        segmentDurationMin = segmentDurationMin,
                        maxHistoryDays = maxHistoryDays,
                        logLevel = logLevel,
                        alwaysPollingScreenStatusEnabled = alwaysPollingScreenStatusEnabled
                    )
                )
                LoggerX.d(
                    TAG,
                    "readServerSettingsByReading: intervalMs=${settings.recordIntervalMs} batchSize=${settings.batchSize} writeLatencyMs=${settings.writeLatencyMs} screenOffRecord=${settings.screenOffRecordEnabled} polling=${settings.alwaysPollingScreenStatusEnabled} logLevel=${settings.logLevel}"
                )
                settings
            }
        } catch (e: FileNotFoundException) {
            LoggerX.e(TAG, "getConfigByReading: 配置文件不存在", tr = e)
            null
        } catch (e: IOException) {
            LoggerX.e(TAG, "getConfigByReading: 读取配置文件失败", tr = e)
            null
        } catch (e: XmlPullParserException) {
            LoggerX.e(TAG, "getConfigByReading: 解析配置文件失败", tr = e)
            null
        }
    }

    fun coerceConfigValue(config: Config): Config {
        val coercedSettings = SharedSettings.readServerSettings(
            XmlBackedSharedPreferences(
                recordIntervalMs = config.recordIntervalMs,
                batchSize = config.batchSize,
                writeLatencyMs = config.writeLatencyMs,
                screenOffRecordEnabled = config.screenOffRecordEnabled,
                segmentDurationMin = config.segmentDurationMin,
                maxHistoryDays = config.maxHistoryDays,
                logLevel = config.logLevel,
                alwaysPollingScreenStatusEnabled = config.alwaysPollingScreenStatusEnabled
            )
        )
        val coerced = ServerSettingsMapper.toConfig(coercedSettings)
        if (coerced != config) {
            LoggerX.v(TAG, "coerceConfigValue: 配置值已裁剪到合法范围")
        }
        return coerced
    }
}

private data class XmlBackedSharedPreferences(
    val recordIntervalMs: Long,
    val batchSize: Int,
    val writeLatencyMs: Long,
    val screenOffRecordEnabled: Boolean,
    val segmentDurationMin: Long,
    val maxHistoryDays: Long,
    val logLevel: LoggerX.LogLevel,
    val alwaysPollingScreenStatusEnabled: Boolean
) : SharedPreferences {
    override fun getAll(): MutableMap<String, Any?> = mutableMapOf()

    override fun getString(key: String?, defValue: String?): String? = defValue

    override fun getStringSet(
        key: String?,
        defValues: MutableSet<String>?
    ): MutableSet<String>? = defValues?.toMutableSet()

    override fun getInt(key: String?, defValue: Int): Int = when (key) {
        ConfigConstants.KEY_BATCH_SIZE -> batchSize
        ConfigConstants.KEY_LOG_LEVEL -> logLevel.priority
        else -> defValue
    }

    override fun getLong(key: String?, defValue: Long): Long = when (key) {
        ConfigConstants.KEY_RECORD_INTERVAL_MS -> recordIntervalMs
        ConfigConstants.KEY_WRITE_LATENCY_MS -> writeLatencyMs
        ConfigConstants.KEY_SEGMENT_DURATION_MIN -> segmentDurationMin
        ConfigConstants.KEY_LOG_MAX_HISTORY_DAYS -> maxHistoryDays
        else -> defValue
    }

    override fun getFloat(key: String?, defValue: Float): Float = defValue

    override fun getBoolean(key: String?, defValue: Boolean): Boolean = when (key) {
        ConfigConstants.KEY_SCREEN_OFF_RECORD_ENABLED -> screenOffRecordEnabled
        ConfigConstants.KEY_ALWAYS_POLLING_SCREEN_STATUS_ENABLED -> alwaysPollingScreenStatusEnabled
        else -> defValue
    }

    override fun contains(key: String?): Boolean = false

    override fun edit(): SharedPreferences.Editor {
        throw UnsupportedOperationException("XmlBackedSharedPreferences does not support edit")
    }

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
}
