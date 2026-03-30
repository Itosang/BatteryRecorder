package yangfentuozi.batteryrecorder.ui.model

import yangfentuozi.batteryrecorder.shared.config.dataclass.AppSettings
import yangfentuozi.batteryrecorder.shared.config.dataclass.ServerSettings
import yangfentuozi.batteryrecorder.shared.config.dataclass.StatisticsSettings
import yangfentuozi.batteryrecorder.shared.util.LoggerX

/**
 * 设置页展示层使用的状态投影。
 *
 * 这里的语义更接近 `SettingsScreenState`：它服务于设置页渲染、交互回显和分组展示，
 * 不是设置项的真值源，也不负责定义配置持久化边界。各字段最终仍分别来自
 * [AppSettings]、[StatisticsSettings]、[ServerSettings]，设置页只是把三类来源摊平成一份
 * 可直接渲染的状态，避免界面层同时依赖多份配置对象。
 *
 * 类名仍保留 `SettingsUiState`，原因是设置页现有 ViewModel、Screen 与相关命名都围绕
 * `UiState` 组织；在没有重构整条设置页命名链路的前提下，继续沿用该名称可以保持语义一致，
 * 减少无收益的重命名扩散。
 *
 * 约束：
 * - 这里的默认值仅用于构造设置页初始展示态，不表示单一真实配置源。
 * - 新增字段时，需要先确认它属于哪一类设置来源，再决定是否纳入这份展示投影。
 */
data class SettingsUiState(
    // AppSettings：设置页中由应用进程本地消费的展示偏好与启动行为。
    /** 启动时检测更新 */
    val checkUpdateOnStartup: Boolean = AppSettings().checkUpdateOnStartup,
    /** 串联双电芯 */
    val dualCellEnabled: Boolean = AppSettings().dualCellEnabled,
    /** 放电也显示正值 */
    val dischargeDisplayPositive: Boolean = AppSettings().dischargeDisplayPositive,
    /** 电流单位校准 */
    val calibrationValue: Int = AppSettings().calibrationValue,
    // ServerSettings：传递给记录服务的运行参数，控制采样、写盘、分段与日志行为。
    /** 采样间隔 */
    val recordIntervalMs: Long = ServerSettings().recordIntervalMs,
    /** 写入延迟 */
    val writeLatencyMs: Long = ServerSettings().writeLatencyMs,
    /** 批量大小 */
    val batchSize: Int = ServerSettings().batchSize,
    /** 息屏记录 */
    val recordScreenOffEnabled: Boolean = ServerSettings().screenOffRecordEnabled,
    /** 轮询获取息屏状态 */
    val alwaysPollingScreenStatusEnabled: Boolean = ServerSettings().alwaysPollingScreenStatusEnabled,
    /** 自动分段时间 */
    val segmentDurationMin: Long = ServerSettings().segmentDurationMin,
    /** 开机自启 */
    val rootBootAutoStartEnabled: Boolean = AppSettings().rootBootAutoStartEnabled,
    /** 日志保留天数 */
    val maxHistoryDays: Long = ServerSettings().maxHistoryDays,
    /** 日志级别 */
    val logLevel: LoggerX.LogLevel = ServerSettings().logLevel,
    // StatisticsSettings：历史统计与续航预测所需的分类、样本窗口和加权参数。
    /** 高负载AppList */
    val gamePackages: Set<String> = StatisticsSettings().gamePackages,
    /** 即使命中游戏特征也要排除在“游戏”场景外的包名集合。 */
    val gameBlacklist: Set<String> = StatisticsSettings().gameBlacklist,
    /** 样本次数 */
    val sceneStatsRecentFileCount: Int = StatisticsSettings().sceneStatsRecentFileCount,
    /** 当此记录加权 */
    val predCurrentSessionWeightEnabled: Boolean = StatisticsSettings().predCurrentSessionWeightEnabled,
    /** 加权强度 */
    val predCurrentSessionWeightMaxX100: Int = StatisticsSettings().predCurrentSessionWeightMaxX100,
    /** 加权半衰期 */
    val predCurrentSessionWeightHalfLifeMin: Long = StatisticsSettings().predCurrentSessionWeightHalfLifeMin
)
