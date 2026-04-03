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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import yangfentuozi.batteryrecorder.R
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
        title = { Text(stringResource(R.string.dialog_segment_duration_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text(stringResource(R.string.dialog_segment_duration_label)) },
                    isError = isError,
                    supportingText = {
                        when {
                            isError -> Text(stringResource(R.string.common_integer_range_hint, config.min, config.max))
                            parsedValue == 0L -> Text(stringResource(R.string.dialog_segment_duration_zero_hint))
                            else -> Text(
                                stringResource(
                                    R.string.common_minutes_to_hours,
                                    parsedValue.toInt(),
                                    parsedValue / 60.0
                                )
                            )
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
