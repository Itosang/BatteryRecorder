package yangfentuozi.batteryrecorder.ui.viewmodel

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import yangfentuozi.batteryrecorder.data.history.StatisticsRequest
import yangfentuozi.batteryrecorder.ipc.Service
import yangfentuozi.batteryrecorder.shared.config.Config
import yangfentuozi.batteryrecorder.shared.config.ConfigConstants
import yangfentuozi.batteryrecorder.ui.model.SettingsUiState

class SettingsViewModel : ViewModel() {
    private lateinit var prefs: SharedPreferences

    private val _checkUpdateOnStartup =
        MutableStateFlow(ConfigConstants.DEF_CHECK_UPDATE_ON_STARTUP)

    // 双电芯设置
    private val _dualCellEnabled = MutableStateFlow(ConfigConstants.DEF_DUAL_CELL_ENABLED)
    val dualCellEnabled: StateFlow<Boolean> = _dualCellEnabled.asStateFlow()

    // 放电电流显示为正值
    private val _dischargeDisplayPositive =
        MutableStateFlow(ConfigConstants.DEF_DISCHARGE_DISPLAY_POSITIVE)
    val dischargeDisplayPositive: StateFlow<Boolean> = _dischargeDisplayPositive.asStateFlow()

    // 校准值
    private val _calibrationValue = MutableStateFlow(ConfigConstants.DEF_CALIBRATION_VALUE)
    val calibrationValue: StateFlow<Int> = _calibrationValue.asStateFlow()

    // 采样间隔 (ms)
    private val _recordIntervalMs = MutableStateFlow(ConfigConstants.DEF_RECORD_INTERVAL_MS)
    val recordIntervalMs: StateFlow<Long> = _recordIntervalMs.asStateFlow()

    // 写入延迟 (ms)
    private val _writeLatencyMs = MutableStateFlow(ConfigConstants.DEF_WRITE_LATENCY_MS)
    val writeLatencyMs: StateFlow<Long> = _writeLatencyMs.asStateFlow()

    // 批次大小
    private val _batchSize = MutableStateFlow(ConfigConstants.DEF_BATCH_SIZE)
    val batchSize: StateFlow<Int> = _batchSize.asStateFlow()

    // 息屏时继续记录
    private val _screenOffRecord = MutableStateFlow(ConfigConstants.DEF_SCREEN_OFF_RECORD_ENABLED)
    val screenOffRecord: StateFlow<Boolean> = _screenOffRecord.asStateFlow()

    // 轮询检查息屏状态
    private val _alwaysPollingScreenStatusEnabled = MutableStateFlow(ConfigConstants.DEF_ALWAYS_POLLING_SCREEN_STATUS_ENABLED)
    val alwaysPollingScreenStatusEnabled: StateFlow<Boolean> = _alwaysPollingScreenStatusEnabled.asStateFlow()

    // 分段时间 (分钟)
    private val _segmentDurationMin = MutableStateFlow(ConfigConstants.DEF_SEGMENT_DURATION_MIN)
    val segmentDurationMin: StateFlow<Long> = _segmentDurationMin.asStateFlow()

    // 开机 ROOT 自启动
    private val _rootBootAutoStartEnabled =
        MutableStateFlow(ConfigConstants.DEF_ROOT_BOOT_AUTO_START_ENABLED)
    val rootBootAutoStartEnabled: StateFlow<Boolean> = _rootBootAutoStartEnabled.asStateFlow()

    // 游戏 App 包名列表
    private val _gamePackages = MutableStateFlow<Set<String>>(emptySet())
    val gamePackages: StateFlow<Set<String>> = _gamePackages.asStateFlow()

    // 用户主动排除的非游戏包名（自动检测时跳过）
    private val _gameBlacklist = MutableStateFlow<Set<String>>(emptySet())
    val gameBlacklist: StateFlow<Set<String>> = _gameBlacklist.asStateFlow()

    // 场景统计与预测样本数量
    private val _sceneStatsRecentFileCount =
        MutableStateFlow(ConfigConstants.DEF_SCENE_STATS_RECENT_FILE_COUNT)
    val sceneStatsRecentFileCount: StateFlow<Int> = _sceneStatsRecentFileCount.asStateFlow()

    // 当次记录加权预测
    private val _predCurrentSessionWeightEnabled =
        MutableStateFlow(ConfigConstants.DEF_PRED_CURRENT_SESSION_WEIGHT_ENABLED)
    val predCurrentSessionWeightEnabled: StateFlow<Boolean> =
        _predCurrentSessionWeightEnabled.asStateFlow()

