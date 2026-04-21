package yangfentuozi.batteryrecorder.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import yangfentuozi.batteryrecorder.R
import yangfentuozi.batteryrecorder.appString
import yangfentuozi.batteryrecorder.data.history.HistoryRecord
import yangfentuozi.batteryrecorder.data.history.HistoryRepository
import yangfentuozi.batteryrecorder.data.history.RecordDetailPowerStats
import yangfentuozi.batteryrecorder.shared.config.SharedSettings
import yangfentuozi.batteryrecorder.shared.config.SettingsConstants
import yangfentuozi.batteryrecorder.shared.data.BatteryStatus
import yangfentuozi.batteryrecorder.shared.data.LineRecord
import yangfentuozi.batteryrecorder.shared.data.RecordsFile
import yangfentuozi.batteryrecorder.shared.util.LoggerX
import yangfentuozi.batteryrecorder.ui.mapper.PowerDisplayMapper
import yangfentuozi.batteryrecorder.ui.model.RecordAppDetailUiEntry
import yangfentuozi.batteryrecorder.ui.model.RecordDetailChartUiState
import yangfentuozi.batteryrecorder.ui.model.RecordDetailSummaryUiState
import yangfentuozi.batteryrecorder.usecase.history.BuildRecordDetailChartUiStateUseCase
import yangfentuozi.batteryrecorder.usecase.history.HistoryListSession
import yangfentuozi.batteryrecorder.usecase.history.LoadHistoryListUseCase
import yangfentuozi.batteryrecorder.usecase.history.LoadRecordDetailUseCase
import yangfentuozi.batteryrecorder.utils.computeEnergyWh
import kotlin.math.abs

private const val TAG = "HistorySharedViewModel"

class HistorySharedViewModel : ViewModel() {
    private val _records = MutableStateFlow<List<HistoryRecord>>(emptyList())
    val records: StateFlow<List<HistoryRecord>> = _records.asStateFlow()

    private val _recordDetail = MutableStateFlow<HistoryRecord?>(null)
    val recordDetail: StateFlow<HistoryRecord?> = _recordDetail.asStateFlow()

    private val _recordChartUiState = MutableStateFlow(RecordDetailChartUiState())
    val recordChartUiState: StateFlow<RecordDetailChartUiState> = _recordChartUiState.asStateFlow()

    private val _recordAppDetailEntries = MutableStateFlow<List<RecordAppDetailUiEntry>>(emptyList())
    val recordAppDetailEntries: StateFlow<List<RecordAppDetailUiEntry>> =
        _recordAppDetailEntries.asStateFlow()

    private val _recordDetailSummaryUiState = MutableStateFlow<RecordDetailSummaryUiState?>(null)
    val recordDetailSummaryUiState: StateFlow<RecordDetailSummaryUiState?> =
        _recordDetailSummaryUiState.asStateFlow()

    private val _recordDetailReferenceVoltageV = MutableStateFlow<Double?>(null)
    val recordDetailReferenceVoltageV: StateFlow<Double?> =
        _recordDetailReferenceVoltageV.asStateFlow()

    private val _isRecordChartLoading = MutableStateFlow(false)
    val isRecordChartLoading: StateFlow<Boolean> = _isRecordChartLoading.asStateFlow()

    private var rawRecordDetail: HistoryRecord? = null
    private var rawRecordDetailPowerStats: RecordDetailPowerStats? = null
    private var rawRecordAppSwitchCount = 0
    private var rawRecordChartSource: List<yangfentuozi.batteryrecorder.data.model.ChartPoint> = emptyList()
    private var recordLineRecords: List<LineRecord> = emptyList()
    private var recordDetailSamplingIntervalMs = SettingsConstants.recordIntervalMs.def
    private var recordDetailContext: Context? = null
    private var detailDischargeDisplayPositive = SettingsConstants.dischargeDisplayPositive.def

    private var dualCellEnabled = SettingsConstants.dualCellEnabled.def
    private var calibrationValue = SettingsConstants.calibrationValue.def
    private var recordScreenOffEnabled = SettingsConstants.screenOffRecordEnabled.def

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isImportExporting = MutableStateFlow(false)
    val isImportExporting: StateFlow<Boolean> = _isImportExporting.asStateFlow()

