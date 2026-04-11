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
import yangfentuozi.batteryrecorder.data.history.HistoryRepository.toFile
import yangfentuozi.batteryrecorder.data.history.RecordAppStatsComputer
import yangfentuozi.batteryrecorder.data.history.RecordDetailPowerStats
import yangfentuozi.batteryrecorder.data.history.RecordDetailPowerStatsComputer
import yangfentuozi.batteryrecorder.data.model.ChartPoint
import yangfentuozi.batteryrecorder.data.model.RecordDetailChartPoint
import yangfentuozi.batteryrecorder.data.model.normalizeRecordDetailChartPoints
import yangfentuozi.batteryrecorder.shared.config.SettingsConstants
import yangfentuozi.batteryrecorder.shared.data.BatteryStatus
import yangfentuozi.batteryrecorder.shared.data.LineRecord
import yangfentuozi.batteryrecorder.shared.data.RecordsFile
import yangfentuozi.batteryrecorder.shared.util.LoggerX
import yangfentuozi.batteryrecorder.utils.computePowerW
import java.io.File
import kotlin.math.roundToLong

private const val TAG = "HistoryViewModel"

data class RecordDetailChartUiState(
    // 原始展示点，保留完整时间精度，供原始曲线与辅助信息使用。
    val points: List<RecordDetailChartPoint> = emptyList(),
    // 趋势点，基于过滤后的原始点重新分桶生成，只用于趋势曲线。
    val trendPoints: List<RecordDetailChartPoint> = emptyList(),
    val minChartTime: Long? = null,
    val maxChartTime: Long? = null,
    val maxViewportStartTime: Long? = null,
    val viewportDurationMs: Long = 0L
)

data class RecordAppDetailUiEntry(
    val key: String,
    val packageName: String?,
    val appLabel: String,
    val averagePowerRaw: Double,
    val averageTempCelsius: Double?,
    val maxTempCelsius: Double?,
    val durationMs: Long,
    val isScreenOff: Boolean
)

data class RecordDetailPowerUiState(
    val averagePower: Double,
    val screenOnAveragePower: Double?,
    val screenOffAveragePower: Double?,
    val totalTransferredMah: Double,
    val screenOnConsumedMah: Double,
    val screenOffConsumedMah: Double
)

private data class LoadedRecordDetailState(
    val detail: HistoryRecord,
    val lineRecords: List<LineRecord>,
    val powerStats: RecordDetailPowerStats?,
    val appEntries: List<RecordAppDetailUiEntry>
)

class HistoryViewModel : ViewModel() {
    private val _records = MutableStateFlow<List<HistoryRecord>>(emptyList())
    val records: StateFlow<List<HistoryRecord>> = _records.asStateFlow()

    private val _recordDetail = MutableStateFlow<HistoryRecord?>(null)
    val recordDetail: StateFlow<HistoryRecord?> = _recordDetail.asStateFlow()

    // 图表 UI 只消费聚合后的 RecordDetailChartUiState，
    // 因此原始点和显示配置都收敛为 ViewModel 内部字段，避免对外暴露重复状态源。
    private val _recordChartUiState = MutableStateFlow(RecordDetailChartUiState())
    val recordChartUiState: StateFlow<RecordDetailChartUiState> = _recordChartUiState.asStateFlow()

    private val _recordAppDetailEntries = MutableStateFlow<List<RecordAppDetailUiEntry>>(emptyList())
    val recordAppDetailEntries: StateFlow<List<RecordAppDetailUiEntry>> =
        _recordAppDetailEntries.asStateFlow()
    private val _recordDetailPowerUiState = MutableStateFlow<RecordDetailPowerUiState?>(null)
    val recordDetailPowerUiState: StateFlow<RecordDetailPowerUiState?> =
        _recordDetailPowerUiState.asStateFlow()
    private val _isRecordChartLoading = MutableStateFlow(false)
    val isRecordChartLoading: StateFlow<Boolean> = _isRecordChartLoading.asStateFlow()

    // recordPoints 保存从记录文件读取并完成“放电正负显示映射”后的原始点。
    // 它仍然保留 ChartPoint，是因为 computePowerW 前还需要读取原始功率字段。
    private var rawRecordDetail: HistoryRecord? = null
    private var rawRecordDetailPowerStats: RecordDetailPowerStats? = null
    private var recordPoints: List<ChartPoint> = emptyList()
    private var recordLineRecords: List<LineRecord> = emptyList()
    private var recordDetailSamplingIntervalMs = SettingsConstants.recordIntervalMs.def
    private var recordDetailContext: Context? = null
    private var detailDischargeDisplayPositive = SettingsConstants.dischargeDisplayPositive.def

