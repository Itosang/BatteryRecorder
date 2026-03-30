package yangfentuozi.batteryrecorder.shared.config

import android.content.Context
import android.content.SharedPreferences
import android.content.SharedPreferences.Editor
import yangfentuozi.batteryrecorder.shared.util.LoggerX

object AppSettingKeys {
    const val CHECK_UPDATE_ON_STARTUP = SettingsConstants.KEY_CHECK_UPDATE_ON_STARTUP
    const val DUAL_CELL_ENABLED = SettingsConstants.KEY_DUAL_CELL_ENABLED
    const val DISCHARGE_DISPLAY_POSITIVE = SettingsConstants.KEY_DISCHARGE_DISPLAY_POSITIVE
    const val CALIBRATION_VALUE = SettingsConstants.KEY_CALIBRATION_VALUE
    const val ROOT_BOOT_AUTO_START_ENABLED = SettingsConstants.KEY_ROOT_BOOT_AUTO_START_ENABLED
}

object StatisticsSettingKeys {
    const val GAME_PACKAGES = SettingsConstants.KEY_GAME_PACKAGES
    const val GAME_BLACKLIST = SettingsConstants.KEY_GAME_BLACKLIST
    const val SCENE_STATS_RECENT_FILE_COUNT = SettingsConstants.KEY_SCENE_STATS_RECENT_FILE_COUNT
    const val PRED_CURRENT_SESSION_WEIGHT_ENABLED =
        SettingsConstants.KEY_PRED_CURRENT_SESSION_WEIGHT_ENABLED
    const val PRED_CURRENT_SESSION_WEIGHT_MAX_X100 =
        SettingsConstants.KEY_PRED_CURRENT_SESSION_WEIGHT_MAX_X100
    const val PRED_CURRENT_SESSION_WEIGHT_HALF_LIFE_MIN =
        SettingsConstants.KEY_PRED_CURRENT_SESSION_WEIGHT_HALF_LIFE_MIN
}

object ServerSettingKeys {
    const val RECORD_INTERVAL_MS = SettingsConstants.KEY_RECORD_INTERVAL_MS
    const val BATCH_SIZE = SettingsConstants.KEY_BATCH_SIZE
    const val WRITE_LATENCY_MS = SettingsConstants.KEY_WRITE_LATENCY_MS
    const val SCREEN_OFF_RECORD_ENABLED = SettingsConstants.KEY_SCREEN_OFF_RECORD_ENABLED
    const val SEGMENT_DURATION_MIN = SettingsConstants.KEY_SEGMENT_DURATION_MIN
    const val ALWAYS_POLLING_SCREEN_STATUS_ENABLED =
        SettingsConstants.KEY_ALWAYS_POLLING_SCREEN_STATUS_ENABLED
    const val MAX_HISTORY_DAYS = SettingsConstants.KEY_LOG_MAX_HISTORY_DAYS
    const val LOG_LEVEL = SettingsConstants.KEY_LOG_LEVEL
}

data class AppSettings(
    val checkUpdateOnStartup: Boolean = SettingsConstants.DEF_CHECK_UPDATE_ON_STARTUP,
    val dualCellEnabled: Boolean = SettingsConstants.DEF_DUAL_CELL_ENABLED,
    val dischargeDisplayPositive: Boolean = SettingsConstants.DEF_DISCHARGE_DISPLAY_POSITIVE,
    val calibrationValue: Int = SettingsConstants.DEF_CALIBRATION_VALUE,
    val rootBootAutoStartEnabled: Boolean = SettingsConstants.DEF_ROOT_BOOT_AUTO_START_ENABLED
)

