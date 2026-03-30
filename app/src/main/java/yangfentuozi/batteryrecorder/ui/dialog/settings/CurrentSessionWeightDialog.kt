package yangfentuozi.batteryrecorder.ui.dialog.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import yangfentuozi.batteryrecorder.shared.config.SettingsConstants
import yangfentuozi.batteryrecorder.ui.theme.AppShape
import kotlin.math.roundToInt

// 加权权重设置Dialog
@Composable
fun CurrentSessionWeightDialog(
    currentMaxX100: Int,
    currentHalfLifeMin: Long,
    onDismiss: () -> Unit,
    onSave: (maxX100: Int, halfLifeMin: Long) -> Unit,
    onReset: () -> Unit
) {
    val minMaxX = SettingsConstants.MIN_PRED_CURRENT_SESSION_WEIGHT_MAX_X100 / 100f
    val maxMaxX = SettingsConstants.MAX_PRED_CURRENT_SESSION_WEIGHT_MAX_X100 / 100f
    val maxXSteps = ((SettingsConstants.MAX_PRED_CURRENT_SESSION_WEIGHT_MAX_X100 -
            SettingsConstants.MIN_PRED_CURRENT_SESSION_WEIGHT_MAX_X100) / 10 - 1).coerceAtLeast(0)
    var maxX by remember {
        val initial = ((currentMaxX100 / 100f) * 10).roundToInt() / 10f
        val normalized = initial.coerceIn(minMaxX, maxMaxX)
        val snapped = (normalized * 10).roundToInt() / 10f
        mutableFloatStateOf(snapped)
    }

    val minHalfLife = SettingsConstants.MIN_PRED_CURRENT_SESSION_WEIGHT_HALF_LIFE_MIN
    val maxHalfLife = SettingsConstants.MAX_PRED_CURRENT_SESSION_WEIGHT_HALF_LIFE_MIN
    var halfLifeMin by remember {
        val initial = currentHalfLifeMin.coerceIn(minHalfLife, maxHalfLife)
        mutableLongStateOf(initial)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("当次记录加权") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "最大倍率",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Slider(
                        value = maxX,
                        onValueChange = { v ->
                            maxX = ((v * 10).roundToInt() / 10f).coerceIn(minMaxX, maxMaxX)
                        },
                        valueRange = minMaxX..maxMaxX,
                        steps = maxXSteps,
                        modifier = Modifier.weight(1F)
                    )
                    val shown = (maxX * 10).roundToInt() / 10.0
                    Text(
                        modifier = Modifier
                            .width(64.dp)
                            .padding(start = 8.dp),
                        text = "${shown}x",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                Text(
                    modifier = Modifier.padding(top = 12.dp),
                    text = "半衰期",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Slider(
                        value = halfLifeMin.toFloat(),
                        onValueChange = { v -> halfLifeMin = v.roundToInt().toLong() },
                        valueRange = minHalfLife.toFloat()..maxHalfLife.toFloat(),
                        steps = (maxHalfLife - minHalfLife - 1).toInt().coerceAtLeast(0),
                        modifier = Modifier.weight(1F)
                    )
                    Text(
                        modifier = Modifier
                            .width(64.dp)
                            .padding(start = 8.dp),
                        text = "${halfLifeMin}m",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                Text(
                    modifier = Modifier.padding(top = 8.dp),
                    text = "越接近当前的记录权重越高",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val maxX100 = (maxX * 100).roundToInt()
                        .coerceIn(
                            SettingsConstants.MIN_PRED_CURRENT_SESSION_WEIGHT_MAX_X100,
                            SettingsConstants.MAX_PRED_CURRENT_SESSION_WEIGHT_MAX_X100
                        )
                    val halfLife = halfLifeMin.coerceIn(minHalfLife, maxHalfLife)
                    onSave(maxX100, halfLife)
                }
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
