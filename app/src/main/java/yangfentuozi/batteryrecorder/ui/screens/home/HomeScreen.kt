package yangfentuozi.batteryrecorder.ui.screens.home

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import yangfentuozi.batteryrecorder.ipc.Service
import yangfentuozi.batteryrecorder.server.recorder.IRecordListener
import yangfentuozi.batteryrecorder.shared.data.BatteryStatus
import yangfentuozi.batteryrecorder.shared.data.RecordsFile
import yangfentuozi.batteryrecorder.ui.components.global.SplicedColumnGroup
import yangfentuozi.batteryrecorder.ui.components.home.BatteryRecorderTopAppBar
import yangfentuozi.batteryrecorder.ui.components.home.CurrentRecordCard
import yangfentuozi.batteryrecorder.ui.components.home.PredictionCard
import yangfentuozi.batteryrecorder.ui.components.home.SceneStatsCard
import yangfentuozi.batteryrecorder.ui.components.home.StartServerCard
import yangfentuozi.batteryrecorder.ui.components.home.StatsCard
import yangfentuozi.batteryrecorder.ui.dialog.home.AboutDialog
import yangfentuozi.batteryrecorder.ui.dialog.home.AdbGuideDialog
import yangfentuozi.batteryrecorder.ui.model.LiveRecordSample
import yangfentuozi.batteryrecorder.ui.theme.AppShape
import yangfentuozi.batteryrecorder.ui.viewmodel.MainViewModel
import yangfentuozi.batteryrecorder.ui.viewmodel.SettingsViewModel
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel = viewModel(),
    settingsViewModel: SettingsViewModel,
    onNavigateToSettings: () -> Unit = {},
    onNavigateToHistoryList: (BatteryStatus) -> Unit = {},
    onNavigateToRecordDetail: (BatteryStatus, String) -> Unit = { _, _ -> },
    onNavigateToPredictionDetail: () -> Unit = {}
) {
    val context = LocalContext.current
    val serviceConnected by viewModel.serviceConnected.collectAsState()
    val showStopDialog by viewModel.showStopDialog.collectAsState()
    val showAboutDialog by viewModel.showAboutDialog.collectAsState()
    val userMessage by viewModel.userMessage.collectAsState()
    var showAdbGuideDialog by remember { mutableStateOf(false) }
    val chargeSummary by viewModel.chargeSummary.collectAsState()
    val dischargeSummary by viewModel.dischargeSummary.collectAsState()
    val currentRecordUiState by viewModel.currentRecordUiState.collectAsState()

    val appSettings by settingsViewModel.appSettings.collectAsState()
    val settingsInitialized by settingsViewModel.initialized.collectAsState()
    val statisticsSettings by settingsViewModel.statisticsSettings.collectAsState()
    val recordIntervalMs by settingsViewModel.recordIntervalMs.collectAsState()
    val latestSettingsInitialized by rememberUpdatedState(settingsInitialized)
    val latestStatisticsSettings by rememberUpdatedState(statisticsSettings)
    val latestRecordIntervalMs by rememberUpdatedState(recordIntervalMs)
    var prevServiceConnected by remember { mutableStateOf(false) }
    val dualCellEnabled = appSettings.dualCellEnabled
    val calibrationValue = appSettings.calibrationValue
    val dischargeDisplayPositive = appSettings.dischargeDisplayPositive

    // 首页续航卡片与场景卡片共用同一批统计结果。
    val sceneStats by viewModel.sceneStats.collectAsState()
    val prediction by viewModel.prediction.collectAsState()

    val lifecycleOwner = LocalLifecycleOwner.current
    val logExportFileNameFormatter = remember {
        DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss")
    }
    val exportLogsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            viewModel.exportLogs(context, uri)
        }
    }

    val listener = remember {
        object : IRecordListener.Stub() {
            override fun onRecord(timestamp: Long, power: Long, status: BatteryStatus, temp: Int) {
                viewModel.onRecordSample(
                    context = context,
                    request = latestStatisticsSettings,
                    recordIntervalMs = latestRecordIntervalMs,
                    sample = LiveRecordSample(
                        power = power,
                        status = status,
                        temp = temp
                    )
                )
            }

            override fun onChangedCurrRecordsFile(recordsFile: RecordsFile) {
                // 当前记录文件切段后，立即切到新分段语义；统计未就绪时显示等待状态。
                viewModel.onCurrentRecordsFileChanged(
                    context = context,
                    request = latestStatisticsSettings,
                    recordIntervalMs = latestRecordIntervalMs,
                    recordsFile = recordsFile
                )
            }
        }
    }

    LaunchedEffect(serviceConnected, settingsInitialized) {
        if (!settingsInitialized) return@LaunchedEffect
        val shouldDoDelayedRefresh = serviceConnected && !prevServiceConnected
        prevServiceConnected = serviceConnected
        if (!shouldDoDelayedRefresh) return@LaunchedEffect
        if (!lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            return@LaunchedEffect
        }

        Service.service?.registerRecordListener(listener)
        run {
            delay(1500)
            viewModel.refreshStatisticsTrackingCurrentRecord(
                context = context,
                request = statisticsSettings,
                recordIntervalMs = recordIntervalMs
            )
        }
    }

    LaunchedEffect(settingsInitialized) {
        if (!settingsInitialized) return@LaunchedEffect
        if (!lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            return@LaunchedEffect
        }
        // 首次设置初始化完成时补一次前台刷新；后续页面恢复依赖 ON_START，
        // 避免返回首页时走 ClearAndReload 先清空卡片与统计。
        viewModel.refreshStatisticsTrackingCurrentRecord(
            context = context,
            request = statisticsSettings,
            recordIntervalMs = recordIntervalMs
        )
    }

    LaunchedEffect(userMessage) {
        val message = userMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        viewModel.consumeUserMessage()
    }

    // 监听生命周期事件
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    if (latestSettingsInitialized) {
                        viewModel.refreshStatisticsTrackingCurrentRecord(
                            context = context,
                            request = latestStatisticsSettings,
                            recordIntervalMs = latestRecordIntervalMs
                        )
                    }
                    Service.service?.registerRecordListener(listener)
                }

                Lifecycle.Event.ON_STOP -> {
                    Service.service?.unregisterRecordListener(listener)
                }

                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Surface {
        Scaffold(
            topBar = {
                BatteryRecorderTopAppBar(
                    onSettingsClick = onNavigateToSettings,
                    onExportLogsClick = {
                        val fileName = buildString {
                            append("log_")
                            append(LocalDateTime.now().format(logExportFileNameFormatter))
                            append(".zip")
                        }
                        exportLogsLauncher.launch(fileName)
                    },
                    onStopServerClick = viewModel::showStopDialog,
                    onAboutClick = viewModel::showAboutDialog,
                    onRefreshClick = {
                        viewModel.forceRefreshStatistics(
                            context = context,
                            request = statisticsSettings,
                            recordIntervalMs = recordIntervalMs
                        )
                    },
                    showStopServer = serviceConnected
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(vertical = 8.dp)
            ) {
                SplicedColumnGroup(
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    // Root 启动卡片
                    item(visible = !serviceConnected) {
                        StartServerCard()
                    }

                    // ADB 启动卡片
                    item(visible = !serviceConnected) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "启动（ADB）",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "通过 ADB 命令启动",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.width(16.dp))
                            Button(
                                shape = AppShape.SplicedGroup.single,
                                onClick = { showAdbGuideDialog = true }
                            ) {
                                Text("查看命令")
                            }
                        }
                    }

                    item {
                        CurrentRecordCard(
                            uiState = currentRecordUiState,
                            dualCellEnabled = dualCellEnabled,
                            calibrationValue = calibrationValue,
                            dischargeDisplayPositive = dischargeDisplayPositive,
                            onClick = {
                                if (!currentRecordUiState.isSwitching) {
                                    currentRecordUiState.record?.let { record ->
                                        onNavigateToRecordDetail(record.type, record.name)
                                    }
                                }
                            }
                        )
                    }

                    // 统计卡片行（自动处理圆角）
                    rowItem {
                        item {
                            StatsCard(
                                title = "充电总结",
                                summary = chargeSummary,
                                dualCellEnabled = dualCellEnabled,
                                calibrationValue = calibrationValue,
                                onClick = { onNavigateToHistoryList(BatteryStatus.Charging) }
                            )
                        }
                        item {
                            StatsCard(
                                title = "放电总结",
                                summary = dischargeSummary,
                                dualCellEnabled = dualCellEnabled,
                                calibrationValue = calibrationValue,
                                onClick = { onNavigateToHistoryList(BatteryStatus.Discharging) }
                            )
                        }
                    }

                    val isDischarging = currentRecordUiState.displayStatus == BatteryStatus.Discharging

                    // 应用预测仅在放电语义下成立，充电记录不展示入口。
                    if (isDischarging) {
                        item {
                            PredictionCard(
                                prediction = prediction,
                                onClick = onNavigateToPredictionDetail
                            )
                        }
                    }
                    item {
                        SceneStatsCard(
                            sceneStats = sceneStats,
                            dualCellEnabled = dualCellEnabled,
                            calibrationValue = calibrationValue,
                            dischargeDisplayPositive = dischargeDisplayPositive
                        )
                    }
                }
            }
        }
    }

    // Stop Server Dialog
    if (showStopDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissStopDialog,
            title = { Text("停止服务") },
            text = { Text("确认停止服务?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.dismissStopDialog()
                        viewModel.stopService()
                    }
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = viewModel::dismissStopDialog
                ) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    // About Dialog
    if (showAboutDialog) {
        AboutDialog(
            onDismiss = viewModel::dismissAboutDialog
        )
    }

    // ADB Guide Dialog
    if (showAdbGuideDialog) {
        AdbGuideDialog(
            onDismiss = { showAdbGuideDialog = false }
        )
    }
}
