package yangfentuozi.batteryrecorder.data.bh

import java.io.File

/**
 * Root 读取通道：真机数据位于 /data/system/battery-history，普通应用无权限，
 * 需通过 `su -c` 读取（KernelSU / Magisk 均可；首次调用会弹一次授权框）。
 *
 * 说明：
 * - 直接可读时（如 root 身份运行或已导入到应用目录）优先走 File IO，不触发 su；
 * - su 通道按需使用，结果做进程内缓存，避免重复弹窗与重复 IO。
 */
object BhRootSource {

    const val REAL_DEVICE_DIR = "/data/system/battery-history"

    /** 文件条目（root stat 结果）。 */
    data class Entry(
        val name: String,
        val path: String,
        /** 文件修改时间（epoch 毫秒）。 */
        val mtimeMs: Long,
        val size: Long
    )

    @Volatile private var suChecked = false
    @Volatile private var suAvailable = false

    /** su 是否可用（结果缓存；失败后可用 [resetSuCheck] 重试，例如用户刚授权）。 */
    fun suAvailable(): Boolean {
        if (suChecked) return suAvailable
        synchronized(this) {
            if (!suChecked) {
                val out = runSu("id").orEmpty()
                suAvailable = out.contains("uid=0")
                suChecked = true
            }
        }
        return suAvailable
    }

    fun resetSuCheck() {
        synchronized(this) {
            suChecked = false
            suAvailable = false
        }
    }

    /** 执行 su 命令并返回 stdout（失败返回 null）。 */
    fun runSu(command: String): String? = try {
        val process = ProcessBuilder("su", "-c", command)
            .redirectErrorStream(false)
            .start()
        val out = process.inputStream.readBytes()
        // 消费 stderr，避免子进程阻塞
        process.errorStream.readBytes()
        process.waitFor()
        String(out, Charsets.UTF_8)
    } catch (t: Throwable) {
        null
    }

    /** 执行 su 命令并返回原始字节（二进制安全，读 .bh 用）。 */
    fun runSuBytes(command: String): ByteArray? = try {
        val process = ProcessBuilder("su", "-c", command).start()
        val out = process.inputStream.readBytes()
        process.errorStream.readBytes()
        process.waitFor()
        if (out.isEmpty()) null else out
    } catch (t: Throwable) {
        null
    }

    /** 直接可读的目录（应用有权限）——不触发 su。 */
    fun readableDir(path: String): Boolean {
        val dir = File(path)
        return dir.isDirectory && dir.listFiles()?.isNotEmpty() == true
    }

    /**
     * 列出目录下全部 .bh 文件。
     *
     * @return 列表（按文件名倒序）；无权限且 su 不可用时返回 null。
     */
    fun listEntries(dirPath: String): List<Entry>? {
        // 1) 直接可读：走 File（mtime 精度不受影响）
        val direct = File(dirPath)
        if (readableDir(dirPath)) {
            return direct.listFiles { f -> f.isFile && f.name.endsWith(".bh") }
                ?.map { Entry(it.name, it.absolutePath, it.lastModified(), it.length()) }
                ?.sortedByDescending { it.name }
        }
        // 2) root 通道：stat 拿 name|mtime秒|mtime完整|size（%y 带纳秒小数，用于把挂钟锚点精确到毫秒）
        if (!suAvailable()) return null
        val out = runSu("stat -c '%n|%Y|%y|%s' $dirPath/*.bh 2>/dev/null") ?: return null
        return out.lineSequence()
            .mapNotNull { line ->
                val parts = line.trim().split("|")
                if (parts.size != 4) return@mapNotNull null
                val path = parts[0]
                val mtimeSec = parts[1].toLongOrNull() ?: return@mapNotNull null
                val size = parts[3].toLongOrNull() ?: 0L
                Entry(File(path).name, path, mtimeSec * 1000 + fractionalMillis(parts[2]), size)
            }
            .sortedByDescending { it.name }
            .toList()
    }

    private val fractionRegex = Regex("\\.(\\d{1,9})")

    /** 从 `stat -c %y`（"2026-10-03 11:25:01.903581571 +0800"）取小数秒并换算为毫秒。 */
    private fun fractionalMillis(statY: String): Long {
        val frac = fractionRegex.find(statY)?.groupValues?.get(1) ?: return 0L
        return frac.padEnd(3, '0').take(3).toLongOrNull() ?: 0L
    }

    /** 读取单个 .bh 原始字节（直接可读 → File；否则 su cat）。 */
    fun readBytes(entry: Entry): ByteArray? {
        val direct = File(entry.path)
        if (direct.canRead()) {
            return try {
                direct.readBytes()
            } catch (_: Throwable) {
                null
            }
        }
        if (!suAvailable()) return null
        return runSuBytes("cat '${entry.path}'")
    }
}
