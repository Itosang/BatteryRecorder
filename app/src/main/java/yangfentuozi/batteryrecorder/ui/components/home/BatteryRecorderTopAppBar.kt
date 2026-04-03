package yangfentuozi.batteryrecorder.ui.components.home

import android.content.Intent
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import yangfentuozi.batteryrecorder.R
import yangfentuozi.batteryrecorder.ui.theme.AppShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatteryRecorderTopAppBar(
    onSettingsClick: () -> Unit = {},
    onRefreshClick: () -> Unit = {},
    onExportLogsClick: () -> Unit = {},
    onStopServerClick: () -> Unit = {},
    onAboutClick: () -> Unit = {},
    showStopServer: Boolean = true,
    showBackButton: Boolean = false,
    onBackClick: () -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current

    TopAppBar(
        title = {
            Text(stringResource(R.string.app_name))
        },
        navigationIcon = {
            if (showBackButton) {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                }
            }
        },
        actions = {
            if (!showBackButton) {
                IconButton(onClick = onRefreshClick) {
                    Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.menu_refresh))
                }
                IconButton(onClick = onSettingsClick) {
                    Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.menu_settings))
                }
            }
            IconButton(onClick = { showMenu = !showMenu }) {
                Icon(Icons.Default.MoreVert, null)
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                shape = AppShape.large,
                offset = DpOffset(x = 0.dp, y = (-48).dp),
                modifier = Modifier.widthIn(min = 160.dp)
            ) {
                if (!showBackButton && showStopServer) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_stop_service)) },
                        onClick = {
                            showMenu = false
                            onStopServerClick()
                        }
                    )
                }
                if (!showBackButton) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_export_logs)) },
                        onClick = {
                            showMenu = false
                            onExportLogsClick()
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_user_guide)) },
                    onClick = {
                        showMenu = false
                        context.startActivity(Intent(Intent.ACTION_VIEW, "https://itosang.github.io/BatteryRecorder".toUri()))
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_about)) },
                    onClick = {
                        showMenu = false
                        onAboutClick()
                    }
                )
            }
        }
    )
}
