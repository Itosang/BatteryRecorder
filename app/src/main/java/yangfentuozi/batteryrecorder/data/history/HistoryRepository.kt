package yangfentuozi.batteryrecorder.data.history

import android.content.Context
import android.net.Uri
import yangfentuozi.batteryrecorder.data.model.ChartPoint
import yangfentuozi.batteryrecorder.ipc.Service
import yangfentuozi.batteryrecorder.shared.Constants
import yangfentuozi.batteryrecorder.shared.data.BatteryStatus
import yangfentuozi.batteryrecorder.shared.data.LineRecord
import yangfentuozi.batteryrecorder.shared.data.RecordFileIO
import yangfentuozi.batteryrecorder.shared.data.RecordFileNames
import yangfentuozi.batteryrecorder.shared.data.RecordFileParser
import yangfentuozi.batteryrecorder.shared.data.RecordsFile
import yangfentuozi.batteryrecorder.shared.data.RecordsStats
import yangfentuozi.batteryrecorder.shared.util.LoggerX
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.zip.ZipInputStream
import java.util.zip.ZipEntry
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipOutputStream

private const val TAG = "HistoryRepository"

data class HistoryRecord(
    val name: String,
    val type: BatteryStatus,
    val stats: RecordsStats,
    val lastModified: Long
) {
    fun asRecordsFile(): RecordsFile =
        RecordsFile(type, name)
}

data class HistorySummary(
    val type: BatteryStatus,
    val recordCount: Int,
    val averagePower: Double,
    val totalDurationMs: Long,
    val totalScreenOnMs: Long,
    val totalScreenOffMs: Long
)

data class RecordCleanupRequest(
    val keepCountPerType: Int? = null,
    val maxDurationMinutes: Int? = null,
    val maxCapacityChangePercent: Int? = null
) {
    val hasKeepCountRule: Boolean
        get() = keepCountPerType != null

    val hasConditionalRules: Boolean
        get() = maxDurationMinutes != null || maxCapacityChangePercent != null

    val hasAnyRule: Boolean
        get() = hasKeepCountRule || hasConditionalRules
}

data class RecordCleanupResult(
    val deletedCount: Int,
    val deletedChargingCount: Int,
    val deletedDischargingCount: Int,
    val failedFiles: List<String>
)

sealed interface CurrentRecordLoadResult {
    data class Success(val record: HistoryRecord) : CurrentRecordLoadResult
    data class InsufficientSamples(val recordsFile: RecordsFile) : CurrentRecordLoadResult
    data class Missing(val recordsFile: RecordsFile) : CurrentRecordLoadResult
    data class Failed(val recordsFile: RecordsFile, val error: Throwable) : CurrentRecordLoadResult
}

private sealed interface RecordCleanupInspection {
    data class Valid(val stats: RecordsStats) : RecordCleanupInspection
    data object InvalidFileName : RecordCleanupInspection
    data class InvalidStats(val error: Throwable) : RecordCleanupInspection
}

private data class RecordCleanupTarget(
    val type: BatteryStatus,
    val file: File
)

object HistoryRepository {

    private const val NOT_ENOUGH_VALID_SAMPLES_PREFIX = "Not enough valid samples after filtering:"
    private const val RECORD_COMPRESSION_BUFFER_SIZE = 64 * 1024
    private val CLEANUP_TARGET_TYPES = listOf(BatteryStatus.Charging, BatteryStatus.Discharging)

    // 逻辑记录名固定为“起始时间戳.txt”，物理文件允许是 `.txt` 或 `.txt.gz`。
    private fun recordFileTimestampOrNull(file: File): Long? =
        RecordFileNames.timestampOrNull(file.name)

    private fun listSortedRecordFiles(dir: File): List<File> =
        RecordFileNames.listStableFiles(dir).also {
            dir.listFiles()
                ?.asSequence()
                ?.filter { it.isFile }
                ?.forEach { file ->
                    if (
                        RecordFileNames.parse(file.name) == null &&
                        !RecordFileNames.isTempFileName(file.name)
                    ) {
                        LoggerX.w(TAG, "[记录] 跳过非法记录文件: ${file.absolutePath}")
                    }
                }
        }

    fun RecordsFile.toFile(context: Context): File? {
        val dataDir = dataDir(context, type)
        return validFile(dataDir, name)
    }