    private val _predCurrentSessionWeightMaxX100 =
        MutableStateFlow(ConfigConstants.DEF_PRED_CURRENT_SESSION_WEIGHT_MAX_X100)
    val predCurrentSessionWeightMaxX100: StateFlow<Int> =
        _predCurrentSessionWeightMaxX100.asStateFlow()

    private val _predCurrentSessionWeightHalfLifeMin =
        MutableStateFlow(ConfigConstants.DEF_PRED_CURRENT_SESSION_WEIGHT_HALF_LIFE_MIN)
    val predCurrentSessionWeightHalfLifeMin: StateFlow<Long> =
        _predCurrentSessionWeightHalfLifeMin.asStateFlow()

    private val _settingsUiState = MutableStateFlow(SettingsUiState())
    val settingsUiState: StateFlow<SettingsUiState> = _settingsUiState.asStateFlow()

    private val _statisticsRequest = MutableStateFlow(StatisticsRequest())
    val statisticsRequest: StateFlow<StatisticsRequest> = _statisticsRequest.asStateFlow()

    private val _initialized = MutableStateFlow(false)
    val initialized: StateFlow<Boolean> = _initialized.asStateFlow()

    private lateinit var serverConfig: Config

    /**
     * 初始化 SharedPreferences
     */
    fun init(context: Context) {
        if (!::prefs.isInitialized) {
            prefs = context.getSharedPreferences(
                ConfigConstants.PREFS_NAME,
                Context.MODE_PRIVATE
            )
            loadSettings()
        }
    }

