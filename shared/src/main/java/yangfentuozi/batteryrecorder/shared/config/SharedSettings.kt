package yangfentuozi.batteryrecorder.shared.config

import android.content.Context
import android.content.SharedPreferences
import android.content.SharedPreferences.Editor
import yangfentuozi.batteryrecorder.shared.config.dataclass.AppSettings
import yangfentuozi.batteryrecorder.shared.config.dataclass.ServerSettings
import yangfentuozi.batteryrecorder.shared.config.dataclass.StatisticsSettings

/**
 * 三类设置的 SharedPreferences 读写入口。
 *
 * 当前约束是：
 * 1. `AppSettings / StatisticsSettings` 延续各自现有的 `ConfigItem` 读写方式。
 * 2. `ServerSettings` 的字段映射统一委托给 `ServerSettingsCodec`。
 * 3. 数值范围的轻量收口放在 UI 与 `SettingsViewModel` 的 setter。
 */
object SharedSettings {
    /**
     * 获取项目统一的设置存储。
     *
     * @param context 任意可用的应用上下文。
     * @return `app_settings` 对应的 SharedPreferences。
     */
    fun getPreferences(context: Context): SharedPreferences =
        context.getSharedPreferences(SettingsConstants.PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * 从默认 SharedPreferences 读取 AppSettings。
     *
     * @param context 用于定位默认设置文件的上下文。
     * @return App 进程本地设置。
     */
    fun readAppSettings(context: Context): AppSettings = readAppSettings(getPreferences(context))

    /**
     * 从指定 SharedPreferences 读取 AppSettings。
     *
     * 这个重载主要给已经长期持有同一 `prefs` 实例的调用方复用，例如 SettingsViewModel。
     *
     * @param prefs 已经定位好的设置存储。
     * @return App 进程本地设置。
     */
    fun readAppSettings(prefs: SharedPreferences): AppSettings =
        AppSettings(
            checkUpdateOnStartup = SettingsConstants.checkUpdateOnStartup.readFromSP(prefs),
            updateChannel = SettingsConstants.updateChannel.readFromSP(prefs),
            dischargeDisplayPositive = SettingsConstants.dischargeDisplayPositive.readFromSP(prefs),
            rootBootAutoStartEnabled = SettingsConstants.rootBootAutoStartEnabled.readFromSP(prefs)
        )

    /**
     * 从默认 SharedPreferences 读取 StatisticsSettings。
     *
     * @param context 用于定位默认设置文件的上下文。
     * @return 统计与预测相关设置。
     */
    fun readStatisticsSettings(context: Context): StatisticsSettings =
        readStatisticsSettings(getPreferences(context))

    /**
     * 从指定 SharedPreferences 读取 StatisticsSettings。
     *
     * @param prefs 已经定位好的设置存储。
     * @return 统计与预测相关设置。
     */
    fun readStatisticsSettings(prefs: SharedPreferences): StatisticsSettings =
        StatisticsSettings(
            gamePackages = SettingsConstants.gamePackages.readFromSP(prefs),
            gameBlacklist = SettingsConstants.gameBlacklist.readFromSP(prefs),
            sceneStatsRecentFileCount = SettingsConstants.sceneStatsRecentFileCount.readFromSP(prefs),
            predWeightedAlgorithmEnabled =
                SettingsConstants.predWeightedAlgorithmEnabled.readFromSP(prefs),
            predWeightedAlgorithmAlphaMaxX100 =
                SettingsConstants.predWeightedAlgorithmAlphaMaxX100.readFromSP(prefs)
        )

    /**
     * 从默认 SharedPreferences 读取 ServerSettings。
     *
     * @param context 用于定位默认设置文件的上下文。
     * @return 服务端运行配置。
     */
    fun readServerSettings(context: Context): ServerSettings =
        ServerSettingsCodec.readFromPreferences(getPreferences(context))

    /**
     * 将 AppSettings 写回 SharedPreferences。
     *
     * @param prefs 目标设置存储。
     * @param settings 需要落盘的 AppSettings。
     * @return 无，异步 apply。
     */
    fun writeAppSettings(prefs: SharedPreferences, settings: AppSettings) {
        val editor = prefs.edit()
        editor.writeAppSettings(settings)
        editor.apply()
    }

    /**
     * 将 ServerSettings 写回 SharedPreferences。
     *
     * 这里保持纯写入职责，默认认为上游已经完成输入限制与数值收口。
     *
     * @param prefs 目标设置存储。
     * @param settings 需要落盘的 ServerSettings。
     * @return 无，异步 apply。
     */
    fun writeServerSettings(prefs: SharedPreferences, settings: ServerSettings) {
        val editor = prefs.edit()
        ServerSettingsCodec.writeToPreferences(editor, settings)
        editor.apply()
    }

    private fun Editor.writeAppSettings(settings: AppSettings) {
        SettingsConstants.checkUpdateOnStartup.writeToSP(this, settings.checkUpdateOnStartup)
        SettingsConstants.updateChannel.writeToSP(this, settings.updateChannel)
        SettingsConstants.dischargeDisplayPositive.writeToSP(this, settings.dischargeDisplayPositive)
        SettingsConstants.rootBootAutoStartEnabled.writeToSP(this, settings.rootBootAutoStartEnabled)
    }

}
