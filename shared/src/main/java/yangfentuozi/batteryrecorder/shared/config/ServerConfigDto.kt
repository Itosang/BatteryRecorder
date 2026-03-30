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
    val recordIntervalMs: Long = SettingsConstants.recordIntervalMs.def,
    val batchSize: Int = SettingsConstants.batchSize.def,
    val writeLatencyMs: Long = SettingsConstants.writeLatencyMs.def,
    val screenOffRecordEnabled: Boolean = SettingsConstants.screenOffRecordEnabled.def,
    val segmentDurationMin: Long = SettingsConstants.segmentDurationMin.def,
    val maxHistoryDays: Long = SettingsConstants.logMaxHistoryDays.def,
    val logLevel: LoggerX.LogLevel = SettingsConstants.logLevel.def,
    val alwaysPollingScreenStatusEnabled: Boolean =
        SettingsConstants.alwaysPollingScreenStatusEnabled.def
) : Parcelable
