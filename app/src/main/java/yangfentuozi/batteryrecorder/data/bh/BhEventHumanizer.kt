package yangfentuozi.batteryrecorder.data.bh

import android.content.Context

/**
 * .bh 原始事件 → 人类可读文本的翻译层。
 *
 * 设计原则：
 * 1. 标题（title）= 严谨译文；含义不明确的事件一律原样显示；
 *    原始串（detail）作为小字灰色备注保留，供核对；
 * 2. 不输出任何建议类、推测类文案，只陈述事件本身；
 * 3. 传感器事件本身对用户无意义，默认折叠为一行统计；
 * 4. 包名通过 PackageManager 解析为应用名（微信/QQ/...），解析不到就显示包名。
 *
 * 纯字符串映射，无 UI 依赖，供统计（[BhRangeStats]）与视图层共用。
 */
object BhEventHumanizer {

    /** 一条人类可读事件（已合并重复）。 */
    data class Human(
        val title: String,
        /** 原始事件串（小字备注）；与标题相同的纯未知事件为 null。 */
        val detail: String?,
        val category: BhHistoryParser.BhCategory,
        /** 合并的重复次数。 */
        val count: Int = 1
    )

    // ---- 关键词字典（按顺序匹配，越具体越靠前）----
    // 原则：只收录含义明确的词条（包名/类名/系统动作等）；含义不确定的事件一律原样显示。
    private val dictionary: List<Pair<String, String>> = listOf(
        // 特定定时器 / 心跳
        "com.xiaomi.push.PING_TIMER" to "小米推送心跳（维持消息连接）",
        "PowerInsightMinPeriodAlarm" to "省电统计定时器",
        "PowerInsightHourlyAlarm" to "省电统计小时任务",
        "CHECK_MUSIC_ACTIVE" to "音乐播放状态检查",
        "org.rcs.service.bfl.sip.heartbeat" to "5G 消息（RCS）网络心跳",
        "smart_doze_time_tick" to "息屏省电计时",
        "MiuiTimeoutCoordinator" to "系统超时协调",
        "MiuiDozeScreenBrightnessController" to "息屏亮度控制",
        "AODUpdatePositionController" to "息屏显示（AOD）刷新",
        "SmartOffAlarmTimeout" to "AOD 关闭定时",
        "ActivityManager-Sleep" to "系统进入睡眠（ActivityManager）",
        // 唤醒锁 / 特殊进程
        "MSF:WakeLock:MsgPush" to "QQ 消息推送唤醒",
        "MSF:WakeLock:Alarm" to "QQ 定时保活唤醒",
        "MicroMsg:ReentrantGuard" to "微信网络栈活动",
        "MicroMsg.PlatformCommC2JavaCallBack" to "微信平台通信回调",
        "MicroMsg.SyncService" to "微信同步服务",
        "QcrilOemhookMsgTunnel" to "基带通信（oemhook）",
        "DhcpClient" to "网络地址续约（DHCP）",
        "AudioSpatial" to "空间音频",
        "Checkin Service" to "系统遥测上报",
        // 蓝牙 / 系统交互
        "android.bluetooth.adapter.action.DISCOVERY_STARTED" to "蓝牙开始扫描",
        "android.bluetooth.device.action.FOUND" to "发现蓝牙设备",
        "android.bluetooth.device.action.ACL_DISCONNECTED" to "蓝牙设备断开",
        "android.bluetooth.input.profile.action.CONNECTION_STATE_CHANGED" to "蓝牙输入设备连接变化",
        "android.policy:FINGERPRINT" to "指纹解锁",
        "android.policy:POWER" to "电源键",
        "PhoneWindowManager.mPowerKeyWakeLock" to "电源键唤醒锁",
        "pm8xxx_rtc_alarm" to "RTC 硬件闹钟",
        // 定时器 / 闹钟类（关键词）
        "ALARM_ACTION" to "应用定时唤醒",
        "TIME_TICK" to "系统时间滴答",
        "GnssLocationProvider" to "定位服务",
        "FoldCoordinator" to "折叠屏状态",
        // 通用活动（字面翻译，不做行为解读）
        "wakelock-change" to "唤醒锁状态变化",
        "procstate-change" to "进程状态变化",
        "screen" to "屏幕",
        "audio" to "音频",
        "video" to "视频",
        "camera" to "相机",
        "bluetooth" to "蓝牙",
        // 5G 信号档位
        "NR MMWAVE" to "5G 信号：毫米波",
        "NR HIGH" to "5G 信号：强",
        "NR MID" to "5G 信号：中",
        "NR LOW" to "5G 信号：弱",
        // 系统前缀
        "*job*r/" to "系统定时任务",
        "*walarm*" to "系统闹钟（wall clock）",
        "*alarm*" to "系统闹钟",
        "*gms_scheduler*" to "Google 服务定时",
        "*location*" to "定位服务",
        "DozeService" to "息屏省电（Doze）",
        "dream:dream" to "息屏屏保",
        "Doze" to "息屏省电（Doze）",
    )

