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
import yangfentuozi.batteryrecorder.ui.components.global.M3ESwitchWidget
import yangfentuozi.batteryrecorder.ui.components.global.SplicedColumnGroup
import yangfentuozi.batteryrecorder.ui.components.settings.SettingsItem
import yangfentuozi.batteryrecorder.ui.dialog.settings.CalibrationDialog
import yangfentuozi.batteryrecorder.ui.model.SettingsUiProps

@Composable
fun CalibrationSection(
    props: SettingsUiProps
) {
    val state = props.state
    val rootActions = props.actions
    val actions = props.actions.calibration
    var showDialog by remember { mutableStateOf(false) }

    SplicedColumnGroup(
        title = "常规",
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        item {
            M3ESwitchWidget(
                text = "启动时检测更新",
                checked = state.checkUpdateOnStartup,
                onCheckedChange = rootActions.setCheckUpdateOnStartup
            )
        }

        item {
            M3ESwitchWidget(
                text = "串联双电芯",
                checked = state.dualCellEnabled,
                onCheckedChange = actions.setDualCellEnabled
            )
        }

        item {
            M3ESwitchWidget(
                text = "放电也显示正值",
                checked = state.dischargeDisplayPositive,
                onCheckedChange = actions.setDischargeDisplayPositiveEnabled
            )
        }

        item {
            SettingsItem(
                title = "电流单位校准",
                summary = "调整电流读数的倍率"
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
