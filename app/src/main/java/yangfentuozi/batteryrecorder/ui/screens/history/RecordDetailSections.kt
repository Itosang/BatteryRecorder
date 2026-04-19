package yangfentuozi.batteryrecorder.ui.screens.history

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import yangfentuozi.batteryrecorder.R
import yangfentuozi.batteryrecorder.data.history.HistoryRecord
import yangfentuozi.batteryrecorder.shared.data.BatteryStatus
import yangfentuozi.batteryrecorder.ui.components.charts.FixedPowerAxisMode
import yangfentuozi.batteryrecorder.ui.components.charts.PowerCapacityChart
import yangfentuozi.batteryrecorder.ui.components.charts.PowerCurveMode
import yangfentuozi.batteryrecorder.ui.components.charts.RecordChartCurveVisibility
import yangfentuozi.batteryrecorder.ui.components.global.SplicedColumnGroup
import yangfentuozi.batteryrecorder.ui.theme.AppShape
import yangfentuozi.batteryrecorder.ui.viewmodel.RecordAppDetailUiEntry
import yangfentuozi.batteryrecorder.ui.viewmodel.RecordDetailChartUiState
import yangfentuozi.batteryrecorder.ui.viewmodel.RecordDetailPowerUiState
import yangfentuozi.batteryrecorder.utils.AppIconMemoryCache
import yangfentuozi.batteryrecorder.utils.computeEnergyWh
import yangfentuozi.batteryrecorder.utils.formatDateTime
import yangfentuozi.batteryrecorder.utils.formatDetailDuration
import yangfentuozi.batteryrecorder.utils.formatDurationHours
import yangfentuozi.batteryrecorder.utils.formatEnergyForDischargeDetail
import yangfentuozi.batteryrecorder.utils.formatPower
import java.util.Locale

// 充电刚开始阶段经常出现短暂反向抖动，不希望仅靠这段预热噪声就把整张图切到双向轴。
private const val CHARGING_NEGATIVE_AXIS_DETECTION_IGNORE_PERCENT = 10

/**
 * 详情页顶部摘要区。
 *
 * 这里保留统一的分组容器与公共时间信息，再把充电/放电差异下沉到两个内容块，
 * 避免主 Screen 同时维护多段交叉分支。
 *
 * @param detailState 当前详情记录。
 * @param powerUiState 详情页功耗统计；充电页只复用其中的部分统计字段。
 * @param chargeDetailBatteryInfoText 充电页电池信息文案。
 * @param dualCellEnabled 双电芯功率展示配置。
 * @param calibrationValue 功率换算倍率。
 * @param useMahForDischargeDetail 放电页是否使用 mAh 展示能量。
 * @param locale 当前区域设置。
 */
@Composable
internal fun RecordDetailSummarySection(
    detailState: HistoryRecord?,
    powerUiState: RecordDetailPowerUiState?,
    chargeDetailBatteryInfoText: String?,
    dualCellEnabled: Boolean,
    calibrationValue: Int,
    useMahForDischargeDetail: Boolean,
    locale: Locale
) {
    val detail = detailState ?: return
    val stats = detail.stats
    val durationMs = stats.endTime - stats.startTime
    val typeLabel = if (detail.type == BatteryStatus.Charging) {
        stringResource(R.string.history_record_type_charging)
    } else {
        stringResource(R.string.history_record_type_discharging)
    }

    SplicedColumnGroup(title = typeLabel) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                InfoRow(
                    stringResource(R.string.history_info_time),
                    "${formatDateTime(stats.startTime)} - ${formatDateTime(stats.endTime)} (${
                        formatDurationHours(durationMs)
                    })"
                )
                if (detail.type == BatteryStatus.Charging) {
                    ChargingRecordSummaryContent(
                        detail = detail,
                        powerUiState = powerUiState,
                        chargeDetailBatteryInfoText = chargeDetailBatteryInfoText,
                        dualCellEnabled = dualCellEnabled,
                        calibrationValue = calibrationValue,
                        durationMs = durationMs,
                        locale = locale
                    )
                } else {
                    DischargingRecordSummaryContent(
                        detail = detail,
                        powerUiState = powerUiState,
                        dualCellEnabled = dualCellEnabled,
                        calibrationValue = calibrationValue,
                        useMahForDischargeDetail = useMahForDischargeDetail,
                        locale = locale
                    )
                }
                InfoRow(
                    stringResource(R.string.history_info_id),
                    detail.name.dropLast(4)
                )
            }
        }
    }
}