    /**
     * 从 SharedPreferences 加载设置
     */
    private fun loadSettings() {
        _checkUpdateOnStartup.value =
            prefs.getBoolean(
                ConfigConstants.KEY_CHECK_UPDATE_ON_STARTUP,
                ConfigConstants.DEF_CHECK_UPDATE_ON_STARTUP
            )

        _dualCellEnabled.value =
            prefs.getBoolean(
                ConfigConstants.KEY_DUAL_CELL_ENABLED,
                ConfigConstants.DEF_DUAL_CELL_ENABLED
            )

        _dischargeDisplayPositive.value =
            prefs.getBoolean(
                ConfigConstants.KEY_DISCHARGE_DISPLAY_POSITIVE,
                ConfigConstants.DEF_DISCHARGE_DISPLAY_POSITIVE
            )

        _calibrationValue.value =
            prefs.getInt(
                ConfigConstants.KEY_CALIBRATION_VALUE,
                ConfigConstants.DEF_CALIBRATION_VALUE
            ).coerceIn(
                ConfigConstants.MIN_CALIBRATION_VALUE,
                ConfigConstants.MAX_CALIBRATION_VALUE
            )

        _recordIntervalMs.value =
            prefs.getLong(
                ConfigConstants.KEY_RECORD_INTERVAL_MS,
                ConfigConstants.DEF_RECORD_INTERVAL_MS
            ).coerceIn(
                ConfigConstants.MIN_RECORD_INTERVAL_MS,
                ConfigConstants.MAX_RECORD_INTERVAL_MS
            )

        _writeLatencyMs.value =
            prefs.getLong(
                ConfigConstants.KEY_WRITE_LATENCY_MS,
                ConfigConstants.DEF_WRITE_LATENCY_MS
            ).coerceIn(
                ConfigConstants.MIN_WRITE_LATENCY_MS,
                ConfigConstants.MAX_WRITE_LATENCY_MS
            )

        _batchSize.value =
            prefs.getInt(
                ConfigConstants.KEY_BATCH_SIZE,
                ConfigConstants.DEF_BATCH_SIZE
            ).coerceIn(
                ConfigConstants.MIN_BATCH_SIZE,
                ConfigConstants.MAX_BATCH_SIZE
            )

        _screenOffRecord.value =
            prefs.getBoolean(
                ConfigConstants.KEY_SCREEN_OFF_RECORD_ENABLED,
                ConfigConstants.DEF_SCREEN_OFF_RECORD_ENABLED
            )

        _alwaysPollingScreenStatusEnabled.value =
            prefs.getBoolean(
                ConfigConstants.KEY_ALWAYS_POLLING_SCREEN_STATUS_ENABLED,
                ConfigConstants.DEF_ALWAYS_POLLING_SCREEN_STATUS_ENABLED
            )

        _segmentDurationMin.value =
            prefs.getLong(
                ConfigConstants.KEY_SEGMENT_DURATION_MIN,
                ConfigConstants.DEF_SEGMENT_DURATION_MIN
            ).coerceIn(
                ConfigConstants.MIN_SEGMENT_DURATION_MIN,
                ConfigConstants.MAX_SEGMENT_DURATION_MIN
            )

        _rootBootAutoStartEnabled.value =
            prefs.getBoolean(
                ConfigConstants.KEY_ROOT_BOOT_AUTO_START_ENABLED,
                ConfigConstants.DEF_ROOT_BOOT_AUTO_START_ENABLED
            )

        serverConfig = Config(
            recordIntervalMs = _recordIntervalMs.value,
            writeLatencyMs = _writeLatencyMs.value,
            batchSize = _batchSize.value,
            screenOffRecordEnabled = _screenOffRecord.value,
            segmentDurationMin = _segmentDurationMin.value
        )

        _gamePackages.value =
            prefs.getStringSet(ConfigConstants.KEY_GAME_PACKAGES, emptySet()) ?: emptySet()

        _gameBlacklist.value =
            prefs.getStringSet(ConfigConstants.KEY_GAME_BLACKLIST, emptySet()) ?: emptySet()

        _sceneStatsRecentFileCount.value =
            prefs.getInt(
                ConfigConstants.KEY_SCENE_STATS_RECENT_FILE_COUNT,
                ConfigConstants.DEF_SCENE_STATS_RECENT_FILE_COUNT
            ).coerceIn(
                ConfigConstants.MIN_SCENE_STATS_RECENT_FILE_COUNT,
                ConfigConstants.MAX_SCENE_STATS_RECENT_FILE_COUNT
            )

        _predCurrentSessionWeightEnabled.value =
            prefs.getBoolean(
                ConfigConstants.KEY_PRED_CURRENT_SESSION_WEIGHT_ENABLED,
                ConfigConstants.DEF_PRED_CURRENT_SESSION_WEIGHT_ENABLED
            )

        _predCurrentSessionWeightMaxX100.value =
            prefs.getInt(
                ConfigConstants.KEY_PRED_CURRENT_SESSION_WEIGHT_MAX_X100,
                ConfigConstants.DEF_PRED_CURRENT_SESSION_WEIGHT_MAX_X100
            ).coerceIn(
                ConfigConstants.MIN_PRED_CURRENT_SESSION_WEIGHT_MAX_X100,
                ConfigConstants.MAX_PRED_CURRENT_SESSION_WEIGHT_MAX_X100
            )

        _predCurrentSessionWeightHalfLifeMin.value =
            prefs.getLong(
                ConfigConstants.KEY_PRED_CURRENT_SESSION_WEIGHT_HALF_LIFE_MIN,
                ConfigConstants.DEF_PRED_CURRENT_SESSION_WEIGHT_HALF_LIFE_MIN
            ).coerceIn(
                ConfigConstants.MIN_PRED_CURRENT_SESSION_WEIGHT_HALF_LIFE_MIN,
                ConfigConstants.MAX_PRED_CURRENT_SESSION_WEIGHT_HALF_LIFE_MIN
            )

        refreshCombinedState()
        _initialized.value = true
    }

    /**
     * 更新双电芯设置
     */
    fun setCheckUpdateOnStartup(enabled: Boolean) {
        viewModelScope.launch {
            prefs.edit { putBoolean(ConfigConstants.KEY_CHECK_UPDATE_ON_STARTUP, enabled) }
            _checkUpdateOnStartup.value = enabled
            refreshCombinedState()
        }
    }

