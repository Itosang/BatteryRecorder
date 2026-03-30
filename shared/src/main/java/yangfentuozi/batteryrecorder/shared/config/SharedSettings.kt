package yangfentuozi.batteryrecorder.shared.config

import android.content.Context
import android.content.SharedPreferences
import android.content.SharedPreferences.Editor
import yangfentuozi.batteryrecorder.shared.config.dataclass.AppSettings
import yangfentuozi.batteryrecorder.shared.config.dataclass.ServerSettings
import yangfentuozi.batteryrecorder.shared.config.dataclass.StatisticsSettings
import yangfentuozi.batteryrecorder.shared.util.LoggerX

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
        normalizeServerSettings(
            recordIntervalMs = SettingsConstants.recordIntervalMs.readFromSP(prefs),
            batchSize = SettingsConstants.batchSize.readFromSP(prefs),
            writeLatencyMs = SettingsConstants.writeLatencyMs.readFromSP(prefs),
            screenOffRecordEnabled = SettingsConstants.screenOffRecordEnabled.readFromSP(prefs),
            segmentDurationMin = SettingsConstants.segmentDurationMin.readFromSP(prefs),
            maxHistoryDays = SettingsConstants.logMaxHistoryDays.readFromSP(prefs),
            logLevel = SettingsConstants.logLevel.readFromSP(prefs),
            alwaysPollingScreenStatusEnabled =
                SettingsConstants.alwaysPollingScreenStatusEnabled.readFromSP(prefs)
        )

    /**
     * 核心规范化入口，显式字段最终都走这里。
     * 用coerce，防止超出范围
     * */
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
        val normalized = normalizeServerSettings(
            recordIntervalMs = settings.recordIntervalMs,
            batchSize = settings.batchSize,
            writeLatencyMs = settings.writeLatencyMs,
            screenOffRecordEnabled = settings.screenOffRecordEnabled,
            segmentDurationMin = settings.segmentDurationMin,
            maxHistoryDays = settings.maxHistoryDays,
            logLevel = settings.logLevel,
            alwaysPollingScreenStatusEnabled = settings.alwaysPollingScreenStatusEnabled
        )
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