/**
 * 充电详情摘要内容。
 *
 * 充电页与放电页共享同一张卡片外壳，但业务语义明显不同，因此单独抽成内容块，
 * 避免在主页面里穿插“如果是充电则...”的条件判断。
 *
 * @param detail 当前详情记录。
 * @param powerUiState 详情页统计。
 * @param chargeDetailBatteryInfoText 电池信息文案。
 * @param dualCellEnabled 双电芯功率展示配置。
 * @param calibrationValue 功率换算倍率。
 * @param durationMs 当前记录时长。
 * @param locale 当前区域设置。
 */
@Composable
private fun ChargingRecordSummaryContent(
    detail: HistoryRecord,
    powerUiState: RecordDetailPowerUiState?,
    chargeDetailBatteryInfoText: String?,
    dualCellEnabled: Boolean,
    calibrationValue: Int,
    durationMs: Long,
    locale: Locale
) {
    val stats = detail.stats
    InfoRow(
        stringResource(R.string.history_info_battery_info),
        chargeDetailBatteryInfoText ?: error("充电详情缺少电池信息")
    )
    powerUiState?.capacityChange?.let { capacityChange ->
        val displayWh = powerUiState.totalTransferredWh ?: computeEnergyWh(
            rawPower = stats.averagePower,
            durationMs = durationMs,
            dualCellEnabled = dualCellEnabled,
            calibrationValue = calibrationValue
        )
        val capacityChangeText = buildString {
            append("${capacityChange.totalPercent}%")
            append(" - ")
            append(
                String.format(
                    locale,
                    "%.3fWh",
                    displayWh
                )
            )
        }
        InfoRow(
            stringResource(R.string.history_info_capacity_change),
            capacityChangeText
        )
    }
    InfoRow(
        stringResource(R.string.history_info_screen_on),
        formatDurationHours(stats.screenOnTimeMs)
    )
    InfoRow(
        stringResource(R.string.history_info_screen_off),
        formatDurationHours(stats.screenOffTimeMs)
    )
}

/**
 * 放电详情摘要内容。
 *
 * 放电页需要展示功耗统计、亮灭屏能量拆分等仅放电场景成立的信息，
 * 将这些逻辑集中在这里可以让主页面只保留“选择哪块内容”的职责。
 *
 * @param detail 当前详情记录。
 * @param powerUiState 详情页统计。
 * @param dualCellEnabled 双电芯功率展示配置。
 * @param calibrationValue 功率换算倍率。
 * @param useMahForDischargeDetail 是否使用 mAh 展示能量。
 * @param locale 当前区域设置。
 */
@Composable
private fun DischargingRecordSummaryContent(
    detail: HistoryRecord,
    powerUiState: RecordDetailPowerUiState?,
    dualCellEnabled: Boolean,
    calibrationValue: Int,
    useMahForDischargeDetail: Boolean,
    locale: Locale
) {
    val stats = detail.stats
    powerUiState?.let {
        RecordDetailPowerSection(
            powerUiState = it,
            dualCellEnabled = dualCellEnabled,
            calibrationValue = calibrationValue
        )
    }
    InfoRow(
        stringResource(R.string.history_info_screen_on),
        buildDischargingScreenDurationText(
            durationText = formatDurationHours(stats.screenOnTimeMs),
            energyWh = powerUiState?.screenOnConsumedWh,
            capacityPercent = powerUiState?.capacityChange?.screenOnPercent,
            useMahForDischargeDetail = useMahForDischargeDetail,
            locale = locale
        )
    )
    InfoRow(
        stringResource(R.string.history_info_screen_off),
        buildDischargingScreenDurationText(
            durationText = formatDurationHours(stats.screenOffTimeMs),
            energyWh = powerUiState?.screenOffConsumedWh,
            capacityPercent = powerUiState?.capacityChange?.screenOffPercent,
            useMahForDischargeDetail = useMahForDischargeDetail,
            locale = locale
        )
    )
}