    /** 唤醒源名称翻译（Abort 事件用，仅保留含义明确的词条）。 */
    private val wakeupSourceNames = mapOf(
        "timerfd" to "定时器",
        "smp2p-sleepstate" to "协处理器休眠状态",
        "wlan" to "WiFi",
    )

    /**
     * 翻译单个事件。
     *
     * @param labelOf 包名 → 应用名解析器（可返回 null，退回包名）。
     */
    /** humanize 的记忆化：同一原始串（大量重复事件）只翻译一次，列表滚动才不卡。 */
    private val memo = object : LinkedHashMap<String, Human>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Human>?): Boolean =
            size > 8192
    }

    fun humanize(text: String, labelOf: (String) -> String? = { null }): Human {
        synchronized(memo) { memo[text]?.let { return it } }
        val result = humanizeUncached(text, labelOf)
        synchronized(memo) { memo[text] = result }
        return result
    }

    private fun humanizeUncached(text: String, labelOf: (String) -> String?): Human {
        // 1. 屏幕状态机
        if (text.startsWith("display=")) return screenEvent(text)

        // 2. 休眠被阻止类
        if (text.startsWith("Abort:")) return abortEvent(text)

        // 3. 剥离事件名前的哈希前缀与 /uN/0xNN 尾巴
        val stripped = text.replaceFirst(Regex("^[0-9a-f]{5,9} "), "")
        val uidNote = parseUidNote(text)
        val core = stripUidTail(stripped)

        // 4. 字典匹配（对去掉 uid 尾巴的主体做包含匹配）
        for ((key, title) in dictionary) {
            if (core.contains(key) || stripped.contains(key)) {
                val extra = buildDetail(text, uidNote)
                return Human(title, extra, BhHistoryParser.classify(text))
            }
        }

        // 5. 包名 → 应用名
        val pkg = findPackage(core)
        if (pkg != null) {
            val label = labelOf(pkg) ?: pkg
            return Human("应用活动：$label", buildDetail(text, uidNote), BhHistoryParser.classify(text))
        }

        // 6. 未知 → 原文当标题（保持可读性的最小值）
        val detail = if (text.contains(Regex("^[0-9a-f]{5,9} "))) text else null
        return Human(text, detail, BhHistoryParser.classify(text))
    }

    // ---- 内部工具 ----

    private fun screenEvent(text: String): Human {
        val state = Regex("state=(\\w+)").find(text)?.groupValues?.get(1) ?: "?"
        val reason = Regex("reason=([\\w_]+)").find(text)?.groupValues?.get(1) ?: "UNKNOWN"
        val title = when {
            state == "ON" && reason == "KEY" -> "屏幕亮起（电源键）"
            state == "ON" -> "屏幕亮起"
            state == "OFF" && reason == "DEFAULT_POLICY" -> "屏幕熄灭（超时）"
            state == "OFF" -> "屏幕熄灭"
            state == "DOZE" && reason == "DRAW_WAKE_LOCK" -> "息屏显示亮起（智能常亮）"
            state == "DOZE" -> "息屏显示"
            state == "DOZE_SUSPEND" -> "息屏显示深度休眠"
            else -> "屏幕状态：$state"
        }
        return Human(title, text, BhHistoryParser.BhCategory.SCREEN)
    }

    private fun abortEvent(text: String): Human {
        // Abort: Pending Wakeup Sources: [x] [y]
        val pending = Regex("Abort: Pending Wakeup Sources: (.+)$").find(text)
        if (pending != null) {
            val name = pending.groupValues[1]
            val translated = wakeupSourceNames.entries
                .firstOrNull { name.contains(it.key) }?.value
            val times = name.split("[").size - 1
            val what = translated ?: name
            val multi = if (times > 1) " ×$times" else ""
            return Human("休眠被阻止：$what$multi", text, BhHistoryParser.BhCategory.SUSPEND)
        }
        if (text.contains("refusing to freeze")) {
            return Human("有应用拒绝进入冻结，阻止了休眠", text, BhHistoryParser.BhCategory.SUSPEND)
        }
        if (text.contains("late suspend")) {
            val dev = Regex("late suspend of ([^ ]+) device").find(text)?.groupValues?.get(1)
            return Human("设备延迟挂起失败${dev?.let { "：$it" } ?: ""}", text, BhHistoryParser.BhCategory.SUSPEND)
        }
        if (text.contains("failed to suspend")) {
            val dev = Regex("Device ([^ ]+) failed").find(text)?.groupValues?.get(1)
            val err = Regex("error (-?\\d+)").find(text)?.groupValues?.get(1)
            val why = if (err == "-16") "（设备忙）" else err?.let { "（error $it）" } ?: ""
            return Human("设备挂起失败${dev?.let { "：$it" } ?: ""}$why", text, BhHistoryParser.BhCategory.SUSPEND)
        }
        if (text.contains("Last active Wakeup Source")) {
            val name = Regex("Last active Wakeup Source: (.+)$").find(text)?.groupValues?.get(1) ?: ""
            val translated = wakeupSourceNames.entries.firstOrNull { name.contains(it.key) }?.value
            return Human("休眠前最后活跃唤醒源：${translated ?: name}", text, BhHistoryParser.BhCategory.SUSPEND)
        }
        return Human(text.removePrefix("Abort: "), text, BhHistoryParser.BhCategory.SUSPEND)
    }

    private fun parseUidNote(text: String): String? {
        val uid = Regex("/u(\\d+)/0x").find(text)?.groupValues?.get(1) ?: return null
        return when (uid) {
            "0" -> null
            "999" -> "分身空间"
            "10", "11", "12" -> "第二用户"
            else -> "用户 $uid"
        }
    }

    private fun stripUidTail(s: String): String = s.replace(Regex("/u\\d+/0x[0-9a-fA-F]+$"), "").trim()

    private fun buildDetail(raw: String, uidNote: String?): String =
        if (uidNote != null) "$raw（$uidNote）" else raw

    /** 从事件里找出最像包名的片段。 */
    private fun findPackage(core: String): String? {
        val m = Regex("([a-zA-Z][\\w]*(?:\\.[\\w]+){1,6})").find(core) ?: return null
        val candidate = m.groupValues[1]
        // 过滤明显非包名（如 android.intent.action.XXX 动作串、含大写动作）
        if (candidate.contains("intent") && candidate.contains("action")) return null
        return candidate
    }
}

/** 构建「包名 → 应用名」解析器（带缓存，解析不到返回 null）。 */
fun buildPackageLabelResolver(context: Context): (String) -> String? {
    val cache = HashMap<String, String?>()
    val pm = context.packageManager
    return { pkg ->
        cache.getOrPut(pkg) {
            try {
                val info = pm.getApplicationInfo(pkg, 0)
                pm.getApplicationLabel(info).toString()
            } catch (_: Throwable) {
                null
            }
        }
    }
}
