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
import androidx.compose.ui.res.stringResource
import yangfentuozi.batteryrecorder.R
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
        title = { Text(stringResource(R.string.dialog_scene_stats_recent_file_count_title)) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { newValue: String ->
                    value = newValue
                    isError = newValue.toIntOrNull() == null ||
                            newValue.toInt() < config.min ||
                            newValue.toInt() > config.max
                },
                label = { Text(stringResource(R.string.settings_prediction_recent_files_label)) },
                isError = isError,
                supportingText = if (isError) {
                    {
                        Text(stringResource(R.string.common_integer_range_hint, config.min, config.max))
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
                Text(stringResource(R.string.common_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onReset) {
                Text(stringResource(R.string.common_reset))
            }
        },
        shape = AppShape.extraLarge
    )
}
