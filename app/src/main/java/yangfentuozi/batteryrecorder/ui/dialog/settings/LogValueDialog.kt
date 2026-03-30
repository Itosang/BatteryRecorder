package yangfentuozi.batteryrecorder.ui.dialog.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import yangfentuozi.batteryrecorder.ui.theme.AppShape

data class LogValueDialogConfig(
    val title: String,
    val label: String,
    val currentValue: String,
    val errorMessage: String,
    val parser: (String) -> Long?,
    val onDismiss: () -> Unit,
    val onSave: (Long) -> Unit,
    val onReset: () -> Unit
)

/**
 * 渲染日志数值配置输入弹窗。
 *
 * @param config 弹窗标题、输入标签、当前值与回调集合。
 * @return 无，直接渲染输入弹窗。
 */
@Composable
fun LogValueDialog(
    config: LogValueDialogConfig
) {
    var value by remember(config.currentValue) { mutableStateOf(config.currentValue) }
    val parsedValue = config.parser(value)
    val isError = parsedValue == null

    AlertDialog(
        onDismissRequest = config.onDismiss,
        title = { Text(config.title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { newValue ->
                    value = newValue
                },
                label = { Text(config.label) },
                isError = isError,
                supportingText = if (isError) {
                    { Text(config.errorMessage) }
                } else {
                    null
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { parsedValue?.let(config.onSave) },
                enabled = parsedValue != null
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = config.onReset) {
                Text("重置")
            }
        },
        shape = AppShape.extraLarge
    )
}
