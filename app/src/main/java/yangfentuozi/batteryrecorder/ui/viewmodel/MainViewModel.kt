package yangfentuozi.batteryrecorder.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import yangfentuozi.batteryrecorder.R
import yangfentuozi.batteryrecorder.appString
import yangfentuozi.batteryrecorder.data.history.HistorySummary
import yangfentuozi.batteryrecorder.data.history.PredictionResult
import yangfentuozi.batteryrecorder.data.history.SceneStats
import yangfentuozi.batteryrecorder.ipc.Service
import yangfentuozi.batteryrecorder.shared.config.SettingsConstants
import yangfentuozi.batteryrecorder.shared.data.BatteryStatus
import yangfentuozi.batteryrecorder.shared.data.RecordsFile
import yangfentuozi.batteryrecorder.shared.util.LoggerX
import yangfentuozi.batteryrecorder.ui.model.CurrentRecordUiState
import yangfentuozi.batteryrecorder.ui.model.HomePredictionDisplay
import yangfentuozi.batteryrecorder.ui.model.LiveRecordSample
import yangfentuozi.batteryrecorder.ui.stateholder.LiveRecordSessionStateHolder
import yangfentuozi.batteryrecorder.usecase.home.ExportLogsUseCase
import yangfentuozi.batteryrecorder.usecase.home.LoadHomeStatsUseCase
import yangfentuozi.batteryrecorder.shared.config.dataclass.StatisticsSettings

private const val TAG = "MainViewModel"

private enum class StatisticsRefreshMode {
    ClearAndReload,
    TrackCurrentRecord
}

class MainViewModel : ViewModel() {
    private val _serviceConnected = MutableStateFlow(false)
    val serviceConnected: StateFlow<Boolean> = _serviceConnected.asStateFlow()

    private val _showStopDialog = MutableStateFlow(false)
    val showStopDialog: StateFlow<Boolean> = _showStopDialog.asStateFlow()

    private val _showAboutDialog = MutableStateFlow(false)
    val showAboutDialog: StateFlow<Boolean> = _showAboutDialog.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    private val _chargeSummary = MutableStateFlow<HistorySummary?>(null)
    val chargeSummary: StateFlow<HistorySummary?> = _chargeSummary.asStateFlow()

    private val _dischargeSummary = MutableStateFlow<HistorySummary?>(null)
    val dischargeSummary: StateFlow<HistorySummary?> = _dischargeSummary.asStateFlow()

    private val _currentRecordUiState = MutableStateFlow(CurrentRecordUiState())
    val currentRecordUiState: StateFlow<CurrentRecordUiState> = _currentRecordUiState.asStateFlow()

    private val _isLoadingStats = MutableStateFlow(false)
    val isLoadingStats: StateFlow<Boolean> = _isLoadingStats.asStateFlow()

    // 场景统计独立于当前记录充放电语义，每次首页统计刷新都按最近放电历史重算。
    private val _sceneStats = MutableStateFlow<SceneStats?>(null)
    val sceneStats: StateFlow<SceneStats?> = _sceneStats.asStateFlow()

    // 仅在拿到当前电量后更新；充电态仍只使用历史放电模型。
    private val _prediction = MutableStateFlow<PredictionResult?>(null)
    val prediction: StateFlow<PredictionResult?> = _prediction.asStateFlow()

    private val _predictionDisplay = MutableStateFlow<HomePredictionDisplay?>(null)
    val predictionDisplay: StateFlow<HomePredictionDisplay?> = _predictionDisplay.asStateFlow()

    private val mainHandler = Handler(Looper.getMainLooper())

    private var statisticsJob: Job? = null
    private var statisticsGeneration: Long = 0L
    private var pendingCurrentRecordsFile: RecordsFile? = null
    private val liveRecordSessionStateHolder = LiveRecordSessionStateHolder()

    private val serviceListener = object : Service.ServiceConnection {
        override fun onServiceConnected() {
            LoggerX.i(TAG, "[首页] 服务已连接")
            mainHandler.post {
                _serviceConnected.value = true
            }
        }

        override fun onServiceDisconnected() {
            LoggerX.w(TAG, "[首页] 服务已断开")
            mainHandler.post {
                _serviceConnected.value = false
            }
        }
    }

    init {
        Service.addListener(serviceListener)
        _serviceConnected.value = Service.service != null
        LoggerX.d(TAG, "[首页] MainViewModel 初始化: serviceConnected=${_serviceConnected.value}")
    }

    override fun onCleared() {
        Service.removeListener(serviceListener)
        mainHandler.removeCallbacksAndMessages(null)
    }

    fun showStopDialog() {
        _showStopDialog.value = true
    }

    fun dismissStopDialog() {
        _showStopDialog.value = false
    }

    fun stopService() {
        if (Service.service == null) {
            LoggerX.w(TAG, "[首页] 用户请求停止服务，但服务未连接")
        } else {
            LoggerX.i(TAG, "[首页] 用户请求停止服务")
        }
        Thread {
            Service.service?.stopService()
        }.start()
    }