    // 确保目录存在且为目录，返回目录对象
    private fun dataDir(context: Context, type: BatteryStatus): File =
        File(File(context.dataDir, Constants.APP_POWER_DATA_PATH), type.dataDirName).apply {
            if (!isDirectory) delete()
            if (!exists()) mkdirs()
        }

    // 验证文件有效性，返回 null 表示无效
    private fun validFile(dir: File, name: String): File? =
        RecordFileNames.resolvePhysicalFile(dir, name)

    private fun logicalRecordName(file: File): String =
        RecordFileNames.logicalNameOrNull(file.name)
            ?: throw IllegalArgumentException("Invalid record file name: ${file.name}")

    private fun buildHistoryRecord(file: File, stats: RecordsStats): HistoryRecord {
        return HistoryRecord(
            name = logicalRecordName(file),
            type = BatteryStatus.fromDataDirName(file.parentFile?.name),
            stats = stats,
            lastModified = file.lastModified()
        )
    }

    private fun Throwable.isInsufficientSamplesError(): Boolean {
        return this is IllegalStateException &&
            message?.startsWith(NOT_ENOUGH_VALID_SAMPLES_PREFIX) == true
    }

    /** 加载统计数据并构建 HistoryRecord */
    fun loadStats(
        context: Context,
        file: File,
        needCaching: Boolean
    ): HistoryRecord? {
        val cacheFile = getPowerStatsCacheFile(context.cacheDir, logicalRecordName(file))
        LoggerX.v(TAG, 
            "[历史] 加载记录统计: file=${logicalRecordName(file)} source=${file.name} needCaching=$needCaching cache=${cacheFile.name}"
        )
        val stats = runCatching {
            RecordsStats.getCachedStats(
                cacheFile = cacheFile,
                sourceFile = file,
                needCaching = needCaching
            )
        }.onFailure { error ->
            LoggerX.e(TAG, "[历史] 加载记录统计失败: ${file.absolutePath}", tr = error)
        }.getOrNull() ?: return null

        return buildHistoryRecord(file, stats)
    }

    /** 仅列出文件，按记录起始时间倒序，不加载 stats */
    fun listRecordFiles(context: Context, type: BatteryStatus): List<File> {
        val files = listSortedRecordFiles(dataDir(context, type))
        LoggerX.d(TAG, "[历史] 列出记录文件: type=$type count=${files.size}")
        return files
    }

    /** 加载指定类型的所有记录，按记录起始时间倒序 */
    fun loadRecords(context: Context, type: BatteryStatus): List<HistoryRecord> {
        val files = listSortedRecordFiles(dataDir(context, type))
        if (files.isEmpty()) {
            LoggerX.d(TAG, "[历史] 无记录文件: type=$type")
            return emptyList()
        }
        val latestFile = files.firstOrNull()
        LoggerX.d(TAG, "[历史] 加载记录列表: type=$type count=${files.size} latest=${latestFile?.name}")

        return files.mapNotNull { loadStats(context, it, it != latestFile) }
    }

    /** 加载指定名称的单条记录 */
    fun loadRecord(context: Context, file: File): HistoryRecord {
        val dataDir = file.parentFile!!
        val latestFile = listSortedRecordFiles(dataDir).firstOrNull()
        val cacheFile = getPowerStatsCacheFile(context.cacheDir, logicalRecordName(file))
        val stats = RecordsStats.getCachedStats(
            cacheFile = cacheFile,
            sourceFile = file,
            needCaching = latestFile != file
        )
        return buildHistoryRecord(file, stats)
    }

    /** 加载记录的图表数据点，用于绘制功率曲线 */
    fun loadRecordPoints(context: Context, recordsFile: RecordsFile): List<ChartPoint> {
        val file = recordsFile.toFile(context)
            ?: throw FileNotFoundException("Record file not found: ${recordsFile.name}")
        return loadRecordPoints(file)
    }