/**
 * 普通模式下的记录详情图表区。
 *
 * 图表仍然是一套共享实现，但功率轴、趋势曲线和视口控制都按记录类型解释，
 * 因此把策略判断收口在这个区块内，而不是散落回主页面。
 *
 * @param detailType 当前详情类型。
 * @param chartUiState 图表状态。
 * @param isTargetRecordLoaded 当前详情是否已经和目标记录对齐。
 * @param isRecordChartLoading 图表是否仍在加载。
 * @param recordStartTime 记录起始时间。
 * @param recordScreenOffEnabled 是否保留息屏点。
 * @param dischargeDisplayPositive 是否将放电展示为正值。
 * @param powerCurveMode 功率曲线模式偏好。
 * @param showCapacity 是否显示电量曲线。
 * @param showTemp 是否显示温度曲线。
 * @param showVoltage 是否显示电压曲线。
 * @param showAppIcons 是否显示应用图标。
 * @param onToggleFullscreen 切换全屏。
 * @param onPowerCurveModeChange 更新功率曲线模式。
 * @param onShowCapacityChange 更新电量曲线开关。
 * @param onShowTempChange 更新温度曲线开关。
 * @param onShowVoltageChange 更新电压曲线开关。
 * @param onShowAppIconsChange 更新应用图标开关。
 */
@Composable
internal fun RecordDetailChartSection(
    detailType: BatteryStatus,
    chartUiState: RecordDetailChartUiState,
    isTargetRecordLoaded: Boolean,
    isRecordChartLoading: Boolean,
    recordStartTime: Long?,
    recordScreenOffEnabled: Boolean,
    dischargeDisplayPositive: Boolean,
    powerCurveMode: PowerCurveMode,
    showCapacity: Boolean,
    showTemp: Boolean,
    showVoltage: Boolean,
    showAppIcons: Boolean,
    onToggleFullscreen: () -> Unit,
    onPowerCurveModeChange: (PowerCurveMode) -> Unit,
    onShowCapacityChange: (Boolean) -> Unit,
    onShowTempChange: (Boolean) -> Unit,
    onShowVoltageChange: (Boolean) -> Unit,
    onShowAppIconsChange: (Boolean) -> Unit
) {
    SplicedColumnGroup(title = stringResource(R.string.history_chart_section_title)) {
        item {
            Column(modifier = Modifier.padding(12.dp)) {
                RecordDetailChartContent(
                    modifier = Modifier.fillMaxWidth(),
                    detailType = detailType,
                    chartUiState = chartUiState,
                    isTargetRecordLoaded = isTargetRecordLoaded,
                    isRecordChartLoading = isRecordChartLoading,
                    recordStartTime = recordStartTime,
                    recordScreenOffEnabled = recordScreenOffEnabled,
                    dischargeDisplayPositive = dischargeDisplayPositive,
                    powerCurveMode = powerCurveMode,
                    showCapacity = showCapacity,
                    showTemp = showTemp,
                    showVoltage = showVoltage,
                    showAppIcons = showAppIcons,
                    isFullscreenMode = false,
                    fullscreenViewportStartMs = null,
                    onToggleFullscreen = onToggleFullscreen,
                    onPowerCurveModeChange = onPowerCurveModeChange,
                    onShowCapacityChange = onShowCapacityChange,
                    onShowTempChange = onShowTempChange,
                    onShowVoltageChange = onShowVoltageChange,
                    onShowAppIconsChange = onShowAppIconsChange,
                    onFullscreenViewportStartChange = {}
                )
            }
        }
    }
}

