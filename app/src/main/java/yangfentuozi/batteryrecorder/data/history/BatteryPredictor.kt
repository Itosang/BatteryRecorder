package yangfentuozi.batteryrecorder.data.history

import yangfentuozi.batteryrecorder.shared.util.LoggerX
import kotlin.math.abs
import kotlin.math.roundToInt

private const val TAG = "BatteryPredictor"

// 预测终点统一按 1% 计算，分别给出当前电量与满电的可用时长
private const val SOC_CUTOFF = 1.0
private const val MIN_SCENE_MS = 30 * 60 * 1000L  // 30 分钟
private const val MIN_APP_SCENE_MS = 10 * 60 * 1000L  // 10 分钟
private const val MIN_FILE_COUNT = 3
private const val MAX_DRAIN_RATE_PER_HOUR = 50.0   // %/h，超过视为数据异常
private const val CURRENT_PROGRESS_START_MS = 10 * 60 * 1000.0
private const val CURRENT_PROGRESS_FULL_MS = 60 * 60 * 1000.0

/**
 * 首页预测专用输入。
 *
 * sceneStats 只负责提供场景平均功率；
 * k 相关字段全部是首页统一的“非游戏”口径。
 */
data class HomePredictionInputs(
    val sceneStats: SceneStats?,
    val weightingEnabled: Boolean,
    val alphaMax: Double,
    val kBase: Double?,
    val kCurrent: Double?,
    val kFallback: Double?,
    val currentNonGameEffectiveMs: Double,
    val kSampleFileCount: Int,
    val kTotalEnergy: Double,
    val kTotalSocDrop: Double,
    val kRawTotalSocDrop: Double,
    val kTotalDurationMs: Long,
    val kCV: Double?,
    val kEffectiveN: Double,
    val insufficientReason: String? = null
) {
    fun serializeWithoutScene(): String =
        listOf(
            if (weightingEnabled) "1" else "0",
            alphaMax.toString(),
            kBase?.toString().orEmpty(),
            kCurrent?.toString().orEmpty(),
            kFallback?.toString().orEmpty(),
            currentNonGameEffectiveMs.toString(),
            kSampleFileCount.toString(),
            kTotalEnergy.toString(),
            kTotalSocDrop.toString(),
            kRawTotalSocDrop.toString(),
            kTotalDurationMs.toString(),
            kCV?.toString().orEmpty(),
            kEffectiveN.toString()
        ).joinToString(",")

    companion object {
        fun fromString(sceneStats: SceneStats?, value: String): HomePredictionInputs? {
            val parts = value.split(",")
            if (parts.size != 13) return null
            return HomePredictionInputs(
                sceneStats = sceneStats,
                weightingEnabled = parts[0] == "1",
                alphaMax = parts[1].toDoubleOrNull() ?: return null,
                kBase = parts[2].toDoubleOrNull(),
                kCurrent = parts[3].toDoubleOrNull(),
                kFallback = parts[4].toDoubleOrNull(),
                currentNonGameEffectiveMs = parts[5].toDoubleOrNull() ?: return null,
                kSampleFileCount = parts[6].toIntOrNull() ?: return null,
                kTotalEnergy = parts[7].toDoubleOrNull() ?: return null,
                kTotalSocDrop = parts[8].toDoubleOrNull() ?: return null,
                kRawTotalSocDrop = parts[9].toDoubleOrNull() ?: return null,
                kTotalDurationMs = parts[10].toLongOrNull() ?: return null,
                kCV = parts[11].toDoubleOrNull(),
                kEffectiveN = parts[12].toDoubleOrNull() ?: return null
            )
        }
    }
}

/**
 * 首页场景预测结果。
 */
data class PredictionResult(
    val screenOffCurrentHours: Double?,
    val screenOffFullHours: Double?,
    val screenOnDailyCurrentHours: Double?,
    val screenOnDailyFullHours: Double?,
    val insufficientData: Boolean,
    val confidenceScore: Int,
    val insufficientReason: String? = null
)

object BatteryPredictor {

