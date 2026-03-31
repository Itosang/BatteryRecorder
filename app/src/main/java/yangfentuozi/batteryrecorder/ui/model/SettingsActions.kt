package yangfentuozi.batteryrecorder.ui.model

import yangfentuozi.batteryrecorder.shared.util.LoggerX

data class CalibrationActions(
    val setDualCellEnabled: (Boolean) -> Unit,
    val setDischargeDisplayPositiveEnabled: (Boolean) -> Unit,
    val setCalibrationValue: (Int) -> Unit
)

data class ServerActions(
    val setRecordIntervalMs: (Long) -> Unit,
    val setWriteLatencyMs: (Long) -> Unit,
    val setBatchSize: (Int) -> Unit,
    val setScreenOffRecordEnabled: (Boolean) -> Unit,
    val setAlwaysPollingScreenStatusEnabled: (Boolean) -> Unit,
    val setSegmentDurationMin: (Long) -> Unit,
    val setRootBootAutoStartEnabled: (Boolean) -> Unit
)

data class LogActions(
    val setMaxHistoryDays: (Long) -> Unit,
    val setLogLevel: (LoggerX.LogLevel) -> Unit
)

data class PredictionActions(
    val setGamePackages: (Set<String>, Set<String>) -> Unit,
    val setSceneStatsRecentFileCount: (Int) -> Unit,
    val setPredWeightedAlgorithmEnabled: (Boolean) -> Unit,
    val setPredWeightedAlgorithmAlphaMaxX100: (Int) -> Unit
)

data class SettingsActions(
    val setCheckUpdateOnStartup: (Boolean) -> Unit,
    val calibration: CalibrationActions,
    val server: ServerActions,
    val log: LogActions,
    val prediction: PredictionActions
)
