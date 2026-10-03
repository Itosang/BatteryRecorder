package yangfentuozi.batteryrecorder.data.bh

import java.io.File

/**
 * .bh 深度解析器 v3：解码 AOSP BatteryStatsHistory delta 格式（含精确时间戳）。
 *
 * 已破解的格式（Redmi K90 / HyperOS，对照 AOSP android-36 BatteryStatsHistory 验证）：
 *
 * 文件 = "GZIP" 8 字节头 + gzip 流；解压后 32 字节头：
 *   [u32 记录数][u32 起始uptime_ms][u32 0][u32 结束uptime_ms][u32 0][u32 常量][u32 0][u32 数据长]
 *
 * 记录流（Parcel 字节流，字符串 4 字节对齐）：
 *   firstToken(u32)：
 *     bit0-18  时间增量 dt（0x7FFFD=后跟 ABS 整条记录；0x7FFFE=后跟 int 增量；0x7FFFF=后跟 long 增量）
 *     bit19 BATT：后跟 batteryLevelInt(+溢出扩展)
 *     bit20 STATE：后跟 stateInt（电池状态/健康/充电器 + 高位 states）
 *     bit21 STATE2：后跟 states2（bit17=扩展块标志）
 *     bit22 WAKELOCK：后跟 索引对（0x8000 标志=内联 tag：string16+uid）
 *     bit23 EVENT：后跟 codeAndIndex（高16位索引；0x8000=内联 tag）
 *     bit24 CHARGE：后跟 int
 *     随后恒有 modemRail(double) + wifiRail(double)
 *     若 states2 bit17：扩展块 [extFlags][descriptor][powerStats][processStateChange]
 *
 * ABS 记录（firstToken==0x7FFFD）：完整 HistoryItem：
 *   time(u64) bat(u32) bat2(u32) charge(u32) modemRail(f64) wifiRail(f64) states(u32) states2(u32)
 *   [内联 wakelock tag][内联 wakeReason tag][事件 int + 内联 tag]（cmd=5/7 时再跟 u64）
 *
 * 字符串编码：string16 = [i32 字符数][utf16le][u16 NUL][对齐4]；
 *             string8  = [i32 字节数][utf8][u8 NUL][对齐4]；内联 tag = string16 + u32 uid
 */
object BhParserV3 {

    /** 调试开关：解析异常/重同步位置输出到 stdout（单元测试用）。 */
    const val DEBUG_TRACE = false

    /** 解码后的事件（带精确时间）。 */
    data class Event(
        val timeMs: Long,
        val code: Int,
        val text: String?,
        val uid: Int,
        val level: Int?,
        val category: BhHistoryParser.BhCategory,
    )

    /** 解码后的记录（轻量：只保留时间与事件）。 */
    data class Decoded(
        val records: Int,
        /** 头部第一个字段：标签池计数（不是记录数）。 */
        val tagPoolHint: Int,
        val startUptimeMs: Long,
        val endUptimeMs: Long,
        val lastTimeMs: Long,
        val events: List<Event>,
        val resyncCount: Int
    )

    private class Reader(val data: ByteArray) {
        var pos = 0
        fun u32(): Int {
            require(pos + 4 <= data.size) { "u32@$pos" }
            val v = (data[pos].toInt() and 0xFF) or
                    ((data[pos + 1].toInt() and 0xFF) shl 8) or
                    ((data[pos + 2].toInt() and 0xFF) shl 16) or
                    ((data[pos + 3].toInt() and 0xFF) shl 24)
            pos += 4
            return v
        }
        fun u64(): Long {
            require(pos + 8 <= data.size) { "u64@$pos" }
            var v = 0L
            for (i in 7 downTo 0) v = (v shl 8) or (data[pos + i].toLong() and 0xFF)
            pos += 8
            return v
        }
        fun skip(n: Int) {
            require(pos + n <= data.size) { "skip@$pos" }
            pos += n
        }
        fun align4() { pos = (pos + 3) and 3.inv() }

        /** string16；返回 null 表示 -1。 */
        fun str16(): String? {
            val l = u32()
            if (l < 0) return null
            require(l <= 4000 && pos + 2 * l + 2 <= data.size) { "s16($l)@$pos" }
            val s = String(data, pos, 2 * l, Charsets.UTF_16LE)
            pos += 2 * l + 2
            align4()
            return s
        }

        /** string8（statSubsystemPowerState 等）。 */
        fun str8(): String? {
            val l = u32()
            if (l < 0) return null
            require(l <= 4000 && pos + l + 1 <= data.size) { "s8($l)@$pos" }
            val s = String(data, pos, l, Charsets.UTF_8)
            pos += l + 1
            align4()
            return s
        }

