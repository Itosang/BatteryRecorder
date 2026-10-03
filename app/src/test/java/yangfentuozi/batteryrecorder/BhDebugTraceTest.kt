package yangfentuozi.batteryrecorder

import org.junit.Test
import java.io.File
import java.util.zip.GZIPInputStream

/**
 * 调试用：逐条打印某个 .bh 的解析轨迹，定位首个错位点。
 */
class BhDebugTraceTest {

    @Test
    fun traceOne() {
        val file = File("../bh-samples/248611586.bh")
        if (!file.isFile) { println("no file"); return }
        val bytes = file.readBytes()
        val gz = bytes.indexOf(0x1F.toByte())
        val raw = GZIPInputStream(bytes.inputStream(gz, bytes.size - gz)).use { it.readBytes() }
        println("raw=${raw.size} headerCount=${u32(raw,0)} start=${u32(raw,4)} end=${u32(raw,12)}")

        var pos = 32
        var time = 0L
        var n = 0
        val level = intArrayOf(0)
        while (pos + 4 <= raw.size && n < 80) {
            n++
            val start = pos
            val tok = u32(raw, pos); pos += 4
            val dt = tok and 0x7FFFF
            var ev = ""
            try {
                if (dt == 0x7FFFD) {
                    time = u64(raw, pos); pos += 8
                    val bat = u32(raw, pos); pos += 4
                    pos += 4 + 4 + 16 + 4 + 4
                    if (bat and 0x10000000 != 0) { pos = skipTag(raw, pos) }
                    if (bat and 0x20000000 != 0) { pos = skipTag(raw, pos) }
                    if (bat and 0x40000000 != 0) {
                        val code = u32(raw, pos); pos += 4
                        val t = readTag(raw, pos); pos = t.second; ev = "ev ${code.toHex()} ${t.first?.take(40)}"
                    }
                    if (bat and 0xFF == 5 || bat and 0xFF == 7) pos += 8
                } else {
                    if (dt == 0x7FFFE) { time += u32(raw, pos).toLong(); pos += 4 }
                    else if (dt == 0x7FFFF) { time += u64(raw, pos); pos += 8 }
                    else time += dt
                    if (tok and 0x80000 != 0) {
                        val bi = u32(raw, pos); pos += 4
                        if (bi and 2 != 0) pos += 4
                        if (bi and 1 != 0) {
                            pos += 17 * 4
                            val l = u32(raw, pos)
                            pos = align4(pos + 4 + l + 1)
                        }
                    }
                    if (tok and 0x100000 != 0) pos += 4
                    var st2 = 0
                    if (tok and 0x200000 != 0) { st2 = u32(raw, pos); pos += 4 }
                    if (tok and 0x400000 != 0) {
                        val idxs = u32(raw, pos); pos += 4
                        val lo = idxs and 0xFFFF; val hi = (idxs ushr 16) and 0xFFFF
                        if (lo != 0xFFFF && (lo and 0x8000) != 0) pos = skipTag(raw, pos)
                        if (hi != 0xFFFF && (hi and 0x8000) != 0) pos = skipTag(raw, pos)
                    }
                    if (tok and 0x800000 != 0) {
                        val ci = u32(raw, pos); pos += 4
                        val idx = (ci ushr 16) and 0xFFFF
                        if (idx != 0xFFFF && (idx and 0x8000) != 0) {
                            val t = readTag(raw, pos); pos = t.second
                            ev = "ev 0x${(ci and 0xFFFF).toString(16)} uid=${t.third} ${t.first?.take(48)}"
                        }
                    }
                    if (tok and 0x1000000 != 0) pos += 4
                    pos += 16
                    if (st2 and (1 shl 17) != 0) {
                        println("#$n @$start tok=0x${tok.toHex()} dt=$dt time=$time EXTENSION states2=0x${st2.toHex()}")
                        // 打印扩展处后续 64 字节
                        println("   ctx: " + hex(raw, pos, 64))
                        return
                    }
                }
            } catch (e: Throwable) {
                println("#$n @$start EXC ${e.javaClass.simpleName}: ${e.message}")
                println("   ctx: " + hex(raw, start, 64))
                return
            }
            if (n <= 12 || tok and 0x800000 != 0) {
                println("#$n @$start tok=0x${tok.toHex()} dt=$dt time=$time $ev")
            }
            if (pos <= start) { println("stall"); return }
        }
        println("done $n records, pos=$pos time=$time")
    }

    private fun readTag(raw: ByteArray, p0: Int): Triple<String?, Int, Int> {
        var p = p0
        val l = u32(raw, p); p += 4
        val s = String(raw, p, 2 * l, Charsets.UTF_16LE)
        p = align4(p + 2 * l + 2)
        val uid = u32(raw, p); p += 4
        return Triple(s, p, uid)
    }

    private fun skipTag(raw: ByteArray, p0: Int): Int {
        var p = p0
        val l = u32(raw, p); p += 4
        p = align4(p + 2 * l + 2)
        return p + 4
    }

    private fun align4(p: Int) = (p + 3) and 3.inv()
    private fun u32(b: ByteArray, p: Int) =
        (b[p].toInt() and 0xFF) or ((b[p+1].toInt() and 0xFF) shl 8) or
        ((b[p+2].toInt() and 0xFF) shl 16) or ((b[p+3].toInt() and 0xFF) shl 24)
    private fun u64(b: ByteArray, p: Int): Long {
        var v = 0L; for (i in 7 downTo 0) v = (v shl 8) or (b[p+i].toLong() and 0xFF); return v
    }
    private fun Int.toHex() = Integer.toHexString(this)
    private fun hex(b: ByteArray, p: Int, n: Int) =
        (p until minOf(p + n, b.size)).joinToString(" ") { "%02x".format(b[it]) }
}