data class StatisticsSettings(
    val gamePackages: Set<String> = emptySet(),
    val gameBlacklist: Set<String> = emptySet(),
    val sceneStatsRecentFileCount: Int = SettingsConstants.DEF_SCENE_STATS_RECENT_FILE_COUNT,
    val predCurrentSessionWeightEnabled: Boolean =
        SettingsConstants.DEF_PRED_CURRENT_SESSION_WEIGHT_ENABLED,
    val predCurrentSessionWeightMaxX100: Int =
        SettingsConstants.DEF_PRED_CURRENT_SESSION_WEIGHT_MAX_X100,
    val predCurrentSessionWeightHalfLifeMin: Long =
        SettingsConstants.DEF_PRED_CURRENT_SESSION_WEIGHT_HALF_LIFE_MIN
)

data class ServerSettings(
    val recordIntervalMs: Long = SettingsConstants.DEF_RECORD_INTERVAL_MS,
    val batchSize: Int = SettingsConstants.DEF_BATCH_SIZE,
    val writeLatencyMs: Long = SettingsConstants.DEF_WRITE_LATENCY_MS,
    val screenOffRecordEnabled: Boolean = SettingsConstants.DEF_SCREEN_OFF_RECORD_ENABLED,
    val segmentDurationMin: Long = SettingsConstants.DEF_SEGMENT_DURATION_MIN,
    val maxHistoryDays: Long = SettingsConstants.DEF_LOG_MAX_HISTORY_DAYS,
    val logLevel: LoggerX.LogLevel = SettingsConstants.DEF_LOG_LEVEL,
    val alwaysPollingScreenStatusEnabled: Boolean =
        SettingsConstants.DEF_ALWAYS_POLLING_SCREEN_STATUS_ENABLED
)

object SharedSettings {
    fun getPreferences(context: Context): SharedPreferences =
        context.getSharedPreferences(SettingsConstants.PREFS_NAME, Context.MODE_PRIVATE)

    fun readAppSettings(context: Context): AppSettings = readAppSettings(getPreferences(context))

    fun readAppSettings(prefs: SharedPreferences): AppSettings =
        AppSettings(
            checkUpdateOnStartup =
                prefs.getBoolean(
                    AppSettingKeys.CHECK_UPDATE_ON_STARTUP,
                    SettingsConstants.DEF_CHECK_UPDATE_ON_STARTUP
                ),
            dualCellEnabled =
                prefs.getBoolean(
                    AppSettingKeys.DUAL_CELL_ENABLED,
                    SettingsConstants.DEF_DUAL_CELL_ENABLED
                ),
            dischargeDisplayPositive =
                prefs.getBoolean(
                    AppSettingKeys.DISCHARGE_DISPLAY_POSITIVE,
                    SettingsConstants.DEF_DISCHARGE_DISPLAY_POSITIVE
                ),
            calibrationValue =
                normalizeCalibrationValue(
                    prefs.getInt(
                        AppSettingKeys.CALIBRATION_VALUE,
                        SettingsConstants.DEF_CALIBRATION_VALUE
                    )
                ),
            rootBootAutoStartEnabled =
                prefs.getBoolean(
                    AppSettingKeys.ROOT_BOOT_AUTO_START_ENABLED,
                    SettingsConstants.DEF_ROOT_BOOT_AUTO_START_ENABLED
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
                        SettingsConstants.DEF_SCENE_STATS_RECENT_FILE_COUNT
                    )
                ),
            predCurrentSessionWeightEnabled =
                prefs.getBoolean(
                    StatisticsSettingKeys.PRED_CURRENT_SESSION_WEIGHT_ENABLED,
                    SettingsConstants.DEF_PRED_CURRENT_SESSION_WEIGHT_ENABLED
                ),
            predCurrentSessionWeightMaxX100 =
                normalizePredCurrentSessionWeightMaxX100(
                    prefs.getInt(
                        StatisticsSettingKeys.PRED_CURRENT_SESSION_WEIGHT_MAX_X100,
                        SettingsConstants.DEF_PRED_CURRENT_SESSION_WEIGHT_MAX_X100
                    )
                ),
            predCurrentSessionWeightHalfLifeMin =
                normalizePredCurrentSessionWeightHalfLifeMin(
                    prefs.getLong(
                        StatisticsSettingKeys.PRED_CURRENT_SESSION_WEIGHT_HALF_LIFE_MIN,
                        SettingsConstants.DEF_PRED_CURRENT_SESSION_WEIGHT_HALF_LIFE_MIN
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
                    SettingsConstants.DEF_RECORD_INTERVAL_MS
                ),
            batchSize =
                prefs.getInt(ServerSettingKeys.BATCH_SIZE, SettingsConstants.DEF_BATCH_SIZE),
            writeLatencyMs =
                prefs.getLong(
                    ServerSettingKeys.WRITE_LATENCY_MS,
                    SettingsConstants.DEF_WRITE_LATENCY_MS
                ),
            screenOffRecordEnabled =
                prefs.getBoolean(
                    ServerSettingKeys.SCREEN_OFF_RECORD_ENABLED,
                    SettingsConstants.DEF_SCREEN_OFF_RECORD_ENABLED
                ),
            segmentDurationMin =
                prefs.getLong(
                    ServerSettingKeys.SEGMENT_DURATION_MIN,
                    SettingsConstants.DEF_SEGMENT_DURATION_MIN
                ),
            maxHistoryDays =
                prefs.getLong(
                    ServerSettingKeys.MAX_HISTORY_DAYS,
                    SettingsConstants.DEF_LOG_MAX_HISTORY_DAYS
                ),
            logLevelPriority =
                prefs.getInt(
                    ServerSettingKeys.LOG_LEVEL,
                    encodeLogLevel(SettingsConstants.DEF_LOG_LEVEL)
                ),
            alwaysPollingScreenStatusEnabled =
                prefs.getBoolean(
                    ServerSettingKeys.ALWAYS_POLLING_SCREEN_STATUS_ENABLED,
                    SettingsConstants.DEF_ALWAYS_POLLING_SCREEN_STATUS_ENABLED
                )
        )

