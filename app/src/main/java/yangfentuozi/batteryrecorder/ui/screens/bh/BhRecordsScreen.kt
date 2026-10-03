package yangfentuozi.batteryrecorder.ui.screens.bh

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import yangfentuozi.batteryrecorder.data.bh.BhEventDb
import yangfentuozi.batteryrecorder.data.bh.BhEventHumanizer
import yangfentuozi.batteryrecorder.data.bh.BhHistoryParser
import yangfentuozi.batteryrecorder.data.bh.BhRangeStats
import yangfentuozi.batteryrecorder.data.bh.BhSyncManager
import yangfentuozi.batteryrecorder.data.bh.BhTimeline
import yangfentuozi.batteryrecorder.data.bh.buildPackageLabelResolver
import yangfentuozi.batteryrecorder.shared.util.LoggerX
import yangfentuozi.batteryrecorder.ui.components.global.SplicedColumnGroup
import yangfentuozi.batteryrecorder.ui.components.global.StatRow
import yangfentuozi.batteryrecorder.ui.theme.AppShape
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 全量电池事件记录（入口 A：首页底部）。
 *
 * - 数据来自 App 内部 SQLite（静默增量同步，按时间轴排序，不保留原始文件分段）；
 * - 默认按日期分类：每天一张卡（事件数/起止时间），点选后在小时级筛选；
 * - 筛选结果 = 统计结果 + 详细信息（详细信息支持按息屏段/小时/固定分钟重新分割）；
 * - 也可由充放电记录详情（入口 B）带入记录时间窗直接查看。
 *
 * 性能约定：查询、统计、明细构建全部在后台线程完成；同步不阻塞首屏
 * （先用库里已有数据渲染，同步完成且确有新增时再刷新）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BhRecordsScreen(
    onNavigateBack: () -> Unit,
    initialStartMs: Long = 0L,
    initialEndMs: Long = 0L
) {
    val context = LocalContext.current
    val db = remember(context) { BhEventDb.get(context) }
    val labelOf = remember(context) { buildPackageLabelResolver(context) }

    var syncing by remember { mutableStateOf(true) }
    var syncText by remember { mutableStateOf("正在同步…") }
    var days by remember { mutableStateOf<List<BhEventDb.DayStat>>(emptyList()) }
    var filterDay by remember { mutableStateOf<Long?>(null) }
    var fromHour by remember { mutableStateOf(0) }
    var toHour by remember { mutableStateOf(23) }
    var segMode by remember { mutableStateOf(BhTimeline.SegmentMode.SCREEN_OFF) }
    var expanded by remember { mutableStateOf<Set<Int>>(emptySet()) }

    var reloadTick by remember { mutableStateOf(0) }
    var dataTick by remember { mutableStateOf(0) }

    // 查询结果：全部在后台线程算好再交给 UI
    var loading by remember { mutableStateOf(false) }
    var stats by remember { mutableStateOf<BhRangeStats.Stats?>(null) }
    var items by remember { mutableStateOf<List<BhTimeline.Item>>(emptyList()) }

    // 固定窗口模式：由充放电记录详情带入固定时间窗（入口 B），
    // 不提供日期/小时筛选，只保留详细信息段的重新分割。
    val fixedWindow = initialStartMs > 0 && initialEndMs > initialStartMs

    val activeStart = if (fixedWindow) initialStartMs else filterDay?.let { it + fromHour * 3600_000L }
    val activeEnd = if (fixedWindow) {
        initialEndMs
    } else {
        filterDay?.let { it + toHour * 3600_000L + 3_599_999L }
    }

    // 首屏：浏览模式先渲染库里已有日期分类；固定窗口模式直接使用传入时间窗
    LaunchedEffect(Unit) {
        if (fixedWindow) return@LaunchedEffect
        days = withContext(Dispatchers.IO) {
            runCatching { db.dayStats() }.getOrDefault(emptyList())
        }
    }

    // 静默增量同步：不阻塞首屏；确有新文件入库时才刷新列表与当前查询
    LaunchedEffect(reloadTick) {
        syncing = true
        syncText = "正在同步…"
        val startedAt = System.currentTimeMillis()
        val result = withContext(Dispatchers.IO) {
            runCatching { BhSyncManager.sync(context, force = reloadTick > 0) }.getOrNull()
        }
        LoggerX.i(
            "BhRecords",
            "[同步] 耗时 ${System.currentTimeMillis() - startedAt}ms · " +
                    (result?.let { "扫描 ${it.scanned} · 新增 ${it.imported} · 跳过 ${it.skipped} · 共 ${it.totalEvents} 事件" } ?: "失败")
        )
        syncText = if (result == null) {
            "同步失败（数据源不可用）"
        } else {
            "已入库 ${result.totalEvents} 个事件 · 本次新增/更新 ${result.imported} 个文件 · 跳过 ${result.skipped}"
        }
        syncing = false
        if (result != null && (result.imported > 0 || reloadTick > 0)) {
            if (!fixedWindow) {
                days = withContext(Dispatchers.IO) {
                    runCatching { db.dayStats() }.getOrDefault(emptyList())
                }
            }
            dataTick++
        }
    }

    // 筛选变化 → 后台查询 + 统计 + 明细（绝不放在组合/主线程里）
    LaunchedEffect(activeStart, activeEnd, dataTick) {
        val s = activeStart
        val e = activeEnd
        if (s == null || e == null) {
            loading = false
            stats = null
            items = emptyList()
            return@LaunchedEffect
        }
        loading = true
        val startedAt = System.currentTimeMillis()
        val computed = withContext(Dispatchers.Default) {
            val rows = runCatching { db.queryRange(s, e) }.getOrDefault(emptyList())
            val groups = runCatching { db.textGroups(s, e) }.getOrDefault(emptyList())
            val st = runCatching { BhRangeStats.stats(groups, labelOf) }.getOrNull()
            val list = runCatching { BhRangeStats.toItems(rows) }.getOrDefault(emptyList())
            Triple(st, list, rows.size to groups.size)
        }
        LoggerX.i(
            "BhRecords",
            "[查询] 事件 ${computed.third.first} · 文本组 ${computed.third.second} · 耗时 ${System.currentTimeMillis() - startedAt}ms"
        )
        stats = computed.first
        items = computed.second
        loading = false
    }

    val segments = remember(items, segMode) { BhTimeline.segment(items, segMode) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("电池事件记录") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { reloadTick++ }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "同步")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    if (syncing) syncText else "$syncText · 数据存于 App 内部数据库",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                // 时间段筛选（日期 + 起止小时，小时级）；固定窗口模式时间由入口传入，不提供筛选
                if (!fixedWindow) {
                    FilterBar(
                        days = days,
                        filterDay = filterDay,
                        fromHour = fromHour,
                        toHour = toHour,
                        onDayChange = { filterDay = it },
                        onFromChange = { fromHour = it.coerceAtMost(toHour) },
                        onToChange = { toHour = it.coerceAtLeast(fromHour) }
                    )
                }
            }

            val s = activeStart
            val e = activeEnd
            if (s == null || e == null) {
                // 默认视图：按日期分类
                item {
                    Text(
                        "按日期分类（点击某天查看该天记录；也可用上方筛选选择日期与小时段）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                itemsIndexed(days) { _, day ->
                    DayCard(day, onClick = {
                        filterDay = day.dayStartMs
                        fromHour = 0
                        toHour = 23
                    })
                }
                if (days.isEmpty() && !syncing) {
                    item { Text("暂无数据", style = MaterialTheme.typography.bodySmall) }
                }
            } else {
                val st = stats
                if (st == null) {
                    item {
                        Text(
                            if (loading) "载入中…" else "该时间段暂无事件",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    item {
                        SplicedColumnGroup(title = "统计结果（" + rangeLabel(s, e) + "）") {
                            item {
                                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                    StatRow("事件总数", "${st.total} 个（精确 ${st.exact} · 近似 ${st.approx}）")
                                    StatRow("屏幕", "亮屏 ${st.screenOn} · 熄屏 ${st.screenOff}" + if (st.dozeOn > 0) " · 息屏显示 ${st.dozeOn}" else "")
                                    StatRow("休眠被阻止", "${st.abortTotal} 次")
                                    StatRow("后台唤醒", "${st.wakeTotal} 次")
                                    if (st.sensorCount > 0) StatRow("传感器活动", "${st.sensorCount} 次")
                                }
                            }
                        }
                    }
                    if (st.abortRank.isNotEmpty()) {
                        item { BhRankGroup("休眠被谁阻止", st.abortRank, " 次", 6) }
                    }
                    if (st.wakeRank.isNotEmpty()) {
                        item { BhRankGroup("后台唤醒排行", st.wakeRank, " 次", 8) }
                    }
                    item {
                        // 标题对齐 SplicedColumnGroup 的组标题（内容边距 16dp + 标题 start 16dp）
                        Text(
                            "详细信息（分段方式）",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                        Spacer(Modifier.height(6.dp))
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            BhTimeline.SegmentMode.entries.forEach { mode ->
                                SelectChip(
                                    text = mode.label,
                                    selected = segMode == mode,
                                    showArrow = false,
                                    onClick = {
                                        segMode = mode
                                        expanded = emptySet()
                                    }
                                )
                            }
                        }
                    }
                    itemsIndexed(segments) { idx, seg ->
                        val isExpanded = idx in expanded
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceBright),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth().clickable {
                                expanded = if (isExpanded) expanded - idx else expanded + idx
                            }
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Text(seg.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "${seg.items.size} 个事件 · 唤醒 ${seg.wakeCount} · 挂起失败 ${seg.abortCount}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                val shown = if (isExpanded) seg.items else seg.items.take(8)
                                Spacer(Modifier.height(6.dp))
                                shown.forEach { item ->
                                    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                                        Text(
                                            BhTimeline.formatTime(item.wallMs) + (if (item.approximate) "≈" else " "),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.width(64.dp)
                                        )
                                        Text(
                                            BhEventHumanizer.humanize(item.rawText, labelOf).title,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (item.category == BhHistoryParser.BhCategory.SUSPEND)
                                                MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                                if (seg.items.size > shown.size) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "点击展开其余 ${seg.items.size - shown.size} 条",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterBar(
    days: List<BhEventDb.DayStat>,
    filterDay: Long?,
    fromHour: Int,
    toHour: Int,
    onDayChange: (Long?) -> Unit,
    onFromChange: (Int) -> Unit,
    onToChange: (Int) -> Unit
) {
    var dayMenu by remember { mutableStateOf(false) }
    var fromMenu by remember { mutableStateOf(false) }
    var toMenu by remember { mutableStateOf(false) }
    val hoursEnabled = filterDay != null

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box {
            SelectChip(
                text = filterDay?.let { dayLabel(it) } ?: "选择日期",
                selected = filterDay != null,
                onClick = { dayMenu = true }
            )
            DropdownMenu(
                expanded = dayMenu,
                onDismissRequest = { dayMenu = false },
                shape = AppShape.large
            ) {
                DropdownMenuItem(text = { Text("全部日期") }, onClick = { onDayChange(null); dayMenu = false })
                days.forEach { d ->
                    DropdownMenuItem(text = { Text(dayLabel(d.dayStartMs)) }, onClick = { onDayChange(d.dayStartMs); dayMenu = false })
                }
            }
        }
        Box {
            SelectChip(
                text = "从 %02d 时".format(fromHour),
                selected = hoursEnabled,
                enabled = hoursEnabled,
                onClick = { fromMenu = true }
            )
            DropdownMenu(
                expanded = fromMenu,
                onDismissRequest = { fromMenu = false },
                shape = AppShape.large
            ) {
                (0..23).forEach { h ->
                    DropdownMenuItem(text = { Text("%02d 时".format(h)) }, onClick = { onFromChange(h); fromMenu = false })
                }
            }
        }
        Box {
            SelectChip(
                text = "到 %02d 时".format(toHour),
                selected = hoursEnabled,
                enabled = hoursEnabled,
                onClick = { toMenu = true }
            )
            DropdownMenu(
                expanded = toMenu,
                onDismissRequest = { toMenu = false },
                shape = AppShape.large
            ) {
                (0..23).forEach { h ->
                    DropdownMenuItem(text = { Text("%02d 时".format(h)) }, onClick = { onToChange(h); toMenu = false })
                }
            }
        }
        if (filterDay != null) {
            SelectChip(
                text = "清除",
                selected = false,
                showArrow = false,
                onClick = {
                    onDayChange(null)
                    onFromChange(0)
                    onToChange(23)
                }
            )
        }
    }
}

/** App 原生风格的可点选胶囊（与充放电记录筛选栏一致）：描边区分、选中主色、下拉箭头提示。 */
@Composable
private fun SelectChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
    showArrow: Boolean = true
) {
    val shape = AppShape.medium
    val borderColor = when {
        selected && enabled -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    val contentColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .clip(shape)
            .border(
                width = if (selected && enabled) 1.5.dp else 1.dp,
                color = borderColor,
                shape = shape
            )
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(
                start = 14.dp,
                end = if (showArrow) 8.dp else 14.dp,
                top = 8.dp,
                bottom = 8.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            color = contentColor,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1
        )
        if (showArrow) {
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun DayCard(day: BhEventDb.DayStat, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceBright),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(dayLabel(day.dayStartMs), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(
                "${day.count} 个事件 · " + timeShort(day.firstMs) + " ~ " + timeShort(day.lastMs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private val dayFmt = SimpleDateFormat("MM-dd EEEE", Locale.getDefault())
private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())

private fun dayLabel(ms: Long): String = dayFmt.format(Date(ms))
private fun timeShort(ms: Long): String = timeFmt.format(Date(ms))
private fun rangeLabel(start: Long, end: Long): String =
    dayFmt.format(Date(start)) + " " + timeShort(start) + " ~ " + timeShort(end)
