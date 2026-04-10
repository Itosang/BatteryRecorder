package yangfentuozi.batteryrecorder.ui.screens.history

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Outbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import yangfentuozi.batteryrecorder.R
import yangfentuozi.batteryrecorder.shared.data.BatteryStatus
import yangfentuozi.batteryrecorder.shared.data.RecordsFile
import yangfentuozi.batteryrecorder.shared.util.LoggerX
import yangfentuozi.batteryrecorder.ui.components.global.SwipeRevealRow
import yangfentuozi.batteryrecorder.ui.theme.AppShape
import yangfentuozi.batteryrecorder.ui.viewmodel.HistoryViewModel
import yangfentuozi.batteryrecorder.ui.viewmodel.SettingsViewModel
import yangfentuozi.batteryrecorder.utils.batteryRecorderScaffoldInsets
import yangfentuozi.batteryrecorder.utils.formatDurationHours
import yangfentuozi.batteryrecorder.utils.formatFullDateTime
import yangfentuozi.batteryrecorder.utils.formatPower
import yangfentuozi.batteryrecorder.utils.navigationBarBottomPadding

private const val NEAR_END_PRELOAD_THRESHOLD = 5
private const val HISTORY_LIST_PREFS_NAME = "history_list"
private const val KEY_HISTORY_LIST_LAYOUT_STYLE = "layout_style"
private val CHARGE_CAPACITY_CHANGE_FILTERS = listOf(20, 40, 70)
private val ChargeHistoryFilterChipShape = AppShape.medium

private enum class HistoryListLayoutStyle {
    Classic,
    Emphasis;