    private val _isPaging = MutableStateFlow(false)
    val isPaging: StateFlow<Boolean> = _isPaging.asStateFlow()

    private val _hasMoreRecords = MutableStateFlow(false)
    val hasMoreRecords: StateFlow<Boolean> = _hasMoreRecords.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    private val _chargeCapacityChangeFilter = MutableStateFlow<Int?>(null)
    val chargeCapacityChangeFilter: StateFlow<Int?> = _chargeCapacityChangeFilter.asStateFlow()

    private var listSession = HistoryListSession()
    private var listLoadToken: Long = 0L
    private var detailLoadToken: Long = 0L
    private var chartComputeToken: Long = 0L

    /**
     * 加载历史列表。
     *
     * @param context 应用上下文。
     * @param type 历史类型。
     */
    fun loadRecords(context: Context, type: BatteryStatus) {
        if (_isLoading.value) return
        _isLoading.value = true
        val shouldResetChargeFilter = listSession.currentListType != type
        val shouldClearDisplayedRecords = shouldResetChargeFilter ||
            !listSession.hasInitializedListContext ||
            _chargeCapacityChangeFilter.value != null
        val token = listLoadToken + 1
        listLoadToken = token
        resetListLoadingState(clearDisplayedRecords = shouldClearDisplayedRecords)
        viewModelScope.launch {
            try {
                val result = if (listSession.currentListType != type || !listSession.hasInitializedListContext) {
                    LoadHistoryListUseCase.reload(
                        context = context,
                        type = type,
                        previousSession = listSession,
                        chargeCapacityChangeFilter = null
                    )
                } else {
                    LoadHistoryListUseCase.refreshOnEnter(
                        context = context,
                        type = type,
                        session = listSession,
                        displayedRecords = _records.value,
                        chargeCapacityChangeFilter = _chargeCapacityChangeFilter.value
                    )
                }
                if (token != listLoadToken) return@launch
                applyHistoryListResult(result)
                if (shouldResetChargeFilter) {
                    _chargeCapacityChangeFilter.value = null
                }
            } finally {
                if (token == listLoadToken) {
                    _isLoading.value = false
                }
            }
        }
    }

    fun loadNextPage(context: Context, type: BatteryStatus) {
        if (_isLoading.value || _isPaging.value || !_hasMoreRecords.value) return
        if (listSession.currentListType != type) return
        val token = listLoadToken
        viewModelScope.launch {
            _isPaging.value = true
            try {
                val result = LoadHistoryListUseCase.loadNextPage(
                    context = context,
                    session = listSession,
                    displayedRecords = _records.value
                )
                if (token != listLoadToken) return@launch
                applyHistoryListResult(result)
            } finally {
                if (token == listLoadToken) {
                    _isPaging.value = false
                }
            }
        }
    }

    fun updateChargeCapacityChangeFilter(context: Context, minCapacityChange: Int?) {
        if (_isLoading.value || listSession.currentListType != BatteryStatus.Charging) return
        val normalizedFilter = minCapacityChange?.takeIf { it > 0 }
        if (_chargeCapacityChangeFilter.value == normalizedFilter) return

        _isLoading.value = true
        val token = listLoadToken + 1
        listLoadToken = token
        resetListLoadingState(clearDisplayedRecords = true)
        viewModelScope.launch {
            try {
                val result = LoadHistoryListUseCase.applyChargeCapacityChangeFilter(
                    context = context,
                    session = listSession,
                    chargeCapacityChangeFilter = normalizedFilter
                )
                if (token != listLoadToken) return@launch
                _chargeCapacityChangeFilter.value = normalizedFilter
                applyHistoryListResult(result)
            } finally {
                if (token == listLoadToken) {
                    _isLoading.value = false
                }
            }
        }
    }

