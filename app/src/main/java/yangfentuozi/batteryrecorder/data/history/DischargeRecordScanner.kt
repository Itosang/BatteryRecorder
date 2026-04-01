package yangfentuozi.batteryrecorder.data.history

import android.content.Context
import yangfentuozi.batteryrecorder.BuildConfig
import yangfentuozi.batteryrecorder.shared.Constants
import yangfentuozi.batteryrecorder.shared.config.SettingsConstants
import yangfentuozi.batteryrecorder.shared.config.dataclass.StatisticsSettings
import yangfentuozi.batteryrecorder.shared.data.BatteryStatus
import yangfentuozi.batteryrecorder.shared.util.LoggerX
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min

private const val TAG = "DischargeRecordScanner"

/**
 * 采样点之间的有效区间。
 *
 * signed 能量保留原始正负号，供展示层还原平均功率方向；
 * magnitude 能量统一取绝对值，供预测与异常校验使用。
 */
data class DischargeInterval(
    val packageName: String?,
    val isDisplayOn: Boolean,
    val durationMs: Long,
    val timeDecayWeight: Double,
    val effectiveDurationMs: Double,
    val signedEnergyRawMs: Double,
    val effectiveEnergyMagnitudeRawMs: Double,
    val capDrop: Double,
    val effectiveCapDrop: Double
)

/**
 * 单个放电文件扫描通过后的聚合结果。
 */
data class AcceptedDischargeFile(
    val file: File,
    val intervals: List<DischargeInterval>,
    val rawTotalEnergyMagnitudeRawMs: Double,
    val effectiveTotalEnergyMagnitudeRawMs: Double,
    val rawTotalDurationMs: Long,
    val effectiveTotalDurationMs: Double,
    val rawTotalCapDrop: Double,
    val effectiveTotalCapDrop: Double
)

/**
 * 扫描摘要目前主要用于调试和未来扩展。
 */
data class DischargeScanSummary(
    val selectedFileCount: Int,
    val acceptedFileCount: Int,
    val rejectedNoValidDurationCount: Int,
    val rejectedNoEnergyCount: Int,
    val rejectedNoSocDropCount: Int,
    val rejectedAbnormalDrainRateCount: Int
)

object DischargeRecordScanner {

    private const val MAX_GAP_FACTOR = 5
    private const val MAX_DRAIN_RATE_PER_HOUR = 50.0
    private const val MIN_CURRENT_SESSION_MS = 10 * 60 * 1000L
    private const val MIN_CURRENT_SESSION_SOC_DROP = 2.0
    private const val DEFAULT_CURRENT_SESSION_WEIGHT_MAX_MULTIPLIER = 3.0
    private const val DEFAULT_CURRENT_SESSION_WEIGHT_HALF_LIFE_MIN = 30L

    private enum class RejectedReason {
        NoValidDuration,
        NoEnergy,
        NoSocDrop,
        AbnormalDrainRate
    }

    private data class ScanFileResult(
        val acceptedFile: AcceptedDischargeFile? = null,
        val rejectedReason: RejectedReason? = null
    )

    /**
     * 先暂存权重，待整文件遍历完成后再决定是否采用有效口径。
     *
     * 是否启用当次加权依赖整文件时长与掉电门槛，因此不能在首轮解析时直接落最终值。
     */
    private data class PendingInterval(
        val packageName: String?,
        val isDisplayOn: Boolean,
        val durationMs: Long,
        val signedEnergyRawMs: Double,
        val capDrop: Double,
        val weight: Double
    )

    internal fun computeMaxGapMs(recordIntervalMs: Long): Long =
        recordIntervalMs * MAX_GAP_FACTOR

    /**
     * 统一裁剪最近放电文件选择范围，保证不同统计器使用相同样本集。
     */
    internal fun listRecentDischargeFiles(
        context: Context,
        recentFileCount: Int
    ): List<File> {
        val effectiveRecentFileCount = SettingsConstants.sceneStatsRecentFileCount.coerce(recentFileCount)
        val dataDir = File(
            File(context.dataDir, Constants.APP_POWER_DATA_PATH),
            BatteryStatus.Discharging.dataDirName
        )
        if (!dataDir.isDirectory) return emptyList()
        val files = dataDir.listFiles()
            ?.filter { it.isFile }
            ?.sortedByDescending { it.lastModified() }
            ?.take(effectiveRecentFileCount)
            ?: emptyList()
        LoggerX.d(TAG, 
            "[预测] 选择最近放电文件: requested=$recentFileCount effective=$effectiveRecentFileCount selected=${files.size}"
        )
        return files
    }

