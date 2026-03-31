package yangfentuozi.batteryrecorder.data.history

import android.content.Context
import yangfentuozi.batteryrecorder.shared.config.dataclass.StatisticsSettings
import yangfentuozi.batteryrecorder.shared.util.LoggerX
import java.io.File
import kotlin.math.sqrt

private const val TAG = "SceneStatsComputer"
private const val MIN_HOME_CURRENT_SESSION_MS = 10 * 60 * 1000L
private const val MIN_HOME_CURRENT_SESSION_SOC_DROP = 1.0

/**
 * 场景统计结果。
 *
 * 同一个模型同时承载展示口径与预测口径：
 * 展示口径保留有符号平均功率用于 UI 显示方向，预测口径保留加权时长与绝对值能量用于续航计算。
 * 因此 effective 时长字段在展示口径实例中会回填为原始时长，统一下游消费与缓存结构。
 */
data class SceneStats(
    val screenOffAvgPowerRaw: Double,
    val screenOffTotalMs: Long,
    val screenOffEffectiveTotalMs: Double,
    val screenOnDailyAvgPowerRaw: Double,
    val screenOnDailyTotalMs: Long,
    val screenOnDailyEffectiveTotalMs: Double,
    val totalEnergyRawMs: Double,
    val totalSocDrop: Double,
    val totalDurationMs: Long,
    val fileCount: Int,
    val rawTotalSocDrop: Double
) {
    override fun toString(): String =
        "$screenOffAvgPowerRaw,$screenOffTotalMs,$screenOffEffectiveTotalMs," +
                "$screenOnDailyAvgPowerRaw,$screenOnDailyTotalMs,$screenOnDailyEffectiveTotalMs," +
                "$totalEnergyRawMs,$totalSocDrop,$totalDurationMs,$fileCount,$rawTotalSocDrop"

    companion object {
        fun fromString(s: String): SceneStats? {
            val p = s.split(",")
            if (p.size != 11) return null

            val offTotalMs = p[1].toLongOrNull() ?: return null
            val dailyTotalMs = p[4].toLongOrNull() ?: return null
            return SceneStats(
                screenOffAvgPowerRaw = p[0].toDoubleOrNull() ?: return null,
                screenOffTotalMs = offTotalMs,
                screenOffEffectiveTotalMs = p[2].toDoubleOrNull() ?: return null,
                screenOnDailyAvgPowerRaw = p[3].toDoubleOrNull() ?: return null,
                screenOnDailyTotalMs = dailyTotalMs,
                screenOnDailyEffectiveTotalMs = p[5].toDoubleOrNull() ?: return null,
                totalEnergyRawMs = p[6].toDoubleOrNull() ?: return null,
                totalSocDrop = p[7].toDoubleOrNull() ?: return null,
                totalDurationMs = p[8].toLongOrNull() ?: return null,
                fileCount = p[9].toIntOrNull() ?: return null,
                rawTotalSocDrop = p[10].toDoubleOrNull() ?: return null
            )
        }
    }
}

/**
 * displayStats 面向 UI 展示，predictionStats 面向首页预测使用的场景平均功率。
 */
data class SceneComputeResult(
    val displayStats: SceneStats?,
    val predictionStats: SceneStats?,
    val homePredictionInputs: HomePredictionInputs?
)

object SceneStatsComputer {

    private data class FileKInput(
        val k: Double,
        val weight: Double
    )

    private data class FileNonGameContribution(
        val fileName: String,
        val rawDurationMs: Long,
        val effectiveDurationMs: Double,
        val effectiveEnergy: Double,
        val effectiveCapDrop: Double
    )

