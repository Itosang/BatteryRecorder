package yangfentuozi.batteryrecorder.shared.config

import yangfentuozi.batteryrecorder.shared.util.LoggerX

object SettingsConstants {
    const val PREFS_NAME = "app_settings"

    private val logLevelConverter =
        object : EnumConfigConverter<LoggerX.LogLevel> {
            override fun fromValue(value: Int): LoggerX.LogLevel? =
                LoggerX.LogLevel.entries.firstOrNull { it.priority == value }

            override fun toValue(value: LoggerX.LogLevel): Int = value.priority
        }

    // server
    /** 记录间隔（毫秒） */
    val recordIntervalMs =
        LongConfigItem(
            key = "record_interval_ms",
            def = 1000L,
            min = 100L,
            max = 60_000L
        )

    /** 单批次写入数量 */
    val batchSize =
        IntConfigItem(
            key = "batch_size",
            def = 200,
            min = 0,
            max = 1000
        )

    /** 写入延迟（毫秒） */
    val writeLatencyMs =
        LongConfigItem(
            key = "write_latency_ms",
            def = 30_000L,
            min = 100L,
            max = 60_000L
        )

    /** 息屏时是否继续记录 */
    val screenOffRecordEnabled =
        BooleanConfigItem(
            key = "screen_off_record_enabled",
            def = true
        )

    /** 数据分段时长（分钟） */
    val segmentDurationMin =
        LongConfigItem(
            key = "segment_duration_min",
            def = 1440L,
            min = 0L,
            max = 2880L
        )

    /** 轮询检查息屏状态 */
    val alwaysPollingScreenStatusEnabled =
        BooleanConfigItem(
            key = "always_polling_screen_status_enabled",
            def = false
        )

    /** 开机后尝试 ROOT 自启动 */
    val rootBootAutoStartEnabled =
        BooleanConfigItem(
            key = "root_boot_auto_start_enabled",
            def = false
        )

    val rootBootAutoStartLastBootCount =
        IntConfigItem(
            key = "root_boot_auto_start_last_boot_count",
            def = -1,
            min = Int.MIN_VALUE,
            max = Int.MAX_VALUE
        )

    // app
    /** 是否启用双电芯模式 */
    val dualCellEnabled =
        BooleanConfigItem(
            key = "dual_cell_enabled",
            def = false
        )

    /** 放电电流显示为正值 */
    val dischargeDisplayPositive =
        BooleanConfigItem(
            key = "discharge_display_positive",
            def = true
        )

    /** 游戏 App 包名列表（高负载排除） */
    val gamePackages =
        StringSetConfigItem(
            key = "game_packages"
        )

    /** 用户主动排除的非游戏包名（自动检测时跳过） */
    val gameBlacklist =
        StringSetConfigItem(
            key = "game_blacklist"
        )

    /** 场景统计与预测使用的最近放电文件数量 */
    val sceneStatsRecentFileCount =
        IntConfigItem(
            key = "scene_stats_recent_file_count",
            def = 20,
            min = 5,
            max = 100
        )

    /** 校准值 */
    val calibrationValue =
        IntConfigItem(
            key = "calibration_value",
            def = -1,
            min = -100_000_000,
            max = 100_000_000
        )

    /** 启动时检测更新 */
    val checkUpdateOnStartup =
        BooleanConfigItem(
            key = "check_update_on_startup",
            def = true
        )

    /** 是否启用“当次记录加权”续航预测 */
    val predCurrentSessionWeightEnabled =
        BooleanConfigItem(
            key = "pred_current_session_weight_enabled",
            def = true
        )

    /** 当次记录加权最大倍率（x100 存储，例如 300 表示 3.00x） */
    val predCurrentSessionWeightMaxX100 =
        IntConfigItem(
            key = "pred_current_session_weight_max_x100",
            def = 300,
            min = 100,
            max = 500
        )

    /** 当次记录加权半衰期（分钟） */
    val predCurrentSessionWeightHalfLifeMin =
        LongConfigItem(
            key = "pred_current_session_weight_half_life_min",
            def = 30L,
            min = 5L,
            max = 60L
        )

    // common
    /** 日志保留天数 */
    val logMaxHistoryDays =
        LongConfigItem(
            key = "log_max_history_days",
            def = 7L,
            min = 1L,
            max = Long.MAX_VALUE
        )

    val logLevel =
        EnumConfigItem(
            key = "log_level",
            def = LoggerX.LogLevel.Info,
            converter = logLevelConverter
        )
}
