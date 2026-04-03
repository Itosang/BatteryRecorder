package yangfentuozi.batteryrecorder.ui.screens.history

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Outbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import yangfentuozi.batteryrecorder.R
import yangfentuozi.batteryrecorder.shared.data.BatteryStatus
import yangfentuozi.batteryrecorder.shared.data.RecordsFile
import yangfentuozi.batteryrecorder.ui.components.charts.FixedPowerAxisMode
import yangfentuozi.batteryrecorder.ui.components.charts.PowerCapacityChart
import yangfentuozi.batteryrecorder.ui.components.charts.PowerCurveMode
import yangfentuozi.batteryrecorder.ui.components.charts.RecordChartCurveVisibility
import yangfentuozi.batteryrecorder.ui.components.global.SplicedColumnGroup
import yangfentuozi.batteryrecorder.ui.dialog.history.ChartGuideDialog
import yangfentuozi.batteryrecorder.ui.theme.AppShape
import yangfentuozi.batteryrecorder.ui.viewmodel.HistoryViewModel
import yangfentuozi.batteryrecorder.ui.viewmodel.RecordAppDetailUiEntry
import yangfentuozi.batteryrecorder.ui.viewmodel.RecordDetailPowerUiState
import yangfentuozi.batteryrecorder.ui.viewmodel.SettingsViewModel
import yangfentuozi.batteryrecorder.utils.AppIconMemoryCache
import yangfentuozi.batteryrecorder.utils.batteryRecorderScaffoldInsets
import yangfentuozi.batteryrecorder.utils.formatDateTime
import yangfentuozi.batteryrecorder.utils.formatDetailDuration
import yangfentuozi.batteryrecorder.utils.formatDurationHours
import yangfentuozi.batteryrecorder.utils.formatPower
import yangfentuozi.batteryrecorder.utils.navigationBarBottomPadding