    fun compute(
        context: Context,
        request: StatisticsSettings,
        recordIntervalMs: Long,
        currentDischargeFileName: String? = null,
    ): SceneComputeResult {
        val files = DischargeRecordScanner.listRecentDischargeFiles(
            context = context,
            recentFileCount = request.sceneStatsRecentFileCount
        )
        if (files.isEmpty()) {
            LoggerX.d(TAG, "[预测] 场景统计无放电文件")
            return SceneComputeResult(
                displayStats = null,
                predictionStats = null,
                homePredictionInputs = HomePredictionInputs(
                    sceneStats = null,
                    weightingEnabled = request.predWeightedAlgorithmEnabled,
                    alphaMax = request.predWeightedAlgorithmAlphaMaxX100 / 100.0,
                    kBase = null,
                    kCurrent = null,
                    kFallback = null,
                    currentNonGameEffectiveMs = 0.0,
                    kSampleFileCount = 0,
                    kTotalEnergy = 0.0,
                    kTotalSocDrop = 0.0,
                    kTotalDurationMs = 0L,
                    kCV = null,
                    kEffectiveN = 0.0,
                    insufficientReason = "最近没有放电记录"
                )
            )
        }

        val cacheKey = buildCacheKey(
            files = files,
            request = request,
            recordIntervalMs = recordIntervalMs,
            currentDischargeFileName = currentDischargeFileName
        )
        val cacheFile = getSceneStatsCacheFile(context.cacheDir, cacheKey)
        LoggerX.d(
            TAG,
            "[预测] 计算场景统计: fileCount=${files.size} cache=${cacheFile.name} current=$currentDischargeFileName"
        )
        if (cacheFile.exists()) {
            val cacheLines = cacheFile.readText().trim().lines()
            val displayStats = cacheLines.getOrNull(0)?.let { SceneStats.fromString(it) }
            val predictionStats = cacheLines.getOrNull(1)?.let { SceneStats.fromString(it) }
            val homePredictionInputs = cacheLines.getOrNull(2)?.let {
                HomePredictionInputs.fromString(predictionStats, it)
            }
            if (displayStats != null && predictionStats != null && homePredictionInputs != null) {
                LoggerX.d(TAG, "[预测] 命中场景统计缓存: ${cacheFile.name}")
                return SceneComputeResult(displayStats, predictionStats, homePredictionInputs)
            }
            LoggerX.w(TAG, "[预测] 场景统计缓存损坏，删除重算: ${cacheFile.absolutePath}")
            cacheFile.delete()
        }

        var rawSignedOffEnergy = 0.0
        var offTime = 0L
        var rawSignedDailyEnergy = 0.0
        var dailyTime = 0L
        var rawSignedGameEnergy = 0.0
        var gameTime = 0L

        var rawTotalCapDrop = 0.0
        var effectiveTotalCapDrop = 0.0
        var usedFileCount = 0

        var effectiveOffEnergy = 0.0
        var effectiveOffTimeWeighted = 0.0
        var effectiveDailyEnergy = 0.0
        var effectiveDailyTimeWeighted = 0.0
        var effectiveGameEnergy = 0.0
        var effectiveGameTimeWeighted = 0.0

        val historicalKEntries = mutableListOf<FileKInput>()
        val gamePackages = request.gamePackages
        var currentNonGameEffectiveMs = 0.0
        var kSampleFileCount = 0
        var kTotalEnergy = 0.0
        var kTotalSocDrop = 0.0
        var kTotalDurationMs = 0L
        var kCurrent: Double? = null

        val scanSummary = DischargeRecordScanner.scan(
            context = context,
            request = request,
            recordIntervalMs = recordIntervalMs,
            currentDischargeFileName = currentDischargeFileName
        ) { acceptedFile ->
            var fileRawSignedOffEnergy = 0.0
            var fileOffTime = 0L
            var fileRawSignedDailyEnergy = 0.0
            var fileDailyTime = 0L
            var fileRawSignedGameEnergy = 0.0
            var fileGameTime = 0L

            var fileHomeEffectiveOffEnergy = 0.0
            var fileHomeEffectiveOffTime = 0.0
            var fileHomeEffectiveDailyEnergy = 0.0
            var fileHomeEffectiveDailyTime = 0.0
            var fileHomeEffectiveGameEnergy = 0.0
            var fileHomeEffectiveGameTime = 0.0
            var fileNonGameRawCapDrop = 0.0

            acceptedFile.intervals.forEach { interval ->
                when {
                    !interval.isDisplayOn -> {
                        fileRawSignedOffEnergy += interval.signedEnergyRawMs
                        fileOffTime += interval.durationMs
                        fileNonGameRawCapDrop += interval.capDrop
                    }

                    interval.packageName == null || interval.packageName !in gamePackages -> {
                        fileRawSignedDailyEnergy += interval.signedEnergyRawMs
                        fileDailyTime += interval.durationMs
                        fileNonGameRawCapDrop += interval.capDrop
                    }

                    else -> {
                        fileRawSignedGameEnergy += interval.signedEnergyRawMs
                        fileGameTime += interval.durationMs
                    }
                }
            }

            val fileNonGameRawDuration = fileOffTime + fileDailyTime
            val useHomeWeightedCurrentFile =
                request.predWeightedAlgorithmEnabled &&
                        acceptedFile.file.name == currentDischargeFileName &&
                        fileNonGameRawDuration >= MIN_HOME_CURRENT_SESSION_MS &&
                        fileNonGameRawCapDrop >= MIN_HOME_CURRENT_SESSION_SOC_DROP

            var fileHomeEffectiveNonGameCapDrop = 0.0
            acceptedFile.intervals.forEach { interval ->
                val homeWeight = if (useHomeWeightedCurrentFile) interval.timeDecayWeight else 1.0
                val homeEffectiveDuration = interval.durationMs.toDouble() * homeWeight
                val homeEffectiveEnergy = kotlin.math.abs(interval.signedEnergyRawMs) * homeWeight
                val homeEffectiveCapDrop = interval.capDrop * homeWeight
                when {
                    !interval.isDisplayOn -> {
                        fileHomeEffectiveOffEnergy += homeEffectiveEnergy
                        fileHomeEffectiveOffTime += homeEffectiveDuration
                        fileHomeEffectiveNonGameCapDrop += homeEffectiveCapDrop
                    }

                    interval.packageName == null || interval.packageName !in gamePackages -> {
                        fileHomeEffectiveDailyEnergy += homeEffectiveEnergy
                        fileHomeEffectiveDailyTime += homeEffectiveDuration
                        fileHomeEffectiveNonGameCapDrop += homeEffectiveCapDrop
                    }

                    else -> {
                        fileHomeEffectiveGameEnergy += homeEffectiveEnergy
                        fileHomeEffectiveGameTime += homeEffectiveDuration
                    }
                }
            }

            usedFileCount += 1
            rawSignedOffEnergy += fileRawSignedOffEnergy
            offTime += fileOffTime
            rawSignedDailyEnergy += fileRawSignedDailyEnergy
            dailyTime += fileDailyTime
            rawSignedGameEnergy += fileRawSignedGameEnergy
            gameTime += fileGameTime
            rawTotalCapDrop += acceptedFile.rawTotalCapDrop
            effectiveOffEnergy += fileHomeEffectiveOffEnergy
            effectiveOffTimeWeighted += fileHomeEffectiveOffTime
            effectiveDailyEnergy += fileHomeEffectiveDailyEnergy
            effectiveDailyTimeWeighted += fileHomeEffectiveDailyTime
            effectiveGameEnergy += fileHomeEffectiveGameEnergy
            effectiveGameTimeWeighted += fileHomeEffectiveGameTime
            effectiveTotalCapDrop += acceptedFile.effectiveTotalCapDrop

            val fileNonGameContribution = FileNonGameContribution(
                fileName = acceptedFile.file.name,
                rawDurationMs = fileOffTime + fileDailyTime,
                effectiveDurationMs = fileHomeEffectiveOffTime + fileHomeEffectiveDailyTime,
                effectiveEnergy = fileHomeEffectiveOffEnergy + fileHomeEffectiveDailyEnergy,
                effectiveCapDrop = fileHomeEffectiveNonGameCapDrop
            )
            collectHomePredictionContribution(
                contribution = fileNonGameContribution,
                currentDischargeFileName = currentDischargeFileName,
                historicalKEntries = historicalKEntries,
                onCurrentEffectiveMs = { currentNonGameEffectiveMs = it },
                onCurrentK = { kCurrent = it },
                onTotals = { durationMs, energy, socDrop ->
                    kSampleFileCount += 1
                    kTotalDurationMs += durationMs
                    kTotalEnergy += energy
                    kTotalSocDrop += socDrop
                }
            )
        }

        if (usedFileCount <= 0) {
            LoggerX.w(TAG, "[预测] 场景统计无有效文件，准备返回不足原因")
            return SceneComputeResult(
                displayStats = null,
                predictionStats = null,
                homePredictionInputs = HomePredictionInputs(
                    sceneStats = null,
                    weightingEnabled = request.predWeightedAlgorithmEnabled,
                    alphaMax = request.predWeightedAlgorithmAlphaMaxX100 / 100.0,
                    kBase = null,
                    kCurrent = null,
                    kFallback = null,
                    currentNonGameEffectiveMs = 0.0,
                    kSampleFileCount = 0,
                    kTotalEnergy = 0.0,
                    kTotalSocDrop = 0.0,
                    kTotalDurationMs = 0L,
                    kCV = null,
                    kEffectiveN = 0.0,
                    insufficientReason = buildScanFailureReason(scanSummary, recordIntervalMs)
                )
            )
        }

        val totalMs = offTime + dailyTime + gameTime
        if (totalMs <= 0L) {
            LoggerX.w(TAG, "[预测] 场景统计总时长无效: off=$offTime daily=$dailyTime game=$gameTime")
            return SceneComputeResult(
                displayStats = null,
                predictionStats = null,
                homePredictionInputs = HomePredictionInputs(
                    sceneStats = null,
                    weightingEnabled = request.predWeightedAlgorithmEnabled,
                    alphaMax = request.predWeightedAlgorithmAlphaMaxX100 / 100.0,
                    kBase = null,
                    kCurrent = null,
                    kFallback = null,
                    currentNonGameEffectiveMs = currentNonGameEffectiveMs,
                    kSampleFileCount = kSampleFileCount,
                    kTotalEnergy = kTotalEnergy,
                    kTotalSocDrop = kTotalSocDrop,
                    kTotalDurationMs = kTotalDurationMs,
                    kCV = null,
                    kEffectiveN = 0.0,
                    insufficientReason = "有效放电记录未形成可统计的场景时长"
                )
            )
        }

        val rawTotalEnergy = rawSignedOffEnergy + rawSignedDailyEnergy + rawSignedGameEnergy
        val effectiveTotalEnergy = effectiveOffEnergy + effectiveDailyEnergy + effectiveGameEnergy

        val displayStats = SceneStats(
            screenOffAvgPowerRaw = if (offTime > 0) rawSignedOffEnergy / offTime.toDouble() else 0.0,
            screenOffTotalMs = offTime,
            screenOffEffectiveTotalMs = offTime.toDouble(),
            screenOnDailyAvgPowerRaw = if (dailyTime > 0) rawSignedDailyEnergy / dailyTime.toDouble() else 0.0,
            screenOnDailyTotalMs = dailyTime,
            screenOnDailyEffectiveTotalMs = dailyTime.toDouble(),
            totalEnergyRawMs = rawTotalEnergy,
            totalSocDrop = rawTotalCapDrop,
            totalDurationMs = totalMs,
            fileCount = usedFileCount,
            rawTotalSocDrop = rawTotalCapDrop
        )

        val predictionStats = SceneStats(
            screenOffAvgPowerRaw = if (effectiveOffTimeWeighted > 0) effectiveOffEnergy / effectiveOffTimeWeighted else 0.0,
            screenOffTotalMs = offTime,
            screenOffEffectiveTotalMs = effectiveOffTimeWeighted,
            screenOnDailyAvgPowerRaw = if (effectiveDailyTimeWeighted > 0) effectiveDailyEnergy / effectiveDailyTimeWeighted else 0.0,
            screenOnDailyTotalMs = dailyTime,
            screenOnDailyEffectiveTotalMs = effectiveDailyTimeWeighted,
            totalEnergyRawMs = effectiveTotalEnergy,
            totalSocDrop = effectiveTotalCapDrop,
            totalDurationMs = totalMs,
            fileCount = usedFileCount,
            rawTotalSocDrop = rawTotalCapDrop
        )

        val kBase = weightedMedian(historicalKEntries.map { it.k to it.weight })
        val kCV = weightedCV(historicalKEntries.map { it.k to it.weight })
        val kEffectiveN = effectiveSampleCount(historicalKEntries.map { it.k to it.weight })
        val kFallback = if (kTotalEnergy > 0.0 && kTotalSocDrop > 0.0) {
            kTotalSocDrop / kTotalEnergy
        } else {
            null
        }
        val insufficientReason = when {
            kSampleFileCount <= 0 -> "最近放电记录仅包含已排除的高负载场景"
            else -> null
        }
        val homePredictionInputs = HomePredictionInputs(
            sceneStats = predictionStats,
            weightingEnabled = request.predWeightedAlgorithmEnabled,
            alphaMax = (request.predWeightedAlgorithmAlphaMaxX100 / 100.0).coerceIn(0.0, 0.8),
            kBase = kBase,
            kCurrent = kCurrent,
            kFallback = kFallback,
            currentNonGameEffectiveMs = currentNonGameEffectiveMs,
            kSampleFileCount = kSampleFileCount,
            kTotalEnergy = kTotalEnergy,
            kTotalSocDrop = kTotalSocDrop,
            kTotalDurationMs = kTotalDurationMs,
            kCV = kCV,
            kEffectiveN = kEffectiveN,
            insufficientReason = insufficientReason
        )

        if (insufficientReason == null) {
            cacheFile.parentFile?.mkdirs()
            cacheFile.writeText(
                displayStats.toString() + "\n" +
                        predictionStats.toString() + "\n" +
                        homePredictionInputs.serializeWithoutScene()
            )
        }
        LoggerX.i(
            TAG,
            "[预测] 场景统计完成: usedFiles=$usedFileCount kSampleFiles=$kSampleFileCount kBase=$kBase kCurrent=$kCurrent kFallback=$kFallback kCV=$kCV kEffectiveN=$kEffectiveN"
        )

        return SceneComputeResult(
            displayStats = displayStats,
            predictionStats = predictionStats,
            homePredictionInputs = homePredictionInputs
        )
    }

