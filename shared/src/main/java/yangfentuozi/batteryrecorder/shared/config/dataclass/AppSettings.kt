package yangfentuozi.batteryrecorder.shared.config.dataclass

import yangfentuozi.batteryrecorder.shared.config.SettingsConstants

/**
 * 应用进程本地设置。
 *
 * 这组字段只在 App 进程内消费，不会下发给运行中的 Server。
 */
data class AppSettings(
    /** 进入应用后是否自动执行更新检查。 */
    val checkUpdateOnStartup: Boolean = SettingsConstants.checkUpdateOnStartup.def,
    /** 是否按双电芯设备展示与计算功率。 */
    val dualCellEnabled: Boolean = SettingsConstants.dualCellEnabled.def,
    /** 放电记录在 UI 中是否显示为正值。 */
    val dischargeDisplayPositive: Boolean = SettingsConstants.dischargeDisplayPositive.def,
    /** 功率校准值，仅影响展示与换算。 */
    val calibrationValue: Int = SettingsConstants.calibrationValue.def,
    /** 开机后是否尝试 ROOT 自启动。 */
    val rootBootAutoStartEnabled: Boolean = SettingsConstants.rootBootAutoStartEnabled.def
)
