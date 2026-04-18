package yangfentuozi.batteryrecorder.ui.guide

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import yangfentuozi.batteryrecorder.R
import yangfentuozi.batteryrecorder.ipc.Service
import yangfentuozi.batteryrecorder.shared.config.SettingsConstants
import yangfentuozi.batteryrecorder.shared.util.LoggerX
import yangfentuozi.batteryrecorder.startup.RootServerStarter
import yangfentuozi.batteryrecorder.ui.dialog.settings.CalibrationDialog
import yangfentuozi.batteryrecorder.ui.theme.AppShape
import yangfentuozi.batteryrecorder.ui.viewmodel.SettingsViewModel
import yangfentuozi.batteryrecorder.utils.batteryRecorderScaffoldInsets

private const val TAG = "StartupGuideScreen"
private const val DOCS_URL = "https://battrec.itosang.com"

private data class AdbCommandItem(
    val title: String,
    val command: String
)

/**
 * 首次启动引导页。
 *
 * @param settingsViewModel 用于读写双电芯与电流校准设置。
 * @param onGuideCompleted 引导完成后的回调。
 * @param modifier 外层修饰符。
 * @return 无，直接渲染引导界面。
 */
@Composable
fun StartupGuideScreen(
    settingsViewModel: SettingsViewModel,
    onGuideCompleted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dualCellEnabled by settingsViewModel.dualCellEnabled.collectAsState()
    val calibrationValue by settingsViewModel.calibrationValue.collectAsState()
    var currentStep by rememberSaveable { mutableStateOf(StartupGuideStep.INTRO) }
    var serviceConnected by rememberSaveable { mutableStateOf(Service.service != null) }
    var showCalibrationDialog by rememberSaveable { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val listener = object : Service.ServiceConnection {
            override fun onServiceConnected() {
                scope.launch(Dispatchers.Main.immediate) {
                    LoggerX.i(TAG, "[引导] Binder 已连接")
                    serviceConnected = true
                }
            }

            override fun onServiceDisconnected() {
                scope.launch(Dispatchers.Main.immediate) {
                    LoggerX.w(TAG, "[引导] Binder 已断开")
                    serviceConnected = false
                }
            }
        }
        Service.addListener(listener)
        onDispose {
            Service.removeListener(listener)
        }
    }

    val nextEnabled = when (currentStep) {
        StartupGuideStep.INTRO -> true
        StartupGuideStep.START_SERVICE -> serviceConnected
        StartupGuideStep.CALIBRATION -> true
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = batteryRecorderScaffoldInsets(),
        bottomBar = {
            StartupGuideBottomBar(
                currentStep = currentStep,
                nextEnabled = nextEnabled,
                onBack = {
                    currentStep = when (currentStep) {
                        StartupGuideStep.START_SERVICE -> StartupGuideStep.INTRO
                        StartupGuideStep.CALIBRATION -> StartupGuideStep.START_SERVICE
                        StartupGuideStep.INTRO -> StartupGuideStep.INTRO
                    }
                },
                onNext = {
                    when (currentStep) {
                        StartupGuideStep.INTRO -> currentStep = StartupGuideStep.START_SERVICE
                        StartupGuideStep.START_SERVICE -> {
                            if (serviceConnected) {
                                currentStep = StartupGuideStep.CALIBRATION
                            }
                        }

                        StartupGuideStep.CALIBRATION -> onGuideCompleted()
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    fadeIn(animationSpec = tween(240)) togetherWith
                        fadeOut(animationSpec = tween(240))
                },
                label = "startup_guide_step_transition"
            ) { step ->
                when (step) {
                    StartupGuideStep.INTRO -> IntroContent(
                        onOpenDocs = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, DOCS_URL.toUri()))
                        }
                    )

                    StartupGuideStep.START_SERVICE -> StartServiceContent(
                        serviceConnected = serviceConnected,
                        onStartRoot = {
                            LoggerX.i(TAG, "[引导] 用户点击 ROOT 启动")
                            Thread {
                                RootServerStarter.start(
                                    context = context,
                                    source = "首次引导"
                                )
                            }.start()
                        }
                    )

                    StartupGuideStep.CALIBRATION -> CalibrationContent(
                        dualCellEnabled = dualCellEnabled,
                        calibrationValue = calibrationValue,
                        serviceConnected = serviceConnected,
                        onDualCellChange = settingsViewModel::setDualCellEnabled,
                        onAdjustCalibration = { showCalibrationDialog = true }
                    )
                }
            }
        }
    }

    if (showCalibrationDialog) {
        CalibrationDialog(
            currentValue = calibrationValue,
            dualCellEnabled = dualCellEnabled,
            serviceConnected = serviceConnected,
            onDismiss = { showCalibrationDialog = false },
            onSave = { value ->
                settingsViewModel.setCalibrationValue(value)
                showCalibrationDialog = false
            },
            onReset = {
                settingsViewModel.setCalibrationValue(SettingsConstants.calibrationValue.def)
                showCalibrationDialog = false
            }
        )
    }
}

