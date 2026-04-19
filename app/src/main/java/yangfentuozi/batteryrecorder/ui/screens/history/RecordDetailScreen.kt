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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Outbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.core.content.edit
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.unit.dp
import yangfentuozi.batteryrecorder.R
import yangfentuozi.batteryrecorder.shared.data.BatteryStatus
import yangfentuozi.batteryrecorder.shared.data.RecordsFile
import yangfentuozi.batteryrecorder.ui.components.charts.PowerCurveMode
import yangfentuozi.batteryrecorder.ui.dialog.history.ChartGuideDialog
import yangfentuozi.batteryrecorder.ui.viewmodel.HistoryViewModel
import yangfentuozi.batteryrecorder.ui.viewmodel.SettingsViewModel
import yangfentuozi.batteryrecorder.utils.batteryRecorderScaffoldInsets
import yangfentuozi.batteryrecorder.utils.formatChargeDetailBatteryInfo
import yangfentuozi.batteryrecorder.utils.navigationBarBottomPadding
import yangfentuozi.batteryrecorder.utils.readDeviceBatteryCapacityMah

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
    val locale = LocalLocale.current.platformLocale
    val activity = remember(context) { context.findActivity() }
    val record by viewModel.recordDetail.collectAsState()
    val chartUiState by viewModel.recordChartUiState.collectAsState()
    val recordAppDetailEntries by viewModel.recordAppDetailEntries.collectAsState()
    val recordDetailPowerUiState by viewModel.recordDetailPowerUiState.collectAsState()
    val recordDetailReferenceVoltageV by viewModel.recordDetailReferenceVoltageV.collectAsState()
    val isRecordChartLoading by viewModel.isRecordChartLoading.collectAsState()
    val userMessage by viewModel.userMessage.collectAsState()
    val appSettings by settingsViewModel.appSettings.collectAsState()
    val dualCellEnabled by settingsViewModel.dualCellEnabled.collectAsState()
    val dischargeDisplayPositive by settingsViewModel.dischargeDisplayPositive.collectAsState()
    val calibrationValue by settingsViewModel.calibrationValue.collectAsState()
    val recordIntervalMs by settingsViewModel.recordIntervalMs.collectAsState()
    val recordScreenOffEnabled by settingsViewModel.screenOffRecord.collectAsState()
    val chartPrefs = remember(context) {
        context.getSharedPreferences(RECORD_DETAIL_CHART_PREFS_NAME, Context.MODE_PRIVATE)
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
                        entries = recordAppDetailEntries,
                        displayConfig = appDetailDisplayConfig
                    )
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
