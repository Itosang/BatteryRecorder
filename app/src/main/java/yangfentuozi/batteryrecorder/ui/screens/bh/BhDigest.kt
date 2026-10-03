package yangfentuozi.batteryrecorder.ui.screens.bh

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import yangfentuozi.batteryrecorder.data.bh.BhRangeStats
import yangfentuozi.batteryrecorder.ui.components.global.SplicedColumnGroup
import yangfentuozi.batteryrecorder.ui.components.global.StatRow

/** 排行分组：标题 + StatRow 列表。 */
@Composable
fun BhRankGroup(
    title: String,
    rows: List<BhRangeStats.Rank>,
    valueSuffix: String = " 次",
    limit: Int = 8
) {
    if (rows.isEmpty()) return
    SplicedColumnGroup(title = title) {
        rows.take(limit).forEach { row ->
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    StatRow(label = row.name, value = "${row.count}$valueSuffix")
                }
            }
        }
    }
}
