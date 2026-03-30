package yangfentuozi.batteryrecorder.data.history

import android.content.Context
import yangfentuozi.batteryrecorder.shared.config.dataclass.StatisticsSettings
import yangfentuozi.batteryrecorder.shared.util.LoggerX
import java.io.File
import kotlin.math.sqrt

private const val TAG = "SceneStatsComputer"

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
        // 历史缓存通过版本号统一失效，这里只解析当前 11 字段格式。
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
 * displayStats 面向 UI 展示，predictionStats 面向预测算法。
 */
data class SceneComputeResult(
    val displayStats: SceneStats?,
    val predictionStats: SceneStats?,
    val medianK: Double?,
    val kCV: Double?,
    val kEffectiveN: Double,
    val insufficientReason: String? = null
)

object SceneStatsComputer {

    /**
     * 聚合最近放电文件的场景统计，并生成展示/预测双口径结果。
     *
     * recordIntervalMs 虽然会影响统计时对“采样断档/有效区间”的判断，
     * 但它的归属仍然是服务端采样配置，不属于 StatisticsSettings。
     * 这里单独传入它，是为了让统计链显式依赖 server 输入，而不是把 server 配置混进统计设置模型。
     */
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
                medianK = null,
                kCV = null,
                kEffectiveN = 0.0,
                insufficientReason = "最近没有放电记录"
            )
        }

        val cacheKey = buildCacheKey(
            files = files,
            request = request,
            recordIntervalMs = recordIntervalMs,
            currentDischargeFileName = currentDischargeFileName
        )
        val cacheFile = getSceneStatsCacheFile(context.cacheDir, cacheKey)
        LoggerX.d(TAG, 
            "[预测] 计算场景统计: fileCount=${files.size} cache=${cacheFile.name} current=$currentDischargeFileName"
        )
        if (cacheFile.exists()) {
            val cacheLines = cacheFile.readText().trim().lines()
            val displayStats = cacheLines.getOrNull(0)?.let { SceneStats.fromString(it) }
            val predictionStats = cacheLines.getOrNull(1)?.let { SceneStats.fromString(it) }
            if (displayStats != null && predictionStats != null) {
                val cachedMedianK = cacheLines.getOrNull(2)?.toDoubleOrNull()
                val cachedKCV = cacheLines.getOrNull(3)?.toDoubleOrNull()
                val cachedKEffN = cacheLines.getOrNull(4)?.toDoubleOrNull() ?: 0.0
                LoggerX.d(TAG, "[预测] 命中场景统计缓存: ${cacheFile.name}")
                return SceneComputeResult(displayStats, predictionStats, cachedMedianK, cachedKCV, cachedKEffN)
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

        data class FileKInput(val capDrop: Double, val energy: Double)
        val fileKInputs = mutableListOf<FileKInput>()
        val gamePackages = request.gamePackages

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

            var fileEffectiveOffEnergy = 0.0
            var fileEffectiveOffTime = 0.0
            var fileEffectiveDailyEnergy = 0.0
            var fileEffectiveDailyTime = 0.0
            var fileEffectiveGameEnergy = 0.0
            var fileEffectiveGameTime = 0.0

            acceptedFile.intervals.forEach { interval ->
                when {
                    !interval.isDisplayOn -> {
                        fileRawSignedOffEnergy += interval.signedEnergyRawMs
                        fileOffTime += interval.durationMs
                        fileEffectiveOffEnergy += interval.effectiveEnergyMagnitudeRawMs
                        fileEffectiveOffTime += interval.effectiveDurationMs
                    }
                    interval.packageName == null || interval.packageName !in gamePackages -> {
                        fileRawSignedDailyEnergy += interval.signedEnergyRawMs
                        fileDailyTime += interval.durationMs
                        fileEffectiveDailyEnergy += interval.effectiveEnergyMagnitudeRawMs
                        fileEffectiveDailyTime += interval.effectiveDurationMs
                    }
                    else -> {
                        fileRawSignedGameEnergy += interval.signedEnergyRawMs
                        fileGameTime += interval.durationMs
                        fileEffectiveGameEnergy += interval.effectiveEnergyMagnitudeRawMs
                        fileEffectiveGameTime += interval.effectiveDurationMs
                    }
                }
            }

            usedFileCount += 1
            fileKInputs += FileKInput(
                capDrop = acceptedFile.effectiveTotalCapDrop,
                energy = acceptedFile.effectiveTotalEnergyMagnitudeRawMs
            )
            rawSignedOffEnergy += fileRawSignedOffEnergy
            offTime += fileOffTime
            rawSignedDailyEnergy += fileRawSignedDailyEnergy
            dailyTime += fileDailyTime
            rawSignedGameEnergy += fileRawSignedGameEnergy
            gameTime += fileGameTime
            rawTotalCapDrop += acceptedFile.rawTotalCapDrop
            effectiveOffEnergy += fileEffectiveOffEnergy
            effectiveOffTimeWeighted += fileEffectiveOffTime
            effectiveDailyEnergy += fileEffectiveDailyEnergy
            effectiveDailyTimeWeighted += fileEffectiveDailyTime
            effectiveGameEnergy += fileEffectiveGameEnergy
            effectiveGameTimeWeighted += fileEffectiveGameTime
            effectiveTotalCapDrop += acceptedFile.effectiveTotalCapDrop
        }

        if (usedFileCount <= 0) {
            LoggerX.w(TAG, "[预测] 场景统计无有效文件，准备返回不足原因")
            return SceneComputeResult(
                displayStats = null,
                predictionStats = null,
                medianK = null,
                kCV = null,
                kEffectiveN = 0.0,
                insufficientReason = buildScanFailureReason(scanSummary, recordIntervalMs)
            )
        }

        // 中位数 k 只使用掉电量足够的文件，降低短样本噪声。
        val minCapDropForMedian = 3.0
        val kEntries = fileKInputs.mapNotNull { input ->
            if (input.energy > 0 && input.capDrop >= minCapDropForMedian) {
                input.capDrop / input.energy to input.capDrop
            } else {
                null
            }
        }
        val medianK = weightedMedian(kEntries)
        val kCV = weightedCV(kEntries)
        val kEffectiveN = effectiveSampleCount(kEntries)

        val totalMs = offTime + dailyTime + gameTime
        if (totalMs <= 0L) {
            LoggerX.w(TAG, "[预测] 场景统计总时长无效: off=$offTime daily=$dailyTime game=$gameTime")
            return SceneComputeResult(
                displayStats = null,
                predictionStats = null,
                medianK = null,
                kCV = null,
                kEffectiveN = 0.0,
                insufficientReason = "有效放电记录未形成可统计的场景时长"
            )
        }

        val rawTotalEnergy = rawSignedOffEnergy + rawSignedDailyEnergy + rawSignedGameEnergy
        val effectiveTotalEnergy = effectiveOffEnergy + effectiveDailyEnergy + effectiveGameEnergy

        // 展示口径保持原始观测值，不被当次加权策略影响。
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

        // 预测口径使用 effective 能量/时长/掉电，反映当次记录加权后的趋势。
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

        cacheFile.parentFile?.mkdirs()
        cacheFile.writeText(
            displayStats.toString() + "\n" + predictionStats.toString() + "\n" +
                    (medianK ?: "") + "\n" + (kCV ?: "") + "\n" + kEffectiveN
        )
        LoggerX.i(TAG, 
            "[预测] 场景统计完成: usedFiles=$usedFileCount totalMs=$totalMs medianK=$medianK kCV=$kCV kEffectiveN=$kEffectiveN"
        )

        return SceneComputeResult(
            displayStats = displayStats,
            predictionStats = predictionStats,
            medianK = medianK,
            kCV = kCV,
            kEffectiveN = kEffectiveN,
            insufficientReason = null
        )
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
        val gamePackages = request.gamePackages
        val recentFileCount = request.sceneStatsRecentFileCount
        val maxGapMs = DischargeRecordScanner.computeMaxGapMs(recordIntervalMs)
        val predCurrentSessionWeightEnabled = request.predCurrentSessionWeightEnabled
        val predCurrentSessionWeightMaxX100 = request.predCurrentSessionWeightMaxX100
        val predCurrentSessionWeightHalfLifeMin = request.predCurrentSessionWeightHalfLifeMin
        // 带上文件长度，避免仅依赖 lastModified 命中陈旧缓存。
        val filesHash = files.joinToString(",") { "${it.name}:${it.lastModified()}:${it.length()}" }.hashCode()
        val gamesHash = gamePackages.sorted().joinToString(",").hashCode()
        val currentNameHash = (currentDischargeFileName ?: "").hashCode()
        return "${HISTORY_STATS_CACHE_VERSION}_${filesHash}_${gamesHash}_${recentFileCount}_${maxGapMs}_" +
                "${predCurrentSessionWeightEnabled}_${predCurrentSessionWeightMaxX100}_${predCurrentSessionWeightHalfLifeMin}_${currentNameHash}"
    }

    /** 加权变异系数 CV = σ_weighted / μ_weighted。 */
    private fun weightedCV(entries: List<Pair<Double, Double>>): Double? {
        if (entries.size < 2) return null
        val sumW = entries.sumOf { it.second }
        if (sumW <= 0) return null
        val kMean = entries.sumOf { it.first * it.second } / sumW
        if (kMean <= 0 || !kMean.isFinite()) return null
        val variance = entries.sumOf { it.second * (it.first - kMean) * (it.first - kMean) } / sumW
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
                return sorted[i - 1].first + (sorted[i].first - sorted[i - 1].first) * fraction
            }
        }
        return sorted.last().first
    }
}
