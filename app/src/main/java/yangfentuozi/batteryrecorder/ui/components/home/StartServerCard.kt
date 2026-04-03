package yangfentuozi.batteryrecorder.ui.components.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import yangfentuozi.batteryrecorder.R
import yangfentuozi.batteryrecorder.shared.util.LoggerX
import yangfentuozi.batteryrecorder.startup.RootServerStarter
import yangfentuozi.batteryrecorder.ui.theme.AppShape

private const val TAG = "StartServerCard"

@Composable
fun StartServerCard(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Row(
        modifier = modifier.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.home_action_start_root),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.home_start_service_title),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.width(16.dp))

        Button(
            shape = AppShape.SplicedGroup.single,
            onClick = {
                LoggerX.i(TAG, "[启动请求] 来源=${RootServerStarter.Source.HOME_BUTTON}，用户点击启动按钮")
                Thread {
                    RootServerStarter.start(
                        context = context,
                        source = RootServerStarter.Source.HOME_BUTTON
                    )
                }.start()
            }
        ) {
            Text(stringResource(R.string.home_action_start_service))
        }
    }
}