    /**
     * 根据首页专用输入计算“息屏/亮屏日常”预测。
     *
     * scene 平均功率只来自 `inputs.sceneStats`，首页统一 K 只来自
     * `kBase / kCurrent / kFallback` 这三组非游戏口径输入。
     * 当加权算法开启时，当前文件通过 `alpha = alphaMax * progress`
     * 决定对最终 K 的影响比例；否则首页只使用历史侧 K。
     *
     * @param inputs 首页预测所需的完整输入；为 `null` 或内部字段不足时直接返回数据不足。
     * @param currentSoc 当前电量百分比。
     * @return 首页场景预测结果；若输入不足或异常则返回带原因的不足结果。
     */
    fun predict(
        inputs: HomePredictionInputs?,
        currentSoc: Int
    ): PredictionResult {
        val insufficientReason = getInsufficientReason(inputs)
        if (insufficientReason != null) {
            LoggerX.w(TAG, "[预测] 首页预测数据不足: reason=$insufficientReason")
            return PredictionResult(
                screenOffCurrentHours = null,
                screenOffFullHours = null,
                screenOnDailyCurrentHours = null,
                screenOnDailyFullHours = null,
                insufficientData = true,
                confidenceScore = 0,
                insufficientReason = insufficientReason
            )
        }
        val validInputs = inputs ?: error("inputs should be non-null after insufficient check")
        val sceneStats = validInputs.sceneStats
            ?: error("sceneStats should be non-null after insufficient check")

        val currentRemaining = (currentSoc - SOC_CUTOFF).coerceAtLeast(0.0)
        val fullRemaining = 100.0 - SOC_CUTOFF

        val currentProgress = computeCurrentProgress(validInputs.currentNonGameEffectiveMs)
        val alpha = if (validInputs.weightingEnabled) {
            validInputs.alphaMax * currentProgress
        } else {
            0.0
        }
        val baseK = validInputs.kBase
        val currentK = validInputs.kCurrent
        val (finalK, finalKSource) = when {
            alpha <= 0.0 || currentK == null -> {
                if (baseK != null) {
                    baseK to "kBase"
                } else {
                    validInputs.kFallback to "kFallback"
                }
            }

            baseK != null -> ((1.0 - alpha) * baseK + alpha * currentK) to "mixed(kBase,kCurrent)"
            else -> validInputs.kFallback to "kFallback"
        }
        if (finalK == null || finalK <= 0.0 || !finalK.isFinite()) {
            LoggerX.w(TAG, "[预测] 首页预测缺少有效 K 值")
            return PredictionResult(
                screenOffCurrentHours = null,
                screenOffFullHours = null,
                screenOnDailyCurrentHours = null,
                screenOnDailyFullHours = null,
                insufficientData = true,
                confidenceScore = 0,
                insufficientReason = "历史记录未形成有效功耗数据"
            )
        }
        LoggerX.d(
            TAG,
            "[预测] 首页 K 回退: source=$finalKSource alpha=$alpha kBase=$baseK kCurrent=$currentK kFallback=${validInputs.kFallback} finalK=$finalK"
        )

        val overallDrainPerHour =
            validInputs.kRawTotalSocDrop / validInputs.kTotalDurationMs * 3_600_000.0
        if (overallDrainPerHour > MAX_DRAIN_RATE_PER_HOUR) {
            LoggerX.w(TAG, "[预测] 首页预测异常掉电: drainPerHour=$overallDrainPerHour")
            return PredictionResult(
                screenOffCurrentHours = null,
                screenOffFullHours = null,
                screenOnDailyCurrentHours = null,
                screenOnDailyFullHours = null,
                insufficientData = true,
                confidenceScore = 0,
                insufficientReason = "历史记录存在异常掉电跳变，无法计算预测"
            )
        }

        val historyConfidence = computeHistoryConfidence(validInputs.kCV, validInputs.kEffectiveN)
        val confidenceScore = (100 * historyConfidence).roundToInt()

        val screenOffCurrentHours =
            if (sceneStats.screenOffTotalMs >= MIN_SCENE_MS && abs(sceneStats.screenOffAvgPowerRaw) > 0) {
                val drainPerMs = finalK * abs(sceneStats.screenOffAvgPowerRaw)
                currentRemaining / (drainPerMs * 3_600_000.0)
            } else {
                null
            }
        val screenOffFullHours =
            if (sceneStats.screenOffTotalMs >= MIN_SCENE_MS && abs(sceneStats.screenOffAvgPowerRaw) > 0) {
                val drainPerMs = finalK * abs(sceneStats.screenOffAvgPowerRaw)
                fullRemaining / (drainPerMs * 3_600_000.0)
            } else {
                null
            }

        val screenOnCurrentHours =
            if (sceneStats.screenOnDailyTotalMs >= MIN_SCENE_MS && abs(sceneStats.screenOnDailyAvgPowerRaw) > 0) {
                val drainPerMs = finalK * abs(sceneStats.screenOnDailyAvgPowerRaw)
                currentRemaining / (drainPerMs * 3_600_000.0)
            } else {
                null
            }
        val screenOnFullHours =
            if (sceneStats.screenOnDailyTotalMs >= MIN_SCENE_MS && abs(sceneStats.screenOnDailyAvgPowerRaw) > 0) {
                val drainPerMs = finalK * abs(sceneStats.screenOnDailyAvgPowerRaw)
                fullRemaining / (drainPerMs * 3_600_000.0)
            } else {
                null
            }

        val result = PredictionResult(
            screenOffCurrentHours = screenOffCurrentHours,
            screenOffFullHours = screenOffFullHours,
            screenOnDailyCurrentHours = screenOnCurrentHours,
            screenOnDailyFullHours = screenOnFullHours,
            insufficientData = false,
            confidenceScore = confidenceScore,
            insufficientReason = null
        )
        LoggerX.i(
            TAG,
            "[预测] 首页预测完成: currentSoc=$currentSoc alpha=$alpha confidence=$confidenceScore offCurrent=${result.screenOffCurrentHours} onCurrent=${result.screenOnDailyCurrentHours}"
        )
        return result
    }

