package yangfentuozi.batteryrecorder.usecase.home

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import yangfentuozi.batteryrecorder.R
import yangfentuozi.batteryrecorder.appString
import yangfentuozi.batteryrecorder.data.history.BatteryPredictor
import yangfentuozi.batteryrecorder.data.history.CurrentRecordLoadResult
import yangfentuozi.batteryrecorder.data.history.HistoryRecord
import yangfentuozi.batteryrecorder.data.history.HistorySummary
import yangfentuozi.batteryrecorder.data.history.PredictionResult
import yangfentuozi.batteryrecorder.data.history.SceneStats
import yangfentuozi.batteryrecorder.data.history.SceneStatsComputer
import yangfentuozi.batteryrecorder.data.history.SyncUtil
import yangfentuozi.batteryrecorder.shared.config.SharedSettings
import yangfentuozi.batteryrecorder.shared.config.dataclass.StatisticsSettings
import yangfentuozi.batteryrecorder.shared.data.BatteryStatus
import yangfentuozi.batteryrecorder.shared.data.RecordsFile
import yangfentuozi.batteryrecorder.shared.util.LoggerX
import yangfentuozi.batteryrecorder.ui.mapper.PowerDisplayMapper
import yangfentuozi.batteryrecorder.ui.model.HomePredictionDisplay
import yangfentuozi.batteryrecorder.ui.model.PredictionConfidenceLevel
import kotlin.math.abs
import kotlin.math.roundToInt

private const val TAG = "LoadHomeStatsUseCase"
private const val PREDICTION_DISPLAY_SCORE_OFFSET = 5
private const val PREDICTION_CONFIDENCE_LOW_MAX = 44
private const val PREDICTION_CONFIDENCE_MEDIUM_MAX = 74
private const val HOME_PREDICTION_SCORE_SMOOTHING_ALPHA = 0.4
private const val HOME_PREDICTION_SCORE_PREFS_NAME = "home_prediction_score"
private const val HOME_PREDICTION_SCORE_KEY = "score"
private const val HOME_PREDICTION_SCORE_CONFIG_KEY = "score_config_key"

private sealed interface CurrentRecordDisplayLoadResult {
    data class Success(val record: HistoryRecord) : CurrentRecordDisplayLoadResult
    data class Pending(val recordsFile: RecordsFile) : CurrentRecordDisplayLoadResult
    data class Missing(val recordsFile: RecordsFile) : CurrentRecordDisplayLoadResult
    data class Failed(val recordsFile: RecordsFile, val error: Throwable) : CurrentRecordDisplayLoadResult
}

/**
 * 首页统计加载结果。
 */
internal data class HomeStatsLoadResult(
    val chargeSummary: HistorySummary?,
    val dischargeSummary: HistorySummary?,
    val nextPendingRecordsFile: RecordsFile?,
    val nextActiveLiveRecordsFileName: String?,
    val nextDisplayStatus: BatteryStatus?,
    val currentRecord: HistoryRecord?,
    val sceneStats: SceneStats?,
    val shouldUpdatePrediction: Boolean,
    val prediction: PredictionResult?,
    val predictionDisplay: HomePredictionDisplay?,
    val currentRecordFailureMessage: String?
)

/**
 * 统一加载首页统计、当前记录和预测展示数据。
 */
internal object LoadHomeStatsUseCase {