/**
 * 全屏模式下的记录详情图表。
 *
 * 全屏仅复用同一张图表组件，并额外启用局部视口拖动；
 * 这样普通模式和全屏模式不会分裂成两套 UI 结构。
 *
 * @param detailType 当前详情类型。
 * @param chartUiState 图表状态。
 * @param isTargetRecordLoaded 当前详情是否已经和目标记录对齐。
 * @param isRecordChartLoading 图表是否仍在加载。
 * @param recordStartTime 记录起始时间。
 * @param recordScreenOffEnabled 是否保留息屏点。
 * @param dischargeDisplayPositive 是否将放电展示为正值。
 * @param powerCurveMode 功率曲线模式偏好。
 * @param showCapacity 是否显示电量曲线。
 * @param showTemp 是否显示温度曲线。
 * @param showVoltage 是否显示电压曲线。
 * @param showAppIcons 是否显示应用图标。
 * @param fullscreenViewportStartMs 当前全屏视口起点。
 * @param onToggleFullscreen 切换全屏。
 * @param onPowerCurveModeChange 更新功率曲线模式。
 * @param onShowCapacityChange 更新电量曲线开关。
 * @param onShowTempChange 更新温度曲线开关。
 * @param onShowVoltageChange 更新电压曲线开关。
 * @param onShowAppIconsChange 更新应用图标开关。
 * @param onFullscreenViewportStartChange 更新全屏视口起点。
 */
@Composable
internal fun RecordDetailFullscreenChart(
    detailType: BatteryStatus,
    chartUiState: RecordDetailChartUiState,
    isTargetRecordLoaded: Boolean,
    isRecordChartLoading: Boolean,
    recordStartTime: Long?,
    recordScreenOffEnabled: Boolean,
    dischargeDisplayPositive: Boolean,
    powerCurveMode: PowerCurveMode,
    showCapacity: Boolean,
    showTemp: Boolean,
    showVoltage: Boolean,
    showAppIcons: Boolean,
    fullscreenViewportStartMs: Long?,
    onToggleFullscreen: () -> Unit,
    onPowerCurveModeChange: (PowerCurveMode) -> Unit,
    onShowCapacityChange: (Boolean) -> Unit,
    onShowTempChange: (Boolean) -> Unit,
    onShowVoltageChange: (Boolean) -> Unit,
    onShowAppIconsChange: (Boolean) -> Unit,
    onFullscreenViewportStartChange: (Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center
    ) {
        RecordDetailChartContent(
            modifier = Modifier.fillMaxWidth(),
            detailType = detailType,
            chartUiState = chartUiState,
            isTargetRecordLoaded = isTargetRecordLoaded,
            isRecordChartLoading = isRecordChartLoading,
            recordStartTime = recordStartTime,
            recordScreenOffEnabled = recordScreenOffEnabled,
            dischargeDisplayPositive = dischargeDisplayPositive,
            powerCurveMode = powerCurveMode,
            showCapacity = showCapacity,
            showTemp = showTemp,
            showVoltage = showVoltage,
            showAppIcons = showAppIcons,
            isFullscreenMode = true,
            fullscreenViewportStartMs = fullscreenViewportStartMs,
            onToggleFullscreen = onToggleFullscreen,
            onPowerCurveModeChange = onPowerCurveModeChange,
            onShowCapacityChange = onShowCapacityChange,
            onShowTempChange = onShowTempChange,
            onShowVoltageChange = onShowVoltageChange,
            onShowAppIconsChange = onShowAppIconsChange,
            onFullscreenViewportStartChange = onFullscreenViewportStartChange
        )
    }
}

