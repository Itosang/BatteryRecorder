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

@Composable
fun WeightedAlgorithmDialog(
    currentAlphaMaxX100: Int,
    onDismiss: () -> Unit,
    onSave: (alphaMaxX100: Int) -> Unit,
    onReset: () -> Unit
) {
    val alphaConfig = SettingsConstants.predWeightedAlgorithmAlphaMaxX100
    val minAlpha = alphaConfig.min.toFloat()
    val maxAlpha = alphaConfig.max.toFloat()
    val alphaSteps = (alphaConfig.max - alphaConfig.min - 1).coerceAtLeast(0)
    var alphaPercent by remember {
        val initial = alphaConfig.coerce(currentAlphaMaxX100).toFloat()
        mutableFloatStateOf(initial)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("加权强度")
                Box(
                    modifier = Modifier
                        .clip(AppShape.small)
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${alphaPercent.roundToInt()}%",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Slider(
                    value = alphaPercent,
                    onValueChange = { v ->
                        alphaPercent = alphaConfig.coerce(v.roundToInt()).toFloat()
                    },
                    valueRange = minAlpha..maxAlpha,
                    steps = alphaSteps,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(alphaConfig.coerce(alphaPercent.roundToInt()))
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
