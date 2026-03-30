package yangfentuozi.batteryrecorder.data.history

import android.content.Context
import yangfentuozi.batteryrecorder.shared.config.dataclass.StatisticsSettings
import java.io.File

/**
 * 单个应用的前台耗电统计结果。
 *
 * 保留原始功率均值与有效掉电/时长，便于展示与预测分别使用各自口径。
 */
data class AppStatsEntry(
    val packageName: String,
    val rawAvgPowerRaw: Double,
    val totalForegroundMs: Long,
    val effectiveForegroundMs: Double,
    val rawSocDrop: Double,
    val effectiveSocDrop: Double
) {
    override fun toString(): String =
        "$packageName,$rawAvgPowerRaw,$totalForegroundMs,$effectiveForegroundMs,$rawSocDrop,$effectiveSocDrop"

    companion object {
        /**
         * 兼容当前缓存格式。
         *
         * 本类字段删减后通过共享缓存版本失效旧缓存，不再兼容已删除字段。
         */
        fun fromString(value: String): AppStatsEntry? {
            val parts = value.split(",")
            if (parts.size < 6) return null
            return AppStatsEntry(
                packageName = parts[0],
                rawAvgPowerRaw = parts[1].toDoubleOrNull() ?: return null,
                totalForegroundMs = parts[2].toLongOrNull() ?: return null,
                effectiveForegroundMs = parts[3].toDoubleOrNull() ?: return null,
                rawSocDrop = parts[4].toDoubleOrNull() ?: return null,
                effectiveSocDrop = parts[5].toDoubleOrNull() ?: return null
            )
        }
    }
}

/**
 * 应用维度统计结果。
 *
 * 目前仅暴露 entries，但保留结果对象以承载计算语义与后续扩展空间。
 */
data class AppStatsComputeResult(
    val entries: List<AppStatsEntry>
)

object AppStatsComputer {

    private data class MutableAppStats(
        var rawSignedEnergyRawMs: Double = 0.0,
        var totalForegroundMs: Long = 0L,
        var effectiveForegroundMs: Double = 0.0,
        var rawSocDrop: Double = 0.0,
        var effectiveSocDrop: Double = 0.0
    )

    /**
     * 基于放电文件聚合应用前台统计。
     *
     * 展示功率使用原始时长口径，预测时长使用 effective 时长/掉电口径，
     * 这样开启当次加权后不会污染“应用平均功率”的展示含义。
     */
    fun compute(
        context: Context,
        request: StatisticsSettings,
        recordIntervalMs: Long,
        currentDischargeFileName: String? = null
    ): AppStatsComputeResult {
        val files = DischargeRecordScanner.listRecentDischargeFiles(
            context = context,
            recentFileCount = request.sceneStatsRecentFileCount
        )
        if (files.isEmpty()) {
            return AppStatsComputeResult(emptyList())
        }

        val recentFileCount = request.sceneStatsRecentFileCount
        val maxGapMs = DischargeRecordScanner.computeMaxGapMs(recordIntervalMs)
        val cacheKey = buildCacheKey(
            files = files,
            recentFileCount = recentFileCount,
            maxGapMs = maxGapMs,
            request = request,
            currentDischargeFileName = currentDischargeFileName
        )
        val cacheFile = getAppStatsCacheFile(context.cacheDir, cacheKey)
        if (cacheFile.exists()) {
            val cacheText = cacheFile.readText().trim()
            if (cacheText.isEmpty()) {
                return AppStatsComputeResult(emptyList())
            }
            val cacheLines = cacheText.lines()
            val cachedEntries = cacheLines.map { line ->
                AppStatsEntry.fromString(line.trim())
            }
            if (cachedEntries.all { it != null }) {
                return AppStatsComputeResult(
                    sortEntriesForDisplay(cachedEntries.filterNotNull())
                )
            }
            cacheFile.delete()
        }

        val statsMap = linkedMapOf<String, MutableAppStats>()
        DischargeRecordScanner.scan(
            context = context,
            request = request,
            recordIntervalMs = recordIntervalMs,
            currentDischargeFileName = currentDischargeFileName
        ) { acceptedFile ->
            // 应用维度展示仍保留有符号能量，避免丢失放电方向；预测耗时另走 effective 时长/掉电口径。
            acceptedFile.intervals.forEach { interval ->
                if (!interval.isDisplayOn) return@forEach
                val packageName = interval.packageName?.takeIf { it.isNotBlank() } ?: return@forEach
                val stats = statsMap.getOrPut(packageName) { MutableAppStats() }
                stats.rawSignedEnergyRawMs += interval.signedEnergyRawMs
                stats.totalForegroundMs += interval.durationMs
                stats.effectiveForegroundMs += interval.effectiveDurationMs
                stats.rawSocDrop += interval.capDrop
                stats.effectiveSocDrop += interval.effectiveCapDrop
            }
        }

        val entries = statsMap.mapNotNull { (packageName, stats) ->
            if (stats.totalForegroundMs <= 0L) return@mapNotNull null
            AppStatsEntry(
                packageName = packageName,
                rawAvgPowerRaw = stats.rawSignedEnergyRawMs / stats.totalForegroundMs.toDouble(),
                totalForegroundMs = stats.totalForegroundMs,
                effectiveForegroundMs = stats.effectiveForegroundMs,
                rawSocDrop = stats.rawSocDrop,
                effectiveSocDrop = stats.effectiveSocDrop
            )
        }

        cacheFile.parentFile?.mkdirs()
        cacheFile.writeText(
            entries.sortedBy { it.packageName }
                .joinToString(separator = "\n") { it.toString() }
        )

        return AppStatsComputeResult(sortEntriesForDisplay(entries))
    }

    private fun sortEntriesForDisplay(entries: List<AppStatsEntry>): List<AppStatsEntry> {
        // 优先按有效前台时长排序，让预测详情页先展示对续航影响更大的应用。
        return entries.sortedWith(
            compareByDescending<AppStatsEntry> { it.totalForegroundMs }
                .thenByDescending { it.rawAvgPowerRaw }
                .thenBy { it.packageName }
        )
    }

    private fun buildCacheKey(
        files: List<File>,
        recentFileCount: Int,
        maxGapMs: Long,
        request: StatisticsSettings,
        currentDischargeFileName: String?
    ): String {
        val filesHash = files.joinToString(",") { "${it.name}:${it.lastModified()}:${it.length()}" }.hashCode()
        return listOf(
            HISTORY_STATS_CACHE_VERSION,
            filesHash,
            recentFileCount,
            maxGapMs,
            request.predCurrentSessionWeightEnabled.hashCode(),
            request.predCurrentSessionWeightMaxX100,
            request.predCurrentSessionWeightHalfLifeMin,
            currentDischargeFileName?.hashCode() ?: 0
        ).joinToString("_").hashCode().toString()
    }
}
