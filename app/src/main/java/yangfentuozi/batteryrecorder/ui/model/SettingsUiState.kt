package yangfentuozi.batteryrecorder.ui.model

import yangfentuozi.batteryrecorder.shared.config.ConfigConstants

data class SettingsUiState(
    val checkUpdateOnStartup: Boolean = ConfigConstants.DEF_CHECK_UPDATE_ON_STARTUP,
    val dualCellEnabled: Boolean = ConfigConstants.DEF_DUAL_CELL_ENABLED,
    val dischargeDisplayPositive: Boolean = ConfigConstants.DEF_DISCHARGE_DISPLAY_POSITIVE,
    val calibrationValue: Int = ConfigConstants.DEF_CALIBRATION_VALUE,
    val recordIntervalMs: Long = ConfigConstants.DEF_RECORD_INTERVAL_MS,
    val writeLatencyMs: Long = ConfigConstants.DEF_WRITE_LATENCY_MS,
    val batchSize: Int = ConfigConstants.DEF_BATCH_SIZE,
    val recordScreenOffEnabled: Boolean = ConfigConstants.DEF_SCREEN_OFF_RECORD_ENABLED,
    val alwaysPollingScreenStatusEnabled: Boolean = ConfigConstants.DEF_ALWAYS_POLLING_SCREEN_STATUS_ENABLED,
    val segmentDurationMin: Long = ConfigConstants.DEF_SEGMENT_DURATION_MIN,
    val rootBootAutoStartEnabled: Boolean = ConfigConstants.DEF_ROOT_BOOT_AUTO_START_ENABLED,
    val gamePackages: Set<String> = emptySet(),
    val gameBlacklist: Set<String> = emptySet(),
    val sceneStatsRecentFileCount: Int = ConfigConstants.DEF_SCENE_STATS_RECENT_FILE_COUNT,
    val predCurrentSessionWeightEnabled: Boolean = ConfigConstants.DEF_PRED_CURRENT_SESSION_WEIGHT_ENABLED,
    val predCurrentSessionWeightMaxX100: Int = ConfigConstants.DEF_PRED_CURRENT_SESSION_WEIGHT_MAX_X100,
    val predCurrentSessionWeightHalfLifeMin: Long = ConfigConstants.DEF_PRED_CURRENT_SESSION_WEIGHT_HALF_LIFE_MIN
)