    private fun collectHomePredictionContribution(
        contribution: FileNonGameContribution,
        currentDischargeFileName: String?,
        historicalKEntries: MutableList<FileKInput>,
        onCurrentEffectiveMs: (Double) -> Unit,
        onCurrentK: (Double?) -> Unit,
        onTotals: (durationMs: Long, energy: Double, socDrop: Double) -> Unit
    ) {
        val hasContribution = contribution.rawDurationMs > 0L && contribution.effectiveEnergy > 0.0
        if (!hasContribution) {
            if (contribution.fileName == currentDischargeFileName) {
                onCurrentEffectiveMs(0.0)
                onCurrentK(null)
            }
            return
        }

        onTotals(
            contribution.rawDurationMs,
            contribution.effectiveEnergy,
            contribution.effectiveCapDrop
        )

        val fileK = if (contribution.effectiveCapDrop > 0.0) {
            contribution.effectiveCapDrop / contribution.effectiveEnergy
        } else {
            null
        }
        if (contribution.fileName == currentDischargeFileName) {
            onCurrentEffectiveMs(contribution.effectiveDurationMs)
            onCurrentK(fileK)
            return
        }
        if (fileK != null && contribution.effectiveCapDrop >= 3.0) {
            historicalKEntries += FileKInput(
                k = fileK,
                weight = contribution.effectiveCapDrop
            )
        }
    }