    // 这三个字段是图表派生状态的输入，不需要被外部订阅，因此使用普通字段即可。
    private var dualCellEnabled = SettingsConstants.dualCellEnabled.def
    private var calibrationValue = SettingsConstants.calibrationValue.def
    private var recordScreenOffEnabled = SettingsConstants.screenOffRecordEnabled.def

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    private val _isImportExporting = MutableStateFlow(false)
    val isImportExporting: StateFlow<Boolean> = _isImportExporting.asStateFlow()

    // 是否正在加载下一页；用于防止滚动触底时重复触发并发分页请求。
    private val _isPaging = MutableStateFlow(false)
    val isPaging: StateFlow<Boolean> = _isPaging.asStateFlow()

    // 当前筛选类型下是否还有未加载的历史文件。
    private val _hasMoreRecords = MutableStateFlow(false)
    val hasMoreRecords: StateFlow<Boolean> = _hasMoreRecords.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    private val _chargeCapacityChangeFilter = MutableStateFlow<Int?>(null)
    val chargeCapacityChangeFilter: StateFlow<Int?> = _chargeCapacityChangeFilter.asStateFlow()

    // 分页上下文：
    // listFiles / loadedRecordCount 描述“总数据集 + 当前游标”。
    // latestListFile 用于沿用统计缓存策略（最新文件不读缓存）。
    // listLoadToken 用于隔离不同轮次加载，避免旧协程回写新状态。
    private var listFiles: List<File> = emptyList()
    private var allListRecordsCache: List<HistoryRecord>? = null
    private var pagedSourceRecords: List<HistoryRecord>? = null
    private var latestListFile: File? = null
    private var loadedRecordCount = 0
    private var listDischargeDisplayPositive = SettingsConstants.dischargeDisplayPositive.def
    private var currentListType: BatteryStatus? = null
    private var hasInitializedListContext = false
    private var listLoadToken: Long = 0L
    private var detailLoadToken: Long = 0L
    private var chartComputeToken: Long = 0L

    private companion object {
        const val PAGE_SIZE = 10

        // 目标不是“固定桶时长”，而是让不同记录长度大致映射到相近数量的趋势点。
        const val TARGET_TREND_BUCKET_COUNT = 240L
    }