    fun setDualCellEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefs.edit { putBoolean(ConfigConstants.KEY_DUAL_CELL_ENABLED, enabled) }
            _dualCellEnabled.value = enabled
            refreshCombinedState()
        }
    }

    /**
     * 更新校准值
     */
    fun setDischargeDisplayPositiveEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefs.edit { putBoolean(ConfigConstants.KEY_DISCHARGE_DISPLAY_POSITIVE, enabled) }
            _dischargeDisplayPositive.value = enabled
            refreshCombinedState()
        }
    }

    fun setCalibrationValue(value: Int) {
        val finalValue =
            value.coerceIn(
                ConfigConstants.MIN_CALIBRATION_VALUE,
                ConfigConstants.MAX_CALIBRATION_VALUE
            )
        viewModelScope.launch {
            prefs.edit { putInt(ConfigConstants.KEY_CALIBRATION_VALUE, finalValue) }
            _calibrationValue.value = finalValue
            refreshCombinedState()
        }
    }

    /**
     * 更新采样间隔
     */
    fun setRecordIntervalMs(value: Long) {
        val finalValue =
            value.coerceIn(
                ConfigConstants.MIN_RECORD_INTERVAL_MS,
                ConfigConstants.MAX_RECORD_INTERVAL_MS
            )
        viewModelScope.launch {
            prefs.edit { putLong(ConfigConstants.KEY_RECORD_INTERVAL_MS, finalValue) }
            _recordIntervalMs.value = finalValue
            serverConfig = serverConfig.copy(recordIntervalMs = finalValue)
            Service.service?.updateConfig(serverConfig)
            refreshCombinedState()
        }
    }

    /**
     * 更新写入延迟
     */
    fun setWriteLatencyMs(value: Long) {
        val finalValue =
            value.coerceIn(
                ConfigConstants.MIN_WRITE_LATENCY_MS,
                ConfigConstants.MAX_WRITE_LATENCY_MS
            )
        viewModelScope.launch {
            prefs.edit { putLong(ConfigConstants.KEY_WRITE_LATENCY_MS, finalValue) }
            _writeLatencyMs.value = finalValue
            serverConfig = serverConfig.copy(writeLatencyMs = finalValue)
            Service.service?.updateConfig(serverConfig)
            refreshCombinedState()
        }
    }

    /**
     * 更新批次大小
     */
    fun setBatchSize(value: Int) {
        val finalValue =
            value.coerceIn(ConfigConstants.MIN_BATCH_SIZE, ConfigConstants.MAX_BATCH_SIZE)
        viewModelScope.launch {
            prefs.edit { putInt(ConfigConstants.KEY_BATCH_SIZE, finalValue) }
            _batchSize.value = finalValue
            serverConfig = serverConfig.copy(batchSize = finalValue)
            Service.service?.updateConfig(serverConfig)
            refreshCombinedState()
        }
    }

    fun setScreenOffRecordEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefs.edit { putBoolean(ConfigConstants.KEY_SCREEN_OFF_RECORD_ENABLED, enabled) }
            _screenOffRecord.value = enabled
            serverConfig = serverConfig.copy(screenOffRecordEnabled = enabled)
            Service.service?.updateConfig(serverConfig)
            refreshCombinedState()
        }
    }

    fun setAlwaysPollingScreenStatusEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefs.edit { putBoolean(ConfigConstants.KEY_ALWAYS_POLLING_SCREEN_STATUS_ENABLED, enabled) }
            _alwaysPollingScreenStatusEnabled.value = enabled
            serverConfig = serverConfig.copy(alwaysPollingScreenStatusEnabled = enabled)
            Service.service?.updateConfig(serverConfig)
            refreshCombinedState()
        }
    }

    fun setSegmentDurationMin(value: Long) {
        val finalValue =
            value.coerceIn(
                ConfigConstants.MIN_SEGMENT_DURATION_MIN,
                ConfigConstants.MAX_SEGMENT_DURATION_MIN
            )
        viewModelScope.launch {
            prefs.edit { putLong(ConfigConstants.KEY_SEGMENT_DURATION_MIN, finalValue) }
            _segmentDurationMin.value = finalValue
            serverConfig = serverConfig.copy(segmentDurationMin = finalValue)
            Service.service?.updateConfig(serverConfig)
            refreshCombinedState()
        }
    }

    fun setRootBootAutoStartEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefs.edit {
                putBoolean(ConfigConstants.KEY_ROOT_BOOT_AUTO_START_ENABLED, enabled)
            }
            _rootBootAutoStartEnabled.value = enabled
            refreshCombinedState()
        }
    }

    fun setGamePackages(packages: Set<String>, detectedGamePkgs: Set<String>) {
        viewModelScope.launch {
            // 用户取消勾选的自动检测游戏 → 加入 blacklist
            val newBlacklist = _gameBlacklist.value + (detectedGamePkgs - packages)
            prefs.edit {
                putStringSet(ConfigConstants.KEY_GAME_PACKAGES, packages)
                putStringSet(ConfigConstants.KEY_GAME_BLACKLIST, newBlacklist)
            }
            _gamePackages.value = packages
            _gameBlacklist.value = newBlacklist
            refreshCombinedState()
        }
    }

    fun setSceneStatsRecentFileCount(value: Int) {
        val finalValue = value.coerceIn(
            ConfigConstants.MIN_SCENE_STATS_RECENT_FILE_COUNT,
            ConfigConstants.MAX_SCENE_STATS_RECENT_FILE_COUNT
        )
        viewModelScope.launch {
            prefs.edit { putInt(ConfigConstants.KEY_SCENE_STATS_RECENT_FILE_COUNT, finalValue) }
            _sceneStatsRecentFileCount.value = finalValue
            refreshCombinedState()
        }
    }

    fun setPredCurrentSessionWeightEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefs.edit {
                putBoolean(
                    ConfigConstants.KEY_PRED_CURRENT_SESSION_WEIGHT_ENABLED,
                    enabled
                )
            }
            _predCurrentSessionWeightEnabled.value = enabled
            refreshCombinedState()
        }
    }

    fun setPredCurrentSessionWeightMaxX100(value: Int) {
        val finalValue = value.coerceIn(
            ConfigConstants.MIN_PRED_CURRENT_SESSION_WEIGHT_MAX_X100,
            ConfigConstants.MAX_PRED_CURRENT_SESSION_WEIGHT_MAX_X100
        )
        viewModelScope.launch {
            prefs.edit {
                putInt(
                    ConfigConstants.KEY_PRED_CURRENT_SESSION_WEIGHT_MAX_X100,
                    finalValue
                )
            }
            _predCurrentSessionWeightMaxX100.value = finalValue
            refreshCombinedState()
        }
    }

    fun setPredCurrentSessionWeightHalfLifeMin(value: Long) {
        val finalValue = value.coerceIn(
            ConfigConstants.MIN_PRED_CURRENT_SESSION_WEIGHT_HALF_LIFE_MIN,
            ConfigConstants.MAX_PRED_CURRENT_SESSION_WEIGHT_HALF_LIFE_MIN
        )
        viewModelScope.launch {
            prefs.edit {
                putLong(
                    ConfigConstants.KEY_PRED_CURRENT_SESSION_WEIGHT_HALF_LIFE_MIN,
                    finalValue
                )
            }
            _predCurrentSessionWeightHalfLifeMin.value = finalValue
            refreshCombinedState()
        }
    }

    private fun buildSettingsUiState(): SettingsUiState {
        return SettingsUiState(
            checkUpdateOnStartup = _checkUpdateOnStartup.value,
            dualCellEnabled = _dualCellEnabled.value,
            dischargeDisplayPositive = _dischargeDisplayPositive.value,
            calibrationValue = _calibrationValue.value,
            recordIntervalMs = _recordIntervalMs.value,
            writeLatencyMs = _writeLatencyMs.value,
            batchSize = _batchSize.value,
            recordScreenOffEnabled = _screenOffRecord.value,
            alwaysPollingScreenStatusEnabled = _alwaysPollingScreenStatusEnabled.value,
            segmentDurationMin = _segmentDurationMin.value,
            rootBootAutoStartEnabled = _rootBootAutoStartEnabled.value,
            gamePackages = _gamePackages.value,
            gameBlacklist = _gameBlacklist.value,
            sceneStatsRecentFileCount = _sceneStatsRecentFileCount.value,
            predCurrentSessionWeightEnabled = _predCurrentSessionWeightEnabled.value,
            predCurrentSessionWeightMaxX100 = _predCurrentSessionWeightMaxX100.value,
            predCurrentSessionWeightHalfLifeMin = _predCurrentSessionWeightHalfLifeMin.value
        )
    }

    private fun buildStatisticsRequest(): StatisticsRequest {
        return StatisticsRequest(
            gamePackages = _gamePackages.value,
            sceneStatsRecentFileCount = _sceneStatsRecentFileCount.value,
            recordIntervalMs = _recordIntervalMs.value,
            predCurrentSessionWeightEnabled = _predCurrentSessionWeightEnabled.value,
            predCurrentSessionWeightMaxX100 = _predCurrentSessionWeightMaxX100.value,
            predCurrentSessionWeightHalfLifeMin = _predCurrentSessionWeightHalfLifeMin.value
        )
    }

    private fun refreshCombinedState() {
        _settingsUiState.value = buildSettingsUiState()
        _statisticsRequest.value = buildStatisticsRequest()
    }

    /**
     * 重新加载设置（从 SharedPreferences）
     */
    fun reloadSettings() {
        if (::prefs.isInitialized) {
            loadSettings()
        }
    }
}
