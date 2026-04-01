package yangfentuozi.batteryrecorder.shared.config

import android.content.Context
import android.content.SharedPreferences
import android.content.SharedPreferences.Editor
import yangfentuozi.batteryrecorder.shared.config.dataclass.AppSettings
import yangfentuozi.batteryrecorder.shared.config.dataclass.ServerSettings
import yangfentuozi.batteryrecorder.shared.config.dataclass.StatisticsSettings
import yangfentuozi.batteryrecorder.shared.util.LoggerX

/**
 * 三类设置的 SharedPreferences 读写入口。
 *
 * 当前约束是：
 * 1. 读取侧只负责“缺字段回退默认值”，不替外部脏值做裁剪。
 * 2. 写入侧只负责落盘，不承担统一合法化。
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
            dualCellEnabled = SettingsConstants.dualCellEnabled.readFromSP(prefs),
            dischargeDisplayPositive = SettingsConstants.dischargeDisplayPositive.readFromSP(prefs),
            calibrationValue = SettingsConstants.calibrationValue.readFromSP(prefs),
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
        readServerSettings(getPreferences(context))

    /**
     * 从指定 SharedPreferences 读取 ServerSettings。
     *
     * 这里不做额外裁剪，只在 key 缺失时回退默认值。
     *
     * @param prefs 已经定位好的设置存储。
     * @return 服务端运行配置。
     */
    fun readServerSettings(prefs: SharedPreferences): ServerSettings =
        ServerSettings(
            recordIntervalMs = prefs.getLong(
                SettingsConstants.recordIntervalMs.key,
                SettingsConstants.recordIntervalMs.def
            ),
            batchSize = prefs.getInt(
                SettingsConstants.batchSize.key,
                SettingsConstants.batchSize.def
            ),
            writeLatencyMs = prefs.getLong(
                SettingsConstants.writeLatencyMs.key,
                SettingsConstants.writeLatencyMs.def
            ),
            screenOffRecordEnabled = prefs.getBoolean(
                SettingsConstants.screenOffRecordEnabled.key,
                SettingsConstants.screenOffRecordEnabled.def
            ),
            segmentDurationMin = prefs.getLong(
                SettingsConstants.segmentDurationMin.key,
                SettingsConstants.segmentDurationMin.def
            ),
            maxHistoryDays = prefs.getLong(
                SettingsConstants.logMaxHistoryDays.key,
                SettingsConstants.logMaxHistoryDays.def
            ),
            logLevel = decodeLogLevel(
                prefs.getInt(
                    SettingsConstants.logLevel.key,
                    encodeLogLevel(SettingsConstants.logLevel.def)
                )
            ),
            alwaysPollingScreenStatusEnabled = prefs.getBoolean(
                SettingsConstants.alwaysPollingScreenStatusEnabled.key,
                SettingsConstants.alwaysPollingScreenStatusEnabled.def
            )
        )

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
        editor.writeServerSettings(settings)
        editor.apply()
    }

    private fun Editor.writeAppSettings(settings: AppSettings) {
        SettingsConstants.checkUpdateOnStartup.writeToSP(this, settings.checkUpdateOnStartup)
        SettingsConstants.dualCellEnabled.writeToSP(this, settings.dualCellEnabled)
        SettingsConstants.dischargeDisplayPositive.writeToSP(this, settings.dischargeDisplayPositive)
        SettingsConstants.calibrationValue.writeToSP(this, settings.calibrationValue)
        SettingsConstants.rootBootAutoStartEnabled.writeToSP(this, settings.rootBootAutoStartEnabled)
    }

    private fun Editor.writeServerSettings(settings: ServerSettings) {
        putLong(SettingsConstants.recordIntervalMs.key, settings.recordIntervalMs)
        putInt(SettingsConstants.batchSize.key, settings.batchSize)
        putLong(SettingsConstants.writeLatencyMs.key, settings.writeLatencyMs)
        putBoolean(SettingsConstants.screenOffRecordEnabled.key, settings.screenOffRecordEnabled)
        putLong(SettingsConstants.segmentDurationMin.key, settings.segmentDurationMin)
        putLong(SettingsConstants.logMaxHistoryDays.key, settings.maxHistoryDays)
        putInt(SettingsConstants.logLevel.key, encodeLogLevel(settings.logLevel))
        putBoolean(
            SettingsConstants.alwaysPollingScreenStatusEnabled.key,
            settings.alwaysPollingScreenStatusEnabled
        )
    }

    /**
     * 将日志级别转成可持久化的整型优先级。
     *
     * @param value 当前日志级别。
     * @return 对应的整型 priority。
     */
    fun encodeLogLevel(value: LoggerX.LogLevel): Int = value.priority

    /**
     * 将持久化的整型优先级还原为日志级别。
     *
     * @param value 持久化后的整型 priority。
     * @return 对应的 LoggerX.LogLevel；非法值交由 LoggerX 的优先级映射处理。
     */
    fun decodeLogLevel(value: Int): LoggerX.LogLevel = LoggerX.LogLevel.fromPriority(value)
}
