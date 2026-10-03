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
    /** 启动更新检测使用的版本通道。 */
    val updateChannel: UpdateChannel = SettingsConstants.updateChannel.def,
    /** 放电记录在 UI 中是否显示为正值。 */
    val dischargeDisplayPositive: Boolean = SettingsConstants.dischargeDisplayPositive.def,
    /** 放电详情页能量单位是否显示为 mAh。 */
    val dischargeDetailUseMah: Boolean = SettingsConstants.dischargeDetailUseMah.def,
    /** 开机后是否尝试 ROOT 自启动。 */
    val rootBootAutoStartEnabled: Boolean = SettingsConstants.rootBootAutoStartEnabled.def,
    /** 是否查看厂商系统原生日志（仅在检测到可用数据源时生效）。 */
    val vendorSystemLogEnabled: Boolean = SettingsConstants.vendorSystemLogEnabled.def
)
