package yangfentuozi.batteryrecorder.ui.viewmodel

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import yangfentuozi.batteryrecorder.shared.config.SharedSettings
import yangfentuozi.batteryrecorder.shared.config.dataclass.AppSettings
import yangfentuozi.batteryrecorder.shared.config.dataclass.ServerSettings
import yangfentuozi.batteryrecorder.shared.config.dataclass.StatisticsSettings
import yangfentuozi.batteryrecorder.shared.config.dataclass.UpdateChannel
import yangfentuozi.batteryrecorder.shared.util.LoggerX
import yangfentuozi.batteryrecorder.usecase.settings.LoadSettingsUseCase
import yangfentuozi.batteryrecorder.usecase.settings.UpdateAppSettingsUseCase
import yangfentuozi.batteryrecorder.usecase.settings.UpdateServerSettingsUseCase
import yangfentuozi.batteryrecorder.usecase.settings.UpdateStatisticsSettingsUseCase

private const val TAG = "SettingsViewModel"

/**
 * 设置页与页外设置消费者共用的 ViewModel。
 *
 * 对外保留三包真值状态：
 * - `appSettings`
 * - `statisticsSettings`
 * - `serverSettings`
 *
 * 另外只保留少量页外高频字段的派生 StateFlow，避免把设置页外调用方也绑到设置页模型上。
 */
class SettingsViewModel : ViewModel() {
    private lateinit var prefs: SharedPreferences

    private val _appSettings = MutableStateFlow(AppSettings())
    val appSettings: StateFlow<AppSettings> = _appSettings.asStateFlow()

    private val _statisticsSettings = MutableStateFlow(StatisticsSettings())
    val statisticsSettings: StateFlow<StatisticsSettings> = _statisticsSettings.asStateFlow()

    private val _serverSettings = MutableStateFlow(ServerSettings())
    val serverSettings: StateFlow<ServerSettings> = _serverSettings.asStateFlow()

    private val _initialized = MutableStateFlow(false)
    val initialized: StateFlow<Boolean> = _initialized.asStateFlow()

