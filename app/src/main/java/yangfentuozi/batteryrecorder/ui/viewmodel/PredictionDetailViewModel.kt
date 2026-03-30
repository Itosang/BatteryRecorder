package yangfentuozi.batteryrecorder.ui.viewmodel

import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import yangfentuozi.batteryrecorder.data.history.AppStatsComputer
import yangfentuozi.batteryrecorder.data.history.BatteryPredictor
import yangfentuozi.batteryrecorder.data.history.HistoryRepository
import yangfentuozi.batteryrecorder.data.history.SyncUtil
import yangfentuozi.batteryrecorder.shared.config.dataclass.StatisticsSettings
import yangfentuozi.batteryrecorder.shared.data.BatteryStatus
import yangfentuozi.batteryrecorder.shared.util.LoggerX

private const val TAG = "PredictionDetailViewModel"

/**
 * 预测详情页单行展示数据。
 *
 * averagePowerRaw 保持原始口径，正负值映射交给 Screen 层根据设置处理。
 */
data class PredictionDetailUiEntry(
    val packageName: String,
    val appLabel: String,
    val averagePowerRaw: Double,
    val currentHours: Double?
)

data class PredictionDetailUiState(
    val isLoading: Boolean = false,
    val entries: List<PredictionDetailUiEntry> = emptyList(),
    val errorMessage: String? = null
)

class PredictionDetailViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(PredictionDetailUiState())
    val uiState: StateFlow<PredictionDetailUiState> = _uiState.asStateFlow()

    // 详情页配置变化时允许新请求覆盖旧请求，避免 isLoading 把刷新吞掉。
    private var loadJob: Job? = null
    private var loadGeneration = 0L

    /**
     * 加载应用维度预测详情。
     *
     * 同步、文件读取与包管理器查询都在 IO 线程执行；仅最后一次请求允许落状态。
     */
    fun load(
        context: Context,
        request: StatisticsSettings,
        recordIntervalMs: Long
    ) {
        val generation = ++loadGeneration
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val entries = try {
                withContext(Dispatchers.IO) {
                    SyncUtil.sync(context)

                    val latestDischargeFile = HistoryRepository
                        .listRecordFiles(context, BatteryStatus.Discharging)
                        .firstOrNull()
                    val latestDischargeRecord = latestDischargeFile?.let { file ->
                        HistoryRepository.loadStats(context, file, needCaching = false)
                    }
                    val currentSoc = latestDischargeRecord?.stats?.endCapacity
                    val packageManager = context.packageManager
                    val appStats = AppStatsComputer.compute(
                        context = context,
                        request = request,
                        recordIntervalMs = recordIntervalMs,
                        currentDischargeFileName = latestDischargeRecord?.name
                    )
                    appStats.entries.mapNotNull { entry ->
                        resolveInstalledAppEntry(
                            packageManager = packageManager,
                            entry = entry,
                            currentSoc = currentSoc
                        )
                    }
                }
            } catch (error: Throwable) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                LoggerX.e(TAG, "加载应用预测失败", tr = error)
                if (generation == loadGeneration) {
                    _uiState.value = PredictionDetailUiState(
                        isLoading = false,
                        errorMessage = "加载应用预测失败"
                    )
                }
                return@launch
            }

            if (generation == loadGeneration) {
                _uiState.value = PredictionDetailUiState(
                    isLoading = false,
                    entries = entries
                )
            }
        }
    }

    private fun resolveInstalledAppEntry(
        packageManager: PackageManager,
        entry: yangfentuozi.batteryrecorder.data.history.AppStatsEntry,
        currentSoc: Int?
    ): PredictionDetailUiEntry? {
        // 仅展示当前仍已安装的应用，卸载包直接跳过。
        val appInfo = runCatching {
            packageManager.getApplicationInfo(entry.packageName, 0)
        }.getOrNull() ?: return null
        val label = runCatching {
            appInfo.loadLabel(packageManager).toString()
        }.getOrDefault(entry.packageName)
        return PredictionDetailUiEntry(
            packageName = entry.packageName,
            appLabel = label,
            averagePowerRaw = entry.rawAvgPowerRaw,
            currentHours = currentSoc?.let { BatteryPredictor.predictAppCurrentHours(entry, it) }
        )
    }
}