    /** 核心规范化入口，显式字段最终都走这里。 */
    fun normalizeServerSettings(
        recordIntervalMs: Long = SettingsConstants.DEF_RECORD_INTERVAL_MS,
        batchSize: Int = SettingsConstants.DEF_BATCH_SIZE,
        writeLatencyMs: Long = SettingsConstants.DEF_WRITE_LATENCY_MS,
        screenOffRecordEnabled: Boolean = SettingsConstants.DEF_SCREEN_OFF_RECORD_ENABLED,
        segmentDurationMin: Long = SettingsConstants.DEF_SEGMENT_DURATION_MIN,
        maxHistoryDays: Long = SettingsConstants.DEF_LOG_MAX_HISTORY_DAYS,
        logLevel: LoggerX.LogLevel = SettingsConstants.DEF_LOG_LEVEL,
        alwaysPollingScreenStatusEnabled: Boolean =
            SettingsConstants.DEF_ALWAYS_POLLING_SCREEN_STATUS_ENABLED
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
            recordIntervalMs = recordIntervalMs ?: SettingsConstants.DEF_RECORD_INTERVAL_MS,
            batchSize = batchSize ?: SettingsConstants.DEF_BATCH_SIZE,
            writeLatencyMs = writeLatencyMs ?: SettingsConstants.DEF_WRITE_LATENCY_MS,
            screenOffRecordEnabled =
                screenOffRecordEnabled ?: SettingsConstants.DEF_SCREEN_OFF_RECORD_ENABLED,
            segmentDurationMin = segmentDurationMin ?: SettingsConstants.DEF_SEGMENT_DURATION_MIN,
            maxHistoryDays = maxHistoryDays ?: SettingsConstants.DEF_LOG_MAX_HISTORY_DAYS,
            logLevel =
                decodeLogLevel(
                    logLevelPriority ?: encodeLogLevel(SettingsConstants.DEF_LOG_LEVEL)
                ),
            alwaysPollingScreenStatusEnabled =
                alwaysPollingScreenStatusEnabled
                    ?: SettingsConstants.DEF_ALWAYS_POLLING_SCREEN_STATUS_ENABLED
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
            SettingsConstants.MIN_CALIBRATION_VALUE,
            SettingsConstants.MAX_CALIBRATION_VALUE
        )

