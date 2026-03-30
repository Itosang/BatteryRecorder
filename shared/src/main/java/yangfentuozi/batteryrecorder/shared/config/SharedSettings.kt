package yangfentuozi.batteryrecorder.shared.config

import android.content.Context
import android.content.SharedPreferences
import android.content.SharedPreferences.Editor
import yangfentuozi.batteryrecorder.shared.util.LoggerX

data class AppSettings(
    val checkUpdateOnStartup: Boolean = SettingsConstants.checkUpdateOnStartup.def,
    val dualCellEnabled: Boolean = SettingsConstants.dualCellEnabled.def,
    val dischargeDisplayPositive: Boolean = SettingsConstants.dischargeDisplayPositive.def,
    val calibrationValue: Int = SettingsConstants.calibrationValue.def,
    val rootBootAutoStartEnabled: Boolean = SettingsConstants.rootBootAutoStartEnabled.def
)

data class StatisticsSettings(
    val gamePackages: Set<String> = emptySet(),
    val gameBlacklist: Set<String> = emptySet(),
    val sceneStatsRecentFileCount: Int = SettingsConstants.sceneStatsRecentFileCount.def,
    val predCurrentSessionWeightEnabled: Boolean =
        SettingsConstants.predCurrentSessionWeightEnabled.def,
    val predCurrentSessionWeightMaxX100: Int =
        SettingsConstants.predCurrentSessionWeightMaxX100.def,
    val predCurrentSessionWeightHalfLifeMin: Long =
        SettingsConstants.predCurrentSessionWeightHalfLifeMin.def
)

data class ServerSettings(
    val recordIntervalMs: Long = SettingsConstants.recordIntervalMs.def,
    val batchSize: Int = SettingsConstants.batchSize.def,
    val writeLatencyMs: Long = SettingsConstants.writeLatencyMs.def,
    val screenOffRecordEnabled: Boolean = SettingsConstants.screenOffRecordEnabled.def,
    val segmentDurationMin: Long = SettingsConstants.segmentDurationMin.def,
    val maxHistoryDays: Long = SettingsConstants.logMaxHistoryDays.def,
    val logLevel: LoggerX.LogLevel = SettingsConstants.logLevel.def,
    val alwaysPollingScreenStatusEnabled: Boolean =
        SettingsConstants.alwaysPollingScreenStatusEnabled.def
)

object SharedSettings {
    fun getPreferences(context: Context): SharedPreferences =
        context.getSharedPreferences(SettingsConstants.PREFS_NAME, Context.MODE_PRIVATE)

    fun readAppSettings(context: Context): AppSettings = readAppSettings(getPreferences(context))

    fun readAppSettings(prefs: SharedPreferences): AppSettings =
        AppSettings(
            checkUpdateOnStartup = SettingsConstants.checkUpdateOnStartup.readFromSP(prefs),
            dualCellEnabled = SettingsConstants.dualCellEnabled.readFromSP(prefs),
            dischargeDisplayPositive = SettingsConstants.dischargeDisplayPositive.readFromSP(prefs),
            calibrationValue = SettingsConstants.calibrationValue.readFromSP(prefs),
            rootBootAutoStartEnabled = SettingsConstants.rootBootAutoStartEnabled.readFromSP(prefs)
        )

    fun readStatisticsSettings(context: Context): StatisticsSettings =
        readStatisticsSettings(getPreferences(context))

    fun readStatisticsSettings(prefs: SharedPreferences): StatisticsSettings =
        StatisticsSettings(
            gamePackages = SettingsConstants.gamePackages.readFromSP(prefs),
            gameBlacklist = SettingsConstants.gameBlacklist.readFromSP(prefs),
            sceneStatsRecentFileCount = SettingsConstants.sceneStatsRecentFileCount.readFromSP(prefs),
            predCurrentSessionWeightEnabled =
                SettingsConstants.predCurrentSessionWeightEnabled.readFromSP(prefs),
            predCurrentSessionWeightMaxX100 =
                SettingsConstants.predCurrentSessionWeightMaxX100.readFromSP(prefs),
            predCurrentSessionWeightHalfLifeMin =
                SettingsConstants.predCurrentSessionWeightHalfLifeMin.readFromSP(prefs)
        )

    fun readServerSettings(context: Context): ServerSettings =
        readServerSettings(getPreferences(context))

