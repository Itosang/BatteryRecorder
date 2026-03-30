package yangfentuozi.batteryrecorder.shared.config

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import yangfentuozi.batteryrecorder.shared.util.LoggerX

/**
 * 仅用于现有 AIDL 和 ContentProvider 边界传输的薄 DTO。
 * 服务端配置的默认值、裁剪与枚举解析统一先收敛到 ServerSettings，再映射为 IPC 边界 DTO。
 */
@Parcelize
data class ServerConfigDto(
    val recordIntervalMs: Long = SettingsConstants.DEF_RECORD_INTERVAL_MS,
    val batchSize: Int = SettingsConstants.DEF_BATCH_SIZE,
    val writeLatencyMs: Long = SettingsConstants.DEF_WRITE_LATENCY_MS,
    val screenOffRecordEnabled: Boolean = SettingsConstants.DEF_SCREEN_OFF_RECORD_ENABLED,
    val segmentDurationMin: Long = SettingsConstants.DEF_SEGMENT_DURATION_MIN,
    val maxHistoryDays: Long = SettingsConstants.DEF_LOG_MAX_HISTORY_DAYS,
    val logLevel: LoggerX.LogLevel = SettingsConstants.DEF_LOG_LEVEL,
    val alwaysPollingScreenStatusEnabled: Boolean = SettingsConstants.DEF_ALWAYS_POLLING_SCREEN_STATUS_ENABLED
) : Parcelable
