package yangfentuozi.batteryrecorder.ui.dialog.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import yangfentuozi.batteryrecorder.shared.config.SettingsConstants
import yangfentuozi.batteryrecorder.ui.theme.AppShape


// 记录分段时间设置Dialog
@Composable
fun SegmentDurationDialog(
    currentValueMin: Long,
    onDismiss: () -> Unit,
    onSave: (Long) -> Unit,
    onReset: () -> Unit
) {
    val config = SettingsConstants.segmentDurationMin
    var value by remember { mutableStateOf(currentValueMin.toString()) }
    val parsedValue = value.toLongOrNull()
    val isError =
        parsedValue == null || parsedValue < config.min || parsedValue > config.max

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("记录分段时间") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text("分钟") },
                    isError = isError,
                    supportingText = {
                        when {
                            isError -> Text("请输入 ${config.min}-${config.max} 之间的整数")
                            parsedValue == 0L -> Text("设置为 0 表示不按时间自动分段")
                            else -> Text("$parsedValue 分钟 = ${"%.1f".format(parsedValue / 60.0)} 小时")
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { parsedValue?.let { onSave(it) } },
                enabled = !isError
            ) {
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
