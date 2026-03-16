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

object ConfigUtil {
    fun getConfigByContentProvider(): Config? {
        return try {
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
            LoggerX.i<ConfigUtil>("getConfigByContentProvider: 请求配置")
            coerceConfigValue(config)
        } catch (e: RemoteException) {
            LoggerX.e<ConfigUtil>("getConfigByContentProvider: 请求配置失败", tr = e)
            null
        } catch (e: NullPointerException) {
            LoggerX.e<ConfigUtil>("getConfigByContentProvider: 请求配置失败", tr = e)
            null
        }
    }

    fun getConfigByReading(configFile: File): Config? {
        if (!configFile.exists()) {
            LoggerX.w<ConfigUtil>("getConfigByReading: 配置文件不存在")
            return null
        }

        return try {
            FileInputStream(configFile).use { fis ->
                val parser = Xml.newPullParser()
                parser.setInput(fis, "UTF-8")

                var eventType = parser.eventType
                var recordIntervalMs = ConfigConstants.DEF_RECORD_INTERVAL_MS
                var batchSize = ConfigConstants.DEF_BATCH_SIZE
                var writeLatencyMs = ConfigConstants.DEF_WRITE_LATENCY_MS
                var screenOffRecordEnabled = ConfigConstants.DEF_SCREEN_OFF_RECORD_ENABLED
                var segmentDurationMin = ConfigConstants.DEF_SEGMENT_DURATION_MIN
                var logLevel = ConfigConstants.DEF_LOG_LEVEL
                var alwaysPollingScreenStatusEnabled = ConfigConstants.DEF_ALWAYS_POLLING_SCREEN_STATUS_ENABLED

                while (eventType != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.START_TAG) {
                        val nameAttr = parser.getAttributeValue(null, "name")
                        val valueAttr = parser.getAttributeValue(null, "value")

                        when (nameAttr) {
                            ConfigConstants.KEY_RECORD_INTERVAL_MS ->
                                recordIntervalMs = valueAttr.toLongOrNull() ?: ConfigConstants.DEF_RECORD_INTERVAL_MS

                            ConfigConstants.KEY_BATCH_SIZE ->
                                batchSize = valueAttr.toIntOrNull() ?: ConfigConstants.DEF_BATCH_SIZE

                            ConfigConstants.KEY_WRITE_LATENCY_MS ->
                                writeLatencyMs = valueAttr.toLongOrNull() ?: ConfigConstants.DEF_WRITE_LATENCY_MS

                            ConfigConstants.KEY_SCREEN_OFF_RECORD_ENABLED -> {
                                screenOffRecordEnabled = valueAttr.toBooleanStrictOrNull() ?: ConfigConstants.DEF_SCREEN_OFF_RECORD_ENABLED
                            }

                            ConfigConstants.KEY_SEGMENT_DURATION_MIN ->
                                segmentDurationMin = valueAttr.toLongOrNull() ?: ConfigConstants.DEF_SEGMENT_DURATION_MIN

                            ConfigConstants.KEY_LOG_LEVEL ->
                                logLevel = LoggerX.LogLevel.fromPriority(valueAttr.trim().toIntOrNull() ?: Int.MIN_VALUE)

                            ConfigConstants.KEY_ALWAYS_POLLING_SCREEN_STATUS_ENABLED ->
                                alwaysPollingScreenStatusEnabled = valueAttr.toBooleanStrictOrNull() ?: ConfigConstants.DEF_ALWAYS_POLLING_SCREEN_STATUS_ENABLED
                        }
                    }
                    eventType = parser.next()
                }

                coerceConfigValue(Config(
                    recordIntervalMs = recordIntervalMs,
                    writeLatencyMs = writeLatencyMs,
                    batchSize = batchSize,
                    screenOffRecordEnabled = screenOffRecordEnabled,
                    segmentDurationMin = segmentDurationMin,
                    logLevel = logLevel,
                    alwaysPollingScreenStatusEnabled = alwaysPollingScreenStatusEnabled
                ))
            }
        } catch (e: FileNotFoundException) {
            LoggerX.w<ConfigUtil>("getConfigByReading: 配置文件不存在", tr = e)
            null
        } catch (e: IOException) {
            LoggerX.w<ConfigUtil>("getConfigByReading: 读取配置文件失败", tr = e)
            null
        } catch (e: XmlPullParserException) {
            LoggerX.w<ConfigUtil>("getConfigByReading: 解析配置文件失败", tr = e)
            null
        }
    }

    fun getConfigBySharedPreferences(prefs: SharedPreferences): Config {
        return coerceConfigValue(Config(
            recordIntervalMs = prefs.getLong(ConfigConstants.KEY_RECORD_INTERVAL_MS, ConfigConstants.DEF_RECORD_INTERVAL_MS),
            writeLatencyMs = prefs.getLong(ConfigConstants.KEY_WRITE_LATENCY_MS, ConfigConstants.DEF_WRITE_LATENCY_MS),
            batchSize = prefs.getInt(ConfigConstants.KEY_BATCH_SIZE, ConfigConstants.DEF_BATCH_SIZE),
            screenOffRecordEnabled = prefs.getBoolean(
                ConfigConstants.KEY_SCREEN_OFF_RECORD_ENABLED,
                ConfigConstants.DEF_SCREEN_OFF_RECORD_ENABLED
            ),
            segmentDurationMin = prefs.getLong(ConfigConstants.KEY_SEGMENT_DURATION_MIN, ConfigConstants.DEF_SEGMENT_DURATION_MIN),
            logLevel = LoggerX.LogLevel.fromPriority(prefs.getInt(ConfigConstants.KEY_LOG_LEVEL, ConfigConstants.DEF_LOG_LEVEL.priority)),
            alwaysPollingScreenStatusEnabled = prefs.getBoolean(ConfigConstants.KEY_ALWAYS_POLLING_SCREEN_STATUS_ENABLED, ConfigConstants.DEF_ALWAYS_POLLING_SCREEN_STATUS_ENABLED)
        ))
    }

    fun coerceConfigValue(config: Config): Config {
        return config.copy(
            recordIntervalMs = config.recordIntervalMs.coerceIn(
                ConfigConstants.MIN_RECORD_INTERVAL_MS,
                ConfigConstants.MAX_RECORD_INTERVAL_MS
            ),
            batchSize = config.batchSize.coerceIn(
                ConfigConstants.MIN_BATCH_SIZE,
                ConfigConstants.MAX_BATCH_SIZE
            ),
            writeLatencyMs = config.writeLatencyMs.coerceIn(
                ConfigConstants.MIN_WRITE_LATENCY_MS,
                ConfigConstants.MAX_WRITE_LATENCY_MS
            ),
            segmentDurationMin = config.segmentDurationMin.coerceIn(
                ConfigConstants.MIN_SEGMENT_DURATION_MIN,
                ConfigConstants.MAX_SEGMENT_DURATION_MIN
            )
        )
    }
}