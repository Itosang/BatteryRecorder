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
import yangfentuozi.batteryrecorder.data.history.BatteryPredictor
import yangfentuozi.batteryrecorder.data.history.CurrentRecordLoadResult
import yangfentuozi.batteryrecorder.data.history.HistoryRecord
import yangfentuozi.batteryrecorder.data.history.HistoryRepository
import yangfentuozi.batteryrecorder.data.history.HistorySummary
import yangfentuozi.batteryrecorder.data.history.PredictionResult
import yangfentuozi.batteryrecorder.data.history.SceneStats
import yangfentuozi.batteryrecorder.data.history.SceneStatsComputer
import yangfentuozi.batteryrecorder.data.history.SyncUtil
import yangfentuozi.batteryrecorder.data.log.LogRepository
import yangfentuozi.batteryrecorder.ipc.Service
import yangfentuozi.batteryrecorder.shared.config.SettingsConstants
import yangfentuozi.batteryrecorder.shared.config.dataclass.StatisticsSettings
import yangfentuozi.batteryrecorder.shared.data.BatteryStatus
import yangfentuozi.batteryrecorder.shared.data.RecordsFile
import yangfentuozi.batteryrecorder.shared.util.LoggerX
import yangfentuozi.batteryrecorder.ui.model.CurrentRecordUiState
import yangfentuozi.batteryrecorder.ui.model.LiveRecordSample

private const val TAG = "MainViewModel"

private const val MAX_LIVE_POINTS = 20

private enum class StatisticsRefreshMode {
    ClearAndReload,
    TrackCurrentRecord
}

private sealed interface CurrentRecordDisplayLoadResult {
    data class Success(val record: HistoryRecord) : CurrentRecordDisplayLoadResult
    data class Pending(val recordsFile: RecordsFile) : CurrentRecordDisplayLoadResult
    data class Missing(val recordsFile: RecordsFile) : CurrentRecordDisplayLoadResult
    data class Failed(val recordsFile: RecordsFile, val error: Throwable) : CurrentRecordDisplayLoadResult
}

