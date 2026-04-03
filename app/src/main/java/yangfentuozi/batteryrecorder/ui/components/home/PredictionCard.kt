package yangfentuozi.batteryrecorder.ui.components.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import yangfentuozi.batteryrecorder.R
import yangfentuozi.batteryrecorder.appString
import yangfentuozi.batteryrecorder.data.history.SceneStats
import yangfentuozi.batteryrecorder.ui.components.global.StatRow
import yangfentuozi.batteryrecorder.ui.model.HomePredictionDisplay
import yangfentuozi.batteryrecorder.utils.computePowerW
import yangfentuozi.batteryrecorder.utils.formatFullRemainingTime
import yangfentuozi.batteryrecorder.utils.formatRemainingTime

/**
 * 首页卡片统一显示“当前电量 / 满电”两种口径，缺数据时保持一致文案。
 */
private fun formatPredictionPair(
    currentHours: Double?,
    fullHours: Double?
): String {
    val currentText = currentHours?.let(::formatRemainingTime) ?: appString(R.string.common_insufficient_data)
    val fullText = fullHours?.let(::formatFullRemainingTime) ?: appString(R.string.common_insufficient_data)
    return "$currentText / $fullText"
}

@Composable
fun PredictionCard(
    predictionDisplay: HomePredictionDisplay?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            // 卡片整体可点击，入口语义是进入更细的应用预测详情页。
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        val confidenceLevel = predictionDisplay?.confidenceLevel


        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.home_prediction_title),
                style = MaterialTheme.typography.titleMedium
            )
            StatusIndicator(confidenceLevel)
        }
        Spacer(Modifier.height(12.dp))

        if (confidenceLevel == null) {
            Text(
                text = predictionDisplay?.insufficientReason ?: stringResource(R.string.common_insufficient_data),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            StatRow(
                label = stringResource(R.string.home_prediction_screen_off),
                value = formatPredictionPair(
                    currentHours = predictionDisplay.screenOffCurrentHours,
                    fullHours = predictionDisplay.screenOffFullHours
                ),
                modifier = Modifier.padding(vertical = 4.dp)
            )
            StatRow(
                label = stringResource(R.string.home_prediction_screen_on_daily),
                value = formatPredictionPair(
                    currentHours = predictionDisplay.screenOnDailyCurrentHours,
                    fullHours = predictionDisplay.screenOnDailyFullHours
                ),
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
    }
}

@Composable
fun SceneStatsCard(
    sceneStats: SceneStats?,
    dualCellEnabled: Boolean,
    calibrationValue: Int,
    dischargeDisplayPositive: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.home_scene_stats_title),
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(12.dp))

        if (sceneStats == null) {
            Text(
                text = stringResource(R.string.common_no_data),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            // 先按统一公式转瓦特，再在展示层决定是否将放电视为正值。
            val offPowerText = if (sceneStats.screenOffTotalMs > 0) {
                var w = computePowerW(
                    sceneStats.screenOffAvgPowerRaw,
                    dualCellEnabled,
                    calibrationValue
                )
                if (dischargeDisplayPositive) w = kotlin.math.abs(w)
                String.format(LocalLocale.current.platformLocale, "%.2f W", w)
            } else stringResource(R.string.common_insufficient_data)

            StatRow(
                label = stringResource(R.string.home_scene_stats_screen_off_avg),
                value = offPowerText,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            // Locale 走 Compose 当前上下文，避免与系统配置更新不同步。
            val dailyPowerText = if (sceneStats.screenOnDailyTotalMs > 0) {
                var w = computePowerW(
                    sceneStats.screenOnDailyAvgPowerRaw,
                    dualCellEnabled,
                    calibrationValue
                )
                if (dischargeDisplayPositive) w = kotlin.math.abs(w)
                String.format(LocalLocale.current.platformLocale, "%.2f W", w)
            } else stringResource(R.string.common_insufficient_data)

            StatRow(
                label = stringResource(R.string.home_scene_stats_screen_on_daily),
                value = dailyPowerText,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
    }
}
