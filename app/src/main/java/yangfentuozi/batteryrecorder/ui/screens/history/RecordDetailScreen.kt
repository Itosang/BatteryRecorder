package yangfentuozi.batteryrecorder.ui.screens.history

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Outbox
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.core.content.edit
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import yangfentuozi.batteryrecorder.R
import yangfentuozi.batteryrecorder.data.bh.BhEventDb
import yangfentuozi.batteryrecorder.data.bh.BhFeature
import yangfentuozi.batteryrecorder.data.bh.BhRangeStats
import yangfentuozi.batteryrecorder.data.bh.BhSyncManager
import yangfentuozi.batteryrecorder.data.bh.BhTimeline
import yangfentuozi.batteryrecorder.data.bh.buildPackageLabelResolver
import yangfentuozi.batteryrecorder.shared.data.BatteryStatus
import yangfentuozi.batteryrecorder.shared.data.RecordsFile
import yangfentuozi.batteryrecorder.ui.components.charts.PowerCurveMode
import yangfentuozi.batteryrecorder.ui.components.global.SplicedColumnGroup
import yangfentuozi.batteryrecorder.ui.components.global.StatRow
import yangfentuozi.batteryrecorder.ui.dialog.history.ChartGuideDialog
import yangfentuozi.batteryrecorder.ui.screens.bh.BhRankGroup
import yangfentuozi.batteryrecorder.ui.viewmodel.HistorySharedViewModel
import yangfentuozi.batteryrecorder.ui.viewmodel.SettingsViewModel
import yangfentuozi.batteryrecorder.shared.util.LoggerX
import yangfentuozi.batteryrecorder.utils.appendRecordDetailScreenshotHeader
import yangfentuozi.batteryrecorder.utils.batteryRecorderScaffoldInsets
import yangfentuozi.batteryrecorder.utils.captureLongScreenshot
import yangfentuozi.batteryrecorder.utils.formatChargeDetailBatteryInfo
import yangfentuozi.batteryrecorder.utils.navigationBarBottomPadding
import yangfentuozi.batteryrecorder.utils.readDeviceBatteryCapacityMah
import yangfentuozi.batteryrecorder.utils.buildRecordDetailScreenshotFileName
import yangfentuozi.batteryrecorder.utils.saveBitmapToPictures

