package yangfentuozi.batteryrecorder.shared.config.dataclass

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import yangfentuozi.batteryrecorder.shared.config.SettingsConstants
import yangfentuozi.batteryrecorder.shared.util.LoggerX

/**
 * 服务端运行配置。
 *
 * 它既是 Server 领域模型，也是当前 AIDL / ContentProvider 的 IPC 边界对象。
 */
@Parcelize
data class ServerSettings(
    /** 是否展示实时功耗通知。 */
    val notificationEnabled: Boolean = SettingsConstants.notificationEnabled.def,
    /** 是否按双电芯设备处理功率展示与通知。 */
    val dualCellEnabled: Boolean = SettingsConstants.dualCellEnabled.def,
    /** 电流校准值，统一用于功率与 mAh 换算。 */
    val calibrationValue: Int = SettingsConstants.calibrationValue.def,
    /**
     * 采样间隔，单位毫秒。
     *
     * 这个值决定 Monitor 的采样频率，同时也会影响部分统计链路对单条记录时间粒度的理解。
     */
    val recordIntervalMs: Long = SettingsConstants.recordIntervalMs.def,
    /** Writer 每次批量刷盘前最多累积的记录数。 */
    val batchSize: Int = SettingsConstants.batchSize.def,
    /** Writer 最长允许延迟刷盘的时间，单位毫秒。 */
    val writeLatencyMs: Long = SettingsConstants.writeLatencyMs.def,
    /** 息屏后是否继续采样与记录。 */
    val screenOffRecordEnabled: Boolean = SettingsConstants.screenOffRecordEnabled.def,
    /** 单个记录文件按时间自动分段的时长，单位分钟，0 表示不分段。 */
    val segmentDurationMin: Long = SettingsConstants.segmentDurationMin.def,
    /** 日志文件保留天数。 */
    val maxHistoryDays: Long = SettingsConstants.logMaxHistoryDays.def,
    /** LoggerX 当前允许输出的最高日志级别。 */
    val logLevel: LoggerX.LogLevel = SettingsConstants.logLevel.def,
    /** 是否启用轮询亮屏状态作为前台亮灭监听的补充。 */
    val alwaysPollingScreenStatusEnabled: Boolean =
        SettingsConstants.alwaysPollingScreenStatusEnabled.def
) : Parcelable