private data class LiveSegmentBuffer(
    var recordsFileName: String? = null,
    val points: ArrayList<Long> = ArrayList(MAX_LIVE_POINTS + 1)
)

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

    // 仅在成功拿到可解析的放电当前记录后更新；其他场景保留最近一次有效放电预测。
    private val _prediction = MutableStateFlow<PredictionResult?>(null)
    val prediction: StateFlow<PredictionResult?> = _prediction.asStateFlow()

    private val mainHandler = Handler(Looper.getMainLooper())

    private var statisticsJob: Job? = null
    private var statisticsGeneration: Long = 0L
    private var pendingCurrentRecordsFile: RecordsFile? = null
    private val liveSegmentBuffer = LiveSegmentBuffer()

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
                LoggerX.i(TAG, "[导出] 开始导出首页日志")
                withContext(Dispatchers.IO) {
                    LogRepository.exportLogsZip(
                        context = context,
                        destinationUri = destinationUri
                    )
                }
                LoggerX.i(TAG, "[导出] 首页日志导出成功")
                _userMessage.value = "导出成功"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LoggerX.e(TAG, "[导出] 日志导出失败", tr = e)
                _userMessage.value = "导出失败"
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

    /**
     * 追加一条实时采样到当前分段缓存。
     *
     * @param power 原始功率采样值。
     * @return 追加后的实时曲线点集合。
     */
    private fun appendLivePoint(power: Long): List<Long> {
        liveSegmentBuffer.points.add(power)
        while (liveSegmentBuffer.points.size > MAX_LIVE_POINTS) {
            liveSegmentBuffer.points.removeAt(0)
        }
        return liveSegmentBuffer.points.toList()
    }

    /**
     * 返回当前活动分段的实时点快照。
     *
     * @return 当前活动分段的实时点列表。
     */
    private fun snapshotLivePoints(): List<Long> {
        return liveSegmentBuffer.points.toList()
    }

    /**
     * 切换当前实时点缓存所属的记录文件。
     *
     * 分段一旦变化，必须立即清空实时点，禁止把旧分段曲线继续展示到新分段语义下。
     *
     * @param nextRecordsFileName 新的活动记录文件名，可为空。
     * @return 无返回值。
     */
    private fun switchActiveLiveSegment(nextRecordsFileName: String?) {
        if (liveSegmentBuffer.recordsFileName == nextRecordsFileName) {
            return
        }
        LoggerX.d(TAG, 
            "[首页] 切换实时分段缓存: ${liveSegmentBuffer.recordsFileName} -> $nextRecordsFileName"
        )
        liveSegmentBuffer.recordsFileName = nextRecordsFileName
        liveSegmentBuffer.points.clear()
        _currentRecordUiState.value =
            _currentRecordUiState.value.copy(
                recordsFileName = nextRecordsFileName,
                livePoints = emptyList()
            )
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
            if (liveSegmentBuffer.recordsFileName == recordsFile.name) {
                return@runOnMainThread
            }
            pendingCurrentRecordsFile = recordsFile
            switchActiveLiveSegment(recordsFile.name)
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
            if (pendingFile != null && liveSegmentBuffer.recordsFileName != pendingFile.name) {
                switchActiveLiveSegment(pendingFile.name)
            }
            val nextDisplayStatus = pendingFile?.type ?: sample.status
            val currentUiState = _currentRecordUiState.value
            val nextLivePoints = if (liveSegmentBuffer.recordsFileName != null) {
                appendLivePoint(sample.power)
            } else {
                currentUiState.livePoints
            }
            _currentRecordUiState.value =
                currentUiState.copy(
                    recordsFileName = liveSegmentBuffer.recordsFileName,
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

    private suspend fun loadCurrentRecordForDisplay(
        context: Context,
        dischargeDisplayPositive: Boolean,
        recordsFile: RecordsFile
    ): CurrentRecordDisplayLoadResult {
        return withContext(Dispatchers.IO) {
            when (val result = HistoryRepository.loadCurrentRecord(context, recordsFile)) {
                is CurrentRecordLoadResult.Success -> {
                    CurrentRecordDisplayLoadResult.Success(
                        mapHistoryRecordForDisplay(result.record, dischargeDisplayPositive)
                    )
                }

                is CurrentRecordLoadResult.InsufficientSamples ->
                    CurrentRecordDisplayLoadResult.Pending(result.recordsFile)

                is CurrentRecordLoadResult.Missing ->
                    CurrentRecordDisplayLoadResult.Missing(result.recordsFile)

                is CurrentRecordLoadResult.Failed ->
                    CurrentRecordDisplayLoadResult.Failed(result.recordsFile, result.error)
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
        liveSegmentBuffer.recordsFileName = null
        liveSegmentBuffer.points.clear()
        _chargeSummary.value = null
        _dischargeSummary.value = null
        _currentRecordUiState.value = CurrentRecordUiState()
    }

    /**
     * 构建当前记录加载失败提示文案。
     *
     * @param recordsFile 当前失败的分段文件。
     * @param error 触发失败的异常。
     * @return 展示给用户的失败提示。
     */
    private fun buildCurrentRecordLoadFailureMessage(
        recordsFile: RecordsFile,
        error: Throwable
    ): String {
        val detail = error.message?.takeIf { it.isNotBlank() } ?: error::class.java.simpleName
        return "当前记录加载失败：${recordsFile.name}（$detail）"
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

        val generation = (++statisticsGeneration)
        LoggerX.i(TAG, 
            "[首页] 开始加载统计: generation=$generation mode=$mode recentFileCount=${request.sceneStatsRecentFileCount} intervalMs=$recordIntervalMs"
        )
        _isLoadingStats.value = true
        val job = viewModelScope.launch {
            try {
                val dischargeDisplayPositive = getDischargeDisplayPositive(context)

                withContext(Dispatchers.IO) {
                    LoggerX.d(TAG, "[首页] 统计前触发同步")
                    runCatching { SyncUtil.sync(context) }
                }

                val chargeSummary = withContext(Dispatchers.IO) {
                    HistoryRepository.loadSummary(context, BatteryStatus.Charging)
                }?.let {
                    mapHistorySummaryForDisplay(
                        it,
                        dischargeDisplayPositive
                    )
                }
                val dischargeSummary = withContext(Dispatchers.IO) {
                    HistoryRepository.loadSummary(context, BatteryStatus.Discharging)
                }?.let {
                    mapHistorySummaryForDisplay(
                        it,
                        dischargeDisplayPositive
                    )
                }

                val serviceCurrentRecordsFile = getServiceCurrentRecordsFile()
                val targetRecordsFile = when {
                    pendingCurrentRecordsFile != null &&
                        serviceCurrentRecordsFile != null &&
                        pendingCurrentRecordsFile != serviceCurrentRecordsFile -> {
                        LoggerX.i(TAG, 
                            "[首页] 目标分段已过期，改为服务端当前文件: ${serviceCurrentRecordsFile.name}"
                        )
                        serviceCurrentRecordsFile
                    }

                    pendingCurrentRecordsFile != null -> pendingCurrentRecordsFile
                    expectedCurrentRecordsFile != null -> expectedCurrentRecordsFile
                    else -> serviceCurrentRecordsFile
                }

                val currentRecordResult = targetRecordsFile?.let {
                    loadCurrentRecordForDisplay(
                        context = context,
                        dischargeDisplayPositive = dischargeDisplayPositive,
                        recordsFile = it
                    )
                }

                val resolvedCurrentRecord = (currentRecordResult as? CurrentRecordDisplayLoadResult.Success)?.record
                val currentRecordFailureMessage = (currentRecordResult as? CurrentRecordDisplayLoadResult.Failed)?.let {
                    buildCurrentRecordLoadFailureMessage(
                        recordsFile = it.recordsFile,
                        error = it.error
                    )
                }
                val nextPendingRecordsFile = when (currentRecordResult) {
                    is CurrentRecordDisplayLoadResult.Pending -> currentRecordResult.recordsFile
                    is CurrentRecordDisplayLoadResult.Missing -> currentRecordResult.recordsFile
                    else -> null
                }

                when (currentRecordResult) {
                    is CurrentRecordDisplayLoadResult.Pending -> {
                        LoggerX.d(TAG, 
                            "[首页] 新分段样本不足，进入等待状态: ${currentRecordResult.recordsFile.name}"
                        )
                    }

                    is CurrentRecordDisplayLoadResult.Missing -> {
                        LoggerX.w(TAG, 
                            "[首页] 当前分段尚未同步到本地，进入等待状态: ${currentRecordResult.recordsFile.name}"
                        )
                    }

                    is CurrentRecordDisplayLoadResult.Failed -> {
                        LoggerX.e(TAG, 
                            "[首页] 当前分段加载失败，终止等待状态: ${currentRecordResult.recordsFile.name}",
                            tr = currentRecordResult.error
                        )
                    }

                    else -> {}
                }

                val currentSceneDischargeFileName = when {
                    nextPendingRecordsFile?.type == BatteryStatus.Discharging -> nextPendingRecordsFile.name
                    resolvedCurrentRecord?.type == BatteryStatus.Discharging -> resolvedCurrentRecord.name
                    serviceCurrentRecordsFile?.type == BatteryStatus.Discharging -> serviceCurrentRecordsFile.name
                    else -> null
                }
                val nextActiveLiveRecordsFileName = when {
                    nextPendingRecordsFile != null -> nextPendingRecordsFile.name
                    resolvedCurrentRecord != null -> resolvedCurrentRecord.name
                    serviceCurrentRecordsFile != null -> serviceCurrentRecordsFile.name
                    else -> null
                }
                val stats = withContext(Dispatchers.IO) {
                    SceneStatsComputer.compute(
                        context = context,
                        request = request,
                        recordIntervalMs = recordIntervalMs,
                        currentDischargeFileName = currentSceneDischargeFileName
                    )
                }
                val shouldRefreshPrediction = resolvedCurrentRecord?.type == BatteryStatus.Discharging

                if (generation == statisticsGeneration) {
                    _chargeSummary.value = chargeSummary
                    _dischargeSummary.value = dischargeSummary
                    pendingCurrentRecordsFile = nextPendingRecordsFile
                    switchActiveLiveSegment(nextActiveLiveRecordsFileName)
                    val currentUiState = _currentRecordUiState.value

                    val nextDisplayStatus = when {
                        nextPendingRecordsFile != null -> nextPendingRecordsFile.type
                        resolvedCurrentRecord != null -> resolvedCurrentRecord.type
                        serviceCurrentRecordsFile != null -> serviceCurrentRecordsFile.type
                        else -> currentUiState.displayStatus
                    }
                    _currentRecordUiState.value =
                        currentUiState.copy(
                            recordsFileName = liveSegmentBuffer.recordsFileName,
                            displayStatus = nextDisplayStatus,
                            isSwitching = nextPendingRecordsFile != null,
                            record = resolvedCurrentRecord,
                            livePoints = snapshotLivePoints()
                        )

                    _sceneStats.value = stats?.displayStats
                    if (shouldRefreshPrediction) {
                        _prediction.value =
                            BatteryPredictor.predict(
                                stats?.predictionStats,
                                resolvedCurrentRecord.stats.endCapacity,
                                stats?.medianK,
                                kCV = stats?.kCV,
                                kEffectiveN = stats?.kEffectiveN ?: 0.0,
                                upstreamInsufficientReason = stats?.insufficientReason
                            )
                    }
                    _userMessage.value = currentRecordFailureMessage

                    LoggerX.i(TAG, 
                        "[首页] 统计加载完成: generation=$generation currentRecord=${resolvedCurrentRecord?.name} pending=${nextPendingRecordsFile?.name}"
                    )
                }
            } finally {
                if (generation == statisticsGeneration) {
                    LoggerX.d(TAG, "[首页] 统计任务结束: generation=$generation")
                    _isLoadingStats.value = false
                }
            }
        }
        statisticsJob = job
    }
}