private const val RECORD_DETAIL_CHART_PREFS_NAME = "record_detail_chart"
private const val KEY_POWER_CURVE_MODE = "power_curve_mode"
private const val KEY_SHOW_CAPACITY_CURVE = "show_capacity_curve"
private const val KEY_SHOW_TEMP_CURVE = "show_temp_curve"
private const val KEY_SHOW_VOLTAGE_CURVE = "show_voltage_curve"
private const val KEY_SHOW_APP_ICONS = "show_app_icons"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordDetailScreen(
    recordsFile: RecordsFile,
    viewModel: HistoryViewModel = viewModel(),
    settingsViewModel: SettingsViewModel,
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val record by viewModel.recordDetail.collectAsState()
    val chartUiState by viewModel.recordChartUiState.collectAsState()
    val recordAppDetailEntries by viewModel.recordAppDetailEntries.collectAsState()
    val recordDetailPowerUiState by viewModel.recordDetailPowerUiState.collectAsState()
    val isRecordChartLoading by viewModel.isRecordChartLoading.collectAsState()
    val userMessage by viewModel.userMessage.collectAsState()
    val dualCellEnabled by settingsViewModel.dualCellEnabled.collectAsState()
    val dischargeDisplayPositive by settingsViewModel.dischargeDisplayPositive.collectAsState()
    val calibrationValue by settingsViewModel.calibrationValue.collectAsState()
    val recordIntervalMs by settingsViewModel.recordIntervalMs.collectAsState()
    val recordScreenOffEnabled by settingsViewModel.screenOffRecord.collectAsState()
    val chartPrefs = remember(context) {
        context.getSharedPreferences(RECORD_DETAIL_CHART_PREFS_NAME, Context.MODE_PRIVATE)
    }
    // 这些是“详情页图表本地展示偏好”，不属于业务配置，因此直接放在页面本地状态里持久化。
    var powerCurveMode by remember(chartPrefs) {
        mutableStateOf(loadPowerCurveMode(chartPrefs.getString(KEY_POWER_CURVE_MODE, null)))
    }
    var showCapacity by remember(chartPrefs) {
        mutableStateOf(chartPrefs.getBoolean(KEY_SHOW_CAPACITY_CURVE, true))
    }
    var showTemp by remember(chartPrefs) {
        mutableStateOf(chartPrefs.getBoolean(KEY_SHOW_TEMP_CURVE, true))
    }
    var showVoltage by remember(chartPrefs) {
        mutableStateOf(chartPrefs.getBoolean(KEY_SHOW_VOLTAGE_CURVE, true))
    }
    var showAppIcons by remember(chartPrefs) {
        mutableStateOf(chartPrefs.getBoolean(KEY_SHOW_APP_ICONS, true))
    }
    var isChartFullscreen by rememberSaveable(recordsFile) { mutableStateOf(false) }
    var fullscreenViewportStartMs by rememberSaveable(recordsFile) { mutableStateOf<Long?>(null) }
    var showDeleteDialog by rememberSaveable(recordsFile) { mutableStateOf(false) }
    var showGuideDialog by rememberSaveable(recordsFile) { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            viewModel.exportRecord(context, recordsFile, uri)
        }
    }

    // 图表展示依赖设置页的功率换算配置与息屏过滤配置；
    // 这几个值任何一个变化，都需要让 ViewModel 重新生成 chartUiState。
    LaunchedEffect(dualCellEnabled, calibrationValue, recordScreenOffEnabled) {
        viewModel.updatePowerDisplayConfig(
            dualCellEnabled = dualCellEnabled,
            calibrationValue = calibrationValue,
            recordScreenOffEnabled = recordScreenOffEnabled
        )
    }

    LaunchedEffect(recordIntervalMs) {
        viewModel.updateRecordDetailSamplingConfig(recordIntervalMs)
    }

    LaunchedEffect(dischargeDisplayPositive) {
        viewModel.updateRecordDetailDisplayConfig(dischargeDisplayPositive)
    }

    LaunchedEffect(recordsFile) {
        // 详情页切换记录文件时，重新加载文件内容与图表点。
        viewModel.loadRecord(context, recordsFile)
    }
    LaunchedEffect(userMessage) {
        val message = userMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        viewModel.consumeUserMessage()
        if (message == context.getString(R.string.toast_delete_success)) {
            onNavigateBack()
        }
    }

    BackHandler(enabled = isChartFullscreen) {
        isChartFullscreen = false
        fullscreenViewportStartMs = null
    }

    LaunchedEffect(activity, isChartFullscreen) {
        if (activity != null) {
            activity.requestedOrientation = if (isChartFullscreen) {
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            } else {
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    DisposableEffect(activity) {
        onDispose {
            if (activity != null && !activity.isChangingConfigurations) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    Scaffold(
        contentWindowInsets = batteryRecorderScaffoldInsets(),
        topBar = {
            if (!isChartFullscreen) {
                TopAppBar(
                    title = { Text(stringResource(R.string.history_record_detail_title)) },
                    actions = {
                        IconButton(
                            onClick = { exportLauncher.launch(recordsFile.name) }
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Outbox,
                                contentDescription = stringResource(R.string.history_export_record)
                            )
                        }
                        IconButton(
                            onClick = { showDeleteDialog = true }
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = stringResource(R.string.history_delete_record)
                            )
                        }
                        IconButton(
                            onClick = { showGuideDialog = true }
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = stringResource(R.string.history_view_chart_guide)
                            )
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        val detail = record
        val isTargetRecordLoaded = detail?.asRecordsFile() == recordsFile
        val detailState = detail?.takeIf { isTargetRecordLoaded }
        val stats = detailState?.stats
        val durationMs = stats?.let { it.endTime - it.startTime }
        val capacityChange = when (detailState?.type) {
            BatteryStatus.Charging -> stats?.let { it.endCapacity - it.startCapacity }
            BatteryStatus.Discharging -> stats?.let { it.startCapacity - it.endCapacity }
            else -> null
        }
        val detailType = detailState?.type ?: recordsFile.type
        val typeLabel = if (detailType == BatteryStatus.Charging) {
            stringResource(R.string.history_record_type_charging)
        } else {
            stringResource(R.string.history_record_type_discharging)
        }

        val fixedPowerMode =
            if (detailType == BatteryStatus.Discharging && !dischargeDisplayPositive) {
                FixedPowerAxisMode.NegativeOnly
            } else {
                FixedPowerAxisMode.PositiveOnly
            }
        val curveVisibility = RecordChartCurveVisibility(
            powerCurveMode = powerCurveMode,
            showCapacity = showCapacity,
            showTemp = showTemp,
            showVoltage = showVoltage
        )

        // 只有全屏模式允许横向拖动浏览局部视口；
        // 非全屏直接展示完整时长，减少普通详情页的认知负担。
        val viewportStartForChart = if (isChartFullscreen && chartUiState.minChartTime != null) {
            val minChartTime = chartUiState.minChartTime!!
            val maxViewportStart = chartUiState.maxViewportStartTime
            val initialStart = fullscreenViewportStartMs ?: minChartTime
            if (maxViewportStart == null) {
                initialStart
            } else {
                initialStart.coerceIn(minChartTime, maxViewportStart)
            }
        } else {
            null
        }
        val viewportEndForChart = if (
            isChartFullscreen &&
            viewportStartForChart != null &&
            chartUiState.maxChartTime != null
        ) {
            (viewportStartForChart + chartUiState.viewportDurationMs)
                .coerceAtMost(chartUiState.maxChartTime!!)
        } else {
            null
        }

        // chartBlock 统一封装普通模式 / 全屏模式下的同一张图表，避免两套 UI 结构分叉。
        val chartBlock: @Composable (Modifier, Boolean) -> Unit = { modifier, isFullscreenMode ->
            val currentChartHeight = if (isFullscreenMode) 320.dp else 240.dp
            if (!isTargetRecordLoaded || isRecordChartLoading) {
                RecordDetailChartLoading(
                    modifier = modifier,
                    chartHeight = currentChartHeight
                )
            } else {
                PowerCapacityChart(
                    points = chartUiState.points,
                    trendPoints = chartUiState.trendPoints,
                    recordScreenOffEnabled = recordScreenOffEnabled,
                    recordStartTime = stats!!.startTime,
                    modifier = modifier,
                    fixedPowerAxisMode = fixedPowerMode,
                    curveVisibility = curveVisibility,
                    chartHeight = currentChartHeight,
                    isFullscreen = isFullscreenMode,
                    onToggleFullscreen = {
                        if (isChartFullscreen) {
                            isChartFullscreen = false
                            fullscreenViewportStartMs = null
                        } else {
                            isChartFullscreen = true
                            fullscreenViewportStartMs = chartUiState.minChartTime
                        }
                    },
                    onTogglePowerVisibility = {
                        val nextValue = powerCurveMode.next()
                        chartPrefs.edit { putString(KEY_POWER_CURVE_MODE, nextValue.name) }
                        powerCurveMode = nextValue
                    },
                    onToggleCapacityVisibility = {
                        val nextValue = !showCapacity
                        chartPrefs.edit { putBoolean(KEY_SHOW_CAPACITY_CURVE, nextValue) }
                        showCapacity = nextValue
                    },
                    onToggleTempVisibility = {
                        val nextValue = !showTemp
                        chartPrefs.edit { putBoolean(KEY_SHOW_TEMP_CURVE, nextValue) }
                        showTemp = nextValue
                    },
                    onToggleVoltageVisibility = {
                        val nextValue = !showVoltage
                        chartPrefs.edit { putBoolean(KEY_SHOW_VOLTAGE_CURVE, nextValue) }
                        showVoltage = nextValue
                    },
                    showAppIcons = showAppIcons,
                    onToggleAppIconsVisibility = {
                        val nextValue = !showAppIcons
                        chartPrefs.edit { putBoolean(KEY_SHOW_APP_ICONS, nextValue) }
                        showAppIcons = nextValue
                    },
                    useFivePercentTimeGrid = isFullscreenMode,
                    visibleStartTime = viewportStartForChart,
                    visibleEndTime = viewportEndForChart,
                    onViewportShift = if (isFullscreenMode && chartUiState.minChartTime != null && chartUiState.maxViewportStartTime != null) { deltaMs ->
                        val minChartTime = chartUiState.minChartTime!!
                        val maxViewportStart = chartUiState.maxViewportStartTime!!
                        val currentStart = (fullscreenViewportStartMs ?: minChartTime)
                            .coerceIn(minChartTime, maxViewportStart)
                        fullscreenViewportStartMs =
                            (currentStart + deltaMs).coerceIn(minChartTime, maxViewportStart)
                    } else {
                        null
                    }
                )
            }
        }

        if (isChartFullscreen) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.Center
            ) {
                chartBlock(Modifier.fillMaxWidth(), true)
            }
            return@Scaffold
        }

        val appDetailDisplayConfig = RecordAppDetailDisplayConfig(
            dischargeDisplayPositive = dischargeDisplayPositive,
            dualCellEnabled = dualCellEnabled,
            calibrationValue = calibrationValue
        )

        // 外层 Box 负责铺满沉浸背景，内层滚动内容只按实际高度展开，
        // 避免 fillMaxSize 的滚动列把底部手势区误表现成“常驻大空白”。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        start = 16.dp,
                        top = 16.dp,
                        end = 16.dp,
                        bottom = navigationBarBottomPadding() + 16.dp
                    ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (detailState != null && stats != null && durationMs != null) {
                    SplicedColumnGroup(title = typeLabel) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                InfoRow(
                                    stringResource(R.string.history_info_time),
                                    "${formatDateTime(stats.startTime)} - ${formatDateTime(stats.endTime)} (${
                                        formatDurationHours(durationMs)
                                    })"
                                )
                                if (
                                    detailState.type == BatteryStatus.Discharging &&
                                    recordDetailPowerUiState != null
                                ) {
                                    RecordDetailPowerSection(
                                        powerUiState = recordDetailPowerUiState!!,
                                        dualCellEnabled = dualCellEnabled,
                                        calibrationValue = calibrationValue
                                    )
                                } else {
                                    InfoRow(
                                        stringResource(R.string.history_info_average_power),
                                        formatPower(
                                            stats.averagePower,
                                            dualCellEnabled,
                                            calibrationValue
                                        )
                                    )
                                }
                                if (detailState.type == BatteryStatus.Charging && capacityChange != null) {
                                    InfoRow(stringResource(R.string.history_info_capacity_change), "${capacityChange}%")
                                }
                                InfoRow(stringResource(R.string.history_info_screen_on), formatDurationHours(stats.screenOnTimeMs))
                                InfoRow(stringResource(R.string.history_info_screen_off), formatDurationHours(stats.screenOffTimeMs))
//                        InfoRow("记录ID", detailState.name.dropLast(4))
                            }
                        }
                    }
                }

                SplicedColumnGroup(title = stringResource(R.string.history_chart_section_title)) {
                    item {
                        Column(modifier = Modifier.padding(12.dp)) {
                            chartBlock(Modifier.fillMaxWidth(), false)
                        }
                    }
                }

                if (
                    detailState?.type == BatteryStatus.Discharging &&
                    recordAppDetailEntries.isNotEmpty()
                ) {
                    SplicedColumnGroup(title = stringResource(R.string.history_app_detail_section_title)) {
                        recordAppDetailEntries.forEach { entry ->
                            item(key = entry.key) {
                                RecordAppDetailRow(
                                    entry = entry,
                                    displayConfig = appDetailDisplayConfig
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showGuideDialog) {
        ChartGuideDialog(
            onDismiss = { showGuideDialog = false }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.history_delete_title)) },
            text = { Text(stringResource(R.string.history_delete_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteRecord(context, recordsFile)
                    }
                ) {
                    Text(stringResource(R.string.common_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RecordDetailChartLoading(
    modifier: Modifier,
    chartHeight: Dp
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(chartHeight),
        contentAlignment = Alignment.Center
    ) {
        LoadingIndicator()
    }
}

private data class RecordAppDetailDisplayConfig(
    val dischargeDisplayPositive: Boolean,
    val dualCellEnabled: Boolean,
    val calibrationValue: Int
)

private fun loadPowerCurveMode(value: String?): PowerCurveMode {
    // 缺省回到 Raw，而不是 Fitted：
    // 这样首次进入详情页时语义更接近旧版本的“功耗曲线”默认行为。
    return PowerCurveMode.entries.firstOrNull { it.name == value } ?: PowerCurveMode.Raw
}

private fun PowerCurveMode.next(): PowerCurveMode {
    // 图例点击按 Raw -> Fitted -> Hidden 循环切换，
    // 让“同一入口控制功耗线模式”比单独再做弹窗或多按钮更轻量。
    return when (this) {
        PowerCurveMode.Raw -> PowerCurveMode.Fitted
        PowerCurveMode.Fitted -> PowerCurveMode.Hidden
        PowerCurveMode.Hidden -> PowerCurveMode.Raw
    }
}

@Composable
private fun RecordAppDetailRow(
    entry: RecordAppDetailUiEntry,
    displayConfig: RecordAppDetailDisplayConfig
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RecordAppDetailIcon(entry = entry)

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = entry.appLabel,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = formatDetailDuration(entry.durationMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.End
                )
            }
            Spacer(Modifier.height(2.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = buildAvgAndTempText(entry, displayConfig),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = buildMaxTempText(entry),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

@Composable
private fun RecordAppDetailIcon(entry: RecordAppDetailUiEntry) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val iconSize = 36.dp
    val iconSizePx = with(density) { iconSize.roundToPx() }
    val packageName = entry.packageName
    var iconBitmap by remember(packageName, iconSizePx) {
        mutableStateOf(
            packageName?.let { AppIconMemoryCache.get(it, iconSizePx) }
        )
    }

    LaunchedEffect(packageName, iconSizePx) {
        if (entry.isScreenOff) return@LaunchedEffect
        val resolvedPackageName = packageName ?: return@LaunchedEffect
        if (iconBitmap != null) return@LaunchedEffect
        if (!AppIconMemoryCache.shouldLoad(resolvedPackageName, iconSizePx)) {
            iconBitmap = AppIconMemoryCache.get(resolvedPackageName, iconSizePx)
            return@LaunchedEffect
        }
        iconBitmap = AppIconMemoryCache.loadAndCache(context, resolvedPackageName, iconSizePx)
    }

    Box(
        modifier = Modifier
            .size(iconSize)
            .clip(AppShape.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        val bitmap = iconBitmap
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

private fun buildAvgAndTempText(
    entry: RecordAppDetailUiEntry,
    displayConfig: RecordAppDetailDisplayConfig
): String {
    val displayMultiplier = if (displayConfig.dischargeDisplayPositive) -1.0 else 1.0
    val powerText = formatPower(
        entry.averagePowerRaw * displayMultiplier,
        displayConfig.dualCellEnabled,
        displayConfig.calibrationValue
    ).replace(" ", "")
    return "AVG:$powerText  ${formatTempText(entry.averageTempCelsius)}"
}

private fun buildMaxTempText(entry: RecordAppDetailUiEntry): String =
    "MAX:${formatTempText(entry.maxTempCelsius)}"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordDetailPowerSection(
    powerUiState: RecordDetailPowerUiState,
    dualCellEnabled: Boolean,
    calibrationValue: Int
) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RecordDetailPowerHeader(
            modifier = Modifier.fillMaxWidth(0.3f)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = buildDetailPowerSummaryText(
                powerUiState = powerUiState,
                dualCellEnabled = dualCellEnabled,
                calibrationValue = calibrationValue
            ),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Start,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordDetailPowerHeader(modifier: Modifier = Modifier) {
    val tooltipState = rememberTooltipState()
    val coroutineScope = rememberCoroutineScope()
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
            TooltipAnchorPosition.Above
        ),
        tooltip = {
            PlainTooltip(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Text(stringResource(R.string.history_power_stats_label))
            }
        },
        state = tooltipState,
        enableUserInput = false
    ) {
        Text(
            text = stringResource(R.string.history_power_unknown),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                coroutineScope.launch {
                    tooltipState.show()
                }
            }
        )
    }
}

private fun formatDetailPowerValue(
    power: Double?,
    dualCellEnabled: Boolean,
    calibrationValue: Int
): String {
    if (power == null) return "--"
    return formatPower(power, dualCellEnabled, calibrationValue)
}

private fun buildDetailPowerSummaryText(
    powerUiState: RecordDetailPowerUiState,
    dualCellEnabled: Boolean,
    calibrationValue: Int
): String {
    val averageText = formatDetailPowerValue(
        power = powerUiState.averagePower,
        dualCellEnabled = dualCellEnabled,
        calibrationValue = calibrationValue
    ).replace(" W","")
    val screenOnText = formatDetailPowerValue(
        power = powerUiState.screenOnAveragePower,
        dualCellEnabled = dualCellEnabled,
        calibrationValue = calibrationValue
    ).replace(" W","")
    val screenOffText = formatDetailPowerValue(
        power = powerUiState.screenOffAveragePower,
        dualCellEnabled = dualCellEnabled,
        calibrationValue = calibrationValue
    ).replace(" W","")
    return "$averageText / $screenOnText / $screenOffText W"
}

private fun formatTempText(tempCelsius: Double?): String {
    if (tempCelsius == null) return "--℃"
    return String.format(java.util.Locale.getDefault(), "%.1f℃", tempCelsius)
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(0.3f)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
        )
    }
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
