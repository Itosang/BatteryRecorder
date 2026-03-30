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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import yangfentuozi.batteryrecorder.shared.util.LoggerX
import yangfentuozi.batteryrecorder.ui.model.displayName
import yangfentuozi.batteryrecorder.ui.theme.AppShape
import kotlin.math.roundToInt

data class LogLevelDialogConfig(
    val currentValue: LoggerX.LogLevel,
    val onDismiss: () -> Unit,
    val onSave: (LoggerX.LogLevel) -> Unit,
    val onReset: () -> Unit
)

/**
 * 渲染日志级别选择弹窗。
 *
 * @param config 当前级别与确认、重置回调集合。
 * @return 无，直接渲染日志级别选择弹窗。
 */
@Composable
fun LogLevelDialog(
    config: LogLevelDialogConfig
) {
    val levels = LoggerX.LogLevel.entries
    var selectedIndex by remember(config.currentValue) {
        mutableIntStateOf(levels.indexOf(config.currentValue).coerceAtLeast(0))
    }

    AlertDialog(
        onDismissRequest = config.onDismiss,
        title = {
            val selectedLevel = levels[selectedIndex]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("日志级别")
                Box(
                    modifier = Modifier
                        .clip(AppShape.small)
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = selectedLevel.displayName,
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
                    value = selectedIndex.toFloat(),
                    onValueChange = { value ->
                        selectedIndex = value.roundToInt().coerceIn(0, levels.lastIndex)
                    },
                    valueRange = 0f..levels.lastIndex.toFloat(),
                    steps = (levels.size - 2).coerceAtLeast(0),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { config.onSave(levels[selectedIndex]) }) {
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