    fun loadRecord(context: Context, recordsFile: RecordsFile) {
        val detailToken = detailLoadToken + 1
        detailLoadToken = detailToken
        viewModelScope.launch {
            _isLoading.value = true
            _isRecordChartLoading.value = true
            clearRecordDetailState()
            try {
                detailDischargeDisplayPositive =
                    SharedSettings.readAppSettings(context).dischargeDisplayPositive
                recordDetailContext = context.applicationContext
                val loadedState = LoadRecordDetailUseCase.execute(
                    context = context,
                    recordsFile = recordsFile,
                    recordIntervalMs = recordDetailSamplingIntervalMs
                )
                if (detailToken != detailLoadToken) return@launch
                if (loadedState == null) {
                    _userMessage.value = appString(R.string.toast_record_file_missing)
                    _isRecordChartLoading.value = false
                    return@launch
                }
                rawRecordDetail = loadedState.detail
                rawRecordDetailPowerStats = loadedState.powerStats
                rawRecordAppSwitchCount = loadedState.appSwitchCount
                recordLineRecords = loadedState.lineRecords
                rawRecordChartSource = loadedState.rawChartPoints
                _recordDetailReferenceVoltageV.value = loadedState.referenceVoltageV
                _recordAppDetailEntries.value = loadedState.appEntries
                applyRecordDetailDisplayConfig()
                requestRecordChartUiStateRecompute(
                    detailType = recordsFile.type,
                    expectedDetailToken = detailToken
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (detailToken != detailLoadToken) return@launch
                LoggerX.e(TAG, "[记录详情] 加载失败: ${recordsFile.name}", tr = e)
                _userMessage.value = appString(R.string.toast_load_record_detail_failed)
                clearRecordDetailState()
                _isRecordChartLoading.value = false
            } finally {
                if (detailToken == detailLoadToken) {
                    _isLoading.value = false
                }
            }
        }
    }

    /**
     * 更新详情页放电功耗的显示符号配置。
     *
     * @param dischargeDisplayPositive 是否将放电显示为正值。
     * @return 仅刷新详情页顶部统计与记录详情，不触发图表重算。
     */
    fun updateRecordDetailDisplayConfig(dischargeDisplayPositive: Boolean) {
        if (detailDischargeDisplayPositive == dischargeDisplayPositive) return
        detailDischargeDisplayPositive = dischargeDisplayPositive
        applyRecordDetailDisplayConfig()
    }

    fun updateRecordDetailSamplingConfig(recordIntervalMs: Long) {
        if (recordDetailSamplingIntervalMs == recordIntervalMs) return
        recordDetailSamplingIntervalMs = recordIntervalMs

        val context = recordDetailContext ?: return
        val detailType = rawRecordDetail?.type ?: return
        if (recordLineRecords.isEmpty()) {
            return
        }

        viewModelScope.launch {
            try {
                val lineRecords = recordLineRecords
                val samplingIntervalMs = recordDetailSamplingIntervalMs
                val powerStats = withContext(Dispatchers.IO) {
                    LoadRecordDetailUseCase.buildRecordDetailPowerStats(
                        detailType = detailType,
                        lineRecords = lineRecords,
                        recordIntervalMs = samplingIntervalMs
                    )
                }
                val appEntries = withContext(Dispatchers.IO) {
                    LoadRecordDetailUseCase.buildRecordAppDetailEntries(
                        context = context,
                        detailType = detailType,
                        lineRecords = lineRecords,
                        recordIntervalMs = samplingIntervalMs
                    )
                }
                rawRecordDetailPowerStats = powerStats
                applyRecordDetailDisplayConfig()
                _recordAppDetailEntries.value = appEntries
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LoggerX.e(TAG, "[记录详情] 重算采样间隔相关统计失败", tr = e)
                _recordAppDetailEntries.value = emptyList()
            }
        }
    }

    fun deleteRecord(context: Context, recordsFile: RecordsFile) {
        if (_isLoading.value) return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val deleted = withContext(Dispatchers.IO) {
                    HistoryRepository.deleteRecord(context, recordsFile)
                }
                if (deleted) {
                    val deletedName = recordsFile.name
                    val result = LoadHistoryListUseCase.removeDeletedRecord(
                        session = listSession,
                        displayedRecords = _records.value,
                        deletedRecordName = deletedName
                    )
                    applyHistoryListResult(result)
                    if (_recordDetail.value?.asRecordsFile() == recordsFile) {
                        detailLoadToken += 1L
                        chartComputeToken += 1L
                        _isRecordChartLoading.value = false
                        clearRecordDetailState()
                    }
                    _userMessage.value = appString(R.string.toast_delete_success)
                } else {
                    _userMessage.value = appString(R.string.toast_delete_failed)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LoggerX.e(TAG, "[删除] 记录删除失败: ${recordsFile.name}", tr = e)
                _userMessage.value = appString(R.string.toast_delete_failed)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun exportRecord(
        context: Context,
        recordsFile: RecordsFile,
        destinationUri: Uri
    ) {
        if (_isLoading.value || _isImportExporting.value) return
        viewModelScope.launch {
            _isImportExporting.value = true
            try {
                withContext(Dispatchers.IO) {
                    HistoryRepository.exportRecord(context, recordsFile, destinationUri)
                }
                _userMessage.value = appString(R.string.toast_export_success)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LoggerX.e(TAG, "[导出] 单记录导出失败: ${recordsFile.name}", tr = e)
                _userMessage.value = appString(R.string.toast_export_failed)
            } finally {
                _isImportExporting.value = false
            }
        }
    }

    fun exportAllRecords(
        context: Context,
        type: BatteryStatus,
        destinationUri: Uri
    ) {
        LoggerX.i(
            TAG,
            "[导出] 收到批量导出请求: type=${type.dataDirName} destination=$destinationUri isLoading=${_isLoading.value} isImportExporting=${_isImportExporting.value}"
        )
        if (_isImportExporting.value) {
            LoggerX.w(
                TAG,
                "[导出] 批量导出被跳过: type=${type.dataDirName} reason=isImportExporting:${_isImportExporting.value}"
            )
            return
        }
        val currentExportRecords = LoadHistoryListUseCase.resolveCurrentExportRecords(
            session = listSession,
            type = type
        )
        viewModelScope.launch {
            _isImportExporting.value = true
            try {
                val exportRecords = currentExportRecords ?: withContext(Dispatchers.IO) {
                    HistoryRepository.listRecordFiles(context, type)
                        .map { file -> RecordsFile.fromFile(file) }
                }
                withContext(Dispatchers.IO) {
                    HistoryRepository.exportRecordsZip(context, exportRecords, destinationUri)
                }
                val exportCount = exportRecords.size
                LoggerX.i(
                    TAG,
                    "[导出] 批量导出成功: type=${type.dataDirName} count=$exportCount destination=$destinationUri"
                )
                _userMessage.value = appString(R.string.toast_export_success)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LoggerX.e(
                    TAG,
                    "[导出] 批量导出失败: ${type.dataDirName} destination=$destinationUri",
                    tr = e
                )
                _userMessage.value = appString(R.string.toast_export_failed)
            } finally {
                _isImportExporting.value = false
            }
        }
    }

    /**
     * 导入当前历史页的一键导出 ZIP。
     *
     * @param context 应用上下文。
     * @param type 当前历史页类型；导入记录会写入对应目录。
     * @param sourceUri 用户选择的 ZIP 文档 Uri。
     * @return 导入成功后立即刷新列表；单条异常记录会跳过，其余合法记录继续导入。
     */
    fun importAllRecords(
        context: Context,
        type: BatteryStatus,
        sourceUri: Uri
    ) {
        LoggerX.i(
            TAG,
            "[导入] 收到批量导入请求: type=${type.dataDirName} source=$sourceUri isLoading=${_isLoading.value} isImportExporting=${_isImportExporting.value}"
        )
        if (_isImportExporting.value) {
            LoggerX.w(
                TAG,
                "[导入] 批量导入被跳过: type=${type.dataDirName} reason=isImportExporting:${_isImportExporting.value}"
            )
            return
        }
        viewModelScope.launch {
            var reloadToken: Long? = null
            _isImportExporting.value = true
            try {
                val importResult = withContext(Dispatchers.IO) {
                    HistoryRepository.importRecordsZip(context, type, sourceUri)
                }
                _isLoading.value = true
                val token = listLoadToken + 1
                reloadToken = token
                listLoadToken = token
                resetListLoadingState(clearDisplayedRecords = false)
                val result = LoadHistoryListUseCase.reload(
                    context = context,
                    type = type,
                    previousSession = listSession,
                    chargeCapacityChangeFilter = _chargeCapacityChangeFilter.value
                )
                if (token != listLoadToken) return@launch
                applyHistoryListResult(result)
                LoggerX.i(
                    TAG,
                    "[导入] 批量导入完成: type=${type.dataDirName} imported=${importResult.importedCount} skipped=${importResult.skippedCount} source=$sourceUri"
                )
                _userMessage.value = if (importResult.skippedCount > 0) {
                    appString(
                        R.string.toast_import_partial_success,
                        importResult.importedCount,
                        importResult.skippedCount
                    )
                } else {
                    appString(R.string.toast_import_success, importResult.importedCount)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LoggerX.e(
                    TAG,
                    "[导入] 批量导入失败: type=${type.dataDirName} source=$sourceUri",
                    tr = e
                )
                _userMessage.value = appString(R.string.toast_import_failed)
            } finally {
                if (reloadToken != null && reloadToken == listLoadToken) {
                    _isLoading.value = false
                }
                _isImportExporting.value = false
            }
        }
    }

    fun consumeUserMessage() {
        _userMessage.value = null
    }

    fun updatePowerDisplayConfig(
        dualCellEnabled: Boolean,
        calibrationValue: Int,
        recordScreenOffEnabled: Boolean
    ) {
        if (
            this.dualCellEnabled == dualCellEnabled &&
            this.calibrationValue == calibrationValue &&
            this.recordScreenOffEnabled == recordScreenOffEnabled
        ) {
            return
        }
        this.dualCellEnabled = dualCellEnabled
        this.calibrationValue = calibrationValue
        this.recordScreenOffEnabled = recordScreenOffEnabled
        val detailType = rawRecordDetail?.type ?: return
        requestRecordChartUiStateRecompute(detailType = detailType)
    }

    private fun applyHistoryListResult(result: yangfentuozi.batteryrecorder.usecase.history.HistoryListUiResult) {
        listSession = result.session
        _records.value = result.records
        _hasMoreRecords.value = result.hasMoreRecords
    }

    /**
     * 异步重算记录详情图表状态。
     *
     * @param detailType 当前详情类型。
     * @param expectedDetailToken 期望命中的详情加载令牌；为 null 时沿用当前令牌。
     * @return 在后台线程完成图表派生计算，并且仅允许当前详情上下文回写结果。
     */
    private fun requestRecordChartUiStateRecompute(
        detailType: BatteryStatus,
        expectedDetailToken: Long? = null
    ) {
        val detailToken = expectedDetailToken ?: detailLoadToken
        val computeToken = chartComputeToken + 1
        chartComputeToken = computeToken
        val rawPoints = rawRecordChartSource
        val dischargeDisplayPositive = detailDischargeDisplayPositive
        val dualCellEnabled = dualCellEnabled
        val calibrationValue = calibrationValue
        val recordScreenOffEnabled = recordScreenOffEnabled

        viewModelScope.launch {
            try {
                val chartUiState = withContext(Dispatchers.Default) {
                    BuildRecordDetailChartUiStateUseCase.execute(
                        rawPoints = rawPoints,
                        batteryStatus = detailType,
                        dischargeDisplayPositive = dischargeDisplayPositive,
                        dualCellEnabled = dualCellEnabled,
                        calibrationValue = calibrationValue,
                        recordScreenOffEnabled = recordScreenOffEnabled
                    )
                }
                if (detailToken != detailLoadToken || computeToken != chartComputeToken) return@launch
                _recordChartUiState.value = chartUiState
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (detailToken != detailLoadToken || computeToken != chartComputeToken) return@launch
                LoggerX.e(TAG, "[记录详情] 图表状态重算失败", tr = e)
                _recordChartUiState.value = RecordDetailChartUiState()
            } finally {
                if (detailToken == detailLoadToken && computeToken == chartComputeToken) {
                    _isRecordChartLoading.value = false
                }
            }
        }
    }

    /**
     * 在开启新一轮列表加载前复位瞬时 UI 状态。
     *
     * @param clearDisplayedRecords 是否同步清空当前展示列表。
     * @return 无返回值。
     */
    private fun resetListLoadingState(clearDisplayedRecords: Boolean) {
        _isPaging.value = false
        if (!clearDisplayedRecords) {
            return
        }
        _records.value = emptyList()
        _hasMoreRecords.value = false
    }

    /**
     * 清空当前详情页状态，确保切换记录时不会短暂显示上一条记录的数据。
     *
     * @return 详情记录、图表状态、应用详情与缓存点位全部被同步重置。
     */
    private fun clearRecordDetailState() {
        rawRecordDetail = null
        rawRecordDetailPowerStats = null
        rawRecordAppSwitchCount = 0
        _recordDetail.value = null
        _recordDetailSummaryUiState.value = null
        _recordDetailReferenceVoltageV.value = null
        rawRecordChartSource = emptyList()
        recordLineRecords = emptyList()
        _recordAppDetailEntries.value = emptyList()
        _recordChartUiState.value = RecordDetailChartUiState()
    }

    /**
     * 基于缓存的原始详情数据重新应用详情展示配置。
     *
     * @return 详情页记录头与功耗拆分统计同步刷新。
     */
    private fun applyRecordDetailDisplayConfig() {
        val detail = rawRecordDetail
        _recordDetail.value = detail?.let {
            PowerDisplayMapper.mapHistoryRecord(it, detailDischargeDisplayPositive)
        }
        _recordDetailSummaryUiState.value = rawRecordDetailPowerStats?.let { stats ->
            detail?.type?.let { detailType ->
                mapRecordDetailPowerUiState(
                    detailType = detailType,
                    stats = stats,
                    dischargeDisplayPositive = detailDischargeDisplayPositive,
                    appSwitchCount = rawRecordAppSwitchCount
                )
            }
        }
    }

    /**
     * 将详情页功耗统计映射为当前显示口径。
     *
     * @param detailType 当前详情页记录类型。
     * @param stats 详情页功耗拆分统计的原始功率值。
     * @param dischargeDisplayPositive 是否将放电视为正值。
     * @param appSwitchCount 当前应用切换次数。
     * @return 返回已经完成正负语义映射的 UI 状态。
     */
    private fun mapRecordDetailPowerUiState(
        detailType: BatteryStatus,
        stats: RecordDetailPowerStats,
        dischargeDisplayPositive: Boolean,
        appSwitchCount: Int
    ): RecordDetailSummaryUiState {
        val multiplier = if (
            detailType == BatteryStatus.Discharging &&
            dischargeDisplayPositive
        ) {
            -1.0
        } else {
            1.0
        }
        val totalTransferredWh = computeEnergyWh(
            rawPower = stats.totalConfidentEnergyRawMs,
            durationMs = 1L,
            dualCellEnabled = dualCellEnabled,
            calibrationValue = calibrationValue
        )
        val screenOnConsumedWh = computeEnergyWh(
            rawPower = stats.screenOnConfidentEnergyRawMs,
            durationMs = 1L,
            dualCellEnabled = dualCellEnabled,
            calibrationValue = calibrationValue
        )
        val screenOffConsumedWh = computeEnergyWh(
            rawPower = stats.screenOffDisplayEnergyRawMs,
            durationMs = 1L,
            dualCellEnabled = dualCellEnabled,
            calibrationValue = calibrationValue
        )
        return RecordDetailSummaryUiState(
            averagePower = stats.averagePowerRaw * multiplier,
            screenOnAveragePower = stats.screenOnAveragePowerRaw?.times(multiplier),
            screenOffAveragePower = stats.screenOffAveragePowerRaw?.times(multiplier),
            totalTransferredWh = abs(totalTransferredWh),
            screenOnConsumedWh = abs(screenOnConsumedWh),
            screenOffConsumedWh = abs(screenOffConsumedWh),
            capacityChange = stats.capacityChange,
            appSwitchCount = appSwitchCount
        )
    }
}