    private fun buildScanFailureReason(
        summary: DischargeScanSummary?,
        recordIntervalMs: Long
    ): String {
        if (summary == null || summary.selectedFileCount <= 0) {
            return "最近没有放电记录"
        }

        val selected = summary.selectedFileCount
        if (summary.rejectedAbnormalDrainRateCount == selected) {
            return "最近${selected}个放电文件均因掉电速率超过 50%/h 被异常校验过滤"
        }
        if (summary.rejectedNoValidDurationCount == selected) {
            val maxGapMs = DischargeRecordScanner.computeMaxGapMs(recordIntervalMs)
            return "最近${selected}个放电文件均无有效采样区间，请检查记录间隔设置是否与历史数据匹配（当前最大间隔 ${maxGapMs}ms）"
        }
        if (summary.rejectedNoSocDropCount == selected) {
            return "最近${selected}个放电文件均未形成有效掉电"
        }
        if (summary.rejectedNoEnergyCount == selected) {
            return "最近${selected}个放电文件均未形成有效功耗数据"
        }

        val rejected = selected - summary.acceptedFileCount
        return "最近${selected}个放电文件中仅 ${summary.acceptedFileCount} 个通过校验，${rejected} 个被过滤"
    }

    private fun buildCacheKey(
        files: List<File>,
        request: StatisticsSettings,
        recordIntervalMs: Long,
        currentDischargeFileName: String?,
    ): String {
        val filesHash = files.joinToString(",") { "${it.name}:${it.lastModified()}:${it.length()}" }
            .hashCode()
        val gamesHash = request.gamePackages.sorted().joinToString(",").hashCode()
        val currentNameHash = (currentDischargeFileName ?: "").hashCode()
        return listOf(
            HISTORY_STATS_CACHE_VERSION,
            filesHash,
            gamesHash,
            request.sceneStatsRecentFileCount,
            DischargeRecordScanner.computeMaxGapMs(recordIntervalMs),
            request.predWeightedAlgorithmEnabled.hashCode(),
            request.predWeightedAlgorithmAlphaMaxX100,
            currentNameHash
        ).joinToString("_")
    }