private const val RECORD_DETAIL_CHART_PREFS_NAME = "record_detail_chart"
private const val KEY_POWER_CURVE_MODE = "power_curve_mode"
private const val KEY_SHOW_CAPACITY_CURVE = "show_capacity_curve"
private const val KEY_SHOW_TEMP_CURVE = "show_temp_curve"
private const val KEY_SHOW_VOLTAGE_CURVE = "show_voltage_curve"
private const val KEY_SHOW_APP_ICONS = "show_app_icons"
private const val TAG = "RecordDetailScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordDetailScreen(
    recordsFile: RecordsFile,
    viewModel: HistorySharedViewModel = viewModel(),
    settingsViewModel: SettingsViewModel,
    onNavigateBack: () -> Unit = {},
    onNavigateToBhRecords: (Long, Long) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val locale = LocalLocale.current.platformLocale
    val activity = remember(context) { context.findActivity() }
    val coroutineScope = rememberCoroutineScope()
    val record by viewModel.recordDetail.collectAsState()
    val chartUiState by viewModel.recordChartUiState.collectAsState()
    val recordAppDetailEntries by viewModel.recordAppDetailEntries.collectAsState()
    val recordDetailPowerUiState by viewModel.recordDetailSummaryUiState.collectAsState()
    val recordDetailReferenceVoltageV by viewModel.recordDetailReferenceVoltageV.collectAsState()
    val isRecordChartLoading by viewModel.isRecordChartLoading.collectAsState()
    val userMessage by viewModel.userMessage.collectAsState()
    val appSettings by settingsViewModel.appSettings.collectAsState()
    val dualCellEnabled by settingsViewModel.dualCellEnabled.collectAsState()
    val dischargeDisplayPositive by settingsViewModel.dischargeDisplayPositive.collectAsState()
    val calibrationValue by settingsViewModel.calibrationValue.collectAsState()
    val recordIntervalMs by settingsViewModel.recordIntervalMs.collectAsState()
    val recordScreenOffEnabled by settingsViewModel.screenOffRecord.collectAsState()
    val detailScrollState = rememberScrollState()
    val longScreenshotLayer = rememberGraphicsLayer()
    val screenshotBackgroundColor = MaterialTheme.colorScheme.background
    val screenshotTextColor = MaterialTheme.colorScheme.onBackground
    val deleteSuccessMessage = stringResource(R.string.toast_delete_success)
    val saveImageSuccessMessage = stringResource(R.string.toast_save_image_success)
    val saveImageFailedMessage = stringResource(R.string.toast_save_image_failed)
    val chartPrefs = remember(context) {
        context.getSharedPreferences(RECORD_DETAIL_CHART_PREFS_NAME, Context.MODE_PRIVATE)
    }
    var longScreenshotViewportSize by remember { mutableStateOf(IntSize.Zero) }

    // 电池事件统计（来自 App 内部数据库；打开时静默同步一次保证数据最新）
    // 门控：设置开关 + 澎湃OS + 数据源检测（BhFeature），关闭或不可用时隐藏该区
    var bhStats by remember { mutableStateOf<BhRangeStats.Stats?>(null) }
    val bhDb = remember(context) { BhEventDb.get(context) }
    LaunchedEffect(record, recordsFile) {
        if (!BhFeature.isEnabled(context)) {
            bhStats = null
            return@LaunchedEffect
        }
        val detail = record?.takeIf { it.asRecordsFile() == recordsFile }
        val stats = detail?.stats
        if (stats == null || stats.endTime <= stats.startTime) {
            bhStats = null
            return@LaunchedEffect
        }
        bhStats = withContext(Dispatchers.IO) {
            val startedAt = System.currentTimeMillis()
            try {
                BhSyncManager.sync(context)
            } catch (_: Throwable) {
            }
            val st = try {
                val groups = bhDb.textGroups(stats.startTime, stats.endTime)
                BhRangeStats.stats(groups, buildPackageLabelResolver(context))
            } catch (_: Throwable) {
                null
            }
            LoggerX.i(
                TAG,
                "[系统事件统计] 耗时 ${System.currentTimeMillis() - startedAt}ms · " +
                        (st?.let { "${it.total} 个事件（精确 ${it.exact}）" } ?: "无数据")
            )
            st
        }
    }
    val chargeDetailBatteryInfoText = remember(
        context,
        locale,
        recordDetailReferenceVoltageV,
        recordsFile.type
    ) {
        if (recordsFile.type != BatteryStatus.Charging) {
            null
        } else {
            formatChargeDetailBatteryInfo(
                locale = locale,
                capacityMah = readDeviceBatteryCapacityMah(context),
                referenceVoltageV = recordDetailReferenceVoltageV
            )
        }
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
    var showGuideDialog by rememberSaveable(recordsFile) { mutableStateOf(false) }
    var isSavingLongScreenshot by remember(recordsFile) { mutableStateOf(false) }
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
        if (message == deleteSuccessMessage) {
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
                            onClick = { exportLauncher.launch(recordsFile.name) },
                            enabled = !isSavingLongScreenshot
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Outbox,
                                contentDescription = stringResource(R.string.history_export_record)
                            )
                        }
                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    isSavingLongScreenshot = true
                                    kotlinx.coroutines.delay(300)
                                    try {
                                        val screenshotBitmap = captureLongScreenshot(
                                            scrollState = detailScrollState,
                                            viewportSize = longScreenshotViewportSize,
                                            graphicsLayer = longScreenshotLayer,
                                            backgroundColorArgb = screenshotBackgroundColor.toArgb()
                                        )
                                        try {
                                            val decoratedBitmap = appendRecordDetailScreenshotHeader(
                                                context = context,
                                                sourceBitmap = screenshotBitmap,
                                                backgroundColorArgb = screenshotBackgroundColor.toArgb(),
                                                textColorArgb = screenshotTextColor.toArgb()
                                            )
                                            try {
                                                withContext(Dispatchers.IO) {
                                                    saveBitmapToPictures(
                                                        context = context,
                                                        displayName = buildRecordDetailScreenshotFileName(recordsFile),
                                                        bitmap = decoratedBitmap
                                                    )
                                                }
                                            } finally {
                                                if (decoratedBitmap !== screenshotBitmap) {
                                                    decoratedBitmap.recycle()
                                                }
                                            }
                                        } finally {
                                            screenshotBitmap.recycle()
                                        }
                                        Toast.makeText(
                                            context,
                                            saveImageSuccessMessage,
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } catch (e: Exception) {
                                        LoggerX.e(
                                            TAG,
                                            "[截图] 保存记录详情长截图失败: ${recordsFile.name}",
                                            tr = e
                                        )
                                        Toast.makeText(
                                            context,
                                            saveImageFailedMessage,
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } finally {
                                        isSavingLongScreenshot = false
                                    }
                                }
                            },
                            enabled = !isSavingLongScreenshot
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.SaveAlt,
                                contentDescription = stringResource(R.string.history_save_long_screenshot)
                            )
                        }
                        IconButton(
                            onClick = { showGuideDialog = true },
                            enabled = !isSavingLongScreenshot
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
        val detailType = detailState?.type ?: recordsFile.type
        val useMahForDischargeDetail =
            detailState?.type == BatteryStatus.Discharging && appSettings.dischargeDetailUseMah

        if (isChartFullscreen) {
            RecordDetailFullscreenChart(
                detailType = detailType,
                chartUiState = chartUiState,
                isTargetRecordLoaded = isTargetRecordLoaded,
                isRecordChartLoading = isRecordChartLoading,
                recordStartTime = stats?.startTime,
                recordScreenOffEnabled = recordScreenOffEnabled,
                dischargeDisplayPositive = dischargeDisplayPositive,
                powerCurveMode = powerCurveMode,
                showCapacity = showCapacity,
                showTemp = showTemp,
                showVoltage = showVoltage,
                showAppIcons = showAppIcons,
                fullscreenViewportStartMs = fullscreenViewportStartMs,
                onToggleFullscreen = {
                    isChartFullscreen = false
                    fullscreenViewportStartMs = null
                },
                onPowerCurveModeChange = { nextValue ->
                    chartPrefs.edit { putString(KEY_POWER_CURVE_MODE, nextValue.name) }
                    powerCurveMode = nextValue
                },
                onShowCapacityChange = { nextValue ->
                    chartPrefs.edit { putBoolean(KEY_SHOW_CAPACITY_CURVE, nextValue) }
                    showCapacity = nextValue
                },
                onShowTempChange = { nextValue ->
                    chartPrefs.edit { putBoolean(KEY_SHOW_TEMP_CURVE, nextValue) }
                    showTemp = nextValue
                },
                onShowVoltageChange = { nextValue ->
                    chartPrefs.edit { putBoolean(KEY_SHOW_VOLTAGE_CURVE, nextValue) }
                    showVoltage = nextValue
                },
                onShowAppIconsChange = { nextValue ->
                    chartPrefs.edit { putBoolean(KEY_SHOW_APP_ICONS, nextValue) }
                    showAppIcons = nextValue
                },
                onFullscreenViewportStartChange = { fullscreenViewportStartMs = it },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
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
                .background(screenshotBackgroundColor)
                .onSizeChanged { longScreenshotViewportSize = it }
                .drawWithContent {
                    longScreenshotLayer.record {
                        drawRect(screenshotBackgroundColor)
                        this@drawWithContent.drawContent()
                    }
                    drawLayer(longScreenshotLayer)
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(detailScrollState)
                    .padding(
                        start = 16.dp,
                        top = 16.dp,
                        end = 16.dp,
                        bottom = navigationBarBottomPadding() + 16.dp
                    ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                RecordDetailSummarySection(
                    detailState = detailState,
                    powerUiState = recordDetailPowerUiState,
                    chargeDetailBatteryInfoText = chargeDetailBatteryInfoText,
                    dualCellEnabled = dualCellEnabled,
                    calibrationValue = calibrationValue,
                    useMahForDischargeDetail = useMahForDischargeDetail,
                    locale = locale
                )

                RecordDetailChartSection(
                    detailType = detailType,
                    chartUiState = chartUiState,
                    isTargetRecordLoaded = isTargetRecordLoaded,
                    isRecordChartLoading = isRecordChartLoading,
                    recordStartTime = stats?.startTime,
                    recordScreenOffEnabled = recordScreenOffEnabled,
                    dischargeDisplayPositive = dischargeDisplayPositive,
                    powerCurveMode = powerCurveMode,
                    showCapacity = showCapacity,
                    showTemp = showTemp,
                    showVoltage = showVoltage,
                    showAppIcons = showAppIcons,
                    onToggleFullscreen = {
                        isChartFullscreen = true
                        fullscreenViewportStartMs = chartUiState.minChartTime
                    },
                    onPowerCurveModeChange = { nextValue ->
                        chartPrefs.edit { putString(KEY_POWER_CURVE_MODE, nextValue.name) }
                        powerCurveMode = nextValue
                    },
                    onShowCapacityChange = { nextValue ->
                        chartPrefs.edit { putBoolean(KEY_SHOW_CAPACITY_CURVE, nextValue) }
                        showCapacity = nextValue
                    },
                    onShowTempChange = { nextValue ->
                        chartPrefs.edit { putBoolean(KEY_SHOW_TEMP_CURVE, nextValue) }
                        showTemp = nextValue
                    },
                    onShowVoltageChange = { nextValue ->
                        chartPrefs.edit { putBoolean(KEY_SHOW_VOLTAGE_CURVE, nextValue) }
                        showVoltage = nextValue
                    },
                    onShowAppIconsChange = { nextValue ->
                        chartPrefs.edit { putBoolean(KEY_SHOW_APP_ICONS, nextValue) }
                        showAppIcons = nextValue
                    }
                )

                if (detailState?.type == BatteryStatus.Discharging) {
                    AppDetailSection(
                        entries = if (isSavingLongScreenshot) {
                            recordAppDetailEntries.filter { it.durationMs >= 120_000L }
                        } else {
                            recordAppDetailEntries
                        },
                        displayConfig = appDetailDisplayConfig
                    )
                }

                // 系统事件（.bh）并入常规记录：挂起失败 / 唤醒归因 / 屏幕状态机。
                RecordSystemEventsSection(
                    stats = bhStats,
                    recordStartMs = detailState?.stats?.startTime ?: 0L,
                    recordEndMs = detailState?.stats?.endTime ?: 0L,
                    onOpenFullDetail = onNavigateToBhRecords
                )
            }
        }
    }

    if (showGuideDialog) {
        ChartGuideDialog(
            onDismiss = { showGuideDialog = false }
        )
    }
}

private fun loadPowerCurveMode(value: String?): PowerCurveMode {
    // 缺省回到 Raw，而不是 Fitted：
    // 这样首次进入详情页时语义更接近旧版本的“功耗曲线”默认行为。
    return PowerCurveMode.entries.firstOrNull { it.name == value } ?: PowerCurveMode.Raw
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * 常规记录详情页的「系统事件」区：展示与该记录时间窗重叠的 .bh 会话事件。
 *
 * 事件内容来自 HyperOS 系统电池历史（/data/system/battery-history），
 * 提供采样记录给不出的信息：挂起失败原因、唤醒锁/定时器归因、屏幕状态机。
 */
@Composable
private fun RecordSystemEventsSection(
    stats: BhRangeStats.Stats?,
    recordStartMs: Long,
    recordEndMs: Long,
    onOpenFullDetail: (Long, Long) -> Unit
) {
    val st = stats ?: return
    if (st.total <= 0) return
    // 统计与明细范围 = 充放电记录的开始~截止时间
    val startMs = recordStartMs.takeIf { it > 0 } ?: return
    val endMs = recordEndMs.takeIf { it > startMs } ?: return

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SplicedColumnGroup(
            title = stringResource(
                R.string.record_detail_system_events_title,
                BhTimeline.formatRange(startMs, endMs)
            )
        ) {
            item {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    StatRow(
                        stringResource(R.string.record_detail_system_events_total),
                        stringResource(
                            R.string.record_detail_system_events_total_value,
                            st.total, st.exact, st.approx
                        )
                    )
                    StatRow(
                        stringResource(R.string.record_detail_system_events_screen),
                        stringResource(
                            R.string.record_detail_system_events_screen_value,
                            st.screenOn, st.screenOff
                        ) + if (st.dozeOn > 0) {
                            stringResource(R.string.record_detail_system_events_doze_suffix, st.dozeOn)
                        } else ""
                    )
                    StatRow(
                        stringResource(R.string.record_detail_system_events_abort),
                        stringResource(R.string.common_times_count, st.abortTotal)
                    )
                    StatRow(
                        stringResource(R.string.record_detail_system_events_wakeup),
                        stringResource(R.string.common_times_count, st.wakeTotal)
                    )
                }
            }
        }
        if (st.abortRank.isNotEmpty()) {
            BhRankGroup(
                title = stringResource(
                    R.string.record_detail_system_events_abort_rank_title,
                    st.abortTotal
                ),
                rows = st.abortRank,
                limit = 3
            )
        }
        // 详细记录入口（范围与统计一致：充放电记录的开始~结束）
        TextButton(onClick = { onOpenFullDetail(startMs, endMs) }) {
            Text(stringResource(R.string.record_detail_system_events_open_detail))
        }
    }
}
