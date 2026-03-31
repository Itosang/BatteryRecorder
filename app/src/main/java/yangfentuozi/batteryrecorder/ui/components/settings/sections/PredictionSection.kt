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
import yangfentuozi.batteryrecorder.ui.dialog.settings.SceneStatsRecentFileCountDialog
import yangfentuozi.batteryrecorder.ui.dialog.settings.WeightedAlgorithmDialog
import yangfentuozi.batteryrecorder.ui.model.SettingsUiProps

@Composable
fun PredictionSection(
    props: SettingsUiProps
) {
    val state = props.state
    val actions = props.actions.prediction
    var showWeightDialog by remember { mutableStateOf(false) }
    var showRecentCountDialog by remember { mutableStateOf(false) }

    SplicedColumnGroup(
        title = "预测",
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        item {
            PredictionGameFilterItem(
                gamePackages = state.gamePackages,
                gameBlacklist = state.gameBlacklist,
                onGamePackagesChange = actions.setGamePackages
            )
        }

        item {
            SettingsItem(
                title = "样本次数",
                summary = "最近 ${state.sceneStatsRecentFileCount} 次"
            ) { showRecentCountDialog = true }
        }

        item {
            M3ESwitchWidget(
                text = "启用加权算法",
                checked = state.predWeightedAlgorithmEnabled,
                onCheckedChange = actions.setPredWeightedAlgorithmEnabled
            )
        }

        item {
            SettingsItem(
                title = "加权强度",
                summary = "最大影响 ${state.predWeightedAlgorithmAlphaMaxX100}%"
            ) { showWeightDialog = true }
        }
    }

    if (showRecentCountDialog) {
        SceneStatsRecentFileCountDialog(
            currentValue = state.sceneStatsRecentFileCount,
            onDismiss = { showRecentCountDialog = false },
            onSave = { count ->
                actions.setSceneStatsRecentFileCount(count)
                showRecentCountDialog = false
            },
            onReset = {
                actions.setSceneStatsRecentFileCount(SettingsConstants.sceneStatsRecentFileCount.def)
                showRecentCountDialog = false
            }
        )
    }

    if (showWeightDialog) {
        WeightedAlgorithmDialog(
            currentAlphaMaxX100 = state.predWeightedAlgorithmAlphaMaxX100,
            onDismiss = { showWeightDialog = false },
            onSave = { alphaMaxX100 ->
                actions.setPredWeightedAlgorithmAlphaMaxX100(alphaMaxX100)
                showWeightDialog = false
            },
            onReset = {
                actions.setPredWeightedAlgorithmAlphaMaxX100(
                    SettingsConstants.predWeightedAlgorithmAlphaMaxX100.def
                )
                showWeightDialog = false
            }
        )
    }
}
