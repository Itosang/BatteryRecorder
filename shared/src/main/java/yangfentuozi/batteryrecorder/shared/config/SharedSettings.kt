package yangfentuozi.batteryrecorder.shared.config

import android.content.Context
import android.content.SharedPreferences
import android.content.SharedPreferences.Editor
import yangfentuozi.batteryrecorder.shared.util.LoggerX

object AppSettingKeys {
    const val CHECK_UPDATE_ON_STARTUP = ConfigConstants.KEY_CHECK_UPDATE_ON_STARTUP
    const val DUAL_CELL_ENABLED = ConfigConstants.KEY_DUAL_CELL_ENABLED
    const val DISCHARGE_DISPLAY_POSITIVE = ConfigConstants.KEY_DISCHARGE_DISPLAY_POSITIVE
    const val CALIBRATION_VALUE = ConfigConstants.KEY_CALIBRATION_VALUE
    const val ROOT_BOOT_AUTO_START_ENABLED = ConfigConstants.KEY_ROOT_BOOT_AUTO_START_ENABLED
}

object StatisticsSettingKeys {
    const val GAME_PACKAGES = ConfigConstants.KEY_GAME_PACKAGES
    const val GAME_BLACKLIST = ConfigConstants.KEY_GAME_BLACKLIST
    const val SCENE_STATS_RECENT_FILE_COUNT = ConfigConstants.KEY_SCENE_STATS_RECENT_FILE_COUNT
    const val PRED_CURRENT_SESSION_WEIGHT_ENABLED =
        ConfigConstants.KEY_PRED_CURRENT_SESSION_WEIGHT_ENABLED
    const val PRED_CURRENT_SESSION_WEIGHT_MAX_X100 =
        ConfigConstants.KEY_PRED_CURRENT_SESSION_WEIGHT_MAX_X100
    const val PRED_CURRENT_SESSION_WEIGHT_HALF_LIFE_MIN =
        ConfigConstants.KEY_PRED_CURRENT_SESSION_WEIGHT_HALF_LIFE_MIN
}

object ServerSettingKeys {
    const val RECORD_INTERVAL_MS = ConfigConstants.KEY_RECORD_INTERVAL_MS
    const val BATCH_SIZE = ConfigConstants.KEY_BATCH_SIZE
    const val WRITE_LATENCY_MS = ConfigConstants.KEY_WRITE_LATENCY_MS
    const val SCREEN_OFF_RECORD_ENABLED = ConfigConstants.KEY_SCREEN_OFF_RECORD_ENABLED
    const val SEGMENT_DURATION_MIN = ConfigConstants.KEY_SEGMENT_DURATION_MIN
    const val ALWAYS_POLLING_SCREEN_STATUS_ENABLED =
        ConfigConstants.KEY_ALWAYS_POLLING_SCREEN_STATUS_ENABLED
    const val MAX_HISTORY_DAYS = ConfigConstants.KEY_LOG_MAX_HISTORY_DAYS
    const val LOG_LEVEL = ConfigConstants.KEY_LOG_LEVEL
}

data class AppSettings(
    val checkUpdateOnStartup: Boolean = ConfigConstants.DEF_CHECK_UPDATE_ON_STARTUP,
    val dualCellEnabled: Boolean = ConfigConstants.DEF_DUAL_CELL_ENABLED,
    val dischargeDisplayPositive: Boolean = ConfigConstants.DEF_DISCHARGE_DISPLAY_POSITIVE,
    val calibrationValue: Int = ConfigConstants.DEF_CALIBRATION_VALUE,
    val rootBootAutoStartEnabled: Boolean = ConfigConstants.DEF_ROOT_BOOT_AUTO_START_ENABLED
)

data class StatisticsSettings(
    val gamePackages: Set<String> = emptySet(),
    val gameBlacklist: Set<String> = emptySet(),
    val sceneStatsRecentFileCount: Int = ConfigConstants.DEF_SCENE_STATS_RECENT_FILE_COUNT,
    val predCurrentSessionWeightEnabled: Boolean =
        ConfigConstants.DEF_PRED_CURRENT_SESSION_WEIGHT_ENABLED,
    val predCurrentSessionWeightMaxX100: Int =
        ConfigConstants.DEF_PRED_CURRENT_SESSION_WEIGHT_MAX_X100,
    val predCurrentSessionWeightHalfLifeMin: Long =
        ConfigConstants.DEF_PRED_CURRENT_SESSION_WEIGHT_HALF_LIFE_MIN
)

