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
        ServerSettings(
            recordIntervalMs = prefs.getLong(
                SettingsConstants.recordIntervalMs.key,
                SettingsConstants.recordIntervalMs.def
            ),
            batchSize = prefs.getInt(
                SettingsConstants.batchSize.key,
                SettingsConstants.batchSize.def
            ),
            writeLatencyMs = prefs.getLong(
                SettingsConstants.writeLatencyMs.key,
                SettingsConstants.writeLatencyMs.def
            ),
            screenOffRecordEnabled = prefs.getBoolean(
                SettingsConstants.screenOffRecordEnabled.key,
                SettingsConstants.screenOffRecordEnabled.def
            ),
            segmentDurationMin = prefs.getLong(
                SettingsConstants.segmentDurationMin.key,
                SettingsConstants.segmentDurationMin.def
            ),
            maxHistoryDays = prefs.getLong(
                SettingsConstants.logMaxHistoryDays.key,
                SettingsConstants.logMaxHistoryDays.def
            ),
            logLevel = decodeLogLevel(
                prefs.getInt(
                    SettingsConstants.logLevel.key,
                    encodeLogLevel(SettingsConstants.logLevel.def)
                )
            ),
            alwaysPollingScreenStatusEnabled = prefs.getBoolean(
                SettingsConstants.alwaysPollingScreenStatusEnabled.key,
                SettingsConstants.alwaysPollingScreenStatusEnabled.def
            )
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
        putLong(SettingsConstants.recordIntervalMs.key, settings.recordIntervalMs)
        putInt(SettingsConstants.batchSize.key, settings.batchSize)
        putLong(SettingsConstants.writeLatencyMs.key, settings.writeLatencyMs)
        putBoolean(SettingsConstants.screenOffRecordEnabled.key, settings.screenOffRecordEnabled)
        putLong(SettingsConstants.segmentDurationMin.key, settings.segmentDurationMin)
        putLong(SettingsConstants.logMaxHistoryDays.key, settings.maxHistoryDays)
        putInt(SettingsConstants.logLevel.key, encodeLogLevel(settings.logLevel))
        putBoolean(
            SettingsConstants.alwaysPollingScreenStatusEnabled.key,
            settings.alwaysPollingScreenStatusEnabled
        )
    }

    fun encodeLogLevel(value: LoggerX.LogLevel): Int = value.priority

    fun decodeLogLevel(value: Int): LoggerX.LogLevel = LoggerX.LogLevel.fromPriority(value)
}