    /** 加权变异系数 CV = σ_weighted / μ_weighted。 */
    private fun weightedCV(entries: List<Pair<Double, Double>>): Double? {
        if (entries.size < 2) return null
        val sumW = entries.sumOf { it.second }
        if (sumW <= 0) return null
        val kMean = entries.sumOf { it.first * it.second } / sumW
        if (kMean <= 0 || !kMean.isFinite()) return null
        val variance =
            entries.sumOf { it.second * (it.first - kMean) * (it.first - kMean) } / sumW
        if (!variance.isFinite()) return null
        return sqrt(variance) / kMean
    }

    /** 加权有效样本量 n_eff = (Σw)^2 / Σ(w^2)。 */
    private fun effectiveSampleCount(entries: List<Pair<Double, Double>>): Double {
        if (entries.isEmpty()) return 0.0
        val sumW = entries.sumOf { it.second }
        val sumW2 = entries.sumOf { it.second * it.second }
        if (sumW2 <= 0) return 0.0
        return sumW * sumW / sumW2
    }

    /** 加权中位数：按 k 升序累积权重到 50% 时线性插值。 */
    private fun weightedMedian(entries: List<Pair<Double, Double>>): Double? {
        if (entries.size < 2) return null
        val sorted = entries.sortedBy { it.first }
        val totalWeight = sorted.sumOf { it.second }
        if (totalWeight <= 0) return null
        val halfWeight = totalWeight * 0.5
        var cumulative = 0.0
        for (i in sorted.indices) {
            val prev = cumulative
            cumulative += sorted[i].second
            if (cumulative >= halfWeight) {
                if (i == 0 || prev >= halfWeight) return sorted[i].first
                val fraction = (halfWeight - prev) / sorted[i].second
                return sorted[i - 1].first +
                        (sorted[i].first - sorted[i - 1].first) * fraction
            }
        }
        return sorted.last().first
    }
}