    /**
     * 将当前文件的非游戏有效时长映射为 0~1 的进度值。
     *
     * 10 分钟以内视为当前文件样本尚未形成，进度固定为 0；
     * 60 分钟及以上视为当前文件信息已经足够，进度固定为 1；
     * 中间区间按线性比例插值。
     *
     * @param currentNonGameEffectiveMs 当前文件非游戏 effective 时长，单位毫秒。
     * @return 位于 `0.0..1.0` 的当前样本进度。
     */
    private fun computeCurrentProgress(currentNonGameEffectiveMs: Double): Double {
        if (currentNonGameEffectiveMs <= CURRENT_PROGRESS_START_MS) {
            return 0.0
        }
        return ((currentNonGameEffectiveMs - CURRENT_PROGRESS_START_MS) /
                (CURRENT_PROGRESS_FULL_MS - CURRENT_PROGRESS_START_MS)).coerceIn(0.0, 1.0)
    }

    private fun computeHistoryConfidence(kCV: Double?, kEffectiveN: Double): Double {
        val cvScore = if (kCV != null) ((0.30 - kCV) / 0.30).coerceIn(0.0, 1.0) else 0.0
        val nScore = ((kEffectiveN - 3.0) / 7.0).coerceIn(0.0, 1.0)
        return (0.7 * cvScore + 0.3 * nScore).coerceIn(0.0, 1.0)
    }

    private fun getInsufficientReason(inputs: HomePredictionInputs?): String? {
        if (inputs == null) {
            return "暂无可用于预测的放电统计数据"
        }
        if (inputs.insufficientReason != null) {
            return inputs.insufficientReason
        }
        if (inputs.sceneStats == null) {
            return "暂无可用于预测的放电统计数据"
        }
        if (inputs.kSampleFileCount < MIN_FILE_COUNT) {
            return "有效放电记录不足 3 份"
        }
        if (inputs.kTotalSocDrop <= 0) {
            return "历史记录未形成有效百分比掉电数据"
        }
        if (inputs.kTotalEnergy <= 0) {
            return "历史记录未形成有效功耗数据"
        }
        if (inputs.kTotalDurationMs <= 0L) {
            return "历史记录总时长无效"
        }
        return null
    }

    /**
     * 计算应用维度的当前剩余时长。
     *
     * 这里仍保留原始时长掉电速率校验，用于过滤 SOC 跳变；
     * 实际剩余时长使用 effective 口径，才能反映加权算法配置。
     */
    fun predictAppCurrentHours(
        entry: AppStatsEntry,
        currentSoc: Int
    ): Double? {
        if (entry.totalForegroundMs < MIN_APP_SCENE_MS) return null
        if (abs(entry.rawAvgPowerRaw) <= 0) return null
        if (entry.rawSocDrop <= 0 || entry.effectiveSocDrop <= 0) return null
        if (entry.effectiveForegroundMs <= 0) return null

        val drainPerHour = entry.rawSocDrop / entry.totalForegroundMs * 3_600_000.0
        if (drainPerHour > MAX_DRAIN_RATE_PER_HOUR) return null

        val currentRemaining = (currentSoc - SOC_CUTOFF).coerceAtLeast(0.0)
        val drainPerMs = entry.effectiveSocDrop / entry.effectiveForegroundMs
        val hours = currentRemaining / (drainPerMs * 3_600_000.0)
        LoggerX.d(
            TAG,
            "[预测] 应用预测完成: package=${entry.packageName} currentSoc=$currentSoc hours=$hours"
        )
        return hours
    }
}