    fun normalizeSceneStatsRecentFileCount(value: Int): Int =
        value.coerceIn(
            SettingsConstants.MIN_SCENE_STATS_RECENT_FILE_COUNT,
            SettingsConstants.MAX_SCENE_STATS_RECENT_FILE_COUNT
        )

    fun normalizePredCurrentSessionWeightMaxX100(value: Int): Int =
        value.coerceIn(
            SettingsConstants.MIN_PRED_CURRENT_SESSION_WEIGHT_MAX_X100,
            SettingsConstants.MAX_PRED_CURRENT_SESSION_WEIGHT_MAX_X100
        )

    fun normalizePredCurrentSessionWeightHalfLifeMin(value: Long): Long =
        value.coerceIn(
            SettingsConstants.MIN_PRED_CURRENT_SESSION_WEIGHT_HALF_LIFE_MIN,
            SettingsConstants.MAX_PRED_CURRENT_SESSION_WEIGHT_HALF_LIFE_MIN
        )

    fun normalizeRecordIntervalMs(value: Long): Long =
        value.coerceIn(
            SettingsConstants.MIN_RECORD_INTERVAL_MS,
            SettingsConstants.MAX_RECORD_INTERVAL_MS
        )

    fun normalizeBatchSize(value: Int): Int =
        value.coerceIn(SettingsConstants.MIN_BATCH_SIZE, SettingsConstants.MAX_BATCH_SIZE)

    fun normalizeWriteLatencyMs(value: Long): Long =
        value.coerceIn(
            SettingsConstants.MIN_WRITE_LATENCY_MS,
            SettingsConstants.MAX_WRITE_LATENCY_MS
        )

    fun normalizeSegmentDurationMin(value: Long): Long =
        value.coerceIn(
            SettingsConstants.MIN_SEGMENT_DURATION_MIN,
            SettingsConstants.MAX_SEGMENT_DURATION_MIN
        )

    fun normalizeMaxHistoryDays(value: Long): Long =
        value.coerceAtLeast(SettingsConstants.MIN_LOG_MAX_HISTORY_DAYS)

    fun encodeLogLevel(value: LoggerX.LogLevel): Int = value.priority

    fun decodeLogLevel(value: Int): LoggerX.LogLevel = LoggerX.LogLevel.fromPriority(value)
}

object ServerSettingsMapper {
    fun toServerConfigDto(settings: ServerSettings): ServerConfigDto =
        ServerConfigDto(
            recordIntervalMs = settings.recordIntervalMs,
            batchSize = settings.batchSize,
            writeLatencyMs = settings.writeLatencyMs,
            screenOffRecordEnabled = settings.screenOffRecordEnabled,
            segmentDurationMin = settings.segmentDurationMin,
            maxHistoryDays = settings.maxHistoryDays,
            logLevel = settings.logLevel,
            alwaysPollingScreenStatusEnabled = settings.alwaysPollingScreenStatusEnabled
        )

    fun fromServerConfigDto(serverConfigDto: ServerConfigDto): ServerSettings =
        SharedSettings.normalizeServerSettings(
            recordIntervalMs = serverConfigDto.recordIntervalMs,
            batchSize = serverConfigDto.batchSize,
            writeLatencyMs = serverConfigDto.writeLatencyMs,
            screenOffRecordEnabled = serverConfigDto.screenOffRecordEnabled,
            segmentDurationMin = serverConfigDto.segmentDurationMin,
            maxHistoryDays = serverConfigDto.maxHistoryDays,
            logLevel = serverConfigDto.logLevel,
            alwaysPollingScreenStatusEnabled = serverConfigDto.alwaysPollingScreenStatusEnabled
        )
}
