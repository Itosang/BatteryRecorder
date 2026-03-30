package yangfentuozi.batteryrecorder.ui.dialog.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import yangfentuozi.batteryrecorder.ipc.Service
import yangfentuozi.batteryrecorder.server.recorder.IRecordListener
import yangfentuozi.batteryrecorder.shared.config.SettingsConstants
import yangfentuozi.batteryrecorder.shared.data.BatteryStatus
import yangfentuozi.batteryrecorder.shared.data.RecordsFile
import yangfentuozi.batteryrecorder.ui.theme.AppShape
import yangfentuozi.batteryrecorder.utils.computePowerW
import kotlin.math.abs

// 校准Dialog
@Composable
fun CalibrationDialog(
    currentValue: Int,
    dualCellEnabled: Boolean,
    serviceConnected: Boolean,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit,
    onReset: () -> Unit
) {
    val config = SettingsConstants.calibrationValue
    var value by remember(currentValue) { mutableIntStateOf(currentValue) }
    var rawPower by remember { mutableStateOf<Long?>(null) }
    val adjustCalibrationValue: (Int, Boolean) -> Int = { current, decrease ->
        val next = if (decrease) {
            if (current < 0) current * 10 else current / 10
        } else {
            if (current > 0) current * 10 else current / 10
        }
        if (next == 0) {
            if (decrease) -1 else 1
        } else {
            config.coerce(next)
        }
    }

    val scope = rememberCoroutineScope()
    val listener = remember {
        object : IRecordListener.Stub() {
            override fun onRecord(timestamp: Long, power: Long, status: BatteryStatus, temp: Int) {
                scope.launch(Dispatchers.Main.immediate) {
                    rawPower = power
                }
            }

            override fun onChangedCurrRecordsFile(recordsFile: RecordsFile) {}
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> Service.service?.registerRecordListener(listener)
                Lifecycle.Event.ON_STOP -> Service.service?.unregisterRecordListener(listener)
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            Service.service?.unregisterRecordListener(listener)
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("电流单位校准") },
        text = {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            top = 4.dp,
                            start = 8.dp,
                            end = 8.dp
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { value = adjustCalibrationValue(value, true) },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = null)
                    }

                    Spacer(modifier = Modifier.width(24.dp))

                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = value.toString(),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }

                    Spacer(modifier = Modifier.width(24.dp))

                    IconButton(
                        onClick = { value = adjustCalibrationValue(value, false) },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (serviceConnected && rawPower != null) {
                    val power = rawPower!!
                    val finalW = run {
                        val valueW = computePowerW(
                            rawPower = power.toDouble(),
                            dualCellEnabled = dualCellEnabled,
                            calibrationValue = value
                        )
                        if (abs(valueW) < 0.005) 0.0 else valueW
                    }
                    Text(
                        text = "显示为 ${String.format("%+.2f W", finalW)}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "充电应显示为正数，放电应显示为负数",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                } else {
                    Text(
                        text = "显示为 --W",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "请启动服务",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(value) }) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onReset) {
                Text("重置")
            }
        },
        shape = AppShape.extraLarge
    )
}
