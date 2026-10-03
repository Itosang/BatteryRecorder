package yangfentuozi.batteryrecorder.usecase.settings

import android.content.SharedPreferences
import androidx.core.content.edit
import yangfentuozi.batteryrecorder.ipc.Service
import yangfentuozi.batteryrecorder.shared.config.ServerSettingsCodec
import yangfentuozi.batteryrecorder.shared.config.SettingsConstants
import yangfentuozi.batteryrecorder.shared.config.SharedSettings
import yangfentuozi.batteryrecorder.shared.config.dataclass.AppSettings
import yangfentuozi.batteryrecorder.shared.config.dataclass.ServerSettings
import yangfentuozi.batteryrecorder.shared.config.dataclass.StatisticsSettings
import yangfentuozi.batteryrecorder.shared.config.dataclass.UpdateChannel
import yangfentuozi.batteryrecorder.shared.util.LoggerX

/**
 * 设置快照。
 */
internal data class LoadedSettingsSnapshot(
    val appSettings: AppSettings,
    val statisticsSettings: StatisticsSettings,
    val serverSettings: ServerSettings
)

/**
 * 统一读取三包设置。
 */
internal object LoadSettingsUseCase {

    /**
     * 读取三包设置。
     *
     * @param prefs 已定位好的设置存储。
     * @return 返回三包设置快照。
     */
    fun execute(prefs: SharedPreferences): LoadedSettingsSnapshot =
        LoadedSettingsSnapshot(
            appSettings = SharedSettings.readAppSettings(prefs),
            statisticsSettings = SharedSettings.readStatisticsSettings(prefs),
            serverSettings = ServerSettingsCodec.readFromPreferences(prefs)
        )
}

/**
 * 统一处理 AppSettings 写盘。
 */
internal object UpdateAppSettingsUseCase {

    /**
     * 更新启动时检测更新开关。
     *
     * @param prefs 已定位好的设置存储。
     * @param current 当前 AppSettings。
     * @param enabled 新开关值。
     * @return 返回更新后的 AppSettings。
     */
    fun updateCheckUpdateOnStartup(
        prefs: SharedPreferences,
        current: AppSettings,
        enabled: Boolean
    ): AppSettings {
        prefs.edit {
            SettingsConstants.checkUpdateOnStartup.writeToSP(this, enabled)
        }
        return current.copy(checkUpdateOnStartup = enabled)
    }

    /**
     * 更新版本通道。
     *
     * @param prefs 已定位好的设置存储。
     * @param current 当前 AppSettings。
     * @param channel 新版本通道。
     * @return 返回更新后的 AppSettings。
     */
    fun updateChannel(
        prefs: SharedPreferences,
        current: AppSettings,
        channel: UpdateChannel
    ): AppSettings {
        prefs.edit {
            SettingsConstants.updateChannel.writeToSP(this, channel)
        }
        return current.copy(updateChannel = channel)
    }

    /**
     * 更新放电正值展示开关。
     *
     * @param prefs 已定位好的设置存储。
     * @param current 当前 AppSettings。
     * @param enabled 新开关值。
     * @return 返回更新后的 AppSettings。
     */
    fun updateDischargeDisplayPositive(
        prefs: SharedPreferences,
        current: AppSettings,
        enabled: Boolean
    ): AppSettings {
        prefs.edit {
            SettingsConstants.dischargeDisplayPositive.writeToSP(this, enabled)
        }
        return current.copy(dischargeDisplayPositive = enabled)
    }

    /**
     * 更新放电详情页 mAh 展示开关。
     *
     * @param prefs 已定位好的设置存储。
     * @param current 当前 AppSettings。
     * @param enabled 新开关值。
     * @return 返回更新后的 AppSettings。
     */
    fun updateDischargeDetailUseMah(
        prefs: SharedPreferences,
        current: AppSettings,
        enabled: Boolean
    ): AppSettings {
        prefs.edit {
            SettingsConstants.dischargeDetailUseMah.writeToSP(this, enabled)
        }
        return current.copy(dischargeDetailUseMah = enabled)
    }

    /**
     * 更新 ROOT 开机自启动开关。
     *
     * @param prefs 已定位好的设置存储。
     * @param current 当前 AppSettings。
     * @param enabled 新开关值。
     * @return 返回更新后的 AppSettings。
     */
    fun updateRootBootAutoStartEnabled(
        prefs: SharedPreferences,
        current: AppSettings,
        enabled: Boolean
    ): AppSettings {
        prefs.edit {
            SettingsConstants.rootBootAutoStartEnabled.writeToSP(this, enabled)
        }
        return current.copy(rootBootAutoStartEnabled = enabled)
    }

