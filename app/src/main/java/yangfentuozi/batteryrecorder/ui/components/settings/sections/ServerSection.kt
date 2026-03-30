package yangfentuozi.batteryrecorder.ui.components.settings.sections

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import yangfentuozi.batteryrecorder.shared.config.SettingsConstants
import yangfentuozi.batteryrecorder.startup.BootAutoStartNotification
import yangfentuozi.batteryrecorder.ui.components.global.M3ESwitchWidget
import yangfentuozi.batteryrecorder.ui.components.global.SplicedColumnGroup
import yangfentuozi.batteryrecorder.ui.components.settings.SettingsItem
import yangfentuozi.batteryrecorder.ui.dialog.settings.BatchSizeDialog
import yangfentuozi.batteryrecorder.ui.dialog.settings.RecordIntervalDialog
import yangfentuozi.batteryrecorder.ui.dialog.settings.SegmentDurationDialog
import yangfentuozi.batteryrecorder.ui.dialog.settings.WriteLatencyDialog
import yangfentuozi.batteryrecorder.ui.model.SettingsUiProps
import kotlin.math.round

@Composable
fun ServerSection(
    props: SettingsUiProps
) {
    val context = LocalContext.current
    val state = props.state
    val actions = props.actions.server
    var showRecordIntervalDialog by remember { mutableStateOf(false) }
    var showWriteLatencyDialog by remember { mutableStateOf(false) }
    var showBatchSizeDialog by remember { mutableStateOf(false) }
    var showSegmentDurationDialog by remember { mutableStateOf(false) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    SplicedColumnGroup(
        title = "服务",
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        item {
            M3ESwitchWidget(
                text = "开机自启（ROOT）",
                checked = state.rootBootAutoStartEnabled,
                onCheckedChange = { enabled ->
                    actions.setRootBootAutoStartEnabled(enabled)
                    if (!enabled) return@M3ESwitchWidget
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val granted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED
                        if (!granted) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                    Toast.makeText(
                        context,
                        BootAutoStartNotification.CONTENT_TEXT,
                        Toast.LENGTH_LONG
                    ).show()
                }
            )
        }

        item {
            M3ESwitchWidget(
                text = "息屏记录",
                checked = state.recordScreenOffEnabled,
                onCheckedChange = actions.setScreenOffRecordEnabled
            )
        }

        item {
            M3ESwitchWidget(
                text = "轮询获取息屏状态",
                checked = state.alwaysPollingScreenStatusEnabled,
                onCheckedChange = actions.setAlwaysPollingScreenStatusEnabled
            )
        }

        item {
            SettingsItem(
                title = "采样间隔",
                summary = "${"%.1f".format(state.recordIntervalMs / 1000.0)} 秒"
            ) { showRecordIntervalDialog = true }
        }

        item {
            SettingsItem(
                title = "写入延迟",
                summary = "${"%.1f".format(state.writeLatencyMs / 1000.0)} 秒"
            ) { showWriteLatencyDialog = true }
        }

        item {
            SettingsItem(
                title = "批量大小",
                summary = "${state.batchSize} 条"
            ) { showBatchSizeDialog = true }
        }

        item {
            val summary = if (state.segmentDurationMin == 0L) {
                "不按时间分段"
            } else {
                "${state.segmentDurationMin} 分钟"
            }
            SettingsItem(
                title = "分段时间",
                summary = summary
            ) { showSegmentDurationDialog = true }
        }
    }

    // 采样间隔对话框
    if (showRecordIntervalDialog) {
        RecordIntervalDialog(
            currentValueMs = state.recordIntervalMs,
            onDismiss = { showRecordIntervalDialog = false },
            onSave = { value ->
                val roundedValue = (round(value / 100.0) * 100).toLong()
                actions.setRecordIntervalMs(roundedValue)
                showRecordIntervalDialog = false
            },
            onReset = {
                actions.setRecordIntervalMs(SettingsConstants.recordIntervalMs.def)
                showRecordIntervalDialog = false
            }
        )
    }

    // 写入延迟对话框
    if (showWriteLatencyDialog) {
        WriteLatencyDialog(
            currentValueMs = state.writeLatencyMs,
            onDismiss = { showWriteLatencyDialog = false },
            onSave = { value ->
                val roundedValue = (round(value / 100.0) * 100).toLong()
                actions.setWriteLatencyMs(roundedValue)
                showWriteLatencyDialog = false
            },
            onReset = {
                actions.setWriteLatencyMs(SettingsConstants.writeLatencyMs.def)
                showWriteLatencyDialog = false
            }
        )
    }

    // 批量大小对话框
    if (showBatchSizeDialog) {
        BatchSizeDialog(
            currentValue = state.batchSize,
            onDismiss = { showBatchSizeDialog = false },
            onSave = { value ->
                actions.setBatchSize(value)
                showBatchSizeDialog = false
            },
            onReset = {
                actions.setBatchSize(SettingsConstants.batchSize.def)
                showBatchSizeDialog = false
            }
        )
    }

    // 分段时间对话框
    if (showSegmentDurationDialog) {
        SegmentDurationDialog(
            currentValueMin = state.segmentDurationMin,
            onDismiss = { showSegmentDurationDialog = false },
            onSave = { value ->
                actions.setSegmentDurationMin(value)
                showSegmentDurationDialog = false
            },
            onReset = {
                actions.setSegmentDurationMin(SettingsConstants.segmentDurationMin.def)
                showSegmentDurationDialog = false
            }
        )
    }
}