    fun readServerSettings(prefs: SharedPreferences): ServerSettings =
        serverSettingsFromStoredValues(
            recordIntervalMs = SettingsConstants.recordIntervalMs.readFromSP(prefs),
            batchSize = SettingsConstants.batchSize.readFromSP(prefs),
            writeLatencyMs = SettingsConstants.writeLatencyMs.readFromSP(prefs),
            screenOffRecordEnabled = SettingsConstants.screenOffRecordEnabled.readFromSP(prefs),
            segmentDurationMin = SettingsConstants.segmentDurationMin.readFromSP(prefs),
            maxHistoryDays = SettingsConstants.logMaxHistoryDays.readFromSP(prefs),
            logLevelPriority = encodeLogLevel(SettingsConstants.logLevel.readFromSP(prefs)),
            alwaysPollingScreenStatusEnabled =
                SettingsConstants.alwaysPollingScreenStatusEnabled.readFromSP(prefs)
        )

    /** 核心规范化入口，显式字段最终都走这里。 */
    fun normalizeServerSettings(
        recordIntervalMs: Long = SettingsConstants.recordIntervalMs.def,
        batchSize: Int = SettingsConstants.batchSize.def,
        writeLatencyMs: Long = SettingsConstants.writeLatencyMs.def,
        screenOffRecordEnabled: Boolean = SettingsConstants.screenOffRecordEnabled.def,
        segmentDurationMin: Long = SettingsConstants.segmentDurationMin.def,
        maxHistoryDays: Long = SettingsConstants.logMaxHistoryDays.def,
        logLevel: LoggerX.LogLevel = SettingsConstants.logLevel.def,
        alwaysPollingScreenStatusEnabled: Boolean =
            SettingsConstants.alwaysPollingScreenStatusEnabled.def
    ): ServerSettings =
        ServerSettings(
            recordIntervalMs = SettingsConstants.recordIntervalMs.coerce(recordIntervalMs),
            batchSize = SettingsConstants.batchSize.coerce(batchSize),
            writeLatencyMs = SettingsConstants.writeLatencyMs.coerce(writeLatencyMs),
            screenOffRecordEnabled = screenOffRecordEnabled,
            segmentDurationMin = SettingsConstants.segmentDurationMin.coerce(segmentDurationMin),
            maxHistoryDays = SettingsConstants.logMaxHistoryDays.coerce(maxHistoryDays),
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
            recordIntervalMs = recordIntervalMs ?: SettingsConstants.recordIntervalMs.def,
            batchSize = batchSize ?: SettingsConstants.batchSize.def,
            writeLatencyMs = writeLatencyMs ?: SettingsConstants.writeLatencyMs.def,
            screenOffRecordEnabled =
                screenOffRecordEnabled ?: SettingsConstants.screenOffRecordEnabled.def,
            segmentDurationMin = segmentDurationMin ?: SettingsConstants.segmentDurationMin.def,
            maxHistoryDays = maxHistoryDays ?: SettingsConstants.logMaxHistoryDays.def,
            logLevel =
                decodeLogLevel(logLevelPriority ?: encodeLogLevel(SettingsConstants.logLevel.def)),
            alwaysPollingScreenStatusEnabled =
                alwaysPollingScreenStatusEnabled
                    ?: SettingsConstants.alwaysPollingScreenStatusEnabled.def
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
        SettingsConstants.checkUpdateOnStartup.writeToSP(this, settings.checkUpdateOnStartup)
        SettingsConstants.dualCellEnabled.writeToSP(this, settings.dualCellEnabled)
        SettingsConstants.dischargeDisplayPositive.writeToSP(this, settings.dischargeDisplayPositive)
        SettingsConstants.calibrationValue.writeToSP(this, settings.calibrationValue)
        SettingsConstants.rootBootAutoStartEnabled.writeToSP(this, settings.rootBootAutoStartEnabled)
    }

    fun Editor.writeServerSettings(settings: ServerSettings) {
        val normalized = normalizeServerSettings(settings)
        SettingsConstants.recordIntervalMs.writeToSP(this, normalized.recordIntervalMs)
        SettingsConstants.batchSize.writeToSP(this, normalized.batchSize)
        SettingsConstants.writeLatencyMs.writeToSP(this, normalized.writeLatencyMs)
        SettingsConstants.screenOffRecordEnabled.writeToSP(this, normalized.screenOffRecordEnabled)
        SettingsConstants.segmentDurationMin.writeToSP(this, normalized.segmentDurationMin)
        SettingsConstants.logMaxHistoryDays.writeToSP(this, normalized.maxHistoryDays)
        SettingsConstants.logLevel.writeToSP(this, normalized.logLevel)
        SettingsConstants.alwaysPollingScreenStatusEnabled.writeToSP(
            this,
            normalized.alwaysPollingScreenStatusEnabled
        )
    }

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
