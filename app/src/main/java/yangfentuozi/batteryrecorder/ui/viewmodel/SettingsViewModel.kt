package yangfentuozi.batteryrecorder.ui.viewmodel

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import yangfentuozi.batteryrecorder.ipc.Service
import yangfentuozi.batteryrecorder.shared.config.SettingsConstants
import yangfentuozi.batteryrecorder.shared.config.SharedSettings
import yangfentuozi.batteryrecorder.shared.config.dataclass.AppSettings
import yangfentuozi.batteryrecorder.shared.config.dataclass.ServerSettings
import yangfentuozi.batteryrecorder.shared.config.dataclass.StatisticsSettings
import yangfentuozi.batteryrecorder.shared.util.LoggerX

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
        appSettings.map { it.dualCellEnabled }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = AppSettings().dualCellEnabled
            )

    val dischargeDisplayPositive: StateFlow<Boolean> =
        appSettings.map { it.dischargeDisplayPositive }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = AppSettings().dischargeDisplayPositive
            )

    val calibrationValue: StateFlow<Int> =
        appSettings.map { it.calibrationValue }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = AppSettings().calibrationValue
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
        val currentAppSettings = SharedSettings.readAppSettings(prefs)
        val currentStatisticsSettings = SharedSettings.readStatisticsSettings(prefs)
        val currentServerSettings = SharedSettings.readServerSettings(prefs)

        _appSettings.value = currentAppSettings
        _statisticsSettings.value = currentStatisticsSettings
        _serverSettings.value = currentServerSettings
        applyLoggerSettings(currentServerSettings)
        _initialized.value = true

        LoggerX.d(
            TAG,
            "[设置] loadSettings 完成: intervalMs=${currentServerSettings.recordIntervalMs} writeLatencyMs=${currentServerSettings.writeLatencyMs} batchSize=${currentServerSettings.batchSize} screenOffRecord=${currentServerSettings.screenOffRecordEnabled} polling=${currentServerSettings.alwaysPollingScreenStatusEnabled} logLevel=${currentServerSettings.logLevel}"
        )
    }

    fun setCheckUpdateOnStartup(enabled: Boolean) {
        viewModelScope.launch {
            prefs.edit {
                SettingsConstants.checkUpdateOnStartup.writeToSP(this, enabled)
            }
            _appSettings.value = _appSettings.value.copy(checkUpdateOnStartup = enabled)
        }
    }

    fun setDualCellEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefs.edit {
                SettingsConstants.dualCellEnabled.writeToSP(this, enabled)
            }
            _appSettings.value = _appSettings.value.copy(dualCellEnabled = enabled)
        }
    }

    fun setDischargeDisplayPositiveEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefs.edit {
                SettingsConstants.dischargeDisplayPositive.writeToSP(this, enabled)
            }
            _appSettings.value = _appSettings.value.copy(dischargeDisplayPositive = enabled)
        }
    }

    fun setCalibrationValue(value: Int) {
        val finalValue = SettingsConstants.calibrationValue.coerce(value)
        viewModelScope.launch {
            prefs.edit {
                SettingsConstants.calibrationValue.writeToSP(this, finalValue)
            }
            _appSettings.value = _appSettings.value.copy(calibrationValue = finalValue)
        }
    }

    fun setRecordIntervalMs(value: Long) {
        val finalValue = SettingsConstants.recordIntervalMs.coerce(value)
        updateServerSettings(
            message = "[设置] 更新记录间隔并准备下发: intervalMs=$finalValue"
        ) { current ->
            current.copy(recordIntervalMs = finalValue)
        }
    }

    fun setWriteLatencyMs(value: Long) {
        val finalValue = SettingsConstants.writeLatencyMs.coerce(value)
        updateServerSettings(
            message = "[设置] 更新写入延迟并准备下发: writeLatencyMs=$finalValue"
        ) { current ->
            current.copy(writeLatencyMs = finalValue)
        }
    }

    fun setBatchSize(value: Int) {
        val finalValue = SettingsConstants.batchSize.coerce(value)
        updateServerSettings(
            message = "[设置] 更新批次大小并准备下发: batchSize=$finalValue"
        ) { current ->
            current.copy(batchSize = finalValue)
        }
    }

    fun setScreenOffRecordEnabled(enabled: Boolean) {
        updateServerSettings(
            message = "[设置] 更新息屏记录并准备下发: enabled=$enabled"
        ) { current ->
            current.copy(screenOffRecordEnabled = enabled)
        }
    }

    fun setAlwaysPollingScreenStatusEnabled(enabled: Boolean) {
        updateServerSettings(
            message = "[设置] 更新轮询亮屏状态并准备下发: enabled=$enabled"
        ) { current ->
            current.copy(alwaysPollingScreenStatusEnabled = enabled)
        }
    }

    fun setSegmentDurationMin(value: Long) {
        val finalValue = SettingsConstants.segmentDurationMin.coerce(value)
        updateServerSettings(
            message = "[设置] 更新分段时长并准备下发: value=$finalValue"
        ) { current ->
            current.copy(segmentDurationMin = finalValue)
        }
    }

    fun setRootBootAutoStartEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefs.edit {
                SettingsConstants.rootBootAutoStartEnabled.writeToSP(this, enabled)
            }
            _appSettings.value = _appSettings.value.copy(rootBootAutoStartEnabled = enabled)
        }
    }

    fun setMaxHistoryDays(value: Long) {
        val finalValue = SettingsConstants.logMaxHistoryDays.coerce(value)
        updateServerSettings(
            message = "[设置] 更新日志保留天数并准备下发: maxHistoryDays=$finalValue"
        ) { current ->
            current.copy(maxHistoryDays = finalValue)
        }
    }

    fun setLogLevel(value: LoggerX.LogLevel) {
        updateServerSettings(
            message = "[设置] 更新日志级别并准备下发: logLevel=$value"
        ) { current ->
            current.copy(logLevel = value)
        }
    }

    fun setGamePackages(packages: Set<String>, detectedGamePkgs: Set<String>) {
        viewModelScope.launch {
            val current = _statisticsSettings.value
            val newBlacklist = current.gameBlacklist + (detectedGamePkgs - packages)
            val updated = current.copy(
                gamePackages = packages,
                gameBlacklist = newBlacklist
            )
            prefs.edit {
                SettingsConstants.gamePackages.writeToSP(this, packages)
                SettingsConstants.gameBlacklist.writeToSP(this, newBlacklist)
            }
            _statisticsSettings.value = updated
        }
    }

    fun setSceneStatsRecentFileCount(value: Int) {
        val finalValue = SettingsConstants.sceneStatsRecentFileCount.coerce(value)
        viewModelScope.launch {
            prefs.edit {
                SettingsConstants.sceneStatsRecentFileCount.writeToSP(this, finalValue)
            }
            _statisticsSettings.value =
                _statisticsSettings.value.copy(sceneStatsRecentFileCount = finalValue)
        }
    }

    fun setPredCurrentSessionWeightEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefs.edit {
                SettingsConstants.predCurrentSessionWeightEnabled.writeToSP(this, enabled)
            }
            _statisticsSettings.value =
                _statisticsSettings.value.copy(predCurrentSessionWeightEnabled = enabled)
        }
    }

    fun setPredCurrentSessionWeightMaxX100(value: Int) {
        val finalValue = SettingsConstants.predCurrentSessionWeightMaxX100.coerce(value)
        viewModelScope.launch {
            prefs.edit {
                SettingsConstants.predCurrentSessionWeightMaxX100.writeToSP(this, finalValue)
            }
            _statisticsSettings.value =
                _statisticsSettings.value.copy(predCurrentSessionWeightMaxX100 = finalValue)
        }
    }

    fun setPredCurrentSessionWeightHalfLifeMin(value: Long) {
        val finalValue = SettingsConstants.predCurrentSessionWeightHalfLifeMin.coerce(value)
        viewModelScope.launch {
            prefs.edit {
                SettingsConstants.predCurrentSessionWeightHalfLifeMin.writeToSP(this, finalValue)
            }
            _statisticsSettings.value =
                _statisticsSettings.value.copy(predCurrentSessionWeightHalfLifeMin = finalValue)
        }
    }

    /**
     * 统一处理服务端设置的持久化、内存态回填和运行中下发。
     *
     * 当前约束是：
     * 1. 数值合法化在各个 setter 里完成。
     * 2. 这里不再额外做总裁剪。
     * 3. 写盘、内存态和 Binder 下发使用同一份 `ServerSettings`。
     *
     * @param message 下发前输出的日志文案。
     * @param transform 基于当前 `ServerSettings` 构造新配置的转换函数。
     * @return 无。
     */
    private fun updateServerSettings(
        message: String,
        transform: (ServerSettings) -> ServerSettings
    ) {
        viewModelScope.launch {
            val updatedSettings = transform(_serverSettings.value)
            SharedSettings.writeServerSettings(prefs, updatedSettings)
            _serverSettings.value = updatedSettings
            applyLoggerSettings(updatedSettings)
            pushServerConfig(updatedSettings, message)
        }
    }

    /**
     * 将新的 ServerSettings 下发给运行中的服务端。
     *
     * @param serverSettings 已经准备好下发的服务端配置。
     * @param message 本次更新的日志文案。
     * @return 无；若服务尚未连接，则仅更新本地状态。
     */
    private fun pushServerConfig(serverSettings: ServerSettings, message: String) {
        LoggerX.i(TAG, message)
        Service.service?.updateConfig(serverSettings)
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