    fun toggled(): HistoryListLayoutStyle {
        return if (this == Classic) Emphasis else Classic
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HistoryListScreen(
    batteryStatus: BatteryStatus,
    viewModel: HistoryViewModel = viewModel(),
    settingsViewModel: SettingsViewModel,
    onNavigateToRecordDetail: (BatteryStatus, String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    // 历史记录列表状态流（用于列表渲染）
    val records by viewModel.records.collectAsState()
    // 是否启用双电芯模式（影响功率显示换算）
    val dualCellEnabled by settingsViewModel.dualCellEnabled.collectAsState()
    // 电流校准值（用于修正功率与 mAh 显示结果）
    val calibrationValue by settingsViewModel.calibrationValue.collectAsState()
    // 一次性用户提示消息（如导出/删除结果提示）
    val userMessage by viewModel.userMessage.collectAsState()
    // 导入/导出期间的共享加载态，避免重复触发文件操作。
    val isImportExporting by viewModel.isImportExporting.collectAsState()
    // 当前是否正在分页加载（避免重复并发请求）
    val isPaging by viewModel.isPaging.collectAsState()
    // 是否还有更多历史记录可加载（用于触底预加载判断）
    val hasMoreRecords by viewModel.hasMoreRecords.collectAsState()
    // 充电历史变化量筛选阈值；null 表示不过滤。
    val chargeCapacityChangeFilter by viewModel.chargeCapacityChangeFilter.collectAsState()
    // 列表滚动状态（用于计算是否接近列表底部）
    val listState = rememberLazyListState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val historyListPrefs = remember(context) {
        context.getSharedPreferences(HISTORY_LIST_PREFS_NAME, Context.MODE_PRIVATE)
    }
    var openRecordName by remember { mutableStateOf<String?>(null) }
    // 历史列表样式只影响当前页面展示，因此使用页面本地偏好持久化，不进入全局业务设置。
    var layoutStyle by remember(historyListPrefs) {
        mutableStateOf(loadHistoryListLayoutStyle(historyListPrefs.getString(KEY_HISTORY_LIST_LAYOUT_STYLE, null)))
    }
    // CreateDocument 回调异步返回，这里暂存要导出的记录，避免回调时丢失上下文
    var pendingExportFile by remember { mutableStateOf<RecordsFile?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        val exportFile = pendingExportFile
        pendingExportFile = null
        if (uri != null && exportFile != null) {
            viewModel.exportRecord(context, exportFile, uri)
        }
    }
    val exportAllLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        LoggerX.i(
            "HistoryListScreen",
            "[导出] 批量导出 CreateDocument 回调: type=${batteryStatus.dataDirName} uri=$uri"
        )
        if (uri != null) {
            viewModel.exportAllRecords(context, batteryStatus, uri)
        }
    }
    val importAllLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        LoggerX.i(
            "HistoryListScreen",
            "[导入] 批量导入 OpenDocument 回调: type=${batteryStatus.dataDirName} uri=$uri"
        )
        if (uri != null) {
            viewModel.importAllRecords(context, batteryStatus, uri)
        }
    }

    LaunchedEffect(batteryStatus) {
        openRecordName = null
        viewModel.loadRecords(context, batteryStatus)
    }
    DisposableEffect(lifecycleOwner, batteryStatus, context, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event != Lifecycle.Event.ON_START) return@LifecycleEventObserver
            viewModel.loadRecords(context, batteryStatus)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    LaunchedEffect(userMessage) {
        val message = userMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        viewModel.consumeUserMessage()
    }
    // 触底预加载：
    // 1) 当最后可见项接近列表尾部（预留 5 条）时触发下一页；
    // 2) 由 hasMoreRecords/isPaging 双重约束避免无效请求与重复并发请求。
    LaunchedEffect(listState, batteryStatus, hasMoreRecords, isPaging) {
        snapshotFlow {
            val totalItems = listState.layoutInfo.totalItemsCount
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            // 预留少量阈值用于“未完全到底”时提前请求，减少等待空窗。
            totalItems > 0 && lastVisible >= totalItems - NEAR_END_PRELOAD_THRESHOLD
        }.collect { nearEnd ->
            if (!nearEnd || !hasMoreRecords || isPaging) return@collect
            viewModel.loadNextPage(context, batteryStatus)
        }
    }

    val title = if (batteryStatus == BatteryStatus.Charging) {
        stringResource(R.string.history_charging_title)
    } else {
        stringResource(R.string.history_discharging_title)
    }
    val emptyText = if (
        batteryStatus == BatteryStatus.Charging &&
        chargeCapacityChangeFilter != null
    ) {
        stringResource(R.string.history_empty_filtered)
    } else {
        stringResource(R.string.history_empty)
    }

    Scaffold(
        contentWindowInsets = batteryRecorderScaffoldInsets(),
        topBar = {
            TopAppBar(
                title = { Text(title) },
                actions = {
                    TextButton(
                        onClick = {
                            layoutStyle = layoutStyle.toggled()
                            historyListPrefs.edit {
                                putString(KEY_HISTORY_LIST_LAYOUT_STYLE, layoutStyle.name)
                            }
                        }
                    ) {
                        Text(
                            text = if (layoutStyle == HistoryListLayoutStyle.Classic) {
                                stringResource(R.string.history_layout_classic)
                            } else {
                                stringResource(R.string.history_layout_emphasis)
                            }
                        )
                    }
                    IconButton(
                        enabled = !isImportExporting,
                        onClick = {
                            LoggerX.i(
                                "HistoryListScreen",
                                "[导入] 点击批量导入: type=${batteryStatus.dataDirName}"
                            )
                            importAllLauncher.launch(
                                arrayOf("application/zip", "application/x-zip-compressed")
                            )
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Download,
                            contentDescription = stringResource(R.string.history_import)
                        )
                    }
                    IconButton(
                        enabled = !isImportExporting,
                        onClick = {
                            val fileName = buildHistoryZipFileName(batteryStatus)
                            LoggerX.i(
                                "HistoryListScreen",
                                "[导出] 点击批量导出: type=${batteryStatus.dataDirName} fileName=$fileName"
                            )
                            exportAllLauncher.launch(fileName)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Outbox,
                            contentDescription = stringResource(R.string.history_export_all)
                        )
                    }
                }
            )
        },
        bottomBar = {
            if (batteryStatus == BatteryStatus.Charging) {
                ChargeHistoryFilterBar(
                    selectedMinCapacityChange = chargeCapacityChangeFilter,
                    onSelectFilter = { minCapacityChange ->
                        val nextFilter = if (chargeCapacityChangeFilter == minCapacityChange) {
                            null
                        } else {
                            minCapacityChange
                        }
                        viewModel.updateChargeCapacityChangeFilter(context, nextFilter)
                    }
                )
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (records.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(16.dp)
                ) {
                    Text(
                        text = emptyText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    state = listState,
                    contentPadding = PaddingValues(
                        top = 8.dp,
                        bottom = if (batteryStatus == BatteryStatus.Charging) {
                            8.dp
                        } else {
                            navigationBarBottomPadding() + 8.dp
                        }
                    ),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    itemsIndexed(records, key = { _, record -> record.name }) { index, record ->
                        SwipeRevealRow(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            isOpen = openRecordName == record.name,
                            onOpenChange = { open ->
                                if (open) {
                                    openRecordName = record.name
                                } else if (openRecordName == record.name) {
                                    openRecordName = null
                                }
                            },
                            isGroupFirst = index == 0,
                            isGroupLast = index == records.size - 1,
                            startActionContent = {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(12.dp)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            enabled = !isImportExporting,
                                            onClick = {
                                                openRecordName = null
                                                pendingExportFile = record.asRecordsFile()
                                                exportLauncher.launch(record.name)
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Outbox,
                                        contentDescription = stringResource(R.string.history_export),
                                        tint = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            },
                            endActionContent = {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(12.dp)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = {
                                                openRecordName = null
                                                viewModel.deleteRecord(context, record.asRecordsFile())
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Delete,
                                        contentDescription = stringResource(R.string.history_delete),
                                        tint = MaterialTheme.colorScheme.onError
                                    )
                                }
                            },
                            content = {
                                val stats = record.stats
                                val durationMs = stats.endTime - stats.startTime
                                val capacityChange = if (record.type == BatteryStatus.Charging) {
                                    stats.endCapacity - stats.startCapacity
                                } else {
                                    stats.startCapacity - stats.endCapacity
                                }
                                val averageLabel = if (record.type == BatteryStatus.Charging) {
                                    stringResource(R.string.history_average_power)
                                } else {
                                    stringResource(R.string.history_average_consumption)
                                }
                                val averagePowerText = formatPower(
                                    stats.averagePower,
                                    dualCellEnabled,
                                    calibrationValue
                                )
                                if (layoutStyle == HistoryListLayoutStyle.Classic) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp)
                                    ) {
                                        Text(
                                            text = formatFullDateTime(stats.startTime),
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            text = stringResource(
                                                R.string.history_item_summary,
                                                formatDurationHours(durationMs),
                                                capacityChange
                                            ),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "$averageLabel $averagePowerText",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                } else {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                text = formatFullDateTime(stats.startTime),
                                                style = MaterialTheme.typography.titleSmall
                                            )
                                            Spacer(Modifier.height(6.dp))
                                            Text(
                                                text = stringResource(
                                                    R.string.history_item_summary,
                                                    formatDurationHours(durationMs),
                                                    capacityChange
                                                ),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Column(
                                            horizontalAlignment = Alignment.End,
                                        ) {
                                            Text(
                                                text = averagePowerText,
                                                style = MaterialTheme.typography.bodyLarge
                                            )
                                            Spacer(modifier = Modifier.height(3.dp))
                                            Text(
                                                text = averageLabel,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            },
                            onContentClick = {
                                onNavigateToRecordDetail(record.type, record.name)
                            }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                }
            }

            if (isImportExporting) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier.size(42.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        tonalElevation = 6.dp,
                        shadowElevation = 6.dp
                    ) {
                        Box(
                            modifier = Modifier.size(38.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            LoadingIndicator()
                        }
                    }
                }
            }
        }
    }
}

/**
 * 解析历史列表布局样式偏好。
 *
 * @param rawValue SharedPreferences 中保存的枚举名称；为空或非法时回退到经典布局。
 * @return 可用于历史列表渲染的布局样式。
 */
private fun loadHistoryListLayoutStyle(rawValue: String?): HistoryListLayoutStyle {
    return HistoryListLayoutStyle.entries.firstOrNull { it.name == rawValue }
        ?: HistoryListLayoutStyle.Classic
}

private fun buildHistoryZipFileName(batteryStatus: BatteryStatus): String {
    return if (batteryStatus == BatteryStatus.Charging) {
        "charge-history.zip"
    } else {
        "discharge-history.zip"
    }
}

@Composable
private fun ChargeHistoryFilterBar(
    selectedMinCapacityChange: Int?,
    onSelectFilter: (Int) -> Unit
) {
    Surface(
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 16.dp,
                    top = 12.dp,
                    end = 16.dp,
                    bottom = navigationBarBottomPadding() +12.dp
                ),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CHARGE_CAPACITY_CHANGE_FILTERS.forEach { threshold ->
                val selected = selectedMinCapacityChange == threshold
                val borderColor = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                }
                val textColor = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
                Box(
                    modifier = Modifier
                        .clip(ChargeHistoryFilterChipShape)
                        .border(
                            width = if (selected) 1.5.dp else 1.dp,
                            color = borderColor,
                            shape = ChargeHistoryFilterChipShape
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onSelectFilter(threshold) }
                        )
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "≥$threshold%",
                        color = textColor,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}
