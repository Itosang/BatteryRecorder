package yangfentuozi.batteryrecorder.ui.components.settings.sections

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpOffset
import yangfentuozi.batteryrecorder.R
import yangfentuozi.batteryrecorder.shared.config.SettingsConstants
import yangfentuozi.batteryrecorder.ui.components.global.M3ESwitchWidget
import yangfentuozi.batteryrecorder.ui.components.global.SplicedColumnGroup
import yangfentuozi.batteryrecorder.ui.components.settings.SettingsItem
import yangfentuozi.batteryrecorder.ui.dialog.settings.CalibrationDialog
import yangfentuozi.batteryrecorder.ui.model.SettingsUiProps
import yangfentuozi.batteryrecorder.ui.model.displayName
import yangfentuozi.batteryrecorder.ui.theme.AppShape
import yangfentuozi.batteryrecorder.shared.config.dataclass.UpdateChannel

@Composable
fun CalibrationSection(
    props: SettingsUiProps
) {
    val state = props.state
    val rootActions = props.actions
    val actions = props.actions.calibration
    var showDialog by remember { mutableStateOf(false) }
    var showUpdateChannelMenu by remember { mutableStateOf(false) }

    SplicedColumnGroup(
        title = stringResource(R.string.settings_section_general),
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        item {
            M3ESwitchWidget(
                text = stringResource(R.string.settings_check_update_on_startup),
                checked = state.checkUpdateOnStartup,
                onCheckedChange = rootActions.setCheckUpdateOnStartup
            )
        }

        item {
            Box(modifier = Modifier.fillMaxWidth()) {
                SettingsItem(
                    title = stringResource(R.string.settings_update_channel),
                    valueText = state.updateChannel.displayName,
                    onClick = { showUpdateChannelMenu = true }
                )

                Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                    DropdownMenu(
                        expanded = showUpdateChannelMenu,
                        onDismissRequest = { showUpdateChannelMenu = false },
                        shape = AppShape.large,
                        offset = DpOffset(x = 0.dp, y = (-24).dp)
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.update_channel_stable)) },
                            onClick = {
                                rootActions.setUpdateChannel(UpdateChannel.Stable)
                                showUpdateChannelMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.update_channel_prerelease)) },
                            onClick = {
                                rootActions.setUpdateChannel(UpdateChannel.Prerelease)
                                showUpdateChannelMenu = false
                            }
                        )
                    }
                }
            }
        }

        item {
            M3ESwitchWidget(
                text = stringResource(R.string.settings_dual_cell),
                checked = state.dualCellEnabled,
                onCheckedChange = actions.setDualCellEnabled
            )
        }

        item {
            M3ESwitchWidget(
                text = stringResource(R.string.settings_discharge_positive),
                checked = state.dischargeDisplayPositive,
                onCheckedChange = actions.setDischargeDisplayPositiveEnabled
            )
        }

        item {
            SettingsItem(
                title = stringResource(R.string.settings_calibration_title),
                summary = stringResource(R.string.settings_calibration_summary)
            ) { showDialog = true }
        }
    }

    if (showDialog) {
        CalibrationDialog(
            currentValue = state.calibrationValue,
            dualCellEnabled = state.dualCellEnabled,
            serviceConnected = props.serviceConnected,
            onDismiss = { showDialog = false },
            onSave = { value ->
                actions.setCalibrationValue(value)
                showDialog = false
            },
            onReset = {
                actions.setCalibrationValue(SettingsConstants.calibrationValue.def)
                showDialog = false
            }
        )
    }
}
