package yangfentuozi.batteryrecorder.shared.config

import yangfentuozi.batteryrecorder.shared.util.LoggerX

object ConfigConstants {
    const val PREFS_NAME = "app_settings"

    // server
    /** 记录间隔（毫秒） */
    const val KEY_RECORD_INTERVAL_MS = "record_interval_ms"
    const val MIN_RECORD_INTERVAL_MS = 100L
    const val MAX_RECORD_INTERVAL_MS = 60_000L
    const val DEF_RECORD_INTERVAL_MS = 1000L

    /** 单批次写入数量 */
    const val KEY_BATCH_SIZE = "batch_size"
    const val MIN_BATCH_SIZE = 0
    const val MAX_BATCH_SIZE = 1000
    const val DEF_BATCH_SIZE = 200

    /** 写入延迟（毫秒） */
    const val KEY_WRITE_LATENCY_MS = "write_latency_ms"
    const val MIN_WRITE_LATENCY_MS = 100L
    const val MAX_WRITE_LATENCY_MS = 60_000L
    const val DEF_WRITE_LATENCY_MS = 30_000L

    /** 息屏时是否继续记录 */
    const val KEY_SCREEN_OFF_RECORD_ENABLED = "screen_off_record_enabled"
    const val DEF_SCREEN_OFF_RECORD_ENABLED = true

    /** 数据分段时长（分钟） */
    const val KEY_SEGMENT_DURATION_MIN = "segment_duration_min"
    const val MIN_SEGMENT_DURATION_MIN = 0L
    const val MAX_SEGMENT_DURATION_MIN = 2880L
    const val DEF_SEGMENT_DURATION_MIN = 1440L

    /** 轮询检查息屏状态 */
    const val KEY_ALWAYS_POLLING_SCREEN_STATUS_ENABLED = "always_polling_screen_status_enabled"
    const val DEF_ALWAYS_POLLING_SCREEN_STATUS_ENABLED = false

    /** 开机后尝试 ROOT 自启动 */
    const val KEY_ROOT_BOOT_AUTO_START_ENABLED = "root_boot_auto_start_enabled"
    const val DEF_ROOT_BOOT_AUTO_START_ENABLED = false
    const val KEY_ROOT_BOOT_AUTO_START_LAST_BOOT_COUNT = "root_boot_auto_start_last_boot_count"
    const val DEF_ROOT_BOOT_AUTO_START_LAST_BOOT_COUNT = -1

    // app
    /** 是否启用双电芯模式 */
    const val KEY_DUAL_CELL_ENABLED = "dual_cell_enabled"
    const val DEF_DUAL_CELL_ENABLED = false

    /** 放电电流显示为正值 */
    const val KEY_DISCHARGE_DISPLAY_POSITIVE = "discharge_display_positive"
    const val DEF_DISCHARGE_DISPLAY_POSITIVE = true

    /** 游戏 App 包名列表（高负载排除） */
    const val KEY_GAME_PACKAGES = "game_packages"

    /** 用户主动排除的非游戏包名（自动检测时跳过） */
    const val KEY_GAME_BLACKLIST = "game_blacklist"

    /** 场景统计与预测使用的最近放电文件数量 */
    const val KEY_SCENE_STATS_RECENT_FILE_COUNT = "scene_stats_recent_file_count"
    const val MIN_SCENE_STATS_RECENT_FILE_COUNT = 5
    const val MAX_SCENE_STATS_RECENT_FILE_COUNT = 100
    const val DEF_SCENE_STATS_RECENT_FILE_COUNT = 20

    /** 校准值 */
    const val KEY_CALIBRATION_VALUE = "calibration_value"
    const val MIN_CALIBRATION_VALUE = -100_000_000
    const val MAX_CALIBRATION_VALUE = 100_000_000
    const val DEF_CALIBRATION_VALUE = -1

    /** 启动时检测更新 */
    const val KEY_CHECK_UPDATE_ON_STARTUP = "check_update_on_startup"
    const val DEF_CHECK_UPDATE_ON_STARTUP = true

    /** 是否启用“当次记录加权”续航预测 */
    const val KEY_PRED_CURRENT_SESSION_WEIGHT_ENABLED = "pred_current_session_weight_enabled"
    const val DEF_PRED_CURRENT_SESSION_WEIGHT_ENABLED = true

    /** 当次记录加权最大倍率（x100 存储，例如 300 表示 3.00x） */
    const val KEY_PRED_CURRENT_SESSION_WEIGHT_MAX_X100 = "pred_current_session_weight_max_x100"
    const val MIN_PRED_CURRENT_SESSION_WEIGHT_MAX_X100 = 100
    const val MAX_PRED_CURRENT_SESSION_WEIGHT_MAX_X100 = 500
    const val DEF_PRED_CURRENT_SESSION_WEIGHT_MAX_X100 = 300

    /** 当次记录加权半衰期（分钟） */
    const val KEY_PRED_CURRENT_SESSION_WEIGHT_HALF_LIFE_MIN =
        "pred_current_session_weight_half_life_min"
    const val MIN_PRED_CURRENT_SESSION_WEIGHT_HALF_LIFE_MIN = 5L
    const val MAX_PRED_CURRENT_SESSION_WEIGHT_HALF_LIFE_MIN = 60L
    const val DEF_PRED_CURRENT_SESSION_WEIGHT_HALF_LIFE_MIN = 30L

    // common
    /** 日志保留天数 */
    const val KEY_LOG_MAX_HISTORY_DAYS = "log_max_history_days"
    const val MIN_LOG_MAX_HISTORY_DAYS = 1L
    const val DEF_LOG_MAX_HISTORY_DAYS = 7L

    const val KEY_LOG_LEVEL = "log_level"
    val DEF_LOG_LEVEL = LoggerX.LogLevel.Info
}
