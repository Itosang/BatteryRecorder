package yangfentuozi.batteryrecorder.ui.dialog.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import yangfentuozi.batteryrecorder.R

private data class CommandItem(
    val title: String,
    val command: String
)

@Composable
fun AdbGuideDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val shellCommand = remember {
        "CLASSPATH=$(pm path yangfentuozi.batteryrecorder | cut -d: -f2) setsid app_process /system/bin yangfentuozi.batteryrecorder.server.Main </dev/null >/dev/null 2>&1 &"
    }
    val commandItems = remember(shellCommand) {
        listOf(
            CommandItem(
                title = context.getString(R.string.adb_guide_pc_title),
                command = "adb shell \"$shellCommand\""
            ),
            CommandItem(
                title = context.getString(R.string.adb_guide_shell_title),
                command = shellCommand
            )
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.adb_guide_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.adb_guide_step_enable_debug),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.adb_guide_step_run_command),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                commandItems.forEachIndexed { index, item ->
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(6.dp))
                    CommandBox(
                        command = item.command,
                        onCopy = { copyCommand(context, item.command) }
                    )
                    if (index != commandItems.lastIndex) {
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_close))
            }
        }
    )
}

@Composable
private fun CommandBox(
    command: String,
    onCopy: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = command,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState())
            )
            IconButton(
                onClick = onCopy,
                modifier = Modifier
                    .height(MaterialTheme.typography.bodySmall.lineHeight.value.dp)
                    .width(MaterialTheme.typography.bodySmall.lineHeight.value.dp + 7.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = stringResource(R.string.adb_guide_copy_command)
                )
            }
        }
    }
}

private fun copyCommand(context: Context, command: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("command", command))
    Toast.makeText(context, context.getString(R.string.adb_guide_copied), Toast.LENGTH_SHORT).show()
}

@Preview
@Composable
fun AdbGuideDialogPreview() {
    AdbGuideDialog(onDismiss = {})
}