    fun showAboutDialog() {
        _showAboutDialog.value = true
    }

    fun dismissAboutDialog() {
        _showAboutDialog.value = false
    }

    /**
     * 导出首页日志 ZIP。
     *
     * @param context 应用上下文。
     * @param destinationUri SAF 目标 URI。
     * @return 无返回值。
     */
    fun exportLogs(context: Context, destinationUri: Uri) {
        viewModelScope.launch {
            try {
                LoggerX.i(TAG, "exportLogs: 开始导出首页日志", notWrite = true)
                _userMessage.value = ExportLogsUseCase.execute(
                    context = context,
                    destinationUri = destinationUri
                ).userMessage
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LoggerX.e(TAG, "exportLogs: 日志导出失败", tr = e)
                _userMessage.value = appString(R.string.toast_export_failed)
            }
        }
    }

    fun consumeUserMessage() {
        _userMessage.value = null
    }

    /**
     * 确保当前记录链路的状态更新始终在主线程执行。
     *
     * @param action 需要在主线程执行的状态更新逻辑。
     * @return 无返回值。
     */
    private fun runOnMainThread(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
            return
        }
        mainHandler.post(action)
    }

    fun loadStatistics(
        context: Context,
        request: StatisticsSettings = StatisticsSettings(),
        recordIntervalMs: Long = SettingsConstants.recordIntervalMs.def
    ) {
        if (_isLoadingStats.value) {
            LoggerX.v(TAG, "[首页] loadStatistics 已在进行，跳过重复请求")
            return
        }
        startLoadStatistics(
            context = context,
            request = request,
            recordIntervalMs = recordIntervalMs,
            mode = StatisticsRefreshMode.ClearAndReload
        )
    }

    fun refreshStatistics(
        context: Context,
        request: StatisticsSettings = StatisticsSettings(),
        recordIntervalMs: Long = SettingsConstants.recordIntervalMs.def
    ) {
        if (_isLoadingStats.value) {
            LoggerX.v(TAG, "[首页] refreshStatistics 已在进行，跳过")
            return
        }
        startLoadStatistics(
            context = context,
            request = request,
            recordIntervalMs = recordIntervalMs,
            mode = StatisticsRefreshMode.ClearAndReload
        )
    }

    fun forceRefreshStatistics(
        context: Context,
        request: StatisticsSettings = StatisticsSettings(),
        recordIntervalMs: Long = SettingsConstants.recordIntervalMs.def
    ) {
        statisticsJob?.cancel()
        startLoadStatistics(
            context = context,
            request = request,
            recordIntervalMs = recordIntervalMs,
            mode = StatisticsRefreshMode.ClearAndReload
        )
    }

    /**
     * 刷新首页统计，并跟踪当前记录文件切换状态。
     *
     * @param context 应用上下文。
     * @param request 当前首页统计请求。
     * @param expectedCurrentRecordsFile 期望收敛到的当前记录文件，可为空。
     * @return 无返回值。
     */
    fun refreshStatisticsTrackingCurrentRecord(
        context: Context,
        request: StatisticsSettings = StatisticsSettings(),
        recordIntervalMs: Long = SettingsConstants.recordIntervalMs.def,
        expectedCurrentRecordsFile: RecordsFile? = null
    ) {
        statisticsJob?.cancel()
        startLoadStatistics(
            context = context,
            request = request,
            recordIntervalMs = recordIntervalMs,
            mode = StatisticsRefreshMode.TrackCurrentRecord,
            expectedCurrentRecordsFile = expectedCurrentRecordsFile
        )
    }

    /**
     * 处理服务端当前记录文件切换通知。
     *
     * @param context 应用上下文。
     * @param request 当前首页统计请求。
     * @param recordsFile 服务端最新当前记录文件。
     * @return 无返回值。
     */
    fun onCurrentRecordsFileChanged(
        context: Context,
        request: StatisticsSettings,
        recordIntervalMs: Long,
        recordsFile: RecordsFile
    ) {
        runOnMainThread {
            if (liveRecordSessionStateHolder.activeRecordsFileName() == recordsFile.name) {
                return@runOnMainThread
            }
            pendingCurrentRecordsFile = recordsFile
            liveRecordSessionStateHolder.switchActiveSegment(recordsFile.name)
            _currentRecordUiState.value =
                _currentRecordUiState.value.copy(
                    recordsFileName = recordsFile.name,
                    displayStatus = recordsFile.type,
                    isSwitching = true,
                    record = null,
                    livePoints = emptyList()
                )
            LoggerX.d(TAG, "[首页] 当前记录文件已切换，等待有效样本: ${recordsFile.name}")
            refreshStatisticsTrackingCurrentRecord(
                context = context.applicationContext,
                request = request,
                recordIntervalMs = recordIntervalMs,
                expectedCurrentRecordsFile = recordsFile
            )
        }
    }

    /**
     * 处理首页实时采样事件。
     *
     * @param context 应用上下文。
     * @param request 当前首页统计请求。
     * @param sample 首页实时采样数据。
     * @return 无返回值。
     */
    fun onRecordSample(
        context: Context,
        request: StatisticsSettings,
        recordIntervalMs: Long,
        sample: LiveRecordSample
    ) {
        runOnMainThread {
            val pendingFile = pendingCurrentRecordsFile
            if (
                pendingFile != null &&
                liveRecordSessionStateHolder.activeRecordsFileName() != pendingFile.name
            ) {
                liveRecordSessionStateHolder.switchActiveSegment(pendingFile.name)
            }
            val nextDisplayStatus = pendingFile?.type ?: sample.status
            val currentUiState = _currentRecordUiState.value
            val nextLivePoints = if (liveRecordSessionStateHolder.activeRecordsFileName() != null) {
                liveRecordSessionStateHolder.appendLivePoint(sample.power)
            } else {
                currentUiState.livePoints
            }
            _currentRecordUiState.value =
                currentUiState.copy(
                    recordsFileName = liveRecordSessionStateHolder.activeRecordsFileName(),
                    displayStatus = nextDisplayStatus,
                    livePoints = nextLivePoints,
                    lastTemp = sample.temp
                )
            if (pendingFile != null && !_isLoadingStats.value) {
                LoggerX.v(TAG, "[首页] 收到新采样，重试当前分段: ${pendingFile.name}")
                refreshStatisticsTrackingCurrentRecord(
                    context = context.applicationContext,
                    request = request,
                    recordIntervalMs = recordIntervalMs,
                    expectedCurrentRecordsFile = pendingFile
                )
            }
        }
    }

    private suspend fun getServiceCurrentRecordsFile(): RecordsFile? {
        return withContext(Dispatchers.IO) {
            runCatching { Service.service?.currRecordsFile }
                .onFailure { error ->
                    LoggerX.w(TAG, "[首页] 读取当前记录文件失败", tr = error)
                }
                .getOrNull()
        }
    }

    private fun clearDisplayedHomeState() {
        pendingCurrentRecordsFile = null
        liveRecordSessionStateHolder.clear()
        _chargeSummary.value = null
        _dischargeSummary.value = null
        _currentRecordUiState.value = CurrentRecordUiState()
    }

    private fun startLoadStatistics(
        context: Context,
        request: StatisticsSettings,
        recordIntervalMs: Long,
        mode: StatisticsRefreshMode,
        expectedCurrentRecordsFile: RecordsFile? = null
    ) {
        if (mode == StatisticsRefreshMode.ClearAndReload) {
            clearDisplayedHomeState()
        }

        val generation = ++statisticsGeneration
        LoggerX.i(
            TAG,
            "[首页] 开始加载统计: generation=$generation mode=$mode recentFileCount=${request.sceneStatsRecentFileCount} intervalMs=$recordIntervalMs"
        )
        _isLoadingStats.value = true
        statisticsJob = viewModelScope.launch {
            try {
                val serviceCurrentRecordsFile = getServiceCurrentRecordsFile()
                val loadResult = LoadHomeStatsUseCase.execute(
                    context = context,
                    request = request,
                    recordIntervalMs = recordIntervalMs,
                    pendingCurrentRecordsFile = pendingCurrentRecordsFile,
                    expectedCurrentRecordsFile = expectedCurrentRecordsFile,
                    serviceCurrentRecordsFile = serviceCurrentRecordsFile
                )
                if (generation != statisticsGeneration) return@launch

                _chargeSummary.value = loadResult.chargeSummary
                _dischargeSummary.value = loadResult.dischargeSummary
                pendingCurrentRecordsFile = loadResult.nextPendingRecordsFile
                liveRecordSessionStateHolder.switchActiveSegment(
                    loadResult.nextActiveLiveRecordsFileName
                )
                val currentUiState = _currentRecordUiState.value
                _currentRecordUiState.value =
                    currentUiState.copy(
                        recordsFileName = liveRecordSessionStateHolder.activeRecordsFileName(),
                        displayStatus = loadResult.nextDisplayStatus ?: currentUiState.displayStatus,
                        isSwitching = loadResult.nextPendingRecordsFile != null,
                        record = loadResult.currentRecord,
                        livePoints = liveRecordSessionStateHolder.snapshotLivePoints()
                    )

                _sceneStats.value = loadResult.sceneStats
                if (loadResult.shouldUpdatePrediction) {
                    _prediction.value = loadResult.prediction
                    _predictionDisplay.value = loadResult.predictionDisplay
                }
                loadResult.currentRecordFailureMessage?.let { message ->
                    _userMessage.value = message
                }
                LoggerX.i(
                    TAG,
                    "[首页] 统计加载完成: generation=$generation currentRecord=${loadResult.currentRecord?.name} pending=${loadResult.nextPendingRecordsFile?.name}"
                )
            } finally {
                if (generation == statisticsGeneration) {
                    LoggerX.d(TAG, "[首页] 统计任务结束: generation=$generation")
                    _isLoadingStats.value = false
                }
            }
        }
    }
}
