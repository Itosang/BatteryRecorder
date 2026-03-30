package yangfentuozi.batteryrecorder.shared.config

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import yangfentuozi.batteryrecorder.shared.util.LoggerX

/**
 * 仅用于现有 AIDL 和 ContentProvider 边界传输的薄 DTO。
 * 服务端配置的默认值、裁剪与枚举解析统一先收敛到 ServerSettings，本轮保留 Config 只是为了兼容现有 IPC 接口。
 */
@Parcelize
data class Config(
    val recordIntervalMs: Long = ConfigConstants.DEF_RECORD_INTERVAL_MS,
    val batchSize: Int = ConfigConstants.DEF_BATCH_SIZE,
    val writeLatencyMs: Long = ConfigConstants.DEF_WRITE_LATENCY_MS,
    val screenOffRecordEnabled: Boolean = ConfigConstants.DEF_SCREEN_OFF_RECORD_ENABLED,
    val segmentDurationMin: Long = ConfigConstants.DEF_SEGMENT_DURATION_MIN,
    val maxHistoryDays: Long = ConfigConstants.DEF_LOG_MAX_HISTORY_DAYS,
    val logLevel: LoggerX.LogLevel = ConfigConstants.DEF_LOG_LEVEL,
    val alwaysPollingScreenStatusEnabled: Boolean = ConfigConstants.DEF_ALWAYS_POLLING_SCREEN_STATUS_ENABLED
) : Parcelable
