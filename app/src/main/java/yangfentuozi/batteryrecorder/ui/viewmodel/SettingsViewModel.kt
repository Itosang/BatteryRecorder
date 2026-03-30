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
import yangfentuozi.batteryrecorder.shared.config.AppSettingKeys
import yangfentuozi.batteryrecorder.shared.config.AppSettings
import yangfentuozi.batteryrecorder.shared.config.ServerSettingKeys
import yangfentuozi.batteryrecorder.shared.config.ServerSettings
import yangfentuozi.batteryrecorder.shared.config.ServerSettingsMapper
import yangfentuozi.batteryrecorder.shared.config.SharedSettings
import yangfentuozi.batteryrecorder.shared.config.StatisticsSettingKeys
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
                putBoolean(AppSettingKeys.CHECK_UPDATE_ON_STARTUP, enabled)
            }
            _appSettings.value = _appSettings.value.copy(checkUpdateOnStartup = enabled)
        }
    }

    fun setDualCellEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefs.edit {
                putBoolean(AppSettingKeys.DUAL_CELL_ENABLED, enabled)
            }
            _appSettings.value = _appSettings.value.copy(dualCellEnabled = enabled)
        }
    }

    fun setDischargeDisplayPositiveEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefs.edit {
                putBoolean(AppSettingKeys.DISCHARGE_DISPLAY_POSITIVE, enabled)
            }
            _appSettings.value = _appSettings.value.copy(dischargeDisplayPositive = enabled)
        }
    }

    fun setCalibrationValue(value: Int) {
        val finalValue = SharedSettings.normalizeCalibrationValue(value)
        viewModelScope.launch {
            prefs.edit {
                putInt(AppSettingKeys.CALIBRATION_VALUE, finalValue)
            }
            _appSettings.value = _appSettings.value.copy(calibrationValue = finalValue)
        }
    }

    fun setRecordIntervalMs(value: Long) {
        val finalValue = SharedSettings.normalizeRecordIntervalMs(value)
        viewModelScope.launch {
            prefs.edit {
                putLong(ServerSettingKeys.RECORD_INTERVAL_MS, finalValue)
            }
            _serverSettings.value = _serverSettings.value.copy(recordIntervalMs = finalValue)
            pushServerConfig(_serverSettings.value, "[设置] 更新记录间隔并准备下发: intervalMs=$finalValue")
        }
    }

    fun setWriteLatencyMs(value: Long) {
        val finalValue = SharedSettings.normalizeWriteLatencyMs(value)
        viewModelScope.launch {
            prefs.edit {
                putLong(ServerSettingKeys.WRITE_LATENCY_MS, finalValue)
            }
            _serverSettings.value = _serverSettings.value.copy(writeLatencyMs = finalValue)
            pushServerConfig(_serverSettings.value, "[设置] 更新写入延迟并准备下发: writeLatencyMs=$finalValue")
        }
    }

    fun setBatchSize(value: Int) {
        val finalValue = SharedSettings.normalizeBatchSize(value)
        viewModelScope.launch {
            prefs.edit {
                putInt(ServerSettingKeys.BATCH_SIZE, finalValue)
            }
            _serverSettings.value = _serverSettings.value.copy(batchSize = finalValue)
            pushServerConfig(_serverSettings.value, "[设置] 更新批次大小并准备下发: batchSize=$finalValue")
        }
    }

    fun setScreenOffRecordEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefs.edit {
                putBoolean(ServerSettingKeys.SCREEN_OFF_RECORD_ENABLED, enabled)
            }
            _serverSettings.value = _serverSettings.value.copy(screenOffRecordEnabled = enabled)
            pushServerConfig(_serverSettings.value, "[设置] 更新息屏记录并准备下发: enabled=$enabled")
        }
    }

    fun setAlwaysPollingScreenStatusEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefs.edit {
                putBoolean(ServerSettingKeys.ALWAYS_POLLING_SCREEN_STATUS_ENABLED, enabled)
            }
            _serverSettings.value =
                _serverSettings.value.copy(alwaysPollingScreenStatusEnabled = enabled)
            pushServerConfig(_serverSettings.value, "[设置] 更新轮询亮屏状态并准备下发: enabled=$enabled")
        }
    }

    fun setSegmentDurationMin(value: Long) {
        val finalValue = SharedSettings.normalizeSegmentDurationMin(value)
        viewModelScope.launch {
            prefs.edit {
                putLong(ServerSettingKeys.SEGMENT_DURATION_MIN, finalValue)
            }
            _serverSettings.value = _serverSettings.value.copy(segmentDurationMin = finalValue)
            pushServerConfig(_serverSettings.value, "[设置] 更新分段时长并准备下发: value=$finalValue")
        }
    }

    fun setRootBootAutoStartEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefs.edit {
                putBoolean(AppSettingKeys.ROOT_BOOT_AUTO_START_ENABLED, enabled)
            }
            _appSettings.value = _appSettings.value.copy(rootBootAutoStartEnabled = enabled)
        }
    }

    fun setMaxHistoryDays(value: Long) {
        val finalValue = SharedSettings.normalizeMaxHistoryDays(value)
        viewModelScope.launch {
            prefs.edit {
                putLong(ServerSettingKeys.MAX_HISTORY_DAYS, finalValue)
            }
            _serverSettings.value = _serverSettings.value.copy(maxHistoryDays = finalValue)
            applyLoggerSettings(_serverSettings.value)
            pushServerConfig(_serverSettings.value, "[设置] 更新日志保留天数并准备下发: maxHistoryDays=$finalValue")
        }
    }

    fun setLogLevel(value: LoggerX.LogLevel) {
        viewModelScope.launch {
            prefs.edit {
                putInt(ServerSettingKeys.LOG_LEVEL, SharedSettings.encodeLogLevel(value))
            }
            _serverSettings.value = _serverSettings.value.copy(logLevel = value)
            applyLoggerSettings(_serverSettings.value)
            pushServerConfig(_serverSettings.value, "[设置] 更新日志级别并准备下发: logLevel=$value")
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
                putStringSet(StatisticsSettingKeys.GAME_PACKAGES, packages)
                putStringSet(StatisticsSettingKeys.GAME_BLACKLIST, newBlacklist)
            }
            _statisticsSettings.value = updated
        }
    }

    fun setSceneStatsRecentFileCount(value: Int) {
        val finalValue = SharedSettings.normalizeSceneStatsRecentFileCount(value)
        viewModelScope.launch {
            prefs.edit {
                putInt(StatisticsSettingKeys.SCENE_STATS_RECENT_FILE_COUNT, finalValue)
            }
            _statisticsSettings.value =
                _statisticsSettings.value.copy(sceneStatsRecentFileCount = finalValue)
        }
    }

    fun setPredCurrentSessionWeightEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefs.edit {
                putBoolean(StatisticsSettingKeys.PRED_CURRENT_SESSION_WEIGHT_ENABLED, enabled)
            }
            _statisticsSettings.value =
                _statisticsSettings.value.copy(predCurrentSessionWeightEnabled = enabled)
        }
    }

    fun setPredCurrentSessionWeightMaxX100(value: Int) {
        val finalValue = SharedSettings.normalizePredCurrentSessionWeightMaxX100(value)
        viewModelScope.launch {
            prefs.edit {
                putInt(StatisticsSettingKeys.PRED_CURRENT_SESSION_WEIGHT_MAX_X100, finalValue)
            }
            _statisticsSettings.value =
                _statisticsSettings.value.copy(predCurrentSessionWeightMaxX100 = finalValue)
        }
    }

    fun setPredCurrentSessionWeightHalfLifeMin(value: Long) {
        val finalValue = SharedSettings.normalizePredCurrentSessionWeightHalfLifeMin(value)
        viewModelScope.launch {
            prefs.edit {
                putLong(StatisticsSettingKeys.PRED_CURRENT_SESSION_WEIGHT_HALF_LIFE_MIN, finalValue)
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

    private fun pushServerConfig(settings: ServerSettings, message: String) {
        LoggerX.i(TAG, message)
        Service.service?.updateConfig(ServerSettingsMapper.toConfig(settings))
    }

    private fun applyLoggerSettings(settings: ServerSettings) {
        LoggerX.maxHistoryDays = settings.maxHistoryDays
        LoggerX.logLevel = settings.logLevel
    }
}
