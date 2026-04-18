package yangfentuozi.batteryrecorder.ui.guide

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import yangfentuozi.batteryrecorder.R
import yangfentuozi.batteryrecorder.ui.theme.AppShape
import yangfentuozi.batteryrecorder.utils.navigationBarBottomPadding

@Composable
internal fun StartupGuideFeatureItem(
    icon: ImageVector,
    title: String,
    description: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            modifier = Modifier.size(48.dp),
            shape = AppShape.medium,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun StartupGuideBottomBar(
    currentStep: StartupGuideStep,
    nextEnabled: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = 24.dp,
                top = 20.dp,
                end = 24.dp,
                bottom = navigationBarBottomPadding() + 24.dp
            )
    ) {
        StartupGuideStepIndicator(
            currentStep = currentStep,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Spacer(Modifier.height(20.dp))
        if (currentStep == StartupGuideStep.INTRO) {
            Button(
                onClick = onNext,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = nextEnabled,
                shape = AppShape.large
            ) {
                Text(
                    text = stringResource(R.string.common_next),
                    fontSize = 16.sp
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    shape = AppShape.large
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.common_back), fontSize = 16.sp)
                }
                Button(
                    onClick = onNext,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    enabled = nextEnabled,
                    shape = AppShape.large
                ) {
                    val isLastStep = currentStep == StartupGuideStep.CALIBRATION
                    Text(
                        text = if (isLastStep) {
                            stringResource(R.string.common_finish)
                        } else {
                            stringResource(R.string.common_next)
                        },
                        fontSize = 16.sp
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        imageVector = if (isLastStep) Icons.Default.Check else Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null
                    )
                }
            }
        }
    }
}

@Composable
internal fun StartupGuideStepIndicator(
    currentStep: StartupGuideStep,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StartupGuideStep.entries.forEach { step ->
            val isActive = step == currentStep
            val targetWidth by animateDpAsState(
                targetValue = if (isActive) 32.dp else 8.dp,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "startupGuideStepWidth"
            )
            Box(
                modifier = Modifier
                    .width(targetWidth)
                    .height(8.dp)
                    .clip(AppShape.small)
                    .background(
                        when {
                            isActive -> MaterialTheme.colorScheme.primary
                            step.ordinal < currentStep.ordinal -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
            )
        }
    }
}