        /** 校验收到的 string16 看起来像可读文本（防错位）。 */
        fun looksStr16(): Boolean {
            if (pos + 4 > data.size) return false
            val save = pos
            val l = u32()
            pos = save
            if (l < 1 || l > 1000 || pos + 4 + 2 * l + 2 > data.size) return false
            var i = pos + 4
            var k = 0
            while (k < l) {
                val hi = data[i + 1].toInt()
                val lo = data[i].toInt() and 0xFF
                if (hi != 0 || lo < 32 || lo >= 127) return false
                i += 2
                k++
            }
            return true
        }

        /** 读取内联 tag：string16 + u32 uid。 */
        fun inlineTag(): Pair<String?, Int>? {
            if (!looksStr16()) return null
            val s = str16() ?: return null
            val uid = u32()
            return s to uid
        }

        /**
         * 跳过一段 Bundle（BNDL）序列化数据。
         *
         * 结构（实测）：每项 = [i32 键长][string16 键][i32 值类型][值]；
         * 键长为 0 即为终止符。嵌套 MAP/BUNDLE/LIST 递归处理。
         * 值类型表（android.os.Parcel VAL_*）：
         *   -1 null, 0 string, 1 integer, 2 map, 3 bundle, 4 parcelable, 5 short, 6 long,
         *   7 float, 8 double, 9 boolean, 10 charsequence, 11 list, 12 sparsearray,
         *   13 bytearray, 14 stringarray, 15 intarray, 16 longarray, 17 booleanarray,
         *   18 objectarray, 19 bundlearray, 20 ibinder, 21 parcelablearray, ...
         */
        fun skipBundle() {
            require(data[pos] == 'B'.code.toByte() && data[pos + 1] == 'N'.code.toByte() &&
                    data[pos + 2] == 'D'.code.toByte() && data[pos + 3] == 'L'.code.toByte()) {
                "BNDL@$pos"
            }
            pos += 4
            u32() // bundle 协议版本
            skipBundleEntries(0)
        }

        private fun skipBundleEntries(depth: Int) {
            require(depth <= 8) { "bundle depth" }
            while (true) {
                val keyLen = u32()
                if (keyLen <= 0) return // 0（或负数）为终止符
                require(keyLen <= 400) { "bundle key $keyLen@$pos" }
                pos += 2 * keyLen + 2
                align4()
                skipValue(depth)
            }
        }

        private fun skipValue(depth: Int) {
            val t = u32()
            when (t) {
                -1 -> {}
                0, 10 -> { // string / charsequence(近似按 string 处理)
                    val l = u32()
                    if (l >= 0) { pos += 2 * l + 2; align4() }
                }
                1, 12 -> u32() // integer（sparsearray 罕见，按 int 保守处理）
                2 -> { // map：嵌套键值对
                    val n = u32()
                    require(n in 0..10000) { "map $n@$pos" }
                    repeat(n) {
                        val kl = u32()
                        require(kl in 0..400) { "map key $kl@$pos" }
                        pos += 2 * kl + 2
                        align4()
                        skipValue(depth + 1)
                    }
                }
                3 -> { // bundle
                    val l = u32()
                    if (l >= 0) { pos += l }
                }
                4 -> { // parcelable：类型名 string16 + 长度前缀数据（保守：仅跳名称）
                    str16()
                    // 具体数据无法安全跳过，交由外层重同步兜底
                    throw IllegalStateException("parcelable in bundle")
                }
                5 -> pos += 2
                6, 8 -> pos += 8
                7 -> pos += 4
                9 -> pos += 4
                11 -> { // list
                    val n = u32()
                    require(n in 0..10000) { "list $n@$pos" }
                    repeat(n) { skipValue(depth + 1) }
                }
                13, 15, 16, 17 -> { // 基本类型数组
                    val n = u32()
                    val bytes = when (t) {
                        13 -> 1
                        15, 17 -> 4
                        else -> 8
                    }
                    if (n >= 0) { pos += n * bytes }
                }
                14 -> { // stringarray
                    val n = u32()
                    require(n in 0..10000) { "sarr $n@$pos" }
                    repeat(n) {
                        val l = u32()
                        if (l >= 0) { pos += 2 * l + 2; align4() }
                    }
                }
                18, 19, 21 -> throw IllegalStateException("object array in bundle")
                20 -> {
                    pos += 4
                    val l = u32()
                    if (l >= 0) pos += l
                }
                else -> throw IllegalStateException("bundle value type $t@$pos")
            }
        }
    }

