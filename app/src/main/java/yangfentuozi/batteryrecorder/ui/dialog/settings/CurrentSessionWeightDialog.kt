package yangfentuozi.batteryrecorder.ui.dialog.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
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
    val maxXConfig = SettingsConstants.predCurrentSessionWeightMaxX100
    val halfLifeConfig = SettingsConstants.predCurrentSessionWeightHalfLifeMin
    val minMaxX = maxXConfig.min / 100f
    val maxMaxX = maxXConfig.max / 100f
    val maxXSteps = ((maxXConfig.max - maxXConfig.min) / 10 - 1).coerceAtLeast(0)
    var maxX by remember {
        val initial = ((currentMaxX100 / 100f) * 10).roundToInt() / 10f
        val normalized = maxXConfig.coerce((initial * 100).roundToInt()) / 100f
        val snapped = (normalized * 10).roundToInt() / 10f
        mutableFloatStateOf(snapped)
    }

    val minHalfLife = halfLifeConfig.min
    val maxHalfLife = halfLifeConfig.max
    var halfLifeMin by remember {
        val initial = halfLifeConfig.coerce(currentHalfLifeMin)
        mutableLongStateOf(initial)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("当次记录加权") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val shown = (maxX * 10).roundToInt() / 10.0
                    Text(
                        text = "最大倍率",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Box(
                        modifier = Modifier
                            .clip(AppShape.small)
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${shown}x",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
                Slider(
                    value = maxX,
                    onValueChange = { v ->
                        maxX = maxXConfig.coerce((v * 100).roundToInt()) / 100f
                    },
                    valueRange = minMaxX..maxMaxX,
                    steps = maxXSteps,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "半衰期",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Box(
                        modifier = Modifier
                            .clip(AppShape.small)
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${halfLifeMin}m",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
                Slider(
                    value = halfLifeMin.toFloat(),
                    onValueChange = { v -> halfLifeMin = v.roundToInt().toLong() },
                    valueRange = minHalfLife.toFloat()..maxHalfLife.toFloat(),
                    steps = (maxHalfLife - minHalfLife - 1).toInt().coerceAtLeast(0),
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "越接近当前的记录权重越高",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val maxX100 = maxXConfig.coerce((maxX * 100).roundToInt())
                    val halfLife = halfLifeConfig.coerce(halfLifeMin)
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
