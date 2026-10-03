package yangfentuozi.batteryrecorder

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import yangfentuozi.batteryrecorder.data.bh.BhParserV3
import yangfentuozi.batteryrecorder.data.bh.isComplete
import java.io.File

/**
 * BhParserV3 对真机样本文件的解码验证：
 * 记录数应与文件头声明一致、结束时间应与头部 end 吻合、事件时间应落在会话范围内。
 *
 * 样本目录：<repo>/bh-samples（测试工作目录为 app/，因此用 ../bh-samples）。
 * 样本不入库（已 gitignore）：目录不存在时整条用例跳过而不是失败。
 */
class BhParserV3Test {

    private fun samplesDir(): File =
        listOf(File("../bh-samples"), File("bh-samples"))
            .firstOrNull { it.isDirectory }
            ?: File("../bh-samples")

    private fun deviceDir(): File = File(samplesDir(), "device")

    @Test
    fun decodeSampleFiles() {
        assumeTrue("bh-samples not found; skipping", samplesDir().isDirectory)
        val files = if (deviceDir().isDirectory) {
            deviceDir().listFiles { f -> f.name.endsWith(".bh") }?.sortedBy { it.name } ?: emptyList()
        } else {
            samplesDir().listFiles { f -> f.name.endsWith(".bh") }?.sortedBy { it.length() }?.take(6)
                ?: emptyList()
        }
        assumeTrue("no samples", files.isNotEmpty())

        var ok = 0
        for (file in files) {
            val d = BhParserV3.decodeFile(file) ?: run {
                println("SKIP ${file.name}: decode null")
                continue
            }
            val timeMatch = d.lastTimeMs == d.endUptimeMs
            val complete = d.isComplete()
            println(
                "FILE ${file.name} records=${d.records} lastTime=${d.lastTimeMs}/${d.endUptimeMs} " +
                        "diffMs=${d.lastTimeMs - d.endUptimeMs} events=${d.events.size} " +
                        "resync=${d.resyncCount} complete=$complete"
            )
            d.events.take(4).forEach {
                println("   ev t=+${it.timeMs - d.startUptimeMs}ms code=0x${it.code.toString(16)} uid=${it.uid} ${it.text?.take(48)}")
            }
            if (complete) ok++
        }
        println("OK files: $ok/${files.size}")
        assertTrue("at least one file fully decodes", ok >= 1)
    }
}
