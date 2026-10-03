package yangfentuozi.batteryrecorder.ui.components.settings.sections

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import yangfentuozi.batteryrecorder.R
import yangfentuozi.batteryrecorder.data.bh.BhFeature
import yangfentuozi.batteryrecorder.ui.components.global.M3ESwitchWidget
import yangfentuozi.batteryrecorder.ui.components.global.SplicedColumnGroup
import yangfentuozi.batteryrecorder.ui.model.SettingsUiProps

/**
 * 「查看厂商系统原生日志」设置分组。
 *
 * 仅在检测到澎湃OS 且 battery-history 数据源存在 .bh 文件时展示（可用性检测见 [BhFeature]）；
 * 开关默认开启，关闭后隐藏相关入口并停止数据同步。
 */
@Composable
fun VendorLogSection(props: SettingsUiProps) {
    val context = LocalContext.current
    var dataAvailable by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        dataAvailable = BhFeature.isDataAvailable(context)
    }
    if (!dataAvailable) return

    SplicedColumnGroup(
        title = stringResource(R.string.settings_section_vendor_system_log),
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        item {
            M3ESwitchWidget(
                text = stringResource(R.string.settings_vendor_system_log),
                summary = stringResource(R.string.settings_vendor_system_log_summary),
                checked = props.state.vendorSystemLogEnabled,
                onCheckedChange = props.actions.setVendorSystemLogEnabled
            )
        }
    }
}
