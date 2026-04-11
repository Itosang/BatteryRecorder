package yangfentuozi.batteryrecorder.shared.config

import yangfentuozi.batteryrecorder.shared.config.dataclass.UpdateChannel
import yangfentuozi.batteryrecorder.shared.util.LoggerX

object SettingsConstants {
    const val PREFS_NAME = "app_settings"

    private val logLevelConverter =
        object : EnumConfigConverter<LoggerX.LogLevel> {
            override fun fromValue(value: Int): LoggerX.LogLevel? =
                LoggerX.LogLevel.entries.firstOrNull { it.priority == value }

            override fun toValue(value: LoggerX.LogLevel): Int = value.priority
        }

    private val updateChannelConverter =
        object : EnumConfigConverter<UpdateChannel> {
            override fun fromValue(value: Int): UpdateChannel? =
                UpdateChannel.fromPersistedValue(value)

            override fun toValue(value: UpdateChannel): Int = value.persistedValue
        }

    // server
    /** 是否启用实时功耗通知 */
    val notificationEnabled =
        BooleanConfigItem(
            key = "notification_enabled",
            def = false
        )

    /** 是否启用双电芯模式 */
    val dualCellEnabled =
        BooleanConfigItem(
            key = "dual_cell_enabled",
            def = false
        )

    /** 电流校准值，统一用于功率与 mAh 换算。 */
    val calibrationValue =
        IntConfigItem(
            key = "calibration_value",
            def = -1,
            min = -100_000_000,
            max = 100_000_000
        )

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

    /** 启动时检测更新 */
    val checkUpdateOnStartup =
        BooleanConfigItem(
            key = "check_update_on_startup",
            def = true
        )

    /** 启动更新检测通道 */
    val updateChannel =
        EnumConfigItem(
            key = "update_channel",
            def = UpdateChannel.Stable,
            converter = updateChannelConverter
        )

    /** 是否启用首页/应用预测使用的加权算法 */
    val predWeightedAlgorithmEnabled =
        BooleanConfigItem(
            key = "pred_weighted_algorithm_enabled",
            def = true
        )

    /** 首页预测里当前文件可影响结果的最大比例（百分比） */
    val predWeightedAlgorithmAlphaMaxX100 =
        IntConfigItem(
            key = "pred_weighted_algorithm_alpha_max_x100",
            def = 20,
            min = 0,
            max = 80
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