    /**
     * 扫描最近的放电文件，并仅对通过异常值校验的文件回调结果。
     *
     * recordIntervalMs 用来推导允许的最大采样间隔，从而判断历史文件里的点间隔是否异常。
     * 它来自 ServerSettings，而不是 StatisticsSettings；这里显式传入是为了保持统计配置边界干净。
     */
    fun scan(
        context: Context,
        request: StatisticsSettings,
        recordIntervalMs: Long,
        currentDischargeFileName: String? = null,
        onAcceptedFile: (AcceptedDischargeFile) -> Unit
    ): DischargeScanSummary? {
        val files = listRecentDischargeFiles(context, request.sceneStatsRecentFileCount)
        if (files.isEmpty()) {
            LoggerX.d(TAG, "[预测] 无放电文件可扫描")
            return null
        }

        val maxGapMs = computeMaxGapMs(recordIntervalMs)
        val maxMultiplier = DEFAULT_CURRENT_SESSION_WEIGHT_MAX_MULTIPLIER
        val halfLifeMs = DEFAULT_CURRENT_SESSION_WEIGHT_HALF_LIFE_MIN * 60_000.0
        val enableTimeDecayWeight =
            request.predWeightedAlgorithmEnabled &&
            currentDischargeFileName != null
        LoggerX.d(TAG, 
            "[预测] 开始扫描放电文件: selected=${files.size} maxGapMs=$maxGapMs enableWeight=$enableTimeDecayWeight current=$currentDischargeFileName"
        )

        var acceptedFileCount = 0
        var rejectedNoValidDurationCount = 0
        var rejectedNoEnergyCount = 0
        var rejectedNoSocDropCount = 0
        var rejectedAbnormalDrainRateCount = 0
        files.forEach { file ->
            val result = scanFile(
                file = file,
                maxGapMs = maxGapMs,
                currentDischargeFileName = currentDischargeFileName,
                enableTimeDecayWeight = enableTimeDecayWeight,
                maxMultiplier = maxMultiplier,
                halfLifeMs = halfLifeMs
            )
            val acceptedFile = result.acceptedFile
            if (acceptedFile == null) {
                when (result.rejectedReason) {
                    RejectedReason.NoValidDuration -> rejectedNoValidDurationCount += 1
                    RejectedReason.NoEnergy -> rejectedNoEnergyCount += 1
                    RejectedReason.NoSocDrop -> rejectedNoSocDropCount += 1
                    RejectedReason.AbnormalDrainRate -> rejectedAbnormalDrainRateCount += 1
                    null -> {}
                }
                LoggerX.d(TAG, 
                    "[预测] 放电文件过滤: file=${file.name} reason=${result.rejectedReason}"
                )
                return@forEach
            }
            acceptedFileCount += 1
            LoggerX.v(TAG, 
                "[预测] 放电文件通过: file=${file.name} intervals=${acceptedFile.intervals.size} rawDurationMs=${acceptedFile.rawTotalDurationMs} rawSocDrop=${acceptedFile.rawTotalCapDrop}"
            )
            onAcceptedFile(acceptedFile)
        }

        val summary = DischargeScanSummary(
            selectedFileCount = files.size,
            acceptedFileCount = acceptedFileCount,
            rejectedNoValidDurationCount = rejectedNoValidDurationCount,
            rejectedNoEnergyCount = rejectedNoEnergyCount,
            rejectedNoSocDropCount = rejectedNoSocDropCount,
            rejectedAbnormalDrainRateCount = rejectedAbnormalDrainRateCount
        )
        LoggerX.i(TAG, 
            "[预测] 放电扫描完成: selected=${summary.selectedFileCount} accepted=${summary.acceptedFileCount} noDuration=${summary.rejectedNoValidDurationCount} noEnergy=${summary.rejectedNoEnergyCount} noSoc=${summary.rejectedNoSocDropCount} abnormal=${summary.rejectedAbnormalDrainRateCount}"
        )
        return summary
    }