/**
 * 图表内容本体。
 *
 * 这里统一管理充/放电图表策略：放电的正负展示、充电的负轴探测、趋势曲线可见性、
 * 以及全屏模式下的局部视口行为。主 Screen 只负责传入状态和响应用户操作。
 *
 * @param modifier 外层修饰符。
 * @param detailType 当前详情类型。
 * @param chartUiState 图表状态。
 * @param isTargetRecordLoaded 当前详情是否已经和目标记录对齐。
 * @param isRecordChartLoading 图表是否仍在加载。
 * @param recordStartTime 记录起始时间。
 * @param recordScreenOffEnabled 是否保留息屏点。
 * @param dischargeDisplayPositive 是否将放电展示为正值。
 * @param powerCurveMode 功率曲线模式偏好。
 * @param showCapacity 是否显示电量曲线。
 * @param showTemp 是否显示温度曲线。
 * @param showVoltage 是否显示电压曲线。
 * @param showAppIcons 是否显示应用图标。
 * @param isFullscreenMode 当前是否处于全屏模式。
 * @param fullscreenViewportStartMs 当前全屏视口起点。
 * @param onToggleFullscreen 切换全屏。
 * @param onPowerCurveModeChange 更新功率曲线模式。
 * @param onShowCapacityChange 更新电量曲线开关。
 * @param onShowTempChange 更新温度曲线开关。
 * @param onShowVoltageChange 更新电压曲线开关。
 * @param onShowAppIconsChange 更新应用图标开关。
 * @param onFullscreenViewportStartChange 更新全屏视口起点。
 */
