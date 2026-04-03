package yangfentuozi.batteryrecorder.ui.components.settings.sections

import androidx.compose.foundation.layout.padding
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
import yangfentuozi.batteryrecorder.ui.components.global.SplicedColumnGroup
import yangfentuozi.batteryrecorder.ui.components.settings.SettingsItem
import yangfentuozi.batteryrecorder.ui.dialog.settings.LogLevelDialog
import yangfentuozi.batteryrecorder.ui.dialog.settings.LogLevelDialogConfig
import yangfentuozi.batteryrecorder.ui.dialog.settings.LogValueDialog
import yangfentuozi.batteryrecorder.ui.dialog.settings.LogValueDialogConfig
import yangfentuozi.batteryrecorder.ui.model.SettingsUiProps
import yangfentuozi.batteryrecorder.ui.model.displayName

// 仅用于UI
@Composable
fun LogSection(
    props: SettingsUiProps
) {
    val state = props.state
    val logActions = props.actions.log
    var showHistoryDaysDialog by remember { mutableStateOf(false) }
    var showLogLevelDialog by remember { mutableStateOf(false) }
    SplicedColumnGroup(
        title = stringResource(R.string.settings_section_logs),
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        item {
            SettingsItem(
                title = stringResource(R.string.settings_log_retention_days),
                summary = stringResource(R.string.common_days_count, state.maxHistoryDays.toInt())
            ) { showHistoryDaysDialog = true }
        }

        item {
            SettingsItem(
                title = stringResource(R.string.settings_log_level),
                summary = state.logLevel.displayName
            ) { showLogLevelDialog = true }
        }
    }

    if (showHistoryDaysDialog) {
        LogValueDialog(
            config = LogValueDialogConfig(
                title = stringResource(R.string.settings_log_retention_days),
                label = stringResource(R.string.settings_log_retention_label),
                currentValue = state.maxHistoryDays.toString(),
                errorMessage = stringResource(
                    R.string.settings_log_retention_error,
                    SettingsConstants.logMaxHistoryDays.min
                ),
                parser = { rawValue ->
                    rawValue.toLongOrNull()
                        ?.takeIf { it >= SettingsConstants.logMaxHistoryDays.min }
                },
                onDismiss = { showHistoryDaysDialog = false },
                onSave = { parsedValue ->
                    logActions.setMaxHistoryDays(parsedValue)
                    showHistoryDaysDialog = false
                },
                onReset = {
                    logActions.setMaxHistoryDays(SettingsConstants.logMaxHistoryDays.def)
                    showHistoryDaysDialog = false
                }
            )
        )
    }

    if (showLogLevelDialog) {
        LogLevelDialog(
            config = LogLevelDialogConfig(
                currentValue = state.logLevel,
                onDismiss = { showLogLevelDialog = false },
                onSave = { level ->
                    logActions.setLogLevel(level)
                    showLogLevelDialog = false
                },
                onReset = {
                    logActions.setLogLevel(SettingsConstants.logLevel.def)
                    showLogLevelDialog = false
                }
            )
        )
    }
}