    /**
     * 更新「查看厂商系统原生日志」开关。
     *
     * @param prefs 已定位好的设置存储。
     * @param current 当前 AppSettings。
     * @param enabled 新开关值。
     * @return 返回更新后的 AppSettings。
     */
    fun updateVendorSystemLogEnabled(
        prefs: SharedPreferences,
        current: AppSettings,
        enabled: Boolean
    ): AppSettings {
        prefs.edit {
            SettingsConstants.vendorSystemLogEnabled.writeToSP(this, enabled)
        }
        return current.copy(vendorSystemLogEnabled = enabled)
    }
}

/**
 * 统一处理 StatisticsSettings 写盘。
 */
internal object UpdateStatisticsSettingsUseCase {

    /**
     * 更新游戏包名集合。
     *
     * @param prefs 已定位好的设置存储。
     * @param current 当前 StatisticsSettings。
     * @param packages 用户确认的游戏包名集合。
     * @param detectedGamePkgs 自动检测出的游戏包名集合。
     * @return 返回更新后的 StatisticsSettings。
     */
    fun updateGamePackages(
        prefs: SharedPreferences,
        current: StatisticsSettings,
        packages: Set<String>,
        detectedGamePkgs: Set<String>
    ): StatisticsSettings {
        val newBlacklist = current.gameBlacklist + (detectedGamePkgs - packages)
        val updated = current.copy(
            gamePackages = packages,
            gameBlacklist = newBlacklist
        )
        prefs.edit {
            SettingsConstants.gamePackages.writeToSP(this, packages)
            SettingsConstants.gameBlacklist.writeToSP(this, newBlacklist)
        }
        return updated
    }

    /**
     * 更新场景统计最近文件数量。
     *
     * @param prefs 已定位好的设置存储。
     * @param current 当前 StatisticsSettings。
     * @param value 新值。
     * @return 返回更新后的 StatisticsSettings。
     */
    fun updateSceneStatsRecentFileCount(
        prefs: SharedPreferences,
        current: StatisticsSettings,
        value: Int
    ): StatisticsSettings {
        val finalValue = SettingsConstants.sceneStatsRecentFileCount.coerce(value)
        prefs.edit {
            SettingsConstants.sceneStatsRecentFileCount.writeToSP(this, finalValue)
        }
        return current.copy(sceneStatsRecentFileCount = finalValue)
    }

    /**
     * 更新首页预测加权算法开关。
     *
     * @param prefs 已定位好的设置存储。
     * @param current 当前 StatisticsSettings。
     * @param enabled 新开关值。
     * @return 返回更新后的 StatisticsSettings。
     */
    fun updatePredWeightedAlgorithmEnabled(
        prefs: SharedPreferences,
        current: StatisticsSettings,
        enabled: Boolean
    ): StatisticsSettings {
        prefs.edit {
            SettingsConstants.predWeightedAlgorithmEnabled.writeToSP(this, enabled)
        }
        return current.copy(predWeightedAlgorithmEnabled = enabled)
    }

    /**
     * 更新首页预测最大权重比例。
     *
     * @param prefs 已定位好的设置存储。
     * @param current 当前 StatisticsSettings。
     * @param value 新值。
     * @return 返回更新后的 StatisticsSettings。
     */
    fun updatePredWeightedAlgorithmAlphaMaxX100(
        prefs: SharedPreferences,
        current: StatisticsSettings,
        value: Int
    ): StatisticsSettings {
        val finalValue = SettingsConstants.predWeightedAlgorithmAlphaMaxX100.coerce(value)
        prefs.edit {
            SettingsConstants.predWeightedAlgorithmAlphaMaxX100.writeToSP(this, finalValue)
        }
        return current.copy(predWeightedAlgorithmAlphaMaxX100 = finalValue)
    }
}

/**
 * 统一处理 ServerSettings 写盘、日志应用与服务端下发。
 */
internal object UpdateServerSettingsUseCase {

    /**
     * 更新双电芯模式。
     *
     * @param prefs 已定位好的设置存储。
     * @param current 当前 ServerSettings。
     * @param enabled 新开关值。
     * @return 返回更新后的 ServerSettings。
     */
    fun updateDualCellEnabled(
        prefs: SharedPreferences,
        current: ServerSettings,
        enabled: Boolean
    ): ServerSettings = update(
        prefs = prefs,
        current = current,
        message = "[设置] 更新双电芯模式并准备下发: enabled=$enabled"
    ) { it.copy(dualCellEnabled = enabled) }