@Composable
private fun RecordDetailChartContent(
    modifier: Modifier,
    detailType: BatteryStatus,
    chartUiState: RecordDetailChartUiState,
    isTargetRecordLoaded: Boolean,
    isRecordChartLoading: Boolean,
    recordStartTime: Long?,
    recordScreenOffEnabled: Boolean,
    dischargeDisplayPositive: Boolean,
    powerCurveMode: PowerCurveMode,
    showCapacity: Boolean,
    showTemp: Boolean,
    showVoltage: Boolean,
    showAppIcons: Boolean,
    isFullscreenMode: Boolean,
    fullscreenViewportStartMs: Long?,
    onToggleFullscreen: () -> Unit,
    onPowerCurveModeChange: (PowerCurveMode) -> Unit,
    onShowCapacityChange: (Boolean) -> Unit,
    onShowTempChange: (Boolean) -> Unit,
    onShowVoltageChange: (Boolean) -> Unit,
    onShowAppIconsChange: (Boolean) -> Unit,
    onFullscreenViewportStartChange: (Long?) -> Unit
) {
    val currentChartHeight = if (isFullscreenMode) 320.dp else 240.dp
    if (!isTargetRecordLoaded || isRecordChartLoading) {
        RecordDetailChartLoading(
            modifier = modifier,
            chartHeight = currentChartHeight
        )
        return
    }
    val safeRecordStartTime = recordStartTime ?: error("记录详情图表缺少起始时间")

    // 双向轴只根据“进入稳定阶段后的负值”触发：
    // 前 10% 的点即使出现负功率，也继续按正轴语义处理。
    val chargingNegativeAxisDetectionIgnoreCount =
        ((chartUiState.points.size * CHARGING_NEGATIVE_AXIS_DETECTION_IGNORE_PERCENT) + 99) / 100
    val chargingAxisDetectionPoints =
        chartUiState.points.drop(chargingNegativeAxisDetectionIgnoreCount)
    val hasNegativeChargingPower = detailType == BatteryStatus.Charging &&
            chargingAxisDetectionPoints.any { it.rawPowerW < 0.0 }
    val fixedPowerMode = when {
        detailType == BatteryStatus.Discharging && !dischargeDisplayPositive -> {
            FixedPowerAxisMode.NegativeOnly
        }

        hasNegativeChargingPower -> FixedPowerAxisMode.Bidirectional
        else -> FixedPowerAxisMode.PositiveOnly
    }
    val displayPowerCurveMode = powerCurveMode.resolveForDetailType(detailType)
    val curveVisibility = RecordChartCurveVisibility(
        powerCurveMode = displayPowerCurveMode,
        showCapacity = showCapacity,
        showTemp = showTemp,
        showVoltage = showVoltage
    )

    // 只有全屏模式允许横向拖动浏览局部视口；
    // 非全屏直接展示完整时长，减少普通详情页的认知负担。
    val viewportStartForChart = if (isFullscreenMode && chartUiState.minChartTime != null) {
        val minChartTime = chartUiState.minChartTime!!
        val maxViewportStart = chartUiState.maxViewportStartTime
        val initialStart = fullscreenViewportStartMs ?: minChartTime
        if (maxViewportStart == null) {
            initialStart
        } else {
            initialStart.coerceIn(minChartTime, maxViewportStart)
        }
    } else {
        null
    }
    val viewportEndForChart = if (
        isFullscreenMode &&
        viewportStartForChart != null &&
        chartUiState.maxChartTime != null
    ) {
        (viewportStartForChart + chartUiState.viewportDurationMs)
            .coerceAtMost(chartUiState.maxChartTime!!)
    } else {
        null
    }

    PowerCapacityChart(
        points = chartUiState.points,
        trendPoints = chartUiState.trendPoints,
        recordScreenOffEnabled = recordScreenOffEnabled,
        recordStartTime = safeRecordStartTime,
        modifier = modifier,
        fixedPowerAxisMode = fixedPowerMode,
        curveVisibility = curveVisibility,
        chartHeight = currentChartHeight,
        isFullscreen = isFullscreenMode,
        onToggleFullscreen = onToggleFullscreen,
        onTogglePowerVisibility = {
            onPowerCurveModeChange(powerCurveMode.next(detailType))
        },
        onToggleCapacityVisibility = {
            onShowCapacityChange(!showCapacity)
        },
        onToggleTempVisibility = {
            onShowTempChange(!showTemp)
        },
        onToggleVoltageVisibility = {
            onShowVoltageChange(!showVoltage)
        },
        showAppIcons = showAppIcons,
        onToggleAppIconsVisibility = {
            onShowAppIconsChange(!showAppIcons)
        },
        useFivePercentTimeGrid = isFullscreenMode,
        visibleStartTime = viewportStartForChart,
        visibleEndTime = viewportEndForChart,
        onViewportShift = if (
            isFullscreenMode &&
            chartUiState.minChartTime != null &&
            chartUiState.maxViewportStartTime != null
        ) { deltaMs ->
            val minChartTime = chartUiState.minChartTime!!
            val maxViewportStart = chartUiState.maxViewportStartTime!!
            val currentStart = (fullscreenViewportStartMs ?: minChartTime)
                .coerceIn(minChartTime, maxViewportStart)
            onFullscreenViewportStartChange(
                (currentStart + deltaMs).coerceIn(minChartTime, maxViewportStart)
            )
        } else {
            null
        }
    )
}

/**
 * 放电应用明细区。
 *
 * 应用维度统计只在放电详情成立，因此把整段列表独立出来，
 * 避免主页面在正文末尾继续维护条件分支和行内布局细节。
 *
 * @param entries 应用维度统计条目。
 * @param displayConfig 放电视图配置。
 */
@Composable
internal fun AppDetailSection(
    entries: List<RecordAppDetailUiEntry>,
    displayConfig: RecordAppDetailDisplayConfig
) {
    if (entries.isEmpty()) return
    SplicedColumnGroup(title = stringResource(R.string.history_app_detail_section_title)) {
        entries.forEach { entry ->
            item(key = entry.key) {
                RecordAppDetailRow(
                    entry = entry,
                    displayConfig = displayConfig
                )
            }
        }
    }
}

/**
 * 放电应用明细展示配置。
 *
 * @param dischargeDisplayPositive 放电是否展示为正值。
 * @param dualCellEnabled 双电芯功率展示配置。
 * @param calibrationValue 功率换算倍率。
 */
internal data class RecordAppDetailDisplayConfig(
    val dischargeDisplayPositive: Boolean,
    val dualCellEnabled: Boolean,
    val calibrationValue: Int
)

