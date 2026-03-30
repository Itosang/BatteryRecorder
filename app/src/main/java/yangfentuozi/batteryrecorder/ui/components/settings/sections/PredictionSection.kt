package yangfentuozi.batteryrecorder.ui.components.settings.sections

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import yangfentuozi.batteryrecorder.shared.config.ConfigConstants
import yangfentuozi.batteryrecorder.ui.components.global.M3ESwitchWidget
import yangfentuozi.batteryrecorder.ui.components.global.SplicedColumnGroup
import yangfentuozi.batteryrecorder.ui.components.settings.SettingsItem
import yangfentuozi.batteryrecorder.ui.dialog.settings.CurrentSessionWeightDialog
import yangfentuozi.batteryrecorder.ui.dialog.settings.SceneStatsRecentFileCountDialog
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
                text = "当次记录加权",
                checked = state.predCurrentSessionWeightEnabled,
                onCheckedChange = actions.setPredCurrentSessionWeightEnabled
            )
        }

        item {
            val maxX = state.predCurrentSessionWeightMaxX100 / 100.0
            val summary = "最大 %.2fx / 半衰期 %d 分钟".format(
                maxX,
                state.predCurrentSessionWeightHalfLifeMin
            )
            SettingsItem(
                title = "加权强度",
                summary = summary
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
                actions.setSceneStatsRecentFileCount(ConfigConstants.DEF_SCENE_STATS_RECENT_FILE_COUNT)
                showRecentCountDialog = false
            }
        )
    }

    if (showWeightDialog) {
        CurrentSessionWeightDialog(
            currentMaxX100 = state.predCurrentSessionWeightMaxX100,
            currentHalfLifeMin = state.predCurrentSessionWeightHalfLifeMin,
            onDismiss = { showWeightDialog = false },
            onSave = { maxX100, halfLifeMin ->
                actions.setPredCurrentSessionWeightMaxX100(maxX100)
                actions.setPredCurrentSessionWeightHalfLifeMin(halfLifeMin)
                showWeightDialog = false
            },
            onReset = {
                actions.setPredCurrentSessionWeightMaxX100(ConfigConstants.DEF_PRED_CURRENT_SESSION_WEIGHT_MAX_X100)
                actions.setPredCurrentSessionWeightHalfLifeMin(ConfigConstants.DEF_PRED_CURRENT_SESSION_WEIGHT_HALF_LIFE_MIN)
                showWeightDialog = false
            }
        )
    }
}