    /**
     * 加载历史列表。
     *
     * 首次进入时初始化分页上下文；同类型页面再次进入时只刷新首条当前记录，必要时回退整页重载。
     *
     * @param context 应用上下文。
     * @param type 历史类型。
     */
    fun loadRecords(context: Context, type: BatteryStatus) {
        if (_isLoading.value) return
        _isLoading.value = true
        viewModelScope.launch {
            try {
                if (currentListType != type || !hasInitializedListContext) {
                    reloadRecordsInternal(
                        context = context,
                        type = type,
                        preserveChargeFilter = false
                    )
                } else {
                    refreshRecordsOnEnterInternal(context, type)
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadNextPage(context: Context, type: BatteryStatus) {
        // 仅在“同一类型 + 非主加载 + 非分页中 + 仍有更多”时允许翻页。
        if (_isLoading.value || _isPaging.value || !_hasMoreRecords.value) return
        if (currentListType != type) return
        val token = listLoadToken
        viewModelScope.launch {
            loadNextPageInternal(context, token)
        }
    }

    fun updateChargeCapacityChangeFilter(context: Context, minCapacityChange: Int?) {
        if (_isLoading.value || currentListType != BatteryStatus.Charging) return
        val normalizedFilter = minCapacityChange?.takeIf { it > 0 }
        if (_chargeCapacityChangeFilter.value == normalizedFilter) return

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val token = listLoadToken + 1
                listLoadToken = token
                val nextPagedSourceRecords = when (normalizedFilter) {
                    null -> null
                    else -> ensureAllListRecordsCache(context, token).filter { record ->
                        computeChargingCapacityChange(record) >= normalizedFilter
                    }
                }
                if (token != listLoadToken) return@launch
                _chargeCapacityChangeFilter.value = normalizedFilter
                pagedSourceRecords = nextPagedSourceRecords
                resetDisplayedRecords(currentSourceCount())
                loadNextPageInternal(context, token)
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun loadNextPageInternal(context: Context, token: Long) {
        // token 不一致代表列表上下文已切换（如充电/放电页切换），当前任务必须丢弃。
        if (token != listLoadToken || _isPaging.value || !_hasMoreRecords.value) return
        _isPaging.value = true
        try {
            val startIndex = loadedRecordCount
            val sourceRecords = pagedSourceRecords
            val sourceSize = sourceRecords?.size ?: listFiles.size
            val endExclusive = (startIndex + PAGE_SIZE).coerceAtMost(sourceSize)
            if (startIndex >= endExclusive) {
                _hasMoreRecords.value = false
                return
            }
            val nextPageRecords =
                sourceRecords?.subList(startIndex, endExclusive) ?: withContext(Dispatchers.IO) {
                    val filesToLoad = listFiles.subList(startIndex, endExclusive).toList()
                    val latestFile = latestListFile
                    val dischargeDisplayPositive = listDischargeDisplayPositive
                    filesToLoad.mapNotNull { file ->
                        buildHistoryRecord(context, file, latestFile, dischargeDisplayPositive)
                    }
                }
            // I/O 返回后再次校验 token，避免旧任务覆盖新列表状态。
            if (token != listLoadToken) return
            if (nextPageRecords.isNotEmpty()) {
                _records.value += nextPageRecords
            }
            loadedRecordCount = endExclusive
            _hasMoreRecords.value = loadedRecordCount < currentSourceCount()
        } finally {
            if (token == listLoadToken) {
                _isPaging.value = false
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
                val dischargeDisplayPositive = getDischargeDisplayPositive(context)
                detailDischargeDisplayPositive = dischargeDisplayPositive
                recordDetailContext = context.applicationContext
                val recordFile = recordsFile.toFile(context)
                if (recordFile == null) {
                    if (detailToken != detailLoadToken) return@launch
                    _userMessage.value = appString(R.string.toast_record_file_missing)
                    _isRecordChartLoading.value = false
                    return@launch
                }

                val loadedState = withContext(Dispatchers.IO) {
                    val detail = HistoryRepository.loadRecord(context, recordFile)
                    val lineRecords = HistoryRepository.loadLineRecords(recordFile)
                    val powerStats = buildRecordDetailPowerStats(
                        detailType = recordsFile.type,
                        lineRecords = lineRecords
                    )
                    val appEntries = buildRecordAppDetailEntries(
                        context = context.applicationContext,
                        detailType = recordsFile.type,
                        lineRecords = lineRecords
                    )
                    LoadedRecordDetailState(
                        detail = detail,
                        lineRecords = lineRecords,
                        powerStats = powerStats,
                        appEntries = appEntries
                    )
                }
                if (detailToken != detailLoadToken) return@launch
                val points = mapChartPointsForDisplay(
                    points = loadedState.lineRecords.toChartPoints(),
                    batteryStatus = recordsFile.type,
                    dischargeDisplayPositive = dischargeDisplayPositive
                )
                rawRecordDetail = loadedState.detail
                rawRecordDetailPowerStats = loadedState.powerStats
                applyRecordDetailDisplayConfig()
                recordLineRecords = loadedState.lineRecords
                recordPoints = points
                _recordAppDetailEntries.value = loadedState.appEntries
                requestRecordChartUiStateRecompute(detailToken)
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
     * @param dischargeDisplayPositive 是否将放电显示为正值
     * @return 仅刷新详情页顶部统计与记录详情，不触发图表重算
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
        val detailType = _recordDetail.value?.type ?: return
        if (detailType != BatteryStatus.Discharging || recordLineRecords.isEmpty()) {
            _recordAppDetailEntries.value = emptyList()
            return
        }

        viewModelScope.launch {
            try {
                val entries = withContext(Dispatchers.IO) {
                    buildRecordAppDetailEntries(
                        context = context,
                        detailType = detailType,
                        lineRecords = recordLineRecords
                    )
                }
                _recordAppDetailEntries.value = entries
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LoggerX.e(TAG, "[记录详情] 重算应用详情失败", tr = e)
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
                    val deletedSourceIndex = findSourceIndexByName(deletedName)
                    _records.value = _records.value.filter { it.asRecordsFile() != recordsFile }
                    if (currentListType == recordsFile.type) {
                        // 删除后同步修正数据源、筛选缓存与游标，避免翻页跳过未显示项。
                        listFiles = listFiles.filter { it.name != deletedName }
                        latestListFile = listFiles.firstOrNull()
                        allListRecordsCache = allListRecordsCache?.filter { record ->
                            record.name != deletedName
                        }
                        pagedSourceRecords = pagedSourceRecords?.filter { record ->
                            record.name != deletedName
                        }
                        if (deletedSourceIndex in 0 until loadedRecordCount) {
                            loadedRecordCount -= 1
                        }
                        loadedRecordCount = loadedRecordCount.coerceAtMost(currentSourceCount())
                        _hasMoreRecords.value = loadedRecordCount < currentSourceCount()
                    }
                    val detail = _recordDetail.value
                    if (detail != null && detail.asRecordsFile() == recordsFile) {
                        _recordDetail.value = null
                        recordPoints = emptyList()
                        recordLineRecords = emptyList()
                        _recordAppDetailEntries.value = emptyList()
                        requestRecordChartUiStateRecompute()
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
        val currentExportRecords = resolveCurrentExportRecords(type)
        viewModelScope.launch {
            _isImportExporting.value = true
            try {
                withContext(Dispatchers.IO) {
                    val exportRecords = currentExportRecords ?: HistoryRepository
                        .listRecordFiles(context, type)
                        .map { file -> RecordsFile.fromFile(file) }
                    LoggerX.i(
                        TAG,
                        "[导出] 开始批量导出记录: type=${type.dataDirName} count=${exportRecords.size} source=${if (currentExportRecords != null) "currentList" else "diskScan"} destination=$destinationUri"
                    )
                    HistoryRepository.exportRecordsZip(context, exportRecords, destinationUri)
                }
                LoggerX.i(
                    TAG,
                    "[导出] 批量导出成功: type=${type.dataDirName} destination=$destinationUri"
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
     * @return 导入成功后立即刷新列表；任一条目非法时整次导入失败。
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
            _isImportExporting.value = true
            try {
                LoggerX.i(
                    TAG,
                    "[导入] 开始批量导入记录: type=${type.dataDirName} source=$sourceUri"
                )
                val importedCount = withContext(Dispatchers.IO) {
                    HistoryRepository.importRecordsZip(context, type, sourceUri)
                }
                reloadRecordsInternal(
                    context = context,
                    type = type,
                    preserveChargeFilter = true
                )
                LoggerX.i(
                    TAG,
                    "[导入] 批量导入成功: type=${type.dataDirName} count=$importedCount source=$sourceUri"
                )
                _userMessage.value = appString(R.string.toast_import_success, importedCount)
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
                _isImportExporting.value = false
            }
        }
    }

    fun consumeUserMessage() {
        _userMessage.value = null
    }

    private suspend fun reloadRecordsInternal(
        context: Context,
        type: BatteryStatus,
        preserveChargeFilter: Boolean
    ) {
        val dischargeDisplayPositive = getDischargeDisplayPositive(context)
        val files = withContext(Dispatchers.IO) {
            HistoryRepository.listRecordFiles(context, type)
        }
        val token = listLoadToken + 1
        listLoadToken = token
        currentListType = type
        listFiles = files
        allListRecordsCache = null
        pagedSourceRecords = null
        latestListFile = files.firstOrNull()
        listDischargeDisplayPositive = dischargeDisplayPositive
        hasInitializedListContext = true

        val nextChargeFilter = if (preserveChargeFilter && type == BatteryStatus.Charging) {
            _chargeCapacityChangeFilter.value
        } else {
            null
        }
        _chargeCapacityChangeFilter.value = nextChargeFilter
        if (nextChargeFilter != null) {
            pagedSourceRecords = ensureAllListRecordsCache(context, token).filter { record ->
                computeChargingCapacityChange(record) >= nextChargeFilter
            }
            if (token != listLoadToken) return
        }
        resetDisplayedRecords(currentSourceCount())
        loadNextPageInternal(context, token)
    }

    /**
     * 历史列表重新进入前台时刷新当前上下文。
     *
     * @param context 应用上下文。
     * @param type 当前历史页类型。
     * @return 文件集合、筛选上下文或显示口径变化时整页重载；仅当前记录增长时只重算首条记录。
     */
    private suspend fun refreshRecordsOnEnterInternal(
        context: Context,
        type: BatteryStatus
    ) {
        if (currentListType != type || !hasInitializedListContext) {
            reloadRecordsInternal(
                context = context,
                type = type,
                preserveChargeFilter = false
            )
            return
        }

        val dischargeDisplayPositive = getDischargeDisplayPositive(context)
        val latestFiles = withContext(Dispatchers.IO) {
            HistoryRepository.listRecordFiles(context, type)
        }
        if (shouldReloadListOnEnter(latestFiles, dischargeDisplayPositive)) {
            reloadRecordsInternal(
                context = context,
                type = type,
                preserveChargeFilter = true
            )
            return
        }

        listFiles = latestFiles
        latestListFile = latestFiles.firstOrNull()
        listDischargeDisplayPositive = dischargeDisplayPositive

        val latestFile = latestListFile ?: return
        val currentFirstRecord = _records.value.firstOrNull() ?: return
        if (currentFirstRecord.lastModified == latestFile.lastModified()) return

        val refreshedRecord = withContext(Dispatchers.IO) {
            buildHistoryRecord(
                context = context,
                file = latestFile,
                latestFile = latestFile,
                dischargeDisplayPositive = dischargeDisplayPositive
            )
        }
        if (refreshedRecord == null) {
            reloadRecordsInternal(
                context = context,
                type = type,
                preserveChargeFilter = true
            )
            return
        }
        replaceCachedRecord(refreshedRecord)
    }

    /**
     * 判断前台刷新是否必须退回整页重载。
     *
     * @param latestFiles 重新列出的文件集合。
     * @param dischargeDisplayPositive 当前放电展示口径。
     * @return 只要整页可见结果可能改变，就返回 true。
     */
    private fun shouldReloadListOnEnter(
        latestFiles: List<File>,
        dischargeDisplayPositive: Boolean
    ): Boolean {
        if (_chargeCapacityChangeFilter.value != null) return true
        if (listDischargeDisplayPositive != dischargeDisplayPositive) return true
        if (latestFiles.size != listFiles.size) return true
        if (latestFiles.indices.any { index -> latestFiles[index].name != listFiles[index].name }) {
            return true
        }

        val latestFile = latestFiles.firstOrNull() ?: return _records.value.isNotEmpty()
        val currentFirstRecord = _records.value.firstOrNull() ?: return true
        return currentFirstRecord.name != latestFile.name
    }

    /**
     * 用最新记录数据回写当前列表缓存。
     *
     * @param refreshedRecord 最新重算得到的记录。
     * @return 同步替换已展示列表与筛选缓存中的同名记录，避免后续继续命中旧值。
     */
    private fun replaceCachedRecord(refreshedRecord: HistoryRecord) {
        _records.value = _records.value.map { record ->
            if (record.name == refreshedRecord.name) refreshedRecord else record
        }
        allListRecordsCache = allListRecordsCache?.map { record ->
            if (record.name == refreshedRecord.name) refreshedRecord else record
        }
        pagedSourceRecords = pagedSourceRecords?.map { record ->
            if (record.name == refreshedRecord.name) refreshedRecord else record
        }
    }

    private fun resetDisplayedRecords(sourceSize: Int) {
        loadedRecordCount = 0
        _records.value = emptyList()
        _isPaging.value = false
        _hasMoreRecords.value = sourceSize > 0
    }

    private fun currentSourceCount(): Int =
        pagedSourceRecords?.size ?: listFiles.size

    private suspend fun ensureAllListRecordsCache(
        context: Context,
        token: Long
    ): List<HistoryRecord> {
        allListRecordsCache?.let { return it }

        val latestFile = latestListFile
        val dischargeDisplayPositive = listDischargeDisplayPositive
        val records = withContext(Dispatchers.IO) {
            listFiles.mapNotNull { file ->
                buildHistoryRecord(context, file, latestFile, dischargeDisplayPositive)
            }
        }
        if (token != listLoadToken) return emptyList()
        allListRecordsCache = records
        return records
    }

    private fun buildHistoryRecord(
        context: Context,
        file: File,
        latestFile: File?,
        dischargeDisplayPositive: Boolean
    ): HistoryRecord? {
        return HistoryRepository.loadStats(context, file, file != latestFile)
            ?.let { historyRecord ->
                mapHistoryRecordForDisplay(historyRecord, dischargeDisplayPositive)
            }
    }

    private fun findSourceIndexByName(recordName: String): Int {
        val sourceRecords = pagedSourceRecords
        if (sourceRecords != null) {
            return sourceRecords.indexOfFirst { record -> record.name == recordName }
        }
        return listFiles.indexOfFirst { file -> file.name == recordName }
    }

    private fun resolveCurrentExportRecords(type: BatteryStatus): List<RecordsFile>? {
        if (currentListType != type) return null
        pagedSourceRecords?.let { records ->
            return records.map { record -> record.asRecordsFile() }
        }
        return listFiles.map { file -> RecordsFile.fromFile(file) }
    }

    private fun computeChargingCapacityChange(record: HistoryRecord): Int =
        record.stats.endCapacity - record.stats.startCapacity

    fun updatePowerDisplayConfig(
        dualCellEnabled: Boolean,
        calibrationValue: Int,
        recordScreenOffEnabled: Boolean
    ) {
        // 三个输入全部未变化时不重算，避免详情页设置流重复回放造成无意义的图表重建。
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
        requestRecordChartUiStateRecompute()
    }

    /**
     * 异步重算记录详情图表状态。
     *
     * @param expectedDetailToken 期望命中的详情加载令牌；为 null 时沿用当前令牌
     * @return 在后台线程完成图表派生计算，并且仅允许当前详情上下文回写结果
     */
    private fun requestRecordChartUiStateRecompute(expectedDetailToken: Long? = null) {
        val detailToken = expectedDetailToken ?: detailLoadToken
        val computeToken = chartComputeToken + 1
        chartComputeToken = computeToken
        val rawPoints = recordPoints
        val dualCellEnabled = dualCellEnabled
        val calibrationValue = calibrationValue
        val recordScreenOffEnabled = recordScreenOffEnabled

        viewModelScope.launch {
            try {
                val chartUiState = withContext(Dispatchers.Default) {
                    // 第一步：把原始记录点换算成图表可直接消费的瓦特值模型。
                    val displayPoints = mapDisplayPoints(
                        rawPoints = rawPoints,
                        dualCellEnabled = dualCellEnabled,
                        calibrationValue = calibrationValue
                    )
                    // 第二步：趋势点始终基于“过滤后的展示点”计算，确保原始曲线和趋势曲线遵循同一展示语义。
                    val filteredDisplayPoints = normalizeRecordDetailChartPoints(
                        points = displayPoints,
                        recordScreenOffEnabled = recordScreenOffEnabled
                    )
                    // 第三步：保留原始点给原始曲线/标记使用，同时额外生成趋势点给趋势曲线使用。
                    val trendPoints = computeTrendPoints(filteredDisplayPoints)
                    computeViewportState(
                        points = displayPoints,
                        trendPoints = trendPoints
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
     * 清空当前详情页状态，确保切换记录时不会短暂显示上一条记录的数据。
     *
     * @return 详情记录、图表状态、应用详情与缓存点位全部被同步重置
     */
    private fun clearRecordDetailState() {
        rawRecordDetail = null
        rawRecordDetailPowerStats = null
        _recordDetail.value = null
        _recordDetailPowerUiState.value = null
        recordPoints = emptyList()
        recordLineRecords = emptyList()
        _recordAppDetailEntries.value = emptyList()
        _recordChartUiState.value = RecordDetailChartUiState()
    }

    /**
     * 基于缓存的原始详情数据重新应用详情展示配置。
     *
     * @return 详情页记录头与功耗拆分统计同步刷新，避免切换设置后出现显示不一致
     */
    private fun applyRecordDetailDisplayConfig() {
        val detail = rawRecordDetail
        _recordDetail.value = detail?.let {
            mapHistoryRecordForDisplay(it, detailDischargeDisplayPositive)
        }
        _recordDetailPowerUiState.value = rawRecordDetailPowerStats?.let { stats ->
            detail?.type?.let { detailType ->
                mapRecordDetailPowerUiState(
                    detailType = detailType,
                    stats = stats,
                    dischargeDisplayPositive = detailDischargeDisplayPositive
                )
            }
        }
    }

    /**
     * 将详情页功耗统计映射为当前显示口径。
     *
     * @param detailType 当前详情页记录类型
     * @param stats 详情页功耗拆分统计的原始功率值
     * @param dischargeDisplayPositive 是否将放电视为正值
     * @return 返回已经完成正负语义映射、但仍保留原始功率单位的 UI 状态
     */
    private fun mapRecordDetailPowerUiState(
        detailType: BatteryStatus,
        stats: RecordDetailPowerStats,
        dischargeDisplayPositive: Boolean
    ): RecordDetailPowerUiState {
        val multiplier = if (
            detailType == BatteryStatus.Discharging &&
            dischargeDisplayPositive
        ) {
            -1.0
        } else {
            1.0
        }
        val calibrationMagnitude = kotlin.math.abs(calibrationValue.toDouble())
        return RecordDetailPowerUiState(
            averagePower = stats.averagePowerRaw * multiplier,
            screenOnAveragePower = stats.screenOnAveragePowerRaw?.times(multiplier),
            screenOffAveragePower = stats.screenOffAveragePowerRaw?.times(multiplier),
            totalTransferredMah = stats.netMahBase * calibrationValue,
            screenOnConsumedMah = stats.screenOnMahBase * calibrationMagnitude,
            screenOffConsumedMah = stats.screenOffMahBase * calibrationMagnitude
        )
    }

    /**
     * 将原始记录点映射为详情图表消费的瓦特值点。
     *
     * @param rawPoints 从记录文件解析出的原始点序列
     * @param dualCellEnabled 是否按双电芯功率口径换算
     * @param calibrationValue 当前电流校准系数
     * @return 返回保留真实功率符号的图表点；充电记录的负值不再在这里裁剪
     */
    private fun mapDisplayPoints(
        rawPoints: List<ChartPoint>,
        dualCellEnabled: Boolean,
        calibrationValue: Int
    ): List<RecordDetailChartPoint> {
        // 详情页图表要求按时间有序；在这里统一排序，后续图表和趋势计算都不再重复关注文件顺序。
        val convertedPoints = rawPoints.sortedBy { it.timestamp }.map { point ->
            val rawPowerW = computePowerW(
                rawPower = point.power,
                dualCellEnabled = dualCellEnabled,
                calibrationValue = calibrationValue
            )
            RecordDetailChartPoint(
                timestamp = point.timestamp,
                rawPowerW = rawPowerW,
                fittedPowerW = rawPowerW,
                packageName = point.packageName,
                capacity = point.capacity,
                isDisplayOn = point.isDisplayOn,
                temp = point.temp,
                voltage = point.voltage
            )
        }
        return convertedPoints
    }

    /**
     * 计算详情图表的时间窗口状态。
     *
     * @param points 原始展示点
     * @param trendPoints 趋势展示点
     * @return 返回图表基础时间范围与全屏模式默认视口大小
     */
    private fun computeViewportState(
        points: List<RecordDetailChartPoint>,
        trendPoints: List<RecordDetailChartPoint>
    ): RecordDetailChartUiState {
        // 全屏模式默认只显示总时长的 25%，让长记录进入详情页时能直接横向拖动浏览局部。
        val minChartTime = points.minOfOrNull { it.timestamp }
        val maxChartTime = points.maxOfOrNull { it.timestamp }
        val totalDurationMs = if (minChartTime != null && maxChartTime != null) {
            (maxChartTime - minChartTime).coerceAtLeast(1L)
        } else {
            0L
        }
        val viewportDurationMs = if (totalDurationMs > 0L) {
            (totalDurationMs * 0.25).roundToLong().coerceAtLeast(1L)
        } else {
            0L
        }
        val maxViewportStart = if (minChartTime != null && maxChartTime != null) {
            (maxChartTime - viewportDurationMs).coerceAtLeast(minChartTime)
        } else {
            null
        }

        return RecordDetailChartUiState(
            points = points,
            trendPoints = trendPoints,
            minChartTime = minChartTime,
            maxChartTime = maxChartTime,
            maxViewportStartTime = maxViewportStart,
            viewportDurationMs = viewportDurationMs
        )
    }

    private fun buildRecordAppDetailEntries(
        context: Context,
        detailType: BatteryStatus,
        lineRecords: List<LineRecord>
    ): List<RecordAppDetailUiEntry> {
        if (detailType != BatteryStatus.Discharging) return emptyList()
        val statsEntries = RecordAppStatsComputer.compute(
            lineRecords,
            recordDetailSamplingIntervalMs
        ).filterNot { it.isScreenOff }
        if (statsEntries.isEmpty()) return emptyList()

        val packageManager = context.packageManager
        return statsEntries.map { entry ->
            val appLabel = resolveAppLabel(
                packageManager = packageManager,
                packageName = entry.packageName
            )
            RecordAppDetailUiEntry(
                key = entry.packageName.orEmpty(),
                packageName = entry.packageName,
                appLabel = appLabel,
                averagePowerRaw = entry.averagePowerRaw,
                averageTempCelsius = entry.averageTempCelsius,
                maxTempCelsius = entry.maxTempCelsius,
                durationMs = entry.totalDurationMs,
                isScreenOff = entry.isScreenOff
            )
        }
    }

    /**
     * 计算记录详情页顶部展示所需的功耗拆分统计。
     *
     * @param detailType 当前详情页记录类型
     * @param lineRecords 当前详情页对应的有效记录点
     * @return 充电/放电记录都返回详情页统计，其它类型直接返回 null
     */
    private fun buildRecordDetailPowerStats(
        detailType: BatteryStatus,
        lineRecords: List<LineRecord>
    ): RecordDetailPowerStats? {
        if (
            detailType != BatteryStatus.Discharging &&
            detailType != BatteryStatus.Charging
        ) {
            return null
        }
        return RecordDetailPowerStatsComputer.compute(lineRecords)
    }

    private fun resolveAppLabel(
        packageManager: android.content.pm.PackageManager,
        packageName: String?
    ): String {
        val resolvedPackageName = packageName?.takeIf { it.isNotBlank() } ?: return ""
        return runCatching {
            val appInfo = packageManager.getApplicationInfo(resolvedPackageName, 0)
            appInfo.loadLabel(packageManager).toString()
        }.getOrDefault(resolvedPackageName)
    }

    private fun List<LineRecord>.toChartPoints(): List<ChartPoint> {
        return map { record ->
            ChartPoint(
                timestamp = record.timestamp,
                power = record.power.toDouble(),
                packageName = record.packageName,
                capacity = record.capacity,
                isDisplayOn = record.isDisplayOn == 1,
                temp = record.temp,
                voltage = record.voltage
            )
        }
    }

    private fun computeTrendPoints(points: List<RecordDetailChartPoint>): List<RecordDetailChartPoint> {
        if (points.isEmpty()) return emptyList()

        // 趋势图按总时长动态分桶，再用桶内中位数表达低频走势。
        // 这里不直接“平滑原始点”，而是先降采样为代表点，再把结果交给图表层画平滑路径，
        // 这样可以把“趋势语义”和“绘制样式”分离。
        val firstTimestamp = points.first().timestamp
        val lastTimestamp = points.last().timestamp
        val bucketDurationMs = computeTrendBucketDurationMs(lastTimestamp - firstTimestamp)
        val bucketPoints = LinkedHashMap<Long, MutableList<RecordDetailChartPoint>>()
        for (point in points) {
            val bucketIndex = (point.timestamp - firstTimestamp) / bucketDurationMs
            bucketPoints.getOrPut(bucketIndex) { ArrayList() } += point
        }

        return bucketPoints.map { (bucketIndex, pointsInBucket) ->
            val bucketStart = firstTimestamp + bucketIndex * bucketDurationMs
            val bucketEnd = (bucketStart + bucketDurationMs).coerceAtMost(lastTimestamp)
            val bucketCenterTimestamp = bucketStart + (bucketEnd - bucketStart) / 2L
            // 时间戳使用桶中心，而容量/温度/亮灭屏状态沿用最接近桶中心的原始点，
            // 这样辅助信息不会凭空插值，仍然来自真实采样。
            val representativePoint = pointsInBucket.minBy { point ->
                kotlin.math.abs(point.timestamp - bucketCenterTimestamp)
            }
            // 功率走势使用桶内中位数而不是平均值，目的是降低少量尖刺对趋势线的干扰。
            val powerValues = pointsInBucket.map { it.rawPowerW }.sorted()
            representativePoint.copy(
                timestamp = bucketCenterTimestamp,
                fittedPowerW = medianOfSorted(powerValues)
            )
        }
    }

    private fun computeTrendBucketDurationMs(totalDurationMs: Long): Long {
        // 先按目标桶数估算，再归一化到“人类可读”的时长阶梯，避免趋势点间距出现难理解的怪值。
        val rawBucketDurationMs =
            (totalDurationMs / TARGET_TREND_BUCKET_COUNT).coerceAtLeast(1_000L)
        return normalizeTrendBucketDurationMs(rawBucketDurationMs)
    }

    private fun normalizeTrendBucketDurationMs(rawBucketDurationMs: Long): Long {
        val readableDurationsMs = buildReadableTrendDurationsMs(rawBucketDurationMs)
        return readableDurationsMs.minBy { duration -> kotlin.math.abs(duration - rawBucketDurationMs) }
    }

    private fun buildReadableTrendDurationsMs(rawBucketDurationMs: Long): List<Long> {
        // 组合 1/2/3/5/10/15/20/30/60 秒与 10^n 倍数，生成一组可读的候选桶宽。
        val baseDurationsSeconds = listOf(1L, 2L, 3L, 5L, 10L, 15L, 20L, 30L, 60L)
        val maxDurationMs = rawBucketDurationMs * 10
        val candidates = LinkedHashSet<Long>()
        var scaleSeconds = 1L
        while (scaleSeconds * 1_000L <= maxDurationMs) {
            for (baseDurationSeconds in baseDurationsSeconds) {
                candidates += baseDurationSeconds * scaleSeconds * 1_000L
            }
            scaleSeconds *= 10L
        }
        return candidates.sorted()
    }

    private fun medianOfSorted(values: List<Double>): Double {
        // 输入已经预排序，因此这里只负责按奇偶长度取中位数，不再重复排序。
        val middleIndex = values.size / 2
        return if (values.size % 2 == 0) {
            (values[middleIndex - 1] + values[middleIndex]) / 2.0
        } else {
            values[middleIndex]
        }
    }
}
