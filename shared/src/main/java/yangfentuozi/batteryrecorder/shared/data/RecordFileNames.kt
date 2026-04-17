package yangfentuozi.batteryrecorder.shared.data

import java.io.File

/**
 * 记录文件命名与逻辑记录定位规则。
 *
 * 当前逻辑记录名统一固定为 `时间戳.txt`；
 * 物理文件允许是 `时间戳.txt` 或 `时间戳.txt.gz`。
 */
object RecordFileNames {
    const val PLAIN_SUFFIX = ".txt"
    const val GZIP_SUFFIX = ".txt.gz"
    const val TEMP_SUFFIX = ".tmp"

    data class Descriptor(
        val timestamp: Long,
        val logicalName: String,
        val isCompressed: Boolean
    )

    /**
     * 解析记录文件描述信息。
     *
     * @param fileName 物理文件名。
     * @return 仅当文件名符合记录规则时返回描述信息，否则返回 `null`。
     */
    fun parse(fileName: String): Descriptor? {
        if (fileName.endsWith(TEMP_SUFFIX)) return null
        val timestampText = when {
            fileName.endsWith(GZIP_SUFFIX) -> fileName.removeSuffix(GZIP_SUFFIX)
            fileName.endsWith(PLAIN_SUFFIX) -> fileName.removeSuffix(PLAIN_SUFFIX)
            else -> return null
        }
        val timestamp = timestampText.toLongOrNull() ?: return null
        return Descriptor(
            timestamp = timestamp,
            logicalName = logicalName(timestamp),
            isCompressed = fileName.endsWith(GZIP_SUFFIX)
        )
    }

    /**
     * 构造逻辑记录名。
     *
     * @param timestamp 记录时间戳。
     * @return 返回统一逻辑记录名。
     */
    fun logicalName(timestamp: Long): String = "$timestamp$PLAIN_SUFFIX"

    /**
     * 从任意支持的记录文件名提取逻辑记录名。
     *
     * @param fileName 物理文件名或逻辑文件名。
     * @return 返回逻辑记录名，不支持则返回 `null`。
     */
    fun logicalNameOrNull(fileName: String): String? = parse(fileName)?.logicalName

    /**
     * 从任意支持的记录文件名提取记录时间戳。
     *
     * @param fileName 物理文件名或逻辑文件名。
     * @return 返回记录时间戳，不支持则返回 `null`。
     */
    fun timestampOrNull(fileName: String): Long? = parse(fileName)?.timestamp

    /**
     * 判断文件名是否是 gzip 记录文件。
     *
     * @param fileName 文件名。
     * @return gzip 记录文件返回 `true`。
     */
    fun isCompressedFileName(fileName: String): Boolean = fileName.endsWith(GZIP_SUFFIX)

    /**
     * 判断文件名是否为压缩过程中的临时文件。
     *
     * @param fileName 文件名。
     * @return 临时文件返回 `true`。
     */
    fun isTempFileName(fileName: String): Boolean = fileName.endsWith(TEMP_SUFFIX)

    /**
     * 在目录中定位当前逻辑记录对应的实际物理文件。
     *
     * 若 `.txt` 与 `.txt.gz` 同时存在，优先返回 `.txt.gz`，避免压缩收尾瞬间出现双份。
     *
     * @param dir 记录目录。
     * @param logicalName 逻辑记录名。
     * @return 返回当前可用物理文件；不存在时返回 `null`。
     */
    fun resolvePhysicalFile(
        dir: File,
        logicalRecordName: String
    ): File? {
        val timestamp = timestampOrNull(logicalRecordName) ?: return null
        val plainFile = File(dir, logicalName(timestamp))
        val gzipFile = File(dir, "${logicalName(timestamp)}.gz")
        return when {
            gzipFile.isFile -> gzipFile
            plainFile.isFile -> plainFile
            else -> null
        }
    }

    /**
     * 列出目录内全部稳定记录文件。
     *
     * 同一逻辑记录若同时存在 plain / gzip，优先返回 gzip，避免枚举重复记录。
     *
     * @param dir 记录目录。
     * @return 返回按记录时间戳倒序排列的稳定物理文件列表。
     */
    fun listStableFiles(dir: File): List<File> {
        val selected = LinkedHashMap<Long, Pair<File, Boolean>>()
        dir.listFiles()
            ?.asSequence()
            ?.filter { it.isFile }
            ?.forEach { file ->
                val descriptor = parse(file.name) ?: return@forEach
                val current = selected[descriptor.timestamp]
                if (current == null || (!current.second && descriptor.isCompressed)) {
                    selected[descriptor.timestamp] = file to descriptor.isCompressed
                }
            }
        return selected
            .toList()
            .sortedByDescending { it.first }
            .map { it.second.first }
    }
}