    /**
     * 扫描单个放电文件，并在通过文件级异常校验后生成区间列表。
     *
     * 实现分成两阶段：
     * 1. 首轮遍历只解析原始区间并提前算好时间衰减权重，不立即决定 effective 口径。
     * 2. 整文件完成后，再根据当前文件门槛决定是否把时间衰减真正应用到
     *    `effectiveDurationMs / effectiveEnergyMagnitudeRawMs / effectiveCapDrop`。
     *
     * 这样可以保证“是否启用当前文件时间衰减”始终由整文件 raw 时长和 raw 掉电统一决定。
     *
     * @param file 待扫描的放电记录文件。
     * @param maxGapMs 允许的最大采样间隔；超过该值的点间隔直接丢弃。
     * @param currentDischargeFileName 当前活动放电文件名。
     * @param enableTimeDecayWeight 是否允许当前活动文件启用时间衰减。
     * @param maxMultiplier 当前文件时间衰减的最大倍率。
     * @param halfLifeMs 当前文件时间衰减的半衰期，单位毫秒。
     * @return 单文件扫描结果；失败时只返回拒绝原因，不产出伪造结果。
     */
    private fun scanFile(
        file: File,
        maxGapMs: Long,
        currentDischargeFileName: String?,
        enableTimeDecayWeight: Boolean,
        maxMultiplier: Double,
        halfLifeMs: Double
    ): ScanFileResult {
        val isCurrentFile = enableTimeDecayWeight && file.name == currentDischargeFileName
        val fileEndTs = if (isCurrentFile) findFileEndTimestamp(file) else null

        val intervals = ArrayList<PendingInterval>()
        var rawTotalEnergyMagnitudeRawMs = 0.0
        var rawTotalDurationMs = 0L
        var rawTotalCapDrop = 0.0

        var prevTs: Long? = null
        var prevPower: Long? = null
        var prevDisplay: Int? = null
        var prevPkg: String? = null
        var prevCap: Int? = null

        file.bufferedReader().useLines { lines ->
            lines.forEach { raw ->
                val line = raw.trim()
                if (line.isEmpty()) return@forEach

                val parts = line.split(",")
                if (parts.size < 6) return@forEach

                val ts = parts[0].toLongOrNull() ?: return@forEach
                val power = parts[1].toLongOrNull() ?: return@forEach
                val pkg = parts[2]
                val capacity = parts[3].toIntOrNull() ?: return@forEach
                val displayOn = parts[4].toIntOrNull() ?: return@forEach

                val pTs = prevTs
                val pPower = prevPower
                val pDisplay = prevDisplay
                val pPkg = prevPkg
                val pCap = prevCap

                prevTs = ts
                prevPower = power
                prevDisplay = displayOn
                prevPkg = pkg
                prevCap = capacity

                if (pTs == null || pPower == null || pDisplay == null || pCap == null) return@forEach

                val dt = ts - pTs
                if (dt <= 0 || dt > maxGapMs) return@forEach

                val signedEnergyRawMs = (pPower.toDouble() + power.toDouble()) * 0.5 * dt.toDouble()
                val energyMagnitudeRawMs = abs(signedEnergyRawMs)
                val weight = if (isCurrentFile && fileEndTs != null) {
                    val midTs = pTs + dt / 2
                    computeTimeDecayWeight(
                        endTs = fileEndTs,
                        midTs = midTs,
                        maxMultiplier = maxMultiplier,
                        halfLifeMs = halfLifeMs
                    )
                } else {
                    1.0
                }
                val capDrop = (pCap - capacity).coerceAtLeast(0).toDouble()
                rawTotalEnergyMagnitudeRawMs += energyMagnitudeRawMs
                rawTotalDurationMs += dt
                rawTotalCapDrop += capDrop
                intervals += PendingInterval(
                    packageName = pPkg,
                    isDisplayOn = pDisplay == 1,
                    durationMs = dt,
                    signedEnergyRawMs = signedEnergyRawMs,
                    capDrop = capDrop,
                    weight = weight
                )
            }
        }

        if (rawTotalDurationMs <= 0L) {
            return ScanFileResult(rejectedReason = RejectedReason.NoValidDuration)
        }
        if (rawTotalEnergyMagnitudeRawMs <= 0.0) {
            return ScanFileResult(rejectedReason = RejectedReason.NoEnergy)
        }
        if (rawTotalCapDrop <= 0.0) {
            return ScanFileResult(rejectedReason = RejectedReason.NoSocDrop)
        }

        val drainPerHour = rawTotalCapDrop / rawTotalDurationMs.toDouble() * 3_600_000.0
        if (drainPerHour > MAX_DRAIN_RATE_PER_HOUR) {
            return ScanFileResult(rejectedReason = RejectedReason.AbnormalDrainRate)
        }

        val useWeightedCurrentSession = isCurrentFile &&
                fileEndTs != null &&
                (BuildConfig.DEBUG || rawTotalDurationMs >= MIN_CURRENT_SESSION_MS) &&
                (BuildConfig.DEBUG || rawTotalCapDrop >= MIN_CURRENT_SESSION_SOC_DROP)
        if (useWeightedCurrentSession) {
            LoggerX.d(TAG, 
                "[预测] 启用加权算法: file=${file.name} endTs=$fileEndTs rawDurationMs=$rawTotalDurationMs rawSocDrop=$rawTotalCapDrop"
            )
        }

        // 第二阶段根据是否启用当次加权生成最终区间，避免重复解析原文件。
        var effectiveTotalEnergyMagnitudeRawMs = 0.0
        var effectiveTotalDurationMs = 0.0
        var effectiveTotalCapDrop = 0.0
        val finalizedIntervals = intervals.map { interval ->
            val effectiveDurationMs = if (useWeightedCurrentSession) {
                interval.durationMs.toDouble() * interval.weight
            } else {
                interval.durationMs.toDouble()
            }
            val effectiveEnergyMagnitudeRawMs = if (useWeightedCurrentSession) {
                abs(interval.signedEnergyRawMs) * interval.weight
            } else {
                abs(interval.signedEnergyRawMs)
            }
            val effectiveCapDrop = if (useWeightedCurrentSession) {
                interval.capDrop * interval.weight
            } else {
                interval.capDrop
            }
            effectiveTotalDurationMs += effectiveDurationMs
            effectiveTotalEnergyMagnitudeRawMs += effectiveEnergyMagnitudeRawMs
            effectiveTotalCapDrop += effectiveCapDrop
            DischargeInterval(
                packageName = interval.packageName,
                isDisplayOn = interval.isDisplayOn,
                durationMs = interval.durationMs,
                timeDecayWeight = interval.weight,
                effectiveDurationMs = effectiveDurationMs,
                signedEnergyRawMs = interval.signedEnergyRawMs,
                effectiveEnergyMagnitudeRawMs = effectiveEnergyMagnitudeRawMs,
                capDrop = interval.capDrop,
                effectiveCapDrop = effectiveCapDrop
            )
        }

        return ScanFileResult(
            acceptedFile = AcceptedDischargeFile(
                file = file,
                intervals = finalizedIntervals,
                rawTotalEnergyMagnitudeRawMs = rawTotalEnergyMagnitudeRawMs,
                effectiveTotalEnergyMagnitudeRawMs = effectiveTotalEnergyMagnitudeRawMs,
                rawTotalDurationMs = rawTotalDurationMs,
                effectiveTotalDurationMs = effectiveTotalDurationMs,
                rawTotalCapDrop = rawTotalCapDrop,
                effectiveTotalCapDrop = effectiveTotalCapDrop
            )
        )
    }