/**
 * 详情页图表加载态。
 *
 * @param modifier 外层修饰符。
 * @param chartHeight 图表占位高度。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RecordDetailChartLoading(
    modifier: Modifier,
    chartHeight: Dp
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(chartHeight),
        contentAlignment = Alignment.Center
    ) {
        LoadingIndicator()
    }
}

/**
 * 根据详情类型解析图表默认曲线模式。
 *
 * 充电详情不支持趋势曲线，因此这里强制把 Fitted 规整回 Raw，
 * 避免 UI 各处重复判断。
 *
 * @param detailType 当前详情类型。
 * @return 当前类型可用的功率曲线模式。
 */
private fun PowerCurveMode.resolveForDetailType(detailType: BatteryStatus): PowerCurveMode {
    return if (detailType == BatteryStatus.Charging && this == PowerCurveMode.Fitted) {
        PowerCurveMode.Raw
    } else {
        this
    }
}

/**
 * 根据详情类型切换到下一个功率曲线模式。
 *
 * 放电页沿用 Raw -> Fitted -> Hidden 循环；
 * 充电页不展示趋势，只在 Raw 和 Hidden 之间切换。
 *
 * @param detailType 当前详情类型。
 * @return 切换后的功率曲线模式。
 */
private fun PowerCurveMode.next(detailType: BatteryStatus): PowerCurveMode {
    return when (resolveForDetailType(detailType)) {
        PowerCurveMode.Raw -> {
            if (detailType == BatteryStatus.Charging) {
                PowerCurveMode.Hidden
            } else {
                PowerCurveMode.Fitted
            }
        }

        PowerCurveMode.Fitted -> PowerCurveMode.Hidden
        PowerCurveMode.Hidden -> PowerCurveMode.Raw
    }
}

/**
 * 组装放电页亮灭屏时长文案。
 *
 * @param durationText 时长文本。
 * @param energyWh 能量值。
 * @param capacityPercent 电量百分比变化。
 * @param useMahForDischargeDetail 是否使用 mAh 展示能量。
 * @param locale 当前区域设置。
 * @return 最终展示文案。
 */
private fun buildDischargingScreenDurationText(
    durationText: String,
    energyWh: Double?,
    capacityPercent: Int?,
    useMahForDischargeDetail: Boolean,
    locale: Locale
): String {
    if (energyWh == null || capacityPercent == null) return durationText
    return "$durationText - ${
        formatEnergyForDischargeDetail(
            locale = locale,
            energyWh = energyWh,
            useMah = useMahForDischargeDetail
        )
    } ($capacityPercent%)"
}

/**
 * 应用维度单行明细。
 *
 * @param entry 单条应用统计。
 * @param displayConfig 放电视图配置。
 */