data class ServerSettings(
    val recordIntervalMs: Long = ConfigConstants.DEF_RECORD_INTERVAL_MS,
    val batchSize: Int = ConfigConstants.DEF_BATCH_SIZE,
    val writeLatencyMs: Long = ConfigConstants.DEF_WRITE_LATENCY_MS,
    val screenOffRecordEnabled: Boolean = ConfigConstants.DEF_SCREEN_OFF_RECORD_ENABLED,
    val segmentDurationMin: Long = ConfigConstants.DEF_SEGMENT_DURATION_MIN,
    val maxHistoryDays: Long = ConfigConstants.DEF_LOG_MAX_HISTORY_DAYS,
    val logLevel: LoggerX.LogLevel = ConfigConstants.DEF_LOG_LEVEL,
    val alwaysPollingScreenStatusEnabled: Boolean =
        ConfigConstants.DEF_ALWAYS_POLLING_SCREEN_STATUS_ENABLED
)

object SharedSettings {
    fun getPreferences(context: Context): SharedPreferences =
        context.getSharedPreferences(ConfigConstants.PREFS_NAME, Context.MODE_PRIVATE)

    fun readAppSettings(context: Context): AppSettings = readAppSettings(getPreferences(context))

    fun readAppSettings(prefs: SharedPreferences): AppSettings =
        AppSettings(
            checkUpdateOnStartup =
                prefs.getBoolean(
                    AppSettingKeys.CHECK_UPDATE_ON_STARTUP,
                    ConfigConstants.DEF_CHECK_UPDATE_ON_STARTUP
                ),
            dualCellEnabled =
                prefs.getBoolean(
                    AppSettingKeys.DUAL_CELL_ENABLED,
                    ConfigConstants.DEF_DUAL_CELL_ENABLED
                ),
            dischargeDisplayPositive =
                prefs.getBoolean(
                    AppSettingKeys.DISCHARGE_DISPLAY_POSITIVE,
                    ConfigConstants.DEF_DISCHARGE_DISPLAY_POSITIVE
                ),
            calibrationValue =
                normalizeCalibrationValue(
                    prefs.getInt(
                        AppSettingKeys.CALIBRATION_VALUE,
                        ConfigConstants.DEF_CALIBRATION_VALUE
                    )
                ),
            rootBootAutoStartEnabled =
                prefs.getBoolean(
                    AppSettingKeys.ROOT_BOOT_AUTO_START_ENABLED,
                    ConfigConstants.DEF_ROOT_BOOT_AUTO_START_ENABLED
                )
        )

    fun readStatisticsSettings(context: Context): StatisticsSettings =
        readStatisticsSettings(getPreferences(context))

    fun readStatisticsSettings(prefs: SharedPreferences): StatisticsSettings =
        StatisticsSettings(
            gamePackages =
                prefs.getStringSet(StatisticsSettingKeys.GAME_PACKAGES, emptySet())?.toSet()
                    ?: emptySet(),
            gameBlacklist =
                prefs.getStringSet(StatisticsSettingKeys.GAME_BLACKLIST, emptySet())?.toSet()
                    ?: emptySet(),
            sceneStatsRecentFileCount =
                normalizeSceneStatsRecentFileCount(
                    prefs.getInt(
                        StatisticsSettingKeys.SCENE_STATS_RECENT_FILE_COUNT,
                        ConfigConstants.DEF_SCENE_STATS_RECENT_FILE_COUNT
                    )
                ),
            predCurrentSessionWeightEnabled =
                prefs.getBoolean(
                    StatisticsSettingKeys.PRED_CURRENT_SESSION_WEIGHT_ENABLED,
                    ConfigConstants.DEF_PRED_CURRENT_SESSION_WEIGHT_ENABLED
                ),
            predCurrentSessionWeightMaxX100 =
                normalizePredCurrentSessionWeightMaxX100(
                    prefs.getInt(
                        StatisticsSettingKeys.PRED_CURRENT_SESSION_WEIGHT_MAX_X100,
                        ConfigConstants.DEF_PRED_CURRENT_SESSION_WEIGHT_MAX_X100
                    )
                ),
            predCurrentSessionWeightHalfLifeMin =
                normalizePredCurrentSessionWeightHalfLifeMin(
                    prefs.getLong(
                        StatisticsSettingKeys.PRED_CURRENT_SESSION_WEIGHT_HALF_LIFE_MIN,
                        ConfigConstants.DEF_PRED_CURRENT_SESSION_WEIGHT_HALF_LIFE_MIN
                    )
                )
        )