    /**
     * 从文件尾部反向读取最后一条有效记录时间戳，避免整文件扫描两遍。
     */
    private fun findFileEndTimestamp(file: File): Long? {
        val length = file.length()
        if (length <= 0L) return null

        val maxRead = 128 * 1024
        var readSize = 4 * 1024
        while (readSize <= maxRead) {
            val bytes = readTailBytes(file, min(readSize.toLong(), length).toInt())
            val ts = parseLastTimestampFromTail(bytes)
            if (ts != null) return ts
            readSize *= 2
        }

        return null
    }

    /**
     * 半衰期权重：越靠近文件结尾的区间权重越大。
     */
    private fun computeTimeDecayWeight(
        endTs: Long,
        midTs: Long,
        maxMultiplier: Double,
        halfLifeMs: Double
    ): Double {
        if (maxMultiplier <= 1.0 || halfLifeMs <= 0.0) return 1.0
        val ageMs = (endTs - midTs).toDouble().coerceAtLeast(0.0)
        val decay = exp(-ln(2.0) * ageMs / halfLifeMs)
        return 1.0 + (maxMultiplier - 1.0) * decay
    }

    /**
     * 仅读取文件尾部字节，供 findFileEndTimestamp 解析最后一行使用。
     */
    private fun readTailBytes(file: File, size: Int): ByteArray {
        RandomAccessFile(file, "r").use { raf ->
            val length = raf.length()
            val readSize = size.coerceAtLeast(1).toLong()
            val start = (length - readSize).coerceAtLeast(0L)
            raf.seek(start)
            val bytes = ByteArray((length - start).toInt())
            raf.readFully(bytes)
            return bytes
        }
    }

    /**
     * 从尾部文本中反向寻找最后一条可解析 timestamp 的记录。
     */
    private fun parseLastTimestampFromTail(bytes: ByteArray): Long? {
        if (bytes.isEmpty()) return null
        val text = String(bytes, Charsets.UTF_8)
        var end = text.length
        while (end > 0) {
            while (end > 0 && (text[end - 1] == '\n' || text[end - 1] == '\r')) end--
            if (end <= 0) return null
            var start = text.lastIndexOf('\n', end - 1)
            if (start < 0) start = 0 else start += 1
            val line = text.substring(start, end).trim()
            val comma = line.indexOf(',')
            if (comma > 0) {
                val ts = line.substring(0, comma).trim().toLongOrNull()
                if (ts != null) return ts
            }
            end = start - 1
        }
        return null
    }
}