    /**
     * 加载首页统计链路所需的全部数据。
     *
     * @param context 应用上下文。
     * @param request 当前统计设置。
     * @param recordIntervalMs 当前采样间隔。
     * @param pendingCurrentRecordsFile 当前等待中的记录文件。
     * @param expectedCurrentRecordsFile 本次刷新期望收敛到的记录文件。
     * @param serviceCurrentRecordsFile 服务端当前活动记录文件。
     * @return 返回首页统计加载结果。
     */
    suspend fun execute(
        context: Context,
        request: StatisticsSettings,
        recordIntervalMs: Long,
        pendingCurrentRecordsFile: RecordsFile?,
        expectedCurrentRecordsFile: RecordsFile?,
        serviceCurrentRecordsFile: RecordsFile?
    ): HomeStatsLoadResult {
        val dischargeDisplayPositive =
            SharedSettings.readAppSettings(context).dischargeDisplayPositive

        withContext(Dispatchers.IO) {
            runCatching { SyncUtil.sync(context) }
        }

        val chargeSummary = withContext(Dispatchers.IO) {
            yangfentuozi.batteryrecorder.data.history.HistoryRepository.loadSummary(
                context,
                BatteryStatus.Charging
            )
        }?.let { summary ->
            PowerDisplayMapper.mapHistorySummary(summary, dischargeDisplayPositive)
        }
        val dischargeSummary = withContext(Dispatchers.IO) {
            yangfentuozi.batteryrecorder.data.history.HistoryRepository.loadSummary(
                context,
                BatteryStatus.Discharging
            )
        }?.let { summary ->
            PowerDisplayMapper.mapHistorySummary(summary, dischargeDisplayPositive)
        }

        val targetRecordsFile = when {
            pendingCurrentRecordsFile != null &&
                serviceCurrentRecordsFile != null &&
                pendingCurrentRecordsFile != serviceCurrentRecordsFile -> {
                serviceCurrentRecordsFile
            }

            pendingCurrentRecordsFile != null -> pendingCurrentRecordsFile
            expectedCurrentRecordsFile != null -> expectedCurrentRecordsFile
            else -> serviceCurrentRecordsFile
        }
        LoggerX.i(
            TAG,
            "[首页] 当前记录目标: pending=%s/%s expected=%s/%s service=%s/%s target=%s/%s",
            pendingCurrentRecordsFile?.type,
            pendingCurrentRecordsFile?.name,
            expectedCurrentRecordsFile?.type,
            expectedCurrentRecordsFile?.name,
            serviceCurrentRecordsFile?.type,
            serviceCurrentRecordsFile?.name,
            targetRecordsFile?.type,
            targetRecordsFile?.name
        )

        val currentRecordResult = targetRecordsFile?.let {
            loadCurrentRecordForDisplay(
                context = context,
                dischargeDisplayPositive = dischargeDisplayPositive,
                recordsFile = it
            )
        }
        val resolvedCurrentRecord =
            (currentRecordResult as? CurrentRecordDisplayLoadResult.Success)?.record
        val currentRecordFailureMessage =
            (currentRecordResult as? CurrentRecordDisplayLoadResult.Failed)?.let {
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
        val currentPredictionSoc = readCurrentBatteryCapacityPercent(context)
            ?: resolvedCurrentRecord?.stats?.endCapacity
        val shouldRefreshPrediction = currentPredictionSoc != null
        val prediction = if (currentPredictionSoc != null) {
            BatteryPredictor.predict(
                stats.homePredictionInputs,
                currentPredictionSoc
            )
        } else {
            null
        }
        return HomeStatsLoadResult(
            chargeSummary = chargeSummary,
            dischargeSummary = dischargeSummary,
            nextPendingRecordsFile = nextPendingRecordsFile,
            nextActiveLiveRecordsFileName = nextActiveLiveRecordsFileName,
            nextDisplayStatus = when {
                nextPendingRecordsFile != null -> nextPendingRecordsFile.type
                resolvedCurrentRecord != null -> resolvedCurrentRecord.type
                serviceCurrentRecordsFile != null -> serviceCurrentRecordsFile.type
                else -> null
            },
            currentRecord = resolvedCurrentRecord,
            sceneStats = stats.displayStats,
            shouldUpdatePrediction = shouldRefreshPrediction,
            prediction = prediction,
            predictionDisplay = if (shouldRefreshPrediction) {
                buildPredictionDisplay(context, request, prediction, stats.homePredictionInputs)
            } else {
                null
            },
            currentRecordFailureMessage = currentRecordFailureMessage
        )
    }

    /**
     * 加载当前记录卡片展示数据。
     *
     * @param context 应用上下文。
     * @param dischargeDisplayPositive 是否将放电视为正值。
     * @param recordsFile 当前目标记录文件。
     * @return 返回当前记录展示加载结果。
     */
    private suspend fun loadCurrentRecordForDisplay(
        context: Context,
        dischargeDisplayPositive: Boolean,
        recordsFile: RecordsFile
    ): CurrentRecordDisplayLoadResult {
        return withContext(Dispatchers.IO) {
            when (val result = yangfentuozi.batteryrecorder.data.history.HistoryRepository
                .loadCurrentRecord(context, recordsFile)) {
                is CurrentRecordLoadResult.Success -> {
                    CurrentRecordDisplayLoadResult.Success(
                        PowerDisplayMapper.mapHistoryRecord(
                            record = result.record,
                            dischargeDisplayPositive = dischargeDisplayPositive
                        )
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

    /**
     * 构建当前记录加载失败提示文案。
     *
     * @param recordsFile 当前失败的分段文件。
     * @param error 触发失败的异常。
     * @return 返回展示给用户的失败提示。
     */
    private fun buildCurrentRecordLoadFailureMessage(
        recordsFile: RecordsFile,
        error: Throwable
    ): String {
        val detail = error.message?.takeIf { it.isNotBlank() } ?: error::class.java.simpleName
        return appString(R.string.toast_current_record_load_failed, recordsFile.name, detail)
    }

    /**
     * 读取系统当前电量百分比。
     *
     * @param context 应用上下文。
     * @return 当前电量百分比；系统广播缺少 level 或 scale 时返回空值。
     */
    private fun readCurrentBatteryCapacityPercent(context: Context): Int? {
        val intent: Intent = context.applicationContext.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        ) ?: return null
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return null
        return (level * 100f / scale).roundToInt()
    }

    /**
     * 把首页预测原始结果映射为卡片展示数据。
     *
     * @param prediction 首页预测算法返回的原始结果。
     * @return 返回首页卡片展示数据；为空时返回空值。
     */
    private fun buildPredictionDisplay(
        context: Context,
        request: StatisticsSettings,
        prediction: PredictionResult?,
        homePredictionInputs: yangfentuozi.batteryrecorder.data.history.HomePredictionInputs?
    ): HomePredictionDisplay? {
        if (prediction == null) {
            return null
        }
        if (prediction.insufficientData) {
            return HomePredictionDisplay(
                insufficientReason = prediction.insufficientReason ?: appString(R.string.common_insufficient_data)
            )
        }

        val adjustedScore =
            (prediction.confidenceScore + PREDICTION_DISPLAY_SCORE_OFFSET).coerceIn(0, 100)
        val rawHistoricalScore = homePredictionInputs?.let { inputs ->
            val sceneStats = inputs.sceneStats ?: return@let null
            val historicalK = inputs.kBase
            if (
                historicalK == null ||
                historicalK <= 0.0 ||
                sceneStats.screenOnDailyTotalMs < 30 * 60 * 1000L ||
                abs(sceneStats.screenOnDailyAvgPowerRaw) <= 0.0
            ) {
                return@let null
            }
            val drainPerMs = historicalK * abs(sceneStats.screenOnDailyAvgPowerRaw)
            if (drainPerMs <= 0.0 || !drainPerMs.isFinite()) {
                return@let null
            }
            val historicalFullHours = 99.0 / (drainPerMs * 3_600_000.0)
            if (!historicalFullHours.isFinite() || historicalFullHours <= 0.0) {
                return@let null
            }
            val confidence = prediction.confidenceScore / 100.0
            historicalFullHours * 3600.0 *
                (0.75 + 0.25 * (2.0 * confidence - confidence * confidence))
        }
        val score = rawHistoricalScore?.let { rawScore ->
            val prefs = context.applicationContext.getSharedPreferences(
                HOME_PREDICTION_SCORE_PREFS_NAME,
                Context.MODE_PRIVATE
            )
            val currentConfigKey = buildPredictionScoreConfigKey(request)
            val previousConfigKey = prefs.getString(HOME_PREDICTION_SCORE_CONFIG_KEY, null)
            val smoothedScore = if (
                previousConfigKey == currentConfigKey &&
                prefs.contains(HOME_PREDICTION_SCORE_KEY)
            ) {
                val previousScore = Double.fromBits(prefs.getLong(HOME_PREDICTION_SCORE_KEY, 0L))
                HOME_PREDICTION_SCORE_SMOOTHING_ALPHA * rawScore +
                    (1.0 - HOME_PREDICTION_SCORE_SMOOTHING_ALPHA) * previousScore
            } else {
                rawScore
            }
            prefs.edit()
                .putLong(HOME_PREDICTION_SCORE_KEY, smoothedScore.toBits())
                .putString(HOME_PREDICTION_SCORE_CONFIG_KEY, currentConfigKey)
                .apply()
            smoothedScore
        }
        return HomePredictionDisplay(
            confidenceLevel = mapPredictionConfidenceLevel(adjustedScore),
            screenOffCurrentHours = prediction.screenOffCurrentHours,
            screenOffFullHours = prediction.screenOffFullHours,
            screenOnDailyCurrentHours = prediction.screenOnDailyCurrentHours,
            screenOnDailyFullHours = prediction.screenOnDailyFullHours,
            score = score
        )
    }

    private fun buildPredictionScoreConfigKey(request: StatisticsSettings): String {
        val sortedGamePackages = request.gamePackages.toList().sorted().joinToString(",")
        val sortedGameBlacklist = request.gameBlacklist.toList().sorted().joinToString(",")
        return listOf(
            request.sceneStatsRecentFileCount.toString(),
            request.predWeightedAlgorithmEnabled.toString(),
            request.predWeightedAlgorithmAlphaMaxX100.toString(),
            sortedGamePackages,
            sortedGameBlacklist
        ).joinToString("|")
    }

    /**
     * 根据首页展示分映射置信度档位。
     *
     * @param adjustedScore 已完成偏移与截断的展示分。
     * @return 返回首页卡片置信度档位。
     */
    private fun mapPredictionConfidenceLevel(adjustedScore: Int): PredictionConfidenceLevel {
        return when (adjustedScore) {
            in 0..PREDICTION_CONFIDENCE_LOW_MAX -> PredictionConfidenceLevel.Low
            in (PREDICTION_CONFIDENCE_LOW_MAX + 1)..PREDICTION_CONFIDENCE_MEDIUM_MAX ->
                PredictionConfidenceLevel.Medium

            else -> PredictionConfidenceLevel.High
        }
    }
}
