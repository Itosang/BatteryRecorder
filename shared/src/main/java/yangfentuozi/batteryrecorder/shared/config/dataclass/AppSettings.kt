package yangfentuozi.batteryrecorder.shared.config.dataclass

import yangfentuozi.batteryrecorder.shared.config.SettingsConstants

/** 应用进程本地设置。 */
data class AppSettings(
    val checkUpdateOnStartup: Boolean = SettingsConstants.checkUpdateOnStartup.def,
    val dualCellEnabled: Boolean = SettingsConstants.dualCellEnabled.def,
    val dischargeDisplayPositive: Boolean = SettingsConstants.dischargeDisplayPositive.def,
    val calibrationValue: Int = SettingsConstants.calibrationValue.def,
    val rootBootAutoStartEnabled: Boolean = SettingsConstants.rootBootAutoStartEnabled.def
)
