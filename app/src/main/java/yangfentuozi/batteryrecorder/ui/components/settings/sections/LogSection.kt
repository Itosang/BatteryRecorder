package yangfentuozi.batteryrecorder.ui.components.settings.sections

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
            title = "日志",
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            item {
                SettingsItem(
                    title = "日志保留天数",
                    summary = "${state.maxHistoryDays} 天"
                ) { showHistoryDaysDialog = true }
            }

            item {
                SettingsItem(
                    title = "日志级别",
                    summary = state.logLevel.displayName
                ) { showLogLevelDialog = true }
            }
        }

    if (showHistoryDaysDialog) {
        LogValueDialog(
            config = LogValueDialogConfig(
                title = "日志保留天数",
                label = "保留天数",
                currentValue = state.maxHistoryDays.toString(),
                errorMessage = "请输入大于等于 ${SettingsConstants.logMaxHistoryDays.min} 的整数",
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
