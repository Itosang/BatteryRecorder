package yangfentuozi.batteryrecorder.ui.dialog.settings

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
import yangfentuozi.batteryrecorder.shared.config.SettingsConstants
import yangfentuozi.batteryrecorder.ui.theme.AppShape

// 预测文件数设置Dialog
@Composable
fun SceneStatsRecentFileCountDialog(
    currentValue: Int,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit,
    onReset: () -> Unit
) {
    val config = SettingsConstants.sceneStatsRecentFileCount
    var value by remember { mutableStateOf(currentValue.toString()) }
    var isError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("样本次数") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { newValue: String ->
                    value = newValue
                    isError = newValue.toIntOrNull() == null ||
                            newValue.toInt() < config.min ||
                            newValue.toInt() > config.max
                },
                label = { Text("最近文件数") },
                isError = isError,
                supportingText = if (isError) {
                    {
                        Text(
                            "请输入 ${config.min}-${config.max} 之间的整数"
                        )
                    }
                } else null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    value.toIntOrNull()?.let { intValue ->
                        if (intValue in config.min..config.max) {
                            onSave(intValue)
                        }
                    }
                },
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
