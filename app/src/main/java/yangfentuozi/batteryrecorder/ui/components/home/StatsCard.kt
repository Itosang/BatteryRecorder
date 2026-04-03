package yangfentuozi.batteryrecorder.ui.components.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import yangfentuozi.batteryrecorder.R
import yangfentuozi.batteryrecorder.data.history.HistorySummary
import yangfentuozi.batteryrecorder.ui.components.global.StatRow
import yangfentuozi.batteryrecorder.utils.formatPower

@Composable
fun StatsCard(
    title: String,
    summary: HistorySummary?,
    modifier: Modifier = Modifier,
    dualCellEnabled: Boolean,
    calibrationValue: Int,
    onClick: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .fillMaxWidth()
            .fillMaxHeight()
            .padding(16.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if (summary != null) {
            Spacer(Modifier.height(12.dp))
            StatRow(
                stringResource(R.string.home_stats_record_count),
                stringResource(R.string.common_times_count, summary.recordCount),
                modifier = Modifier.padding(vertical = 4.dp)
            )
            StatRow(
                stringResource(R.string.home_stats_average_power),
                formatPower(
                    powerW = summary.averagePower,
                    dualCellEnabled = dualCellEnabled,
                    calibrationValue = calibrationValue
                ),
                modifier = Modifier.padding(vertical = 4.dp)
            )
        } else {
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.common_no_data),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
