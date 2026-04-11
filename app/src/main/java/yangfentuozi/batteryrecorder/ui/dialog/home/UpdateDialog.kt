package yangfentuozi.batteryrecorder.ui.dialog.home

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import kotlinx.coroutines.delay
import yangfentuozi.batteryrecorder.R
import yangfentuozi.batteryrecorder.ui.components.global.MarkdownText
import yangfentuozi.batteryrecorder.ui.model.displayName
import yangfentuozi.batteryrecorder.utils.AppDownloader
import yangfentuozi.batteryrecorder.utils.AppUpdate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateDialog(
    update: AppUpdate,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var downloadStarted by remember { mutableStateOf(false) }

    val installPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
    }

    LaunchedEffect(downloadStarted) {
        if (!downloadStarted) return@LaunchedEffect
        var checking = true
        while (checking) {
            delay(1500)
            val downloadId = AppDownloader.getDownloadId(context)
            if (downloadId == -1L) {
                checking = false
                continue
            }
            val installed = AppDownloader.checkAndInstallForId(context, downloadId)
            if (installed) {
                onDismiss()
                checking = false
            }
        }
    }

    val startDownload = remember {
        {
            downloadStarted = true
            AppDownloader.downloadApk(context, update.downloadUrl, update.versionName)
        }
    }

    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 640.dp)
                    .padding(24.dp)
            ) {
                Text(
                    text = stringResource(R.string.update_found_new_version),
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "${update.versionName} (${update.versionCode}) ${update.updateChannel.displayName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
                if (update.body.isNotBlank()) {
                    Column(
                        modifier = Modifier
                            .padding(top = 16.dp)
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                    ) {
                        MarkdownText(markdown = update.body)
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.common_cancel))
                    }
                    TextButton(onClick = {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                update.downloadUrl.toUri()
                            )
                        )
                        onDismiss()
                    }) {
                        Text(stringResource(R.string.update_open_in_browser))
                    }
                    TextButton(onClick = {
                        if (AppDownloader.canRequestPackageInstalls(context)) {
                            startDownload()
                            onDismiss()
                        } else {
                            val intent = AppDownloader.createInstallPermissionIntent()
                            installPermissionLauncher.launch(intent)
                        }
                    }) {
                        Text(stringResource(R.string.update_download))
                    }
                }
            }
        }
    }
}