    /**
     * 更新电流校准值。
     *
     * @param prefs 已定位好的设置存储。
     * @param current 当前 ServerSettings。
     * @param value 新值。
     * @return 返回更新后的 ServerSettings。
     */
    fun updateCalibrationValue(
        prefs: SharedPreferences,
        current: ServerSettings,
        value: Int
    ): ServerSettings {
        val finalValue = SettingsConstants.calibrationValue.coerce(value)
        return update(
            prefs = prefs,
            current = current,
            message = "[设置] 更新电流校准并准备下发: calibration=$finalValue"
        ) { it.copy(calibrationValue = finalValue) }
    }

    /**
     * 更新实时通知开关。
     *
     * @param prefs 已定位好的设置存储。
     * @param current 当前 ServerSettings。
     * @param enabled 新开关值。
     * @return 返回更新后的 ServerSettings。
     */
    fun updateNotificationEnabled(
        prefs: SharedPreferences,
        current: ServerSettings,
        enabled: Boolean
    ): ServerSettings = update(
        prefs = prefs,
        current = current,
        message = "[设置] 更新实时通知并准备下发: enabled=$enabled"
    ) { it.copy(notificationEnabled = enabled) }

    /**
     * 更新通知兼容模式开关。
     *
     * @param prefs 已定位好的设置存储。
     * @param current 当前 ServerSettings。
     * @param enabled 新开关值。
     * @return 返回更新后的 ServerSettings。
     */
    fun updateNotificationCompatModeEnabled(
        prefs: SharedPreferences,
        current: ServerSettings,
        enabled: Boolean
    ): ServerSettings = update(
        prefs = prefs,
        current = current,
        message = "[设置] 更新通知兼容模式并准备下发: enabled=$enabled"
    ) { it.copy(notificationCompatModeEnabled = enabled) }

    fun updateNotificationIconCompatModeEnabled(
        prefs: SharedPreferences,
        current: ServerSettings,
        enabled: Boolean
    ): ServerSettings = update(
        prefs = prefs,
        current = current,
        message = "[设置] 更新通知图标兼容模式并准备下发: enabled=$enabled"
    ) { it.copy(notificationIconCompatModeEnabled = enabled) }

    /**
     * 更新记录间隔。
     *
     * @param prefs 已定位好的设置存储。
     * @param current 当前 ServerSettings。
     * @param value 新值。
     * @return 返回更新后的 ServerSettings。
     */
    fun updateRecordIntervalMs(
        prefs: SharedPreferences,
        current: ServerSettings,
        value: Long
    ): ServerSettings {
        val finalValue = SettingsConstants.recordIntervalMs.coerce(value)
        return update(
            prefs = prefs,
            current = current,
            message = "[设置] 更新记录间隔并准备下发: intervalMs=$finalValue"
        ) { it.copy(recordIntervalMs = finalValue) }
    }

    /**
     * 更新写入延迟。
     *
     * @param prefs 已定位好的设置存储。
     * @param current 当前 ServerSettings。
     * @param value 新值。
     * @return 返回更新后的 ServerSettings。
     */
    fun updateWriteLatencyMs(
        prefs: SharedPreferences,
        current: ServerSettings,
        value: Long
    ): ServerSettings {
        val finalValue = SettingsConstants.writeLatencyMs.coerce(value)
        return update(
            prefs = prefs,
            current = current,
            message = "[设置] 更新写入延迟并准备下发: writeLatencyMs=$finalValue"
        ) { it.copy(writeLatencyMs = finalValue) }
    }

    /**
     * 更新批次大小。
     *
     * @param prefs 已定位好的设置存储。
     * @param current 当前 ServerSettings。
     * @param value 新值。
     * @return 返回更新后的 ServerSettings。
     */
    fun updateBatchSize(
        prefs: SharedPreferences,
        current: ServerSettings,
        value: Int
    ): ServerSettings {
        val finalValue = SettingsConstants.batchSize.coerce(value)
        return update(
            prefs = prefs,
            current = current,
            message = "[设置] 更新批次大小并准备下发: batchSize=$finalValue"
        ) { it.copy(batchSize = finalValue) }
    }

    /**
     * 更新息屏记录开关。
     *
     * @param prefs 已定位好的设置存储。
     * @param current 当前 ServerSettings。
     * @param enabled 新开关值。
     * @return 返回更新后的 ServerSettings。
     */
    fun updateScreenOffRecordEnabled(
        prefs: SharedPreferences,
        current: ServerSettings,
        enabled: Boolean
    ): ServerSettings = update(
        prefs = prefs,
        current = current,
        message = "[设置] 更新息屏记录并准备下发: enabled=$enabled"
    ) { it.copy(screenOffRecordEnabled = enabled) }