    fun readServerSettings(context: Context): ServerSettings =
        readServerSettings(getPreferences(context))

    fun readServerSettings(prefs: SharedPreferences): ServerSettings =
        serverSettingsFromStoredValues(
            recordIntervalMs =
                prefs.getLong(
                    ServerSettingKeys.RECORD_INTERVAL_MS,
                    ConfigConstants.DEF_RECORD_INTERVAL_MS
                ),
            batchSize =
                prefs.getInt(ServerSettingKeys.BATCH_SIZE, ConfigConstants.DEF_BATCH_SIZE),
            writeLatencyMs =
                prefs.getLong(
                    ServerSettingKeys.WRITE_LATENCY_MS,
                    ConfigConstants.DEF_WRITE_LATENCY_MS
                ),
            screenOffRecordEnabled =
                prefs.getBoolean(
                    ServerSettingKeys.SCREEN_OFF_RECORD_ENABLED,
                    ConfigConstants.DEF_SCREEN_OFF_RECORD_ENABLED
                ),
            segmentDurationMin =
                prefs.getLong(
                    ServerSettingKeys.SEGMENT_DURATION_MIN,
                    ConfigConstants.DEF_SEGMENT_DURATION_MIN
                ),
            maxHistoryDays =
                prefs.getLong(
                    ServerSettingKeys.MAX_HISTORY_DAYS,
                    ConfigConstants.DEF_LOG_MAX_HISTORY_DAYS
                ),
            logLevelPriority =
                prefs.getInt(
                    ServerSettingKeys.LOG_LEVEL,
                    encodeLogLevel(ConfigConstants.DEF_LOG_LEVEL)
                ),
            alwaysPollingScreenStatusEnabled =
                prefs.getBoolean(
                    ServerSettingKeys.ALWAYS_POLLING_SCREEN_STATUS_ENABLED,
                    ConfigConstants.DEF_ALWAYS_POLLING_SCREEN_STATUS_ENABLED
                )
        )

    /** 核心规范化入口，显式字段最终都走这里。 */
    fun normalizeServerSettings(
        recordIntervalMs: Long = ConfigConstants.DEF_RECORD_INTERVAL_MS,
        batchSize: Int = ConfigConstants.DEF_BATCH_SIZE,
        writeLatencyMs: Long = ConfigConstants.DEF_WRITE_LATENCY_MS,
        screenOffRecordEnabled: Boolean = ConfigConstants.DEF_SCREEN_OFF_RECORD_ENABLED,
        segmentDurationMin: Long = ConfigConstants.DEF_SEGMENT_DURATION_MIN,
        maxHistoryDays: Long = ConfigConstants.DEF_LOG_MAX_HISTORY_DAYS,
        logLevel: LoggerX.LogLevel = ConfigConstants.DEF_LOG_LEVEL,
        alwaysPollingScreenStatusEnabled: Boolean =
            ConfigConstants.DEF_ALWAYS_POLLING_SCREEN_STATUS_ENABLED
    ): ServerSettings =
        ServerSettings(
            recordIntervalMs = normalizeRecordIntervalMs(recordIntervalMs),
            batchSize = normalizeBatchSize(batchSize),
            writeLatencyMs = normalizeWriteLatencyMs(writeLatencyMs),
            screenOffRecordEnabled = screenOffRecordEnabled,
            segmentDurationMin = normalizeSegmentDurationMin(segmentDurationMin),
            maxHistoryDays = normalizeMaxHistoryDays(maxHistoryDays),
            logLevel = logLevel,
            alwaysPollingScreenStatusEnabled = alwaysPollingScreenStatusEnabled
        )