@Composable
private fun IntroContent(
    onOpenDocs: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.app_name),
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = stringResource(R.string.startup_guide_intro_subtitle),
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(80.dp))
        Column(horizontalAlignment = Alignment.Start) {
            StartupGuideFeatureItem(
                icon = Icons.Default.AdminPanelSettings,
                title = stringResource(R.string.startup_guide_feature_start_title),
                description = stringResource(R.string.startup_guide_feature_start_desc)
            )
            Spacer(Modifier.height(18.dp))
            StartupGuideFeatureItem(
                icon = Icons.Default.Bolt,
                title = stringResource(R.string.startup_guide_feature_record_title),
                description = stringResource(R.string.startup_guide_feature_record_desc)
            )
            Spacer(Modifier.height(18.dp))
            StartupGuideFeatureItem(
                icon = Icons.Default.AutoGraph,
                title = stringResource(R.string.startup_guide_feature_calibration_title),
                description = stringResource(R.string.startup_guide_feature_calibration_desc)
            )
        }
//        Spacer(Modifier.height(28.dp))
//        TextButton(onClick = onOpenDocs) {
//            Text(stringResource(R.string.startup_guide_open_docs))
//        }
    }
}

@Composable
private fun StartServiceContent(
    serviceConnected: Boolean,
    onStartRoot: () -> Unit
) {
    val context = LocalContext.current
    val shellCommand = remember(context) {
        "${context.applicationInfo.nativeLibraryDir}/libstarter.so"
    }
    val commandItems = remember(shellCommand, context) {
        listOf(
            AdbCommandItem(
                title = context.getString(R.string.adb_guide_pc_title),
                command = "adb shell \"$shellCommand\""
            ),
            AdbCommandItem(
                title = context.getString(R.string.adb_guide_shell_title),
                command = shellCommand
            )
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.startup_guide_start_title),
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = stringResource(R.string.startup_guide_start_subtitle),
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(28.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = AppShape.large,
            color = if (serviceConnected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = stringResource(R.string.startup_guide_service_status_title),
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = if (serviceConnected) {
                        stringResource(R.string.startup_guide_service_status_connected)
                    } else {
                        stringResource(R.string.startup_guide_service_status_waiting)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (serviceConnected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = AppShape.large,
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.home_action_start_root),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.home_start_service_title),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(16.dp))
                Button(
                    onClick = onStartRoot,
                    enabled = !serviceConnected,
                    shape = AppShape.SplicedGroup.single
                ) {
                    Text(stringResource(R.string.home_action_start_service))
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = AppShape.large,
            tonalElevation = 2.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.home_action_start_adb),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = stringResource(R.string.adb_guide_step_enable_debug),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = stringResource(R.string.adb_guide_step_run_command),
                    style = MaterialTheme.typography.bodyMedium
                )
                commandItems.forEach { item ->
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodySmall
                    )
                    StartupGuideCommandBox(
                        command = item.command,
                        onCopy = { copyCommand(context, item.command) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CalibrationContent(
    dualCellEnabled: Boolean,
    calibrationValue: Int,
    serviceConnected: Boolean,
    onDualCellChange: (Boolean) -> Unit,
    onAdjustCalibration: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.startup_guide_calibration_title),
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = stringResource(R.string.startup_guide_calibration_subtitle),
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(28.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = AppShape.large,
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_dual_cell),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.startup_guide_dual_cell_summary),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(16.dp))
                Switch(
                    checked = dualCellEnabled,
                    onCheckedChange = onDualCellChange
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = AppShape.large,
            tonalElevation = 2.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.settings_calibration_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = stringResource(
                        R.string.startup_guide_calibration_current_value,
                        calibrationValue
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (serviceConnected) {
                        stringResource(R.string.startup_guide_calibration_connected_hint)
                    } else {
                        stringResource(R.string.startup_guide_calibration_disconnected_hint)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = onAdjustCalibration,
                    shape = AppShape.SplicedGroup.single
                ) {
                    Text(stringResource(R.string.startup_guide_adjust_calibration))
                }
            }
        }
    }
}

@Composable
private fun StartupGuideCommandBox(
    command: String,
    onCopy: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = command,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState())
            )
            IconButton(
                onClick = onCopy,
                modifier = Modifier
                    .height(MaterialTheme.typography.bodySmall.lineHeight.value.dp)
                    .width(MaterialTheme.typography.bodySmall.lineHeight.value.dp + 7.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = stringResource(R.string.adb_guide_copy_command)
                )
            }
        }
    }
}

private fun copyCommand(context: Context, command: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("command", command))
    Toast.makeText(context, context.getString(R.string.adb_guide_copied), Toast.LENGTH_SHORT).show()
}
