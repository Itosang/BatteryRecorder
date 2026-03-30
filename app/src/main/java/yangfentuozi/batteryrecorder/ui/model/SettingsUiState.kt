package yangfentuozi.batteryrecorder.ui.model

import yangfentuozi.batteryrecorder.shared.config.AppSettings
import yangfentuozi.batteryrecorder.shared.config.ServerSettings
import yangfentuozi.batteryrecorder.shared.config.StatisticsSettings
import yangfentuozi.batteryrecorder.shared.util.LoggerX

data class SettingsScreenState(
    val checkUpdateOnStartup: Boolean = AppSettings().checkUpdateOnStartup,
    val dualCellEnabled: Boolean = AppSettings().dualCellEnabled,
    val dischargeDisplayPositive: Boolean = AppSettings().dischargeDisplayPositive,
    val calibrationValue: Int = AppSettings().calibrationValue,
    val recordIntervalMs: Long = ServerSettings().recordIntervalMs,
    val writeLatencyMs: Long = ServerSettings().writeLatencyMs,
    val batchSize: Int = ServerSettings().batchSize,
    val recordScreenOffEnabled: Boolean = ServerSettings().screenOffRecordEnabled,
    val alwaysPollingScreenStatusEnabled: Boolean = ServerSettings().alwaysPollingScreenStatusEnabled,
    val segmentDurationMin: Long = ServerSettings().segmentDurationMin,
    val rootBootAutoStartEnabled: Boolean = AppSettings().rootBootAutoStartEnabled,
    val maxHistoryDays: Long = ServerSettings().maxHistoryDays,
    val logLevel: LoggerX.LogLevel = ServerSettings().logLevel,
    val gamePackages: Set<String> = StatisticsSettings().gamePackages,
    val gameBlacklist: Set<String> = StatisticsSettings().gameBlacklist,
    val sceneStatsRecentFileCount: Int = StatisticsSettings().sceneStatsRecentFileCount,
    val predCurrentSessionWeightEnabled: Boolean = StatisticsSettings().predCurrentSessionWeightEnabled,
    val predCurrentSessionWeightMaxX100: Int = StatisticsSettings().predCurrentSessionWeightMaxX100,
    val predCurrentSessionWeightHalfLifeMin: Long = StatisticsSettings().predCurrentSessionWeightHalfLifeMin
)

typealias SettingsUiState = SettingsScreenState
