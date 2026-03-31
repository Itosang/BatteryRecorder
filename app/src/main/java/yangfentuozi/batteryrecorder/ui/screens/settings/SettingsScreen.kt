package yangfentuozi.batteryrecorder.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import yangfentuozi.batteryrecorder.ui.components.settings.sections.CalibrationSection
import yangfentuozi.batteryrecorder.ui.components.settings.sections.LogSection
import yangfentuozi.batteryrecorder.ui.components.settings.sections.PredictionSection
import yangfentuozi.batteryrecorder.ui.components.settings.sections.ServerSection
import yangfentuozi.batteryrecorder.ui.model.CalibrationActions
import yangfentuozi.batteryrecorder.ui.model.LogActions
import yangfentuozi.batteryrecorder.ui.model.PredictionActions
import yangfentuozi.batteryrecorder.ui.model.ServerActions
import yangfentuozi.batteryrecorder.ui.model.SettingsActions
import yangfentuozi.batteryrecorder.ui.model.SettingsUiProps
import yangfentuozi.batteryrecorder.ui.model.SettingsUiState
import yangfentuozi.batteryrecorder.ui.viewmodel.SettingsViewModel
import yangfentuozi.batteryrecorder.utils.batteryRecorderScaffoldInsets
import yangfentuozi.batteryrecorder.utils.bottomWithNavigationBar

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    settingsViewModel: SettingsViewModel,
    onNavigateBack: () -> Unit = {}
) {
    val appSettings by settingsViewModel.appSettings.collectAsState()
    val statisticsSettings by settingsViewModel.statisticsSettings.collectAsState()
    val serverSettings by settingsViewModel.serverSettings.collectAsState()
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
            log = LogActions(
                setMaxHistoryDays = settingsViewModel::setMaxHistoryDays,
                setLogLevel = settingsViewModel::setLogLevel
            ),
            prediction = PredictionActions(
                setGamePackages = settingsViewModel::setGamePackages,
                setSceneStatsRecentFileCount = settingsViewModel::setSceneStatsRecentFileCount,
                setPredWeightedAlgorithmEnabled = settingsViewModel::setPredWeightedAlgorithmEnabled,
                setPredWeightedAlgorithmAlphaMaxX100 = settingsViewModel::setPredWeightedAlgorithmAlphaMaxX100
            )
        )
    }
    /**
     * 这里搞一个合集是为了设置页传参方便
     * 正常来说应该使用单独的
     * */
    val settingsState = remember(appSettings, statisticsSettings, serverSettings) {
        SettingsUiState(
            checkUpdateOnStartup = appSettings.checkUpdateOnStartup,
            dualCellEnabled = appSettings.dualCellEnabled,
            dischargeDisplayPositive = appSettings.dischargeDisplayPositive,
            calibrationValue = appSettings.calibrationValue,
            recordIntervalMs = serverSettings.recordIntervalMs,
            writeLatencyMs = serverSettings.writeLatencyMs,
            batchSize = serverSettings.batchSize,
            recordScreenOffEnabled = serverSettings.screenOffRecordEnabled,
            alwaysPollingScreenStatusEnabled = serverSettings.alwaysPollingScreenStatusEnabled,
            segmentDurationMin = serverSettings.segmentDurationMin,
            rootBootAutoStartEnabled = appSettings.rootBootAutoStartEnabled,
            maxHistoryDays = serverSettings.maxHistoryDays,
            logLevel = serverSettings.logLevel,
            gamePackages = statisticsSettings.gamePackages,
            gameBlacklist = statisticsSettings.gameBlacklist,
            sceneStatsRecentFileCount = statisticsSettings.sceneStatsRecentFileCount,
            predWeightedAlgorithmEnabled = statisticsSettings.predWeightedAlgorithmEnabled,
            predWeightedAlgorithmAlphaMaxX100 = statisticsSettings.predWeightedAlgorithmAlphaMaxX100
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
        contentWindowInsets = batteryRecorderScaffoldInsets(),
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
            contentPadding = PaddingValues(
                top = 8.dp,
                bottom = padding.bottomWithNavigationBar(16.dp)
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                CalibrationSection(props = props)
            }
            item {
                ServerSection(props = props)
            }
            item {
                LogSection(props = props)
            }
            item {
                PredictionSection(props = props)
            }
        }
    }
}