    /** 存储层原始值适配入口，负责把 SharedPreferences、XML 读出的值接到核心规范化入口。 */
    fun serverSettingsFromStoredValues(
        recordIntervalMs: Long? = null,
        batchSize: Int? = null,
        writeLatencyMs: Long? = null,
        screenOffRecordEnabled: Boolean? = null,
        segmentDurationMin: Long? = null,
        maxHistoryDays: Long? = null,
        logLevelPriority: Int? = null,
        alwaysPollingScreenStatusEnabled: Boolean? = null
    ): ServerSettings =
        normalizeServerSettings(
            recordIntervalMs = recordIntervalMs ?: ConfigConstants.DEF_RECORD_INTERVAL_MS,
            batchSize = batchSize ?: ConfigConstants.DEF_BATCH_SIZE,
            writeLatencyMs = writeLatencyMs ?: ConfigConstants.DEF_WRITE_LATENCY_MS,
            screenOffRecordEnabled =
                screenOffRecordEnabled ?: ConfigConstants.DEF_SCREEN_OFF_RECORD_ENABLED,
            segmentDurationMin = segmentDurationMin ?: ConfigConstants.DEF_SEGMENT_DURATION_MIN,
            maxHistoryDays = maxHistoryDays ?: ConfigConstants.DEF_LOG_MAX_HISTORY_DAYS,
            logLevel =
                decodeLogLevel(
                    logLevelPriority ?: encodeLogLevel(ConfigConstants.DEF_LOG_LEVEL)
                ),
            alwaysPollingScreenStatusEnabled =
                alwaysPollingScreenStatusEnabled
                    ?: ConfigConstants.DEF_ALWAYS_POLLING_SCREEN_STATUS_ENABLED
        )

    /** 对象重载仅用于已有 ServerSettings 重新套用同一套规则，避免调用方手动拆字段。 */
    fun normalizeServerSettings(settings: ServerSettings): ServerSettings =
        normalizeServerSettings(
            recordIntervalMs = settings.recordIntervalMs,
            batchSize = settings.batchSize,
            writeLatencyMs = settings.writeLatencyMs,
            screenOffRecordEnabled = settings.screenOffRecordEnabled,
            segmentDurationMin = settings.segmentDurationMin,
            maxHistoryDays = settings.maxHistoryDays,
            logLevel = settings.logLevel,
            alwaysPollingScreenStatusEnabled = settings.alwaysPollingScreenStatusEnabled
        )

    fun writeAppSettings(prefs: SharedPreferences, settings: AppSettings) {
        val editor = prefs.edit()
        editor.writeAppSettings(settings)
        editor.apply()
    }

    fun writeServerSettings(prefs: SharedPreferences, settings: ServerSettings) {
        val editor = prefs.edit()
        editor.writeServerSettings(settings)
        editor.apply()
    }

    fun Editor.writeAppSettings(settings: AppSettings) {
        putBoolean(AppSettingKeys.CHECK_UPDATE_ON_STARTUP, settings.checkUpdateOnStartup)
        putBoolean(AppSettingKeys.DUAL_CELL_ENABLED, settings.dualCellEnabled)
        putBoolean(
            AppSettingKeys.DISCHARGE_DISPLAY_POSITIVE,
            settings.dischargeDisplayPositive
        )
        putInt(
            AppSettingKeys.CALIBRATION_VALUE,
            normalizeCalibrationValue(settings.calibrationValue)
        )
        putBoolean(
            AppSettingKeys.ROOT_BOOT_AUTO_START_ENABLED,
            settings.rootBootAutoStartEnabled
        )
    }

    fun Editor.writeServerSettings(settings: ServerSettings) {
        val normalized = normalizeServerSettings(settings)
        putLong(
            ServerSettingKeys.RECORD_INTERVAL_MS,
            normalized.recordIntervalMs
        )
        putInt(ServerSettingKeys.BATCH_SIZE, normalized.batchSize)
        putLong(
            ServerSettingKeys.WRITE_LATENCY_MS,
            normalized.writeLatencyMs
        )
        putBoolean(
            ServerSettingKeys.SCREEN_OFF_RECORD_ENABLED,
            normalized.screenOffRecordEnabled
        )
        putLong(
            ServerSettingKeys.SEGMENT_DURATION_MIN,
            normalized.segmentDurationMin
        )
        putLong(
            ServerSettingKeys.MAX_HISTORY_DAYS,
            normalized.maxHistoryDays
        )
        putInt(ServerSettingKeys.LOG_LEVEL, encodeLogLevel(normalized.logLevel))
        putBoolean(
            ServerSettingKeys.ALWAYS_POLLING_SCREEN_STATUS_ENABLED,
            normalized.alwaysPollingScreenStatusEnabled
        )
    }