    fun loadRecordPoints(file: File): List<ChartPoint> {
        return loadLineRecords(file).map { record ->
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

    fun loadLineRecords(file: File): List<LineRecord> =
        RecordFileParser.parseToList(file)

    /** 从 service.currRecordsFile 加载当前记录 */
    fun loadLatestRecord(
        context: Context
    ): HistoryRecord? {
        val service = Service.service ?: return null
        val serviceFile = runCatching { service.currRecordsFile }
            .getOrNull()
            ?.toFile(context)
        LoggerX.d(TAG, "[历史] 加载当前记录: serviceFile=${serviceFile?.name}")
        return serviceFile?.let { file ->
            loadStats(context, file, false)
        }
    }

    fun loadCurrentRecord(
        context: Context,
        recordsFile: RecordsFile
    ): CurrentRecordLoadResult {
        val sourceFile = recordsFile.toFile(context)
            ?: return CurrentRecordLoadResult.Missing(recordsFile)
        val latestFile = listSortedRecordFiles(sourceFile.parentFile ?: return CurrentRecordLoadResult.Missing(recordsFile))
            .firstOrNull()
        val cacheFile = getPowerStatsCacheFile(context.cacheDir, recordsFile.name)
        val stats = runCatching {
            RecordsStats.getCachedStats(
                cacheFile = cacheFile,
                sourceFile = sourceFile,
                needCaching = latestFile != sourceFile
            )
        }.getOrElse { error ->
            if (error.isInsufficientSamplesError()) {
                LoggerX.w(TAG, 
                    "[历史] 当前记录样本不足，等待更多采样: ${sourceFile.absolutePath}"
                )
                return CurrentRecordLoadResult.InsufficientSamples(recordsFile)
            }
            LoggerX.e(TAG, "[历史] 加载当前记录失败: ${sourceFile.absolutePath}", tr = error)
            return CurrentRecordLoadResult.Failed(recordsFile, error)
        }
        return CurrentRecordLoadResult.Success(buildHistoryRecord(sourceFile, stats))
    }

    /** 加载统计摘要，按时长加权计算平均功率 */
    fun loadSummary(
        context: Context,
        type: BatteryStatus,
        avgPowerLimit: Int = 20
    ): HistorySummary? {
        val dataDir = dataDir(context, type)
        val files = listSortedRecordFiles(dataDir)

        val recordCount = files.size
        if (recordCount == 0) {
            LoggerX.d(TAG, "[历史] 汇总为空: type=$type")
            return null
        }

        val latestFile = files.first()
        val sampleFiles = files.take(avgPowerLimit.coerceAtLeast(0))
        if (sampleFiles.isEmpty()) {
            LoggerX.w(TAG, "[历史] 汇总样本为空: type=$type avgPowerLimit=$avgPowerLimit")
            return null
        }

        val records = sampleFiles.mapNotNull { loadStats(context, it, it != latestFile) }
        if (records.isEmpty()) {
            LoggerX.w(TAG, "[历史] 汇总记录加载失败: type=$type sampleCount=${sampleFiles.size}")
            return null
        }

        var totalDurationMs = 0L
        var weightedPowerSum = 0.0
        var totalScreenOnMs = 0L
        var totalScreenOffMs = 0L

        records.forEach { record ->
            val stats = record.stats
            val durationMs = (stats.endTime - stats.startTime).coerceAtLeast(0)
            totalDurationMs += durationMs
            weightedPowerSum += stats.averagePower * durationMs
            totalScreenOnMs += stats.screenOnTimeMs
            totalScreenOffMs += stats.screenOffTimeMs
        }

        val averagePower = if (totalDurationMs > 0) {
            weightedPowerSum / totalDurationMs
        } else {
            records.map { it.stats.averagePower }.average()
        }

        val summary = HistorySummary(
            type = type,
            recordCount = recordCount,
            averagePower = averagePower,
            totalDurationMs = totalDurationMs,
            totalScreenOnMs = totalScreenOnMs,
            totalScreenOffMs = totalScreenOffMs
        )
        LoggerX.d(TAG, 
            "[历史] 汇总完成: type=$type recordCount=$recordCount durationMs=$totalDurationMs avgPower=$averagePower"
        )
        return summary
    }

    /** 删除记录及其缓存文件 */
    fun deleteRecord(context: Context, recordsFile: RecordsFile): Boolean {
        val sourceFile = recordsFile.toFile(context) ?: return false.also {
            LoggerX.w(TAG, "[历史] 删除记录失败，文件不存在: ${recordsFile.name}")
        }
        return deleteRecordFile(context, sourceFile, recordsFile.type)
    }

    /**
     * 压缩本地非活跃历史记录。
     *
     * @param context 应用上下文。
     * @param activeRecordsFile 当前仍在写入的记录；会按逻辑记录名排除，不参与压缩。
     * @return 返回成功压缩的记录数量。
     */
    fun compressHistoricalRecords(
        context: Context,
        activeRecordsFile: RecordsFile? = null
    ): Int {
        val activeLogicalName = activeRecordsFile?.name
        var compressedCount = 0

        CLEANUP_TARGET_TYPES.forEach { type ->
            dataDir(context, type).listFiles()
                ?.asSequence()
                ?.filter { file ->
                    val descriptor = RecordFileNames.parse(file.name) ?: return@filter false
                    !descriptor.isCompressed && descriptor.logicalName != activeLogicalName
                }
                ?.sortedBy { it.name }
                ?.forEach { file ->
                    if (compressRecordFile(file)) {
                        compressedCount += 1
                    }
                }
        }

        LoggerX.i(
            TAG,
            "[历史] 本地历史压缩完成: compressed=$compressedCount active=${activeLogicalName ?: "null"}"
        )
        return compressedCount
    }

    /**
     * 按主页清理规则扫描并删除历史记录。
     *
     * @param context 应用上下文。
     * @param request 用户确认后的清理规则。
     * @param activeRecordsFile 当前正在写入的记录文件；该文件始终受保护，不参与删除。
     * @return 返回本次清理的删除结果与失败文件列表。
     */
    fun cleanupRecords(
        context: Context,
        request: RecordCleanupRequest,
        activeRecordsFile: RecordsFile? = null
    ): RecordCleanupResult {
        require(request.hasAnyRule) { "Record cleanup request has no rules" }
        request.keepCountPerType?.let { require(it > 0) { "keepCountPerType must be > 0" } }
        request.maxDurationMinutes?.let { require(it > 0) { "maxDurationMinutes must be > 0" } }
        request.maxCapacityChangePercent?.let {
            require(it in 1..100) { "maxCapacityChangePercent must be in 1..100" }
        }

        val protectedPath = activeRecordsFile?.toFile(context)?.absolutePath
        val cleanupTargets = LinkedHashMap<String, RecordCleanupTarget>()

        if (request.keepCountPerType != null) {
            CLEANUP_TARGET_TYPES.forEach { type ->
                val overflowFiles = listSortedRecordFiles(dataDir(context, type))
                    .drop(request.keepCountPerType)
                overflowFiles.forEach { file ->
                    if (file.absolutePath == protectedPath) return@forEach
                    val key = "${type.dataDirName}/${file.name}"
                    if (cleanupTargets[key] == null) {
                        cleanupTargets[key] = RecordCleanupTarget(type = type, file = file)
                    }
                }
            }
        }

        if (request.hasConditionalRules) {
            CLEANUP_TARGET_TYPES.forEach { type ->
                listAllRecordFiles(context, type).forEach { file ->
                    if (file.absolutePath == protectedPath) return@forEach
                    when (val inspection = inspectRecordForCleanup(context, file)) {
                        is RecordCleanupInspection.Valid -> {
                            val stats = inspection.stats
                            val durationMs = (stats.endTime - stats.startTime).coerceAtLeast(0L)
                            val capacityChange = computeCapacityChange(type, stats)
                            val durationMatched =
                                request.maxDurationMinutes == null ||
                                    durationMs < request.maxDurationMinutes * 60_000L
                            val capacityMatched =
                                request.maxCapacityChangePercent == null ||
                                    capacityChange < request.maxCapacityChangePercent
                            if (!durationMatched || !capacityMatched) {
                                return@forEach
                            }
                            val key = "${type.dataDirName}/${file.name}"
                            if (cleanupTargets[key] == null) {
                                cleanupTargets[key] = RecordCleanupTarget(type = type, file = file)
                            }
                        }

                        RecordCleanupInspection.InvalidFileName -> {
                            LoggerX.w(
                                TAG,
                                "[记录清理] 文件名非法，跳过条件清理: ${file.absolutePath}"
                            )
                        }

                        is RecordCleanupInspection.InvalidStats -> {
                            LoggerX.w(
                                TAG,
                                "[记录清理] 记录解析失败，跳过条件清理: file=${file.absolutePath}",
                                tr = inspection.error
                            )
                        }
                    }
                }
            }
        }

        if (cleanupTargets.isEmpty()) {
            LoggerX.i(TAG, "[记录清理] 没有命中任何待删除记录")
            return RecordCleanupResult(
                deletedCount = 0,
                deletedChargingCount = 0,
                deletedDischargingCount = 0,
                failedFiles = emptyList()
            )
        }

        var deletedCount = 0
        var deletedChargingCount = 0
        var deletedDischargingCount = 0
        val failedFiles = mutableListOf<String>()

        cleanupTargets.values.forEach { target ->
            val deleted = deleteRecordFile(
                context = context,
                file = target.file,
                type = target.type
            )
            if (!deleted) {
                failedFiles += "${target.type.dataDirName}/${target.file.name}"
                return@forEach
            }
            deletedCount += 1
            when (target.type) {
                BatteryStatus.Charging -> deletedChargingCount += 1
                BatteryStatus.Discharging -> deletedDischargingCount += 1
                else -> {}
            }
        }

        LoggerX.i(
            TAG,
            "[记录清理] 执行完成: deleted=$deletedCount failed=${failedFiles.size}"
        )
        return RecordCleanupResult(
            deletedCount = deletedCount,
            deletedChargingCount = deletedChargingCount,
            deletedDischargingCount = deletedDischargingCount,
            failedFiles = failedFiles
        )
    }

    /** 导出单条记录到用户选择位置 */
    fun exportRecord(
        context: Context,
        recordsFile: RecordsFile,
        destinationUri: Uri
    ) {
        val sourceFile = recordsFile.toFile(context)
            ?: throw FileNotFoundException("Record file not found: ${recordsFile.name}")
        // 目标由 SAF 提供，必须通过 ContentResolver 流写入而不是直接按文件路径访问
        val outputStream = context.contentResolver.openOutputStream(destinationUri, "w")
            ?: throw IOException("Failed to open destination: $destinationUri")

        outputStream.use { output ->
            RecordFileIO.copyAsPlainText(sourceFile, output)
        }
        LoggerX.i(TAG, "[历史] 导出记录成功: source=${recordsFile.name} destination=$destinationUri")
    }

    /** 导出当前列表内的所有记录到 ZIP */
    fun exportRecordsZip(
        context: Context,
        recordsFiles: List<RecordsFile>,
        destinationUri: Uri
    ) {
        LoggerX.i(
            TAG,
            "[历史] 开始导出记录压缩包: count=${recordsFiles.size} destination=$destinationUri"
        )
        val outputStream = context.contentResolver.openOutputStream(destinationUri, "w")
            ?: throw IOException("Failed to open destination: $destinationUri")
        LoggerX.d(TAG, "[历史] 导出记录压缩包输出流已打开: destination=$destinationUri")

        outputStream.use { rawOutput ->
            ZipOutputStream(rawOutput).use { zipOutput ->
                LoggerX.d(TAG, "[历史] 导出记录压缩包 ZIP 已创建: destination=$destinationUri")
                recordsFiles.forEach { recordsFile ->
                    val sourceFile = recordsFile.toFile(context)
                        ?: throw FileNotFoundException("Record file not found: ${recordsFile.name}")
                    LoggerX.d(
                        TAG,
                        "[历史] 写入导出 ZIP 条目: file=${recordsFile.name} source=${sourceFile.name} size=${sourceFile.length()} destination=$destinationUri"
                    )
                    zipOutput.putNextEntry(ZipEntry(recordsFile.name))
                    RecordFileIO.copyAsPlainText(sourceFile, zipOutput)
                    zipOutput.closeEntry()
                    LoggerX.d(
                        TAG,
                        "[历史] 导出 ZIP 条目写入完成: file=${recordsFile.name} destination=$destinationUri"
                    )
                }
            }
        }
        LoggerX.i(TAG, "[历史] 导出记录压缩包成功: count=${recordsFiles.size} destination=$destinationUri")
    }

    /**
     * 从用户选择的 ZIP 导入当前类型的历史记录。
     *
     * @param context 应用上下文。
     * @param type 目标历史类型；决定导入落盘目录。
     * @param sourceUri 用户选择的 ZIP 文档 Uri。
     * @return 返回成功导入的记录数；任一条目不符合一键导出格式时直接抛错，不执行部分导入。
     */
    fun importRecordsZip(
        context: Context,
        type: BatteryStatus,
        sourceUri: Uri
    ): Int {
        LoggerX.i(TAG, "[历史] 开始导入记录压缩包: type=${type.dataDirName} source=$sourceUri")
        val inputStream = context.contentResolver.openInputStream(sourceUri)
            ?: throw IOException("Failed to open source: $sourceUri")
        LoggerX.d(TAG, "[历史] 导入记录压缩包输入流已打开: source=$sourceUri")
        val targetDir = dataDir(context, type)
        val stagingDir = File(
            context.cacheDir,
            "history-import-${type.dataDirName}-${System.currentTimeMillis()}"
        )
        if (!stagingDir.exists() && !stagingDir.mkdirs()) {
            throw IOException("Failed to create staging dir: ${stagingDir.absolutePath}")
        }

        try {
            val stagedEntries = inputStream.use { rawInput ->
                ZipInputStream(rawInput.buffered()).use { zipInput ->
                    val seenNames = LinkedHashSet<String>()
                    buildList {
                        var entry = zipInput.nextEntry
                        while (entry != null) {
                            LoggerX.d(
                                TAG,
                                "[历史] 读取导入 ZIP 条目: type=${type.dataDirName} entry=${entry.name} size=${entry.size}"
                            )
                            if (entry.isDirectory) {
                                throw IOException("ZIP 包含目录条目，不是一键导出格式: ${entry.name}")
                            }

                            val entryName = entry.name.trim()
                            if (entryName.isEmpty()) {
                                throw IOException("ZIP 包含空文件名条目")
                            }
                            if (entryName.contains('/') || entryName.contains('\\')) {
                                throw IOException("ZIP 条目路径非法，不是一键导出格式: ${entry.name}")
                            }
                            if (!entryName.endsWith(RecordFileNames.PLAIN_SUFFIX) ||
                                recordFileTimestampOrNull(File(entryName)) == null
                            ) {
                                throw IOException("ZIP 条目文件名非法: ${entry.name}")
                            }
                            if (!seenNames.add(entryName)) {
                                throw IOException("ZIP 包含重复记录文件: $entryName")
                            }

                            val stagedFile = File(stagingDir, entryName)
                            stagedFile.outputStream().use { output ->
                                zipInput.copyTo(output)
                            }
                            validateImportedRecordFile(stagedFile)
                            LoggerX.d(
                                TAG,
                                "[历史] 导入 ZIP 条目校验完成: type=${type.dataDirName} entry=$entryName size=${stagedFile.length()}"
                            )
                            add(stagedFile)
                            zipInput.closeEntry()
                            entry = zipInput.nextEntry
                        }
                    }
                }
            }
            if (stagedEntries.isEmpty()) {
                throw IOException("ZIP 中没有可导入的记录文件")
            }
            stagedEntries.forEach { stagedFile ->
                deleteRecordVariants(context.cacheDir, targetDir, stagedFile.name)
                val destinationFile = File(targetDir, stagedFile.name)
                stagedFile.copyTo(destinationFile, overwrite = true)
                getPowerStatsCacheFile(context.cacheDir, stagedFile.name).delete()
                LoggerX.d(
                    TAG,
                    "[历史] 导入记录文件写入完成: type=${type.dataDirName} file=${stagedFile.name} destination=${destinationFile.absolutePath}"
                )
            }
            LoggerX.i(TAG, "[历史] 导入记录压缩包成功: type=${type.dataDirName} count=${stagedEntries.size} source=$sourceUri")
            return stagedEntries.size
        } finally {
            stagingDir.deleteRecursively()
        }
    }

    private fun validateImportedRecordFile(file: File) {
        var validRecordCount = 0
        RecordFileParser.forEachValidRecord(file) { _ ->
            validRecordCount += 1
        }

        if (validRecordCount == 0) {
            throw IOException("记录文件没有有效数据: ${file.name}")
        }
    }

    /**
     * 列出指定类型目录下的所有物理记录文件。
     *
     * @param context 应用上下文。
     * @param type 历史类型。
     * @return 返回目录内全部文件，不做文件名与内容合法性过滤。
     */
    private fun listAllRecordFiles(context: Context, type: BatteryStatus): List<File> =
        listSortedRecordFiles(dataDir(context, type))

    /**
     * 解析单条记录文件，供条件清理判断使用。
     *
     * @param context 应用上下文。
     * @param file 待检查的物理记录文件。
     * @return 返回有效统计，或返回异常类型用于清理判定。
     */
    private fun inspectRecordForCleanup(
        context: Context,
        file: File
    ): RecordCleanupInspection {
        if (recordFileTimestampOrNull(file) == null) {
            LoggerX.w(TAG, "[记录清理] 文件名非法，按异常记录处理: ${file.absolutePath}")
            return RecordCleanupInspection.InvalidFileName
        }
        val cacheFile = getPowerStatsCacheFile(context.cacheDir, logicalRecordName(file))
        return runCatching {
            RecordsStats.getCachedStats(
                cacheFile = cacheFile,
                sourceFile = file,
                needCaching = true
            )
        }.fold(
            onSuccess = { stats -> RecordCleanupInspection.Valid(stats) },
            onFailure = { error -> RecordCleanupInspection.InvalidStats(error) }
        )
    }

    /**
     * 根据充放电语义计算记录的电量变化百分比。
     *
     * @param type 记录所属类型。
     * @param stats 已解析的记录统计。
     * @return 返回用于阈值比较的正向电量变化值。
     */
    private fun computeCapacityChange(
        type: BatteryStatus,
        stats: RecordsStats
    ): Int = when (type) {
        BatteryStatus.Charging -> (stats.endCapacity - stats.startCapacity).coerceAtLeast(0)
        BatteryStatus.Discharging -> (stats.startCapacity - stats.endCapacity).coerceAtLeast(0)
        else -> 0
    }

    /**
     * 删除物理记录文件，并同步清理对应统计缓存。
     *
     * @param context 应用上下文。
     * @param file 已定位到数据目录内的记录文件。
     * @param type 文件所属的充放电类型。
     * @return 删除成功返回 true，否则返回 false。
     */
    private fun deleteRecordFile(
        context: Context,
        file: File,
        type: BatteryStatus
    ): Boolean {
        val logicalName = logicalRecordName(file)
        val parentDir = file.parentFile ?: return false
        if (runCatching {
                deleteRecordVariants(context.cacheDir, parentDir, logicalName)
            }.getOrDefault(false)
        ) {
            LoggerX.i(TAG, "[记录清理] 删除记录成功: type=${type.dataDirName} file=$logicalName")
            return true
        }
        LoggerX.w(TAG, "[记录清理] 删除记录失败: type=${type.dataDirName} file=$logicalName")
        return false
    }

    private fun deleteRecordVariants(
        cacheRoot: File,
        dataDir: File,
        logicalName: String
    ): Boolean {
        val plainFile = File(dataDir, logicalName)
        val gzipFile = File(dataDir, "$logicalName.gz")
        val removedData = runCatching {
            var removed = false
            if (plainFile.exists()) {
                removed = plainFile.delete() || removed
            }
            if (gzipFile.exists()) {
                removed = gzipFile.delete() || removed
            }
            removed
        }.getOrDefault(false)
        runCatching { getPowerStatsCacheFile(cacheRoot, logicalName).delete() }
        return removedData
    }

    private fun compressRecordFile(sourceFile: File): Boolean {
        val descriptor = RecordFileNames.parse(sourceFile.name) ?: return false
        if (descriptor.isCompressed) return false

        val parentDir = sourceFile.parentFile
            ?: throw IOException("记录文件没有父目录: ${sourceFile.absolutePath}")
        val gzipFile = File(parentDir, "${descriptor.logicalName}.gz")
        val tempFile = File(parentDir, "${descriptor.logicalName}.gz${RecordFileNames.TEMP_SUFFIX}")

        if (gzipFile.exists()) {
            if (!sourceFile.delete()) {
                throw IOException("删除重复明文记录失败: ${sourceFile.absolutePath}")
            }
            LoggerX.i(
                TAG,
                "[历史] 删除重复明文记录: source=${sourceFile.name} target=${gzipFile.name}"
            )
            return true
        }

        if (tempFile.exists() && !tempFile.delete()) {
            throw IOException("删除压缩临时文件失败: ${tempFile.absolutePath}")
        }

        try {
            sourceFile.inputStream().buffered(RECORD_COMPRESSION_BUFFER_SIZE).use { input ->
                GZIPOutputStream(
                    tempFile.outputStream().buffered(RECORD_COMPRESSION_BUFFER_SIZE)
                ).use { output ->
                    input.copyTo(output, RECORD_COMPRESSION_BUFFER_SIZE)
                }
            }
            if (!tempFile.renameTo(gzipFile)) {
                throw IOException("记录压缩临时文件改名失败: ${tempFile.absolutePath}")
            }
            if (!sourceFile.delete()) {
                throw IOException("压缩完成后删除明文记录失败: ${sourceFile.absolutePath}")
            }
            LoggerX.i(
                TAG,
                "[历史] 本地历史压缩成功: source=${sourceFile.name} target=${gzipFile.name}"
            )
            return true
        } catch (error: Throwable) {
            tempFile.delete()
            throw error
        }
    }
}
