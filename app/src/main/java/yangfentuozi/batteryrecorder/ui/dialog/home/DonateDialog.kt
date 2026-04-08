package yangfentuozi.batteryrecorder.ui.dialog.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.circle
import org.intellij.lang.annotations.Language
import yangfentuozi.batteryrecorder.R
import yangfentuozi.batteryrecorder.ui.theme.AppShape

/**
 * 首页捐赠弹窗，展示维护说明与收款码。
 *
 * @param onDismiss 关闭弹窗回调。
 * @return 无返回值。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DonateDialog(onDismiss: () -> Unit) {
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.donate_dialog_title),
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = stringResource(R.string.donate_dialog_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Image(
                    painter = painterResource(R.drawable.donate_qr),
                    contentDescription = stringResource(R.string.donate_dialog_qr_content_description),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .sizeIn(maxWidth = 360.dp)
                        .padding(horizontal = 8.dp)
                        .clip(AppShape.extraLarge)
                    ,
                    contentScale = ContentScale.Fit
                )
                TextButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onDismiss
                ) {
                    Text(stringResource(R.string.common_close))
                }
            }
        }
    }
}

@Preview(locale = "zh")
@Composable
private fun DonateDialogPreview() {
    MaterialTheme {
        DonateDialog(onDismiss = {})
    }
}