    fun normalizeCalibrationValue(value: Int): Int =
        value.coerceIn(
            ConfigConstants.MIN_CALIBRATION_VALUE,
            ConfigConstants.MAX_CALIBRATION_VALUE
        )

    fun normalizeSceneStatsRecentFileCount(value: Int): Int =
        value.coerceIn(
            ConfigConstants.MIN_SCENE_STATS_RECENT_FILE_COUNT,
            ConfigConstants.MAX_SCENE_STATS_RECENT_FILE_COUNT
        )

    fun normalizePredCurrentSessionWeightMaxX100(value: Int): Int =
        value.coerceIn(
            ConfigConstants.MIN_PRED_CURRENT_SESSION_WEIGHT_MAX_X100,
            ConfigConstants.MAX_PRED_CURRENT_SESSION_WEIGHT_MAX_X100
        )

    fun normalizePredCurrentSessionWeightHalfLifeMin(value: Long): Long =
        value.coerceIn(
            ConfigConstants.MIN_PRED_CURRENT_SESSION_WEIGHT_HALF_LIFE_MIN,
            ConfigConstants.MAX_PRED_CURRENT_SESSION_WEIGHT_HALF_LIFE_MIN
        )

    fun normalizeRecordIntervalMs(value: Long): Long =
        value.coerceIn(
            ConfigConstants.MIN_RECORD_INTERVAL_MS,
            ConfigConstants.MAX_RECORD_INTERVAL_MS
        )

    fun normalizeBatchSize(value: Int): Int =
        value.coerceIn(ConfigConstants.MIN_BATCH_SIZE, ConfigConstants.MAX_BATCH_SIZE)

    fun normalizeWriteLatencyMs(value: Long): Long =
        value.coerceIn(
            ConfigConstants.MIN_WRITE_LATENCY_MS,
            ConfigConstants.MAX_WRITE_LATENCY_MS
        )

    fun normalizeSegmentDurationMin(value: Long): Long =
        value.coerceIn(
            ConfigConstants.MIN_SEGMENT_DURATION_MIN,
            ConfigConstants.MAX_SEGMENT_DURATION_MIN
        )

    fun normalizeMaxHistoryDays(value: Long): Long =
        value.coerceAtLeast(ConfigConstants.MIN_LOG_MAX_HISTORY_DAYS)

    fun encodeLogLevel(value: LoggerX.LogLevel): Int = value.priority

    fun decodeLogLevel(value: Int): LoggerX.LogLevel = LoggerX.LogLevel.fromPriority(value)
}

object ServerSettingsMapper {
    fun toConfig(settings: ServerSettings): Config =
        Config(
            recordIntervalMs = settings.recordIntervalMs,
            batchSize = settings.batchSize,
            writeLatencyMs = settings.writeLatencyMs,
            screenOffRecordEnabled = settings.screenOffRecordEnabled,
            segmentDurationMin = settings.segmentDurationMin,
            maxHistoryDays = settings.maxHistoryDays,
            logLevel = settings.logLevel,
            alwaysPollingScreenStatusEnabled = settings.alwaysPollingScreenStatusEnabled
        )

    fun fromConfig(config: Config): ServerSettings =
        SharedSettings.normalizeServerSettings(
            recordIntervalMs = config.recordIntervalMs,
            batchSize = config.batchSize,
            writeLatencyMs = config.writeLatencyMs,
            screenOffRecordEnabled = config.screenOffRecordEnabled,
            segmentDurationMin = config.segmentDurationMin,
            maxHistoryDays = config.maxHistoryDays,
            logLevel = config.logLevel,
            alwaysPollingScreenStatusEnabled = config.alwaysPollingScreenStatusEnabled
        )
}
