package yangfentuozi.batteryrecorder.ui.screens.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import yangfentuozi.batteryrecorder.ipc.Service
import yangfentuozi.batteryrecorder.ui.components.settings.sections.AppSection
import yangfentuozi.batteryrecorder.ui.components.settings.sections.CalibrationSection
import yangfentuozi.batteryrecorder.ui.components.settings.sections.PredictionSection
import yangfentuozi.batteryrecorder.ui.components.settings.sections.ServerSection
import yangfentuozi.batteryrecorder.ui.model.CalibrationActions
import yangfentuozi.batteryrecorder.ui.model.PredictionActions
import yangfentuozi.batteryrecorder.ui.model.ServerActions
import yangfentuozi.batteryrecorder.ui.model.SettingsActions
import yangfentuozi.batteryrecorder.ui.model.SettingsUiProps
import yangfentuozi.batteryrecorder.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    settingsViewModel: SettingsViewModel,
    onNavigateBack: () -> Unit = {}
) {
    val settingsState by settingsViewModel.settingsUiState.collectAsState()
    val serviceConnected = Service.service != null
    val actions = remember(settingsViewModel) {
        SettingsActions(
            setCheckUpdateOnStartup = settingsViewModel::setCheckUpdateOnStartup,
            calibration = CalibrationActions(
                setDualCellEnabled = settingsViewModel::setDualCellEnabled,
                setDischargeDisplayPositiveEnabled = settingsViewModel::setDischargeDisplayPositiveEnabled,
                setCalibrationValue = settingsViewModel::setCalibrationValue
            ),
            server = ServerActions(
                setRecordIntervalMs = settingsViewModel::setRecordIntervalMs,
                setWriteLatencyMs = settingsViewModel::setWriteLatencyMs,
                setBatchSize = settingsViewModel::setBatchSize,
                setScreenOffRecordEnabled = settingsViewModel::setScreenOffRecordEnabled,
                setAlwaysPollingScreenStatusEnabled = settingsViewModel::setAlwaysPollingScreenStatusEnabled,
                setSegmentDurationMin = settingsViewModel::setSegmentDurationMin,
                setRootBootAutoStartEnabled = settingsViewModel::setRootBootAutoStartEnabled
            ),
            prediction = PredictionActions(
                setGamePackages = settingsViewModel::setGamePackages,
                setSceneStatsRecentFileCount = settingsViewModel::setSceneStatsRecentFileCount,
                setPredCurrentSessionWeightEnabled = settingsViewModel::setPredCurrentSessionWeightEnabled,
                setPredCurrentSessionWeightMaxX100 = settingsViewModel::setPredCurrentSessionWeightMaxX100,
                setPredCurrentSessionWeightHalfLifeMin = settingsViewModel::setPredCurrentSessionWeightHalfLifeMin
            )
        )
    }
    val props = SettingsUiProps(
        state = settingsState,
        actions = actions,
        serviceConnected = serviceConnected
    )

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("设置") },
                scrollBehavior = scrollBehavior,
                windowInsets = TopAppBarDefaults.windowInsets.add(WindowInsets(left = 12.dp)),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp)
        ) {
            item {
                AppSection(props = props)
            }
            item { Spacer(modifier = Modifier.size(16.dp)) }
            item {
                // 校准设置
                CalibrationSection(props = props)
            }
            item { Spacer(modifier = Modifier.size(16.dp)) }
            item {
                // 服务器设置
                ServerSection(props = props)
            }
            item { Spacer(modifier = Modifier.size(16.dp)) }
            item {
                PredictionSection(props = props)
            }
        }
    }
}