    /** 解析单个 .bh 字节。 */
    fun decode(bytes: ByteArray): Decoded? {
        val gz = bytes.indexOf(0x1F.toByte())
        if (gz < 0) return null
        val raw = try {
            java.util.zip.GZIPInputStream(bytes.inputStream(gz, bytes.size - gz)).use { it.readBytes() }
        } catch (t: Throwable) {
            return null
        }
        if (raw.size < 36) return null
        val r = Reader(raw)
        r.pos = 0
        val tagPoolHint = r.u32()
        val startUp = r.u32().toLong() and 0xFFFFFFFFL
        r.u32()
        val endUp = r.u32().toLong() and 0xFFFFFFFFL
        r.pos = 32

        var time = 0L
        var level = 0
        var resyncs = 0
        val events = ArrayList<Event>()
        var records = 0
        var lastTime = 0L

        fun parseRecord(from: Int, baseTime: Long): Triple<Int, Long, Event?> {
            r.pos = from
            var t = baseTime
            var ev: Event? = null
            val tok = r.u32()
            val dt = tok and 0x7FFFF
            if (dt == 0x7FFFD) {
                t = r.u64()
                val bat = r.u32()
                val cmd = bat and 0xFF
                level = (bat ushr 8) and 0xFF
                r.skip(4 + 4 + 16 + 4 + 4) // bat2, charge, rails, states, states2
                if (bat and 0x10000000 != 0) r.inlineTag()
                if (bat and 0x20000000 != 0) r.inlineTag()
                if (bat and 0x40000000 != 0) {
                    val code = r.u32()
                    val tag = if (r.looksStr16()) r.inlineTag() else null
                    if (tag != null) {
                        ev = Event(t, code, tag.first, tag.second, level, BhHistoryParser.classify(tag.first!!))
                    }
                }
                if (cmd == 5 || cmd == 7) r.skip(8)
                return Triple(r.pos, t, ev)
            }
            if (dt == 0x7FFFE) t += r.u32()
            else if (dt == 0x7FFFF) t += r.u64()
            else t += dt
            if (tok and 0x80000 != 0) {
                val bi = r.u32()
                if (bi and 2 != 0) r.u32()
                if (bi and 1 != 0) {
                    r.skip(17 * 4)
                    r.str8()
                }
            }
            var states2 = 0
            if (tok and 0x100000 != 0) r.u32()
            if (tok and 0x200000 != 0) states2 = r.u32()
            if (tok and 0x400000 != 0) {
                val idxs = r.u32()
                if ((idxs and 0xFFFF) != 0xFFFF && ((idxs and 0xFFFF) and 0x8000) != 0) r.inlineTag()
                val hi = (idxs ushr 16) and 0xFFFF
                if (hi != 0xFFFF && (hi and 0x8000) != 0) r.inlineTag()
            }
            if (tok and 0x800000 != 0) {
                val ci = r.u32()
                val code = ci and 0xFFFF
                val idx = (ci ushr 16) and 0xFFFF
                if (idx != 0xFFFF && (idx and 0x8000) != 0) {
                    val tag = if (r.looksStr16()) r.inlineTag() else null
                    if (tag != null) {
                        ev = Event(t, code, tag.first, tag.second, level, BhHistoryParser.classify(tag.first!!))
                    }
                }
            }
            if (tok and 0x1000000 != 0) r.u32()
            r.skip(16)
            // 扩展块：不完整解码，标记为负 states2 供上层重同步
            if (states2 and (1 shl 17) != 0) throw ExtensionBlock(r.pos)
            return Triple(r.pos, t, ev)
        }

        /**
         * 结构化跳过一个扩展块（descriptor / PowerStats / ProcessStateChange）。
         * @return 跳过后的 (位置, 时间) ；无法解析返回 null。
         */
        fun structuredSkipExtension(from: Int, baseTime: Long, depth: Int = 0): Pair<Int, Long>? {
            if (depth > 4) return null
            return try {
                val q = Reader(raw).also { it.pos = from }
                val extf = q.u32()
                if (extf and 1 != 0) {
                    q.u32() // firstWord
                    q.u32() // powerComponentId
                    q.str16() // name
                    val nl = q.u32()
                    repeat(nl) { q.u32(); q.str16() }
                    q.u32() // extras 长度前缀（不可靠，自定界解析为准）
                    q.skipBundle()
                }
                if (extf and 2 != 0) {
                    val ln = q.u32()
                    require(ln in 4..raw.size) { "powerstats len $ln" }
                    q.skip(ln)
                }
                if (extf and 4 != 0) {
                    val bits = q.u32()
                    if (bits and 0x80000000.toInt() != 0) q.u32()
                }
                // 校验：此后 4 条记录（允许再遇到扩展块，递归跳过）须时间单调、窗口内
                var qq = q.pos
                var tt = baseTime
                var n = 0
                while (n < 4) {
                    try {
                        val res = parseRecord(qq, tt)
                        qq = res.first
                        tt = res.second
                    } catch (e: ExtensionBlock) {
                        // parseRecord 抛出时共享 Reader 的 pos 即扩展块起点
                        val jp = structuredSkipExtension(r.pos, tt, depth + 1) ?: return null
                        qq = jp.first
                        tt = jp.second
                    }
                    if (tt < startUp || tt > endUp + 120_000) return null
                    n++
                }
                q.pos to tt
            } catch (_: Throwable) {
                null
            }
        }

        while (r.pos + 4 <= raw.size) {
            val startPos = r.pos
            val rec = try {
                parseRecord(startPos, time)
            } catch (e: ExtensionBlock) {
                val jumped = structuredSkipExtension(r.pos, time)
                if (jumped != null) {
                    r.pos = jumped.first
                    time = jumped.second
                    records++
                    lastTime = time
                    resyncs++
                    continue
                }
                // 兜底：全文件重同步扫描
                var cand = -1
                var scan = r.pos
                val limit = raw.size - 40
                while (scan < limit) {
                    try {
                        var q = scan
                        var tt = time
                        var n = 0
                        while (n < 4) {
                            val res = parseRecord(q, tt)
                            q = res.first
                            tt = res.second
                            if (tt < startUp || tt > endUp + 120_000) throw IllegalStateException("oow")
                            n++
                        }
                        cand = scan
                        break
                    } catch (_: Throwable) {
                        scan += 1
                    }
                }
                if (cand < 0) break
                resyncs++
                val res = parseRecord(cand, time)
                r.pos = res.first
                time = res.second
                records++
                lastTime = time
                continue
            } catch (e: Throwable) {
                // 软恢复：任何解析异常都先尝试重同步，而不是直接放弃整个文件
                if (DEBUG_TRACE) println("V3 recover @$startPos rec=$records: ${e.javaClass.simpleName}: ${e.message}")
                val jumpedEx = structuredSkipExtension(startPos + 1, lastTime)
                if (jumpedEx != null) {
                    r.pos = jumpedEx.first
                    time = jumpedEx.second
                    records++
                    lastTime = time
                    resyncs++
                    continue
                }
                var candR = -1
                var scanR = startPos + 1
                while (scanR < raw.size - 40) {
                    try {
                        var q = scanR
                        var tt = lastTime
                        var n = 0
                        while (n < 4) {
                            val res = parseRecord(q, tt)
                            q = res.first
                            tt = res.second
                            if (tt < startUp || tt > endUp + 120_000) throw IllegalStateException("oow")
                            n++
                        }
                        candR = scanR
                        break
                    } catch (_: Throwable) {
                        scanR += 1
                    }
                }
                if (candR < 0) break
                resyncs++
                val resR = parseRecord(candR, lastTime)
                r.pos = resR.first
                time = resR.second
                records++
                lastTime = time
                continue
            }
            r.pos = rec.first
            time = rec.second
            if (time < startUp || time > endUp + 120_000) {
                // 时间越界 = 解体；交给下一轮兜底（把当前位置当作扩展块尝试结构化跳过）
                val jumped = structuredSkipExtension(startPos + 1, lastTime)
                if (jumped != null) {
                    r.pos = jumped.first
                    time = jumped.second
                    records++
                    lastTime = time
                    resyncs++
                    continue
                }
                break
            }
            records++
            lastTime = time
            rec.third?.let { events.add(it) }
            if (r.pos <= startPos) break
        }

        if (DEBUG_TRACE && lastTime != endUp) {
            println("V3 abnormal-end $records recs, last=$lastTime end=$endUp")
        }
        return Decoded(
            records = records,
            tagPoolHint = tagPoolHint,
            startUptimeMs = startUp,
            endUptimeMs = endUp,
            lastTimeMs = lastTime,
            events = events,
            resyncCount = resyncs
        )
    }

    private class ExtensionBlock(pos: Int) : RuntimeException("extension@$pos")

    fun decodeFile(file: File): Decoded? = try {
        decode(file.readBytes())
    } catch (t: Throwable) {
        null
    }
}

/**
 * 是否算"完整解码"：
 * 记录数 > 0 且最后一条记录时间落在会话结束 ±120s 内（末尾扩展块跳过失败会造成尾部缺失）。
 */
fun BhParserV3.Decoded.isComplete(): Boolean =
    records > 0 && lastTimeMs >= endUptimeMs - 120_000 && lastTimeMs <= endUptimeMs + 120_000