    /**
     * 更新精确息屏记录开关。
     *
     * @param prefs 已定位好的设置存储。
     * @param current 当前 ServerSettings。
     * @param enabled 新开关值。
     * @return 返回更新后的 ServerSettings。
     */
    fun updatePreciseScreenOffRecordEnabled(
        prefs: SharedPreferences,
        current: ServerSettings,
        enabled: Boolean
    ): ServerSettings = update(
        prefs = prefs,
        current = current,
        message = "[设置] 更新精确息屏记录并准备下发: enabled=$enabled"
    ) { it.copy(preciseScreenOffRecordEnabled = enabled) }

    /**
     * 更新轮询亮屏状态开关。
     *
     * @param prefs 已定位好的设置存储。
     * @param current 当前 ServerSettings。
     * @param enabled 新开关值。
     * @return 返回更新后的 ServerSettings。
     */
    fun updateAlwaysPollingScreenStatusEnabled(
        prefs: SharedPreferences,
        current: ServerSettings,
        enabled: Boolean
    ): ServerSettings = update(
        prefs = prefs,
        current = current,
        message = "[设置] 更新轮询亮屏状态并准备下发: enabled=$enabled"
    ) { it.copy(alwaysPollingScreenStatusEnabled = enabled) }

    /**
     * 更新分段时长。
     *
     * @param prefs 已定位好的设置存储。
     * @param current 当前 ServerSettings。
     * @param value 新值。
     * @return 返回更新后的 ServerSettings。
     */
    fun updateSegmentDurationMin(
        prefs: SharedPreferences,
        current: ServerSettings,
        value: Long
    ): ServerSettings {
        val finalValue = SettingsConstants.segmentDurationMin.coerce(value)
        return update(
            prefs = prefs,
            current = current,
            message = "[设置] 更新分段时长并准备下发: value=$finalValue"
        ) { it.copy(segmentDurationMin = finalValue) }
    }

    /**
     * 更新日志保留天数。
     *
     * @param prefs 已定位好的设置存储。
     * @param current 当前 ServerSettings。
     * @param value 新值。
     * @return 返回更新后的 ServerSettings。
     */
    fun updateMaxHistoryDays(
        prefs: SharedPreferences,
        current: ServerSettings,
        value: Long
    ): ServerSettings {
        val finalValue = SettingsConstants.logMaxHistoryDays.coerce(value)
        return update(
            prefs = prefs,
            current = current,
            message = "[设置] 更新日志保留天数并准备下发: maxHistoryDays=$finalValue"
        ) { it.copy(maxHistoryDays = finalValue) }
    }

    /**
     * 更新日志级别。
     *
     * @param prefs 已定位好的设置存储。
     * @param current 当前 ServerSettings。
     * @param value 新值。
     * @return 返回更新后的 ServerSettings。
     */
    fun updateLogLevel(
        prefs: SharedPreferences,
        current: ServerSettings,
        value: LoggerX.LogLevel
    ): ServerSettings = update(
        prefs = prefs,
        current = current,
        message = "[设置] 更新日志级别并准备下发: logLevel=$value"
    ) { it.copy(logLevel = value) }

    /**
     * 统一处理 ServerSettings 更新链路。
     *
     * @param prefs 已定位好的设置存储。
     * @param current 当前 ServerSettings。
     * @param message 本次下发的日志文案。
     * @param transform 基于当前配置构造新配置的转换函数。
     * @return 返回更新后的 ServerSettings。
     */
    private fun update(
        prefs: SharedPreferences,
        current: ServerSettings,
        message: String,
        transform: (ServerSettings) -> ServerSettings
    ): ServerSettings {
        val updatedSettings = transform(current)
        SharedSettings.writeServerSettings(prefs, updatedSettings)
        applyLoggerSettings(updatedSettings)
        LoggerX.i("SettingsViewModel", message)
        Service.service?.syncSettings(updatedSettings)
        return updatedSettings
    }

    /**
     * 让 App 进程日志行为立即跟随最新设置。
     *
     * @param settings 最新服务端配置。
     * @return 无返回值。
     */
    private fun applyLoggerSettings(settings: ServerSettings) {
        LoggerX.maxHistoryDays = settings.maxHistoryDays
        LoggerX.logLevel = settings.logLevel
    }
}