@Composable
private fun RecordAppDetailRow(
    entry: RecordAppDetailUiEntry,
    displayConfig: RecordAppDetailDisplayConfig
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RecordAppDetailIcon(entry = entry)

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = entry.appLabel,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = formatDetailDuration(entry.durationMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.End
                )
            }
            Spacer(Modifier.height(2.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = buildAvgAndTempText(entry, displayConfig),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = buildMaxTempText(entry),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

/**
 * 应用图标槽位。
 *
 * @param entry 单条应用统计。
 */
@Composable
private fun RecordAppDetailIcon(entry: RecordAppDetailUiEntry) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val iconSize = 36.dp
    val iconSizePx = with(density) { iconSize.roundToPx() }
    val packageName = entry.packageName
    var iconBitmap by remember(packageName, iconSizePx) {
        mutableStateOf(
            packageName?.let { AppIconMemoryCache.get(it, iconSizePx) }
        )
    }

    LaunchedEffect(packageName, iconSizePx) {
        if (entry.isScreenOff) return@LaunchedEffect
        val resolvedPackageName = packageName ?: return@LaunchedEffect
        if (iconBitmap != null) return@LaunchedEffect
        if (!AppIconMemoryCache.shouldLoad(resolvedPackageName, iconSizePx)) {
            iconBitmap = AppIconMemoryCache.get(resolvedPackageName, iconSizePx)
            return@LaunchedEffect
        }
        iconBitmap = AppIconMemoryCache.loadAndCache(context, resolvedPackageName, iconSizePx)
    }

    Box(
        modifier = Modifier
            .size(iconSize)
            .clip(AppShape.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        val bitmap = iconBitmap
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * 放电详情功率统计摘要。
 *
 * @param powerUiState 功率统计状态。
 * @param dualCellEnabled 双电芯功率展示配置。
 * @param calibrationValue 功率换算倍率。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordDetailPowerSection(
    powerUiState: RecordDetailPowerUiState,
    dualCellEnabled: Boolean,
    calibrationValue: Int
) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RecordDetailPowerHeader(
            modifier = Modifier.fillMaxWidth(0.3f)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = buildDetailPowerSummaryText(
                powerUiState = powerUiState,
                dualCellEnabled = dualCellEnabled,
                calibrationValue = calibrationValue
            ),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Start,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * 放电详情功率统计标题。
 *
 * @param modifier 外层修饰符。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordDetailPowerHeader(modifier: Modifier = Modifier) {
    val tooltipState = rememberTooltipState()
    val coroutineScope = rememberCoroutineScope()
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
            TooltipAnchorPosition.Above
        ),
        tooltip = {
            PlainTooltip(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Text(stringResource(R.string.history_power_stats_label))
            }
        },
        state = tooltipState,
        enableUserInput = false
    ) {
        Text(
            text = stringResource(R.string.history_power_unknown),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                coroutineScope.launch {
                    tooltipState.show()
                }
            }
        )
    }
}

private fun buildAvgAndTempText(
    entry: RecordAppDetailUiEntry,
    displayConfig: RecordAppDetailDisplayConfig
): String {
    val displayMultiplier = if (displayConfig.dischargeDisplayPositive) -1.0 else 1.0
    val powerText = formatPower(
        entry.averagePowerRaw * displayMultiplier,
        displayConfig.dualCellEnabled,
        displayConfig.calibrationValue
    ).replace(" ", "")
    return "AVG:$powerText  ${formatTempText(entry.averageTempCelsius)}"
}

private fun buildMaxTempText(entry: RecordAppDetailUiEntry): String =
    "MAX:${formatTempText(entry.maxTempCelsius)}"

private fun formatDetailPowerValue(
    power: Double?,
    dualCellEnabled: Boolean,
    calibrationValue: Int
): String {
    if (power == null) return "--"
    return formatPower(power, dualCellEnabled, calibrationValue)
}

private fun buildDetailPowerSummaryText(
    powerUiState: RecordDetailPowerUiState,
    dualCellEnabled: Boolean,
    calibrationValue: Int
): String {
    val averageText = formatDetailPowerValue(
        power = powerUiState.averagePower,
        dualCellEnabled = dualCellEnabled,
        calibrationValue = calibrationValue
    ).replace(" W", "")
    val screenOnText = formatDetailPowerValue(
        power = powerUiState.screenOnAveragePower,
        dualCellEnabled = dualCellEnabled,
        calibrationValue = calibrationValue
    ).replace(" W", "")
    val screenOffText = formatDetailPowerValue(
        power = powerUiState.screenOffAveragePower,
        dualCellEnabled = dualCellEnabled,
        calibrationValue = calibrationValue
    ).replace(" W", "")
    return "$averageText / $screenOnText / $screenOffText W"
}

private fun formatTempText(tempCelsius: Double?): String {
    if (tempCelsius == null) return "--℃"
    return String.format(Locale.getDefault(), "%.1f℃", tempCelsius)
}

/**
 * 摘要区信息行。
 *
 * @param label 左侧标签。
 * @param value 右侧内容。
 */
@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(0.3f)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
        )
    }
}