    val dualCellEnabled: StateFlow<Boolean> =
        serverSettings.map { it.dualCellEnabled }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = ServerSettings().dualCellEnabled
            )

    val dischargeDisplayPositive: StateFlow<Boolean> =
        appSettings.map { it.dischargeDisplayPositive }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = AppSettings().dischargeDisplayPositive
            )

    val calibrationValue: StateFlow<Int> =
        serverSettings.map { it.calibrationValue }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = ServerSettings().calibrationValue
            )

    val recordIntervalMs: StateFlow<Long> =
        serverSettings.map { it.recordIntervalMs }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = ServerSettings().recordIntervalMs
            )

    val screenOffRecord: StateFlow<Boolean> =
        serverSettings.map { it.screenOffRecordEnabled }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = ServerSettings().screenOffRecordEnabled
            )

    /**
     * 初始化设置存储并加载三包设置。
     *
     * @param context 用于定位默认 SharedPreferences 的应用上下文。
     * @return 无；多次调用时仅首次生效。
     */
    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = SharedSettings.getPreferences(context)
        loadSettings()
    }

    /**
     * 从 SharedPreferences 重载三包设置到内存态。
     *
     * 这里使用 `prefs` 重载，是因为 ViewModel 已经长期持有同一个 SharedPreferences 实例，
     * 再绕回 `context` 入口只会多一层无意义包装。
     *
     * @return 无，直接更新内部 StateFlow。
     */
    private fun loadSettings() {
        val snapshot = LoadSettingsUseCase.execute(prefs)

        _appSettings.value = snapshot.appSettings
        _statisticsSettings.value = snapshot.statisticsSettings
        _serverSettings.value = snapshot.serverSettings
        applyLoggerSettings(snapshot.serverSettings)
        _initialized.value = true

        LoggerX.d(
            TAG,
            "[设置] loadSettings 完成: notification=${snapshot.serverSettings.notificationEnabled} compatMode=${snapshot.serverSettings.notificationCompatModeEnabled} dualCell=${snapshot.serverSettings.dualCellEnabled} calibration=${snapshot.serverSettings.calibrationValue} intervalMs=${snapshot.serverSettings.recordIntervalMs} writeLatencyMs=${snapshot.serverSettings.writeLatencyMs} batchSize=${snapshot.serverSettings.batchSize} screenOffRecord=${snapshot.serverSettings.screenOffRecordEnabled} preciseScreenOffRecord=${snapshot.serverSettings.preciseScreenOffRecordEnabled} polling=${snapshot.serverSettings.alwaysPollingScreenStatusEnabled} logLevel=${snapshot.serverSettings.logLevel}"
        )
    }

    fun setCheckUpdateOnStartup(enabled: Boolean) {
        viewModelScope.launch {
            _appSettings.value = UpdateAppSettingsUseCase.updateCheckUpdateOnStartup(
                prefs = prefs,
                current = _appSettings.value,
                enabled = enabled
            )
        }
    }

    fun setUpdateChannel(channel: UpdateChannel) {
        viewModelScope.launch {
            _appSettings.value = UpdateAppSettingsUseCase.updateChannel(
                prefs = prefs,
                current = _appSettings.value,
                channel = channel
            )
        }
    }

    fun setDualCellEnabled(enabled: Boolean) {
        viewModelScope.launch {
            _serverSettings.value = UpdateServerSettingsUseCase.updateDualCellEnabled(
                prefs = prefs,
                current = _serverSettings.value,
                enabled = enabled
            )
        }
    }

    fun setDischargeDisplayPositiveEnabled(enabled: Boolean) {
        viewModelScope.launch {
            _appSettings.value = UpdateAppSettingsUseCase.updateDischargeDisplayPositive(
                prefs = prefs,
                current = _appSettings.value,
                enabled = enabled
            )
        }
    }

    fun setDischargeDetailUseMahEnabled(enabled: Boolean) {
        viewModelScope.launch {
            _appSettings.value = UpdateAppSettingsUseCase.updateDischargeDetailUseMah(
                prefs = prefs,
                current = _appSettings.value,
                enabled = enabled
            )
        }
    }

    fun setCalibrationValue(value: Int) {
        viewModelScope.launch {
            _serverSettings.value = UpdateServerSettingsUseCase.updateCalibrationValue(
                prefs = prefs,
                current = _serverSettings.value,
                value = value
            )
        }
    }

    fun setNotificationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            _serverSettings.value = UpdateServerSettingsUseCase.updateNotificationEnabled(
                prefs = prefs,
                current = _serverSettings.value,
                enabled = enabled
            )
        }
    }

    /**
     * 更新通知兼容模式并下发到运行中的服务端。
     *
     * @param enabled `true` 表示每次更新通知都新建 Builder；`false` 表示继续复用 Builder。
     * @return 无。
     */
    fun setNotificationCompatModeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            _serverSettings.value = UpdateServerSettingsUseCase.updateNotificationCompatModeEnabled(
                prefs = prefs,
                current = _serverSettings.value,
                enabled = enabled
            )
        }
    }

    fun setNotificationIconCompatModeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            _serverSettings.value = UpdateServerSettingsUseCase.updateNotificationIconCompatModeEnabled(
                prefs = prefs,
                current = _serverSettings.value,
                enabled = enabled
            )
        }
    }

    fun setRecordIntervalMs(value: Long) {
        viewModelScope.launch {
            _serverSettings.value = UpdateServerSettingsUseCase.updateRecordIntervalMs(
                prefs = prefs,
                current = _serverSettings.value,
                value = value
            )
        }
    }

    fun setWriteLatencyMs(value: Long) {
        viewModelScope.launch {
            _serverSettings.value = UpdateServerSettingsUseCase.updateWriteLatencyMs(
                prefs = prefs,
                current = _serverSettings.value,
                value = value
            )
        }
    }

    fun setBatchSize(value: Int) {
        viewModelScope.launch {
            _serverSettings.value = UpdateServerSettingsUseCase.updateBatchSize(
                prefs = prefs,
                current = _serverSettings.value,
                value = value
            )
        }
    }

    fun setScreenOffRecordEnabled(enabled: Boolean) {
        viewModelScope.launch {
            _serverSettings.value = UpdateServerSettingsUseCase.updateScreenOffRecordEnabled(
                prefs = prefs,
                current = _serverSettings.value,
                enabled = enabled
            )
        }
    }

    /**
     * 更新精确息屏记录开关并下发到运行中的服务端。
     *
     * @param enabled `true` 表示允许 Server 在息屏记录阶段持有唤醒锁；`false` 表示恢复默认自然息屏采样。
     * @return 无。
     */
    fun setPreciseScreenOffRecordEnabled(enabled: Boolean) {
        viewModelScope.launch {
            _serverSettings.value = UpdateServerSettingsUseCase.updatePreciseScreenOffRecordEnabled(
                prefs = prefs,
                current = _serverSettings.value,
                enabled = enabled
            )
        }
    }

    fun setAlwaysPollingScreenStatusEnabled(enabled: Boolean) {
        viewModelScope.launch {
            _serverSettings.value = UpdateServerSettingsUseCase.updateAlwaysPollingScreenStatusEnabled(
                prefs = prefs,
                current = _serverSettings.value,
                enabled = enabled
            )
        }
    }

    fun setSegmentDurationMin(value: Long) {
        viewModelScope.launch {
            _serverSettings.value = UpdateServerSettingsUseCase.updateSegmentDurationMin(
                prefs = prefs,
                current = _serverSettings.value,
                value = value
            )
        }
    }

    fun setRootBootAutoStartEnabled(enabled: Boolean) {
        viewModelScope.launch {
            _appSettings.value = UpdateAppSettingsUseCase.updateRootBootAutoStartEnabled(
                prefs = prefs,
                current = _appSettings.value,
                enabled = enabled
            )
        }
    }

    fun setVendorSystemLogEnabled(enabled: Boolean) {
        viewModelScope.launch {
            _appSettings.value = UpdateAppSettingsUseCase.updateVendorSystemLogEnabled(
                prefs = prefs,
                current = _appSettings.value,
                enabled = enabled
            )
        }
    }

    fun setMaxHistoryDays(value: Long) {
        viewModelScope.launch {
            _serverSettings.value = UpdateServerSettingsUseCase.updateMaxHistoryDays(
                prefs = prefs,
                current = _serverSettings.value,
                value = value
            )
        }
    }

    fun setLogLevel(value: LoggerX.LogLevel) {
        viewModelScope.launch {
            _serverSettings.value = UpdateServerSettingsUseCase.updateLogLevel(
                prefs = prefs,
                current = _serverSettings.value,
                value = value
            )
        }
    }

    fun setGamePackages(packages: Set<String>, detectedGamePkgs: Set<String>) {
        viewModelScope.launch {
            _statisticsSettings.value = UpdateStatisticsSettingsUseCase.updateGamePackages(
                prefs = prefs,
                current = _statisticsSettings.value,
                packages = packages,
                detectedGamePkgs = detectedGamePkgs
            )
        }
    }

    fun setSceneStatsRecentFileCount(value: Int) {
        viewModelScope.launch {
            _statisticsSettings.value = UpdateStatisticsSettingsUseCase.updateSceneStatsRecentFileCount(
                prefs = prefs,
                current = _statisticsSettings.value,
                value = value
            )
        }
    }

    fun setPredWeightedAlgorithmEnabled(enabled: Boolean) {
        viewModelScope.launch {
            _statisticsSettings.value = UpdateStatisticsSettingsUseCase.updatePredWeightedAlgorithmEnabled(
                prefs = prefs,
                current = _statisticsSettings.value,
                enabled = enabled
            )
        }
    }

    fun setPredWeightedAlgorithmAlphaMaxX100(value: Int) {
        viewModelScope.launch {
            _statisticsSettings.value =
                UpdateStatisticsSettingsUseCase.updatePredWeightedAlgorithmAlphaMaxX100(
                    prefs = prefs,
                    current = _statisticsSettings.value,
                    value = value
                )
        }
    }

    /**
     * 让 App 进程内的 LoggerX 立即跟随设置值变化。
     *
     * @param settings 最新的服务端配置。
     * @return 无。
     */
    private fun applyLoggerSettings(settings: ServerSettings) {
        LoggerX.maxHistoryDays = settings.maxHistoryDays
        LoggerX.logLevel = settings.logLevel
    }
}
