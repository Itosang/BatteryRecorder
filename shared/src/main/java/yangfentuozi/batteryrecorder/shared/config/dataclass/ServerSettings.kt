package yangfentuozi.batteryrecorder.shared.config.dataclass

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import yangfentuozi.batteryrecorder.shared.config.SettingsConstants
import yangfentuozi.batteryrecorder.shared.util.LoggerX

/** 服务端运行配置。 */
@Parcelize
data class ServerSettings(
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
