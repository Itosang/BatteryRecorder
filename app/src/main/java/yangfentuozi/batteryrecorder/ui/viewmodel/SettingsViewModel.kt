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
import yangfentuozi.batteryrecorder.shared.config.AppSettings
import yangfentuozi.batteryrecorder.shared.config.ServerConfigDto
import yangfentuozi.batteryrecorder.shared.config.ServerSettings
import yangfentuozi.batteryrecorder.shared.config.ServerSettingsMapper
import yangfentuozi.batteryrecorder.shared.config.SettingsConstants
import yangfentuozi.batteryrecorder.shared.config.SharedSettings
import yangfentuozi.batteryrecorder.shared.config.StatisticsSettings
import yangfentuozi.batteryrecorder.shared.util.LoggerX

private const val TAG = "SettingsViewModel"

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

    val checkUpdateOnStartup: StateFlow<Boolean> =
        appSettings.map { it.checkUpdateOnStartup }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = AppSettings().checkUpdateOnStartup
            )

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

    val rootBootAutoStartEnabled: StateFlow<Boolean> =
        appSettings.map { it.rootBootAutoStartEnabled }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = AppSettings().rootBootAutoStartEnabled
            )

    val recordIntervalMs: StateFlow<Long> =
        serverSettings.map { it.recordIntervalMs }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = ServerSettings().recordIntervalMs
            )

    val writeLatencyMs: StateFlow<Long> =
        serverSettings.map { it.writeLatencyMs }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = ServerSettings().writeLatencyMs
            )

    val batchSize: StateFlow<Int> =
        serverSettings.map { it.batchSize }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = ServerSettings().batchSize
            )

    val screenOffRecord: StateFlow<Boolean> =
        serverSettings.map { it.screenOffRecordEnabled }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = ServerSettings().screenOffRecordEnabled
            )

    val alwaysPollingScreenStatusEnabled: StateFlow<Boolean> =
        serverSettings.map { it.alwaysPollingScreenStatusEnabled }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = ServerSettings().alwaysPollingScreenStatusEnabled
            )

    val segmentDurationMin: StateFlow<Long> =
        serverSettings.map { it.segmentDurationMin }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = ServerSettings().segmentDurationMin
            )

    val maxHistoryDays: StateFlow<Long> =
        serverSettings.map { it.maxHistoryDays }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = ServerSettings().maxHistoryDays
            )

    val logLevel: StateFlow<LoggerX.LogLevel> =
        serverSettings.map { it.logLevel }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = ServerSettings().logLevel
            )

    val gamePackages: StateFlow<Set<String>> =
        statisticsSettings.map { it.gamePackages }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = StatisticsSettings().gamePackages
            )

    val gameBlacklist: StateFlow<Set<String>> =
        statisticsSettings.map { it.gameBlacklist }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = StatisticsSettings().gameBlacklist
            )

    val sceneStatsRecentFileCount: StateFlow<Int> =
        statisticsSettings.map { it.sceneStatsRecentFileCount }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = StatisticsSettings().sceneStatsRecentFileCount
            )

    val predCurrentSessionWeightEnabled: StateFlow<Boolean> =
        statisticsSettings.map { it.predCurrentSessionWeightEnabled }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = StatisticsSettings().predCurrentSessionWeightEnabled
            )

    val predCurrentSessionWeightMaxX100: StateFlow<Int> =
        statisticsSettings.map { it.predCurrentSessionWeightMaxX100 }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = StatisticsSettings().predCurrentSessionWeightMaxX100
            )

    val predCurrentSessionWeightHalfLifeMin: StateFlow<Long> =
        statisticsSettings.map { it.predCurrentSessionWeightHalfLifeMin }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = StatisticsSettings().predCurrentSessionWeightHalfLifeMin
            )

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = SharedSettings.getPreferences(context)
        loadSettings()
    }

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

    fun reloadSettings() {
        if (::prefs.isInitialized) {
            loadSettings()
        }
    }

    private fun updateServerSettings(
        message: String,
        transform: (ServerSettings) -> ServerSettings
    ) {
        viewModelScope.launch {
            val normalizedSettings = SharedSettings.normalizeServerSettings(
                transform(_serverSettings.value)
            )
            SharedSettings.writeServerSettings(prefs, normalizedSettings)
            _serverSettings.value = normalizedSettings
            applyLoggerSettings(normalizedSettings)
            pushServerConfig(ServerSettingsMapper.toServerConfigDto(normalizedSettings), message)
        }
    }

    private fun pushServerConfig(serverConfigDto: ServerConfigDto, message: String) {
        LoggerX.i(TAG, message)
        Service.service?.updateConfig(serverConfigDto)
    }

    private fun applyLoggerSettings(settings: ServerSettings) {
        LoggerX.maxHistoryDays = settings.maxHistoryDays
        LoggerX.logLevel = settings.logLevel
    }
}
