package yangfentuozi.batteryrecorder.ui.components.charts

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import yangfentuozi.batteryrecorder.data.model.RecordDetailChartPoint
import yangfentuozi.batteryrecorder.data.model.normalizeRecordDetailChartPoints
import yangfentuozi.batteryrecorder.ui.theme.AppShape
import yangfentuozi.batteryrecorder.utils.AppIconMemoryCache
import yangfentuozi.batteryrecorder.utils.formatDateTime
import yangfentuozi.batteryrecorder.utils.formatRelativeTime
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 创建标准文本画笔
 */
private fun createTextPaint(color: Int, textSize: Float) = Paint().apply {
    this.color = color
    this.textSize = textSize
    isAntiAlias = true
}

private const val CAPACITY_CURVE_SCALE = 0.9f
private val CAPACITY_COLOR = Color(0xFF4CAF50)
private val VOLTAGE_COLOR = Color(0xFFFFB300)
private val TEMP_COLOR = Color(0xFFFF8A65)
private val SCREEN_ON_COLOR = Color(0xFF2E7D32)
private val SCREEN_OFF_COLOR = Color(0xFFD32F2F)
private val LINE_STROKE_WIDTH = 1.3.dp
private const val APP_ICON_ALPHA = 0.55f
private const val TEMP_EXPAND_STEP_TENTHS = 100.0    // 10℃
private const val VOLTAGE_AXIS_MIN_UV = 2_500_000.0 // 2.5V
private const val VOLTAGE_AXIS_MAX_UV = 5_000_000.0 // 5.0V
// 横屏全屏下记录详情通常会查看长时间段数据，双指平移稍微提速以减少来回拖动次数。
private const val FULLSCREEN_TWO_FINGER_PAN_SPEED_MULTIPLIER = 2.0f

enum class PowerCurveMode {
    Raw,
    Fitted,
    Hidden,
}

/**
 * 图表可见曲线配置。
 *
 * 这里将功率曲线单独建模为 mode，而不是简单的 show/hide：
 * - Raw：显示原始功率线
 * - Fitted：显示趋势线
 * - Hidden：隐藏功率线
 *
 * 这样图表层只关心当前要画哪一类功率数据，不需要知道页面上是通过什么交互切换到这个状态的。
 */
data class RecordChartCurveVisibility(
    val powerCurveMode: PowerCurveMode,
    val showCapacity: Boolean,
    val showTemp: Boolean,
    val showVoltage: Boolean,
)

/**
 * 图表坐标系统，封装坐标转换逻辑
 */
private class ChartCoordinates(
    val paddingLeft: Float,
    val paddingTop: Float,
    val chartWidth: Float,
    val chartHeight: Float,
    val minTime: Long,
    val maxTime: Long,
    val minPower: Double,
    val maxPower: Double,
    val minTemp: Double,
    val maxTemp: Double,
    val minVoltage: Double,
    val maxVoltage: Double,
    private val contentWidth: Float,
    private val contentOffsetPx: Float,
) {
    val timeRange = max(1L, maxTime - minTime).toDouble()
    private val powerRange = max(1e-6, maxPower - minPower)

    /**
     * 将时间戳映射到静态内容层坐标。
     *
     * @param timestamp 需要映射的数据点时间戳
     * @return 内容层内的绝对 X 坐标，范围为 0..contentWidth
     */
    fun timeToContentX(timestamp: Long): Float =
        ((timestamp - minTime) / timeRange).toFloat() * contentWidth

    /**
     * 将内容层坐标映射到当前视口坐标。
     *
     * @param contentX 静态内容层内的绝对 X 坐标
     * @return 当前 Canvas 视口中的 X 坐标
     */
    fun contentXToViewportX(contentX: Float): Float =
        paddingLeft + contentX - contentOffsetPx

    /**
     * 将时间戳映射到当前视口坐标。
     *
     * @param timestamp 需要映射的数据点时间戳
     * @return 当前 Canvas 视口中的 X 坐标
     */
    fun timeToX(timestamp: Long): Float = contentXToViewportX(timeToContentX(timestamp))

    /**
     * 将视口坐标反推为时间戳。
     *
     * @param viewportX 当前 Canvas 视口中的 X 坐标
     * @return 反推得到的时间戳
     */
    fun viewportXToTime(viewportX: Float): Long {
        if (contentWidth <= 0f) return minTime
        val contentX = (viewportX - paddingLeft + contentOffsetPx).coerceIn(0f, contentWidth)
        return minTime + (contentX / contentWidth * timeRange).toLong()
    }

    fun powerToY(value: Double): Float {
        val normalized = ((value - minPower) / powerRange).toFloat()
        return paddingTop + (1f - normalized) * chartHeight
    }

    fun capacityToY(capacity: Double): Float {
        val normalized = (capacity / 100.0).toFloat()
        return paddingTop + (1f - normalized) * chartHeight * CAPACITY_CURVE_SCALE
    }

    fun tempToY(temp: Double): Float {
        val tempRange = max(1.0, maxTemp - minTemp)
        val normalized = ((temp - minTemp) / tempRange).toFloat()
        return paddingTop + (1f - normalized) * chartHeight * 0.9f
    }

    fun voltageToY(voltage: Double): Float {
        val voltageRange = max(1.0, maxVoltage - minVoltage)
        val normalized = ((voltage - minVoltage) / voltageRange).toFloat()
        return paddingTop + (1f - normalized) * chartHeight * 0.9f
    }

    /**
     * 根据 X 坐标查找最近的数据点
     */
    fun findPointAtX(
        offsetX: Float,
        points: List<RecordDetailChartPoint>
    ): RecordDetailChartPoint? {
        if (chartWidth <= 0f) return null
        val targetTime = viewportXToTime(offsetX)
        return points.minByOrNull { abs(it.timestamp - targetTime) }
    }
}

private data class FullscreenStaticChartLayout(
    val baseCoords: ChartCoordinates,
    val powerPath: Path?,
    val capacityPath: Path?,
    val tempPath: Path?,
    val voltagePath: Path?,
    val appIconPlacements: List<AppIconPlacement>,
)

private data class NormalStaticChartLayerLayout(
    val coords: ChartCoordinates,
    val powerPath: Path?,
    val capacityPath: Path?,
    val tempPath: Path?,
    val voltagePath: Path?,
    val appIconPlacements: List<AppIconPlacement>,
    val screenStatePaths: ScreenStatePaths?,
    val capacityMarkerLayouts: List<TextPointMarkerLayout>,
    val tempMarkerLayouts: List<TextPointMarkerLayout>,
    val voltageMarkerLayouts: List<TextPointMarkerLayout>,
    val peakAnnotationLayout: PeakAnnotationLayout?,
)

private data class ScreenStatePaths(
    val screenOnPath: Path?,
    val screenOffPath: Path?,
)

private data class TextPointMarkerLayout(
    val center: Offset,
    val label: String,
    val labelX: Float,
    val labelBaselineY: Float,
)

private data class PeakAnnotationLayout(
    val peakY: Float,
    val label: String,
    val labelX: Float,
    val labelBaselineY: Float,
)

private data class PeakPowerDisplay(
    val peakPlotPowerW: Double,
    val label: String,
)

private sealed interface ChartPreparationResult {
    data object Loading : ChartPreparationResult
    data object Empty : ChartPreparationResult
    data class Ready(val state: PreparedChartState) : ChartPreparationResult
}

private data class ChartPreparationRequest(
    val points: List<RecordDetailChartPoint>,
    val trendPoints: List<RecordDetailChartPoint>,
    val recordScreenOffEnabled: Boolean,
    val fixedPowerAxisMode: FixedPowerAxisMode,
    val curveVisibility: RecordChartCurveVisibility,
    val isFullscreen: Boolean,
    val visibleStartTime: Long?,
    val visibleEndTime: Long?,
    val showAppIcons: Boolean,
    val canvasSize: IntSize,
    val paddingLeftPx: Float,
    val paddingTopPx: Float,
    val paddingBottomPx: Float,
    val appIconSizePx: Int,
    val density: Density,
)

private data class PreparedChartState(
    val fullMinTime: Long,
    val fullMaxTime: Long,
    val viewportStart: Long,
    val viewportEnd: Long,
    val viewportDurationMs: Long,
    val isStaticFullscreen: Boolean,
    val renderFilteredPoints: List<RecordDetailChartPoint>,
    val renderRawPoints: List<RecordDetailChartPoint>,
    val activePowerPoints: List<RecordDetailChartPoint>,
    val selectablePoints: List<RecordDetailChartPoint>,
    val capacityMarkers: List<CapacityMarker>,
    val tempMarkerPoints: List<RecordDetailChartPoint>,
    val voltageMarkerPoints: List<RecordDetailChartPoint>,
    val powerAxisConfig: FixedPowerAxisConfig,
    val hasVisiblePowerCurve: Boolean,
    val isNegativeMode: Boolean,
    val minPower: Double,
    val maxPower: Double,
    val minTemp: Double,
    val maxTemp: Double,
    val minVoltage: Double,
    val maxVoltage: Double,
    val peakDisplay: PeakPowerDisplay?,
    val paddingRightPx: Float,
    val chartWidthPx: Float,
    val chartHeightPx: Float,
    val fullscreenContentWidthPx: Float,
    val fullscreenContentOffsetPx: Float,
    val normalStaticLayout: NormalStaticChartLayerLayout?,
    val fullscreenStaticLayout: FullscreenStaticChartLayout?,
    val appIconPackages: Set<String>,
)

/** 功率-电量图表（当前仅服务记录详情页单一场景）。 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PowerCapacityChart(
    points: List<RecordDetailChartPoint>,
    trendPoints: List<RecordDetailChartPoint>,
    recordScreenOffEnabled: Boolean,
    recordStartTime: Long,
    modifier: Modifier,
    fixedPowerAxisMode: FixedPowerAxisMode,
    curveVisibility: RecordChartCurveVisibility,
    chartHeight: Dp,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    onTogglePowerVisibility: () -> Unit,
    onToggleCapacityVisibility: () -> Unit,
    onToggleTempVisibility: () -> Unit,
    onToggleVoltageVisibility: () -> Unit,
    showAppIcons: Boolean,
    onToggleAppIconsVisibility: () -> Unit,
    useFivePercentTimeGrid: Boolean,
    visibleStartTime: Long?,
    visibleEndTime: Long?,
    onViewportShift: ((Long) -> Unit)?,
) {
    val powerColor = MaterialTheme.colorScheme.primary
    val capacityColor = CAPACITY_COLOR
    val tempColor = TEMP_COLOR
    val voltageColor = VOLTAGE_COLOR
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val axisLabelColor = MaterialTheme.colorScheme.onSurface
    val strokeWidth = LINE_STROKE_WIDTH
    val screenOnColor = SCREEN_ON_COLOR
    val screenOffColor = SCREEN_OFF_COLOR
    val peakLineColor = MaterialTheme.colorScheme.error

    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val appIconSizePx = with(density) { AppIconMemoryCache.chartIconSizeDp.roundToPx() }
    val paddingLeftPx = with(density) { 32.dp.toPx() }
    val paddingTopPx = with(density) { 6.dp.toPx() }
    val paddingBottomPx = with(density) { 24.dp.toPx() }
    val canvasSizeState = remember { mutableStateOf(IntSize.Zero) }
    val canvasSize = canvasSizeState.value
    val selectedPointState = remember(recordStartTime, points, trendPoints) {
        mutableStateOf<RecordDetailChartPoint?>(null)
    }
    val lastPreparedStateState = remember(recordStartTime, isFullscreen) {
        mutableStateOf<PreparedChartState?>(null)
    }
    val appIconClipPath = remember(appIconSizePx, density, layoutDirection) {
        outlineToPath(
            AppShape.icon.createOutline(
                size = Size(appIconSizePx.toFloat(), appIconSizePx.toFloat()),
                layoutDirection = layoutDirection,
                density = density
            )
        )
    }
    val preparationRequest = ChartPreparationRequest(
        points = points,
        trendPoints = trendPoints,
        recordScreenOffEnabled = recordScreenOffEnabled,
        fixedPowerAxisMode = fixedPowerAxisMode,
        curveVisibility = curveVisibility,
        isFullscreen = isFullscreen,
        visibleStartTime = visibleStartTime,
        visibleEndTime = visibleEndTime,
        showAppIcons = showAppIcons,
        canvasSize = canvasSize,
        paddingLeftPx = paddingLeftPx,
        paddingTopPx = paddingTopPx,
        paddingBottomPx = paddingBottomPx,
        appIconSizePx = appIconSizePx,
        density = density
    )
    val chartPreparationKey = if (isFullscreen) {
        preparationRequest.copy(
            visibleStartTime = null,
            visibleEndTime = null
        )
    } else {
        preparationRequest
    }
    val chartPreparation = produceState<ChartPreparationResult>(
        initialValue = lastPreparedStateState.value
            ?.let<PreparedChartState, ChartPreparationResult> { state ->
                ChartPreparationResult.Ready(state)
            }
            ?: ChartPreparationResult.Loading,
        key1 = chartPreparationKey
    ) {
        if (lastPreparedStateState.value == null) {
            value = ChartPreparationResult.Loading
        }
        val nextResult = withContext(Dispatchers.Default) {
            prepareChartState(preparationRequest)
        }
        when (nextResult) {
            is ChartPreparationResult.Ready -> {
                lastPreparedStateState.value = nextResult.state
                value = nextResult
            }

            ChartPreparationResult.Empty -> {
                lastPreparedStateState.value = null
                value = nextResult
            }

            ChartPreparationResult.Loading -> {
                if (lastPreparedStateState.value == null) {
                    value = nextResult
                }
            }
        }
    }
    val preparedState = when (val preparationResult = chartPreparation.value) {
        ChartPreparationResult.Loading -> {
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .height(chartHeight)
                    .onSizeChanged { canvasSizeState.value = it },
                contentAlignment = Alignment.Center
            ) {
                LoadingIndicator()
            }
            return
        }

        ChartPreparationResult.Empty -> {
            Text(
                text = "暂无数据",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return
        }

        is ChartPreparationResult.Ready -> preparationResult.state
    }

    val selectedPointInfoPadding = 4.dp
    val fullMinTime = preparedState.fullMinTime
    val fullMaxTime = preparedState.fullMaxTime
    val isStaticFullscreen = preparedState.isStaticFullscreen
    val renderFilteredPoints = preparedState.renderFilteredPoints
    val renderRawPoints = preparedState.renderRawPoints
    val activePowerPoints = preparedState.activePowerPoints
    val capacityMarkers = preparedState.capacityMarkers
    val tempMarkerPoints = preparedState.tempMarkerPoints
    val voltageMarkerPoints = preparedState.voltageMarkerPoints
    val powerAxisConfig = preparedState.powerAxisConfig
    val hasVisiblePowerCurve = preparedState.hasVisiblePowerCurve
    val isNegativeMode = preparedState.isNegativeMode
    val minPower = preparedState.minPower
    val maxPower = preparedState.maxPower
    val minTemp = preparedState.minTemp
    val maxTemp = preparedState.maxTemp
    val minVoltage = preparedState.minVoltage
    val maxVoltage = preparedState.maxVoltage
    val peakDisplay = preparedState.peakDisplay
    val chartWidthPx = preparedState.chartWidthPx
    val paddingRightPx = preparedState.paddingRightPx
    val fullscreenContentWidthPx = preparedState.fullscreenContentWidthPx
    val normalStaticLayout = preparedState.normalStaticLayout
    val fullscreenStaticLayout = preparedState.fullscreenStaticLayout
    val viewportStart = if (isFullscreen) {
        (visibleStartTime ?: fullMinTime).coerceIn(fullMinTime, fullMaxTime)
    } else {
        preparedState.viewportStart
    }
    val viewportEnd = if (isFullscreen) {
        (visibleEndTime ?: fullMaxTime).coerceIn(viewportStart, fullMaxTime)
    } else {
        preparedState.viewportEnd
    }
    val viewportDurationMs = if (isFullscreen) {
        (viewportEnd - viewportStart).coerceAtLeast(1L)
    } else {
        preparedState.viewportDurationMs
    }
    val selectablePoints = remember(
        isFullscreen,
        activePowerPoints,
        viewportStart,
        viewportEnd,
        preparedState.selectablePoints
    ) {
        if (isFullscreen) {
            activePowerPoints.filter { it.timestamp in viewportStart..viewportEnd }
                .ifEmpty { activePowerPoints }
        } else {
            preparedState.selectablePoints
        }
    }
    val fullscreenContentOffsetPx = if (!isFullscreen) {
        preparedState.fullscreenContentOffsetPx
    } else {
        val totalDurationMs = (fullMaxTime - fullMinTime).coerceAtLeast(1L)
        val fullscreenMaxOffsetPx =
            (fullscreenContentWidthPx - chartWidthPx).coerceAtLeast(0f)
        if (fullscreenMaxOffsetPx <= 0f) {
            0f
        } else {
            (((viewportStart - fullMinTime) / totalDurationMs.toDouble()) * fullscreenContentWidthPx)
                .toFloat()
                .coerceIn(0f, fullscreenMaxOffsetPx)
        }
    }
    val powerValueSelector = remember(
        curveVisibility.powerCurveMode,
        isNegativeMode,
        minPower,
        maxPower
    ) {
        { point: RecordDetailChartPoint ->
            selectPowerValueForChart(point, curveVisibility.powerCurveMode, isNegativeMode)
                .coerceIn(minPower, maxPower)
        }
    }

    LaunchedEffect(selectablePoints, viewportStart, viewportEnd) {
        val selected = selectedPointState.value ?: return@LaunchedEffect
        if (selected.timestamp !in viewportStart..viewportEnd ||
            selectablePoints.none { it.timestamp == selected.timestamp }
        ) {
            selectedPointState.value = null
        }
    }

    val appIconPackages = preparedState.appIconPackages
    val appIcons = rememberAppIconBitmaps(appIconPackages, appIconSizePx)
    val normalStaticLayerDrawBlock: DrawScope.() -> Unit = remember(
        normalStaticLayout,
        appIcons,
        showAppIcons,
        appIconClipPath,
        curveVisibility,
        activePowerPoints,
        powerValueSelector,
        hasVisiblePowerCurve,
        powerAxisConfig,
        gridColor,
        axisLabelColor,
        powerColor,
        capacityColor,
        tempColor,
        voltageColor,
        screenOnColor,
        screenOffColor,
        peakLineColor,
        strokeWidth,
        useFivePercentTimeGrid,
        viewportStart,
        viewportEnd
    ) {
        val layout = normalStaticLayout ?: return@remember {}
        val coords = layout.coords
        val verticalGridSegments = if (useFivePercentTimeGrid) 20 else 4
        val timeLabelSegments = if (useFivePercentTimeGrid) 20 else 4
        val timeLabelStep = if (useFivePercentTimeGrid) 4 else 1
        {
            // 普通模式拆成“静态底图 + 独立选中态覆盖层”两张 Canvas：
            // 底图只在预计算结果变化时重绘，点选反馈则走上层 Canvas，避免拖动选点时整张图反复重画。
            drawVerticalGridLines(
                coords,
                gridColor,
                verticalGridSegments,
                viewportStart,
                viewportEnd
            )
            if (hasVisiblePowerCurve) {
                drawFixedPowerGridLines(
                    coords,
                    gridColor,
                    powerAxisConfig.majorStepW,
                    powerAxisConfig.minorStepW
                )
                drawFixedPowerAxisLabels(
                    coords,
                    axisLabelColor,
                    powerAxisConfig.majorStepW,
                    powerAxisConfig.minorStepW,
                    if (isNegativeMode) -1 else 1
                )
            }
            drawTimeAxisLabels(
                coords,
                axisLabelColor,
                { value -> formatDateTime(value) },
                timeLabelSegments,
                timeLabelStep,
                viewportStart,
                viewportEnd
            )
            clipRect(
                left = coords.paddingLeft,
                top = coords.paddingTop,
                right = coords.paddingLeft + coords.chartWidth,
                bottom = coords.paddingTop + coords.chartHeight
            ) {
                // 曲线顺序固定为电压 -> 温度 -> 功率 -> 电量。
                // 电压与温度、电量都属于辅助信息，电压优先级最低，尽量不压住主要功率走势；
                // 温度与电量属于辅助信息，让功率线保持更靠上的视觉优先级；
                // 电量放在最后，避免较亮颜色被另外两条曲线完全覆盖。
                layout.voltagePath?.let { path ->
                    drawPath(
                        path = path,
                        color = voltageColor,
                        style = Stroke(
                            width = strokeWidth.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
                layout.tempPath?.let { path ->
                    drawPath(
                        path = path,
                        color = tempColor,
                        style = Stroke(
                            width = strokeWidth.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
                layout.powerPath?.let { path ->
                    drawPath(
                        path = path,
                        color = powerColor,
                        style = Stroke(
                            width = strokeWidth.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
                layout.capacityPath?.let { path ->
                    drawPath(
                        path = path,
                        color = capacityColor,
                        style = Stroke(
                            width = strokeWidth.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
                if (activePowerPoints.size == 1) {
                    // 单点数据无法形成 path，这里补绘圆点，避免“有数据但图上什么都没有”。
                    val point = activePowerPoints.first()
                    val pointX = coords.timeToX(point.timestamp)
                    if (curveVisibility.showVoltage && point.voltage > 0L) {
                        drawCircle(
                            voltageColor,
                            radius = 2.8.dp.toPx(),
                            center = Offset(pointX, coords.voltageToY(point.voltage.toDouble()))
                        )
                    }
                    if (curveVisibility.showTemp) {
                        drawCircle(
                            tempColor,
                            radius = 2.8.dp.toPx(),
                            center = Offset(pointX, coords.tempToY(point.temp.toDouble()))
                        )
                    }
                    if (hasVisiblePowerCurve) {
                        drawCircle(
                            powerColor,
                            radius = 2.8.dp.toPx(),
                            center = Offset(
                                pointX,
                                coords.powerToY(powerValueSelector(point))
                            )
                        )
                    }
                    if (curveVisibility.showCapacity) {
                        drawCircle(
                            capacityColor,
                            radius = 2.8.dp.toPx(),
                            center = Offset(
                                pointX,
                                coords.capacityToY(point.capacity.toDouble())
                            )
                        )
                    }
                }
            }
            // 峰值横线和标签故意放在曲线之后、标记之前：
            // 1. 它需要盖住网格线，保持“峰值基准线”清晰；
            // 2. 又不能压住电量/温度/电压文字标记，否则会让标签相互争抢可读性。
            layout.peakAnnotationLayout?.let { peakLayout ->
                drawPeakAnnotation(peakLayout, coords, peakLineColor)
            }

            if (layout.capacityMarkerLayouts.isNotEmpty()) {
                // 标记文本必须在曲线之后绘制，否则会被路径盖住而失去“可读标签”的意义。
                clipRect(
                    left = coords.paddingLeft,
                    top = coords.paddingTop,
                    right = coords.paddingLeft + coords.chartWidth,
                    bottom = coords.paddingTop + coords.chartHeight
                ) {
                    drawTextPointMarkerLayouts(layout.capacityMarkerLayouts, capacityColor)
                }
            }
            if (layout.tempMarkerLayouts.isNotEmpty()) {
                clipRect(
                    left = coords.paddingLeft,
                    top = coords.paddingTop,
                    right = coords.paddingLeft + coords.chartWidth,
                    bottom = coords.paddingTop + coords.chartHeight
                ) {
                    drawTextPointMarkerLayouts(layout.tempMarkerLayouts, tempColor)
                }
            }
            if (layout.voltageMarkerLayouts.isNotEmpty()) {
                clipRect(
                    left = coords.paddingLeft,
                    top = coords.paddingTop,
                    right = coords.paddingLeft + coords.chartWidth,
                    bottom = coords.paddingTop + coords.chartHeight
                ) {
                    drawTextPointMarkerLayouts(layout.voltageMarkerLayouts, voltageColor)
                }
            }
            layout.screenStatePaths?.let { screenPaths ->
                // 屏幕状态线属于图表外底部附属轨道，不参与主绘图区的遮挡关系。
                clipRect(
                    left = coords.paddingLeft,
                    top = 0f,
                    right = coords.paddingLeft + coords.chartWidth,
                    bottom = size.height
                ) {
                    drawScreenStatePaths(
                        screenPaths,
                        screenOnColor = screenOnColor,
                        screenOffColor = screenOffColor,
                        strokeWidth = 4.dp
                    )
                }
            }

            if (showAppIcons) {
                // 图标始终压在最上层，用于表达“该时间桶主要前台应用”。
                // 如果放到曲线之下，折线与标记文本会把图标切碎，辨识度会明显下降。
                clipRect(
                    left = coords.paddingLeft,
                    top = coords.paddingTop,
                    right = coords.paddingLeft + coords.chartWidth,
                    bottom = coords.paddingTop + coords.chartHeight
                ) {
                    layout.appIconPlacements.forEach { placement ->
                        val icon = appIcons[placement.packageName] ?: return@forEach
                        translate(left = placement.topLeft.x, top = placement.topLeft.y) {
                            clipPath(appIconClipPath) {
                                drawImage(
                                    image = icon,
                                    topLeft = Offset.Zero,
                                    alpha = APP_ICON_ALPHA
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    Column(modifier = modifier) {
        SelectedPointInfo(
            selected = selectedPointState.value,
            recordStartTime = recordStartTime,
                    powerCurveMode = curveVisibility.powerCurveMode,
                    isFullscreen = isFullscreen,
                    onToggleFullscreen = onToggleFullscreen,
                    startPadding = selectedPointInfoPadding
                )

        Spacer(modifier = Modifier.height(13.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            if (isStaticFullscreen) {
                // 全屏模式允许平移视口，因此不能像普通模式那样把主绘制完全静态化到固定视口。
                // 这里改为“一张可平移的大内容层 + 每帧按偏移裁剪显示”，以换取拖动时的连续性。
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(chartHeight)
                        .onSizeChanged { canvasSizeState.value = it }
                        // 双指拖动只在全屏模式有意义，用来平移视口，而不是修改数据本身。
                        .pointerInput(
                            renderFilteredPoints,
                            paddingRightPx,
                            viewportStart,
                            viewportEnd,
                            onViewportShift
                        ) {
                            if (onViewportShift == null) return@pointerInput
                            val paddingLeft = 32.dp.toPx()
                            val chartWidth = size.width - paddingLeft - paddingRightPx
                            if (chartWidth <= 0f) return@pointerInput
                            awaitEachGesture {
                                var lastCentroidX: Float? = null
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val activeChanges = event.changes.filter { it.pressed }
                                    if (activeChanges.isEmpty()) break
                                    if (activeChanges.size < 2) {
                                        lastCentroidX = null
                                        continue
                                    }
                                    val centroidX =
                                        activeChanges.map { it.position.x }.average().toFloat()
                                    val previousCentroidX = lastCentroidX
                                    if (previousCentroidX != null) {
                                        val deltaX = centroidX - previousCentroidX
                                        // 质心横向位移按当前视口时长换算成时间偏移，并额外乘上全屏双指平移速度系数。
                                        val deltaMs =
                                            (
                                                (-deltaX / chartWidth) *
                                                    viewportDurationMs *
                                                    FULLSCREEN_TWO_FINGER_PAN_SPEED_MULTIPLIER
                                                ).toLong()
                                        if (deltaMs != 0L) {
                                            onViewportShift(deltaMs)
                                        }
                                        event.changes.forEach { it.consume() }
                                    }
                                    lastCentroidX = centroidX
                                }
                            }
                        }
                        // 单击选择最近点，显示当前时刻的功率/电量/温度摘要。
                        .pointerInput(
                            selectablePoints,
                            paddingRightPx,
                            fullscreenContentWidthPx,
                            fullscreenContentOffsetPx
                        ) {
                            val paddingLeft = 32.dp.toPx()
                            val chartWidth = size.width - paddingLeft - paddingRightPx
                            val coords = ChartCoordinates(
                                paddingLeft = paddingLeft,
                                paddingTop = 0f,
                                chartWidth = chartWidth,
                                chartHeight = 0f,
                                minTime = fullMinTime,
                                maxTime = fullMaxTime,
                                minPower = 0.0,
                                maxPower = 0.0,
                                minTemp = 0.0,
                                maxTemp = 0.0,
                                minVoltage = 0.0,
                                maxVoltage = 0.0,
                                contentWidth = fullscreenContentWidthPx,
                                contentOffsetPx = fullscreenContentOffsetPx
                            )
                            detectTapGestures { offset ->
                                selectedPointState.value =
                                    coords.findPointAtX(offset.x, selectablePoints)
                            }
                        }
                        // 拖动选择与点击选择共用同一套“最近点”逻辑，保持交互反馈一致。
                        .pointerInput(
                            selectablePoints,
                            paddingRightPx,
                            fullscreenContentWidthPx,
                            fullscreenContentOffsetPx
                        ) {
                            val paddingLeft = 32.dp.toPx()
                            val chartWidth = size.width - paddingLeft - paddingRightPx
                            val coords = ChartCoordinates(
                                paddingLeft = paddingLeft,
                                paddingTop = 0f,
                                chartWidth = chartWidth,
                                chartHeight = 0f,
                                minTime = fullMinTime,
                                maxTime = fullMaxTime,
                                minPower = 0.0,
                                maxPower = 0.0,
                                minTemp = 0.0,
                                maxTemp = 0.0,
                                minVoltage = 0.0,
                                maxVoltage = 0.0,
                                contentWidth = fullscreenContentWidthPx,
                                contentOffsetPx = fullscreenContentOffsetPx
                            )
                            detectDragGestures { change, _ ->
                                change.consume()
                                selectedPointState.value =
                                    coords.findPointAtX(change.position.x, selectablePoints)
                            }
                        }
                ) {
                    val paddingLeft = 32.dp.toPx()
                    val paddingRight = paddingRightPx
                    val paddingTop = 6.dp.toPx()
                    val paddingBottom = 24.dp.toPx()
                    val chartWidth = size.width - paddingLeft - paddingRight
                    val chartHeight = size.height - paddingTop - paddingBottom
                    if (chartWidth <= 0f || chartHeight <= 0f) return@Canvas

                    val verticalGridSegments = if (useFivePercentTimeGrid) 20 else 4
                    val timeLabelSegments = if (useFivePercentTimeGrid) 20 else 4
                    val timeLabelStep = if (useFivePercentTimeGrid) 4 else 1

                    val coords = ChartCoordinates(
                        paddingLeft, paddingTop, chartWidth, chartHeight,
                        if (isStaticFullscreen) fullMinTime else viewportStart,
                        if (isStaticFullscreen) fullMaxTime else viewportEnd,
                        minPower,
                        maxPower,
                        minTemp,
                        maxTemp,
                        minVoltage,
                        maxVoltage,
                        contentWidth = if (isStaticFullscreen) fullscreenContentWidthPx else chartWidth,
                        contentOffsetPx = if (isStaticFullscreen) fullscreenContentOffsetPx else 0f
                    )
                    val staticCoords = fullscreenStaticLayout?.baseCoords ?: coords
                    val appIconPlacements = fullscreenStaticLayout?.appIconPlacements ?: emptyList()

                    // 全屏静态层预先按“全时段内容坐标”构建；进入视口时仅做 translate。
                    // 非静态全屏或普通模式则直接按当前视口现算，避免为了很短时间窗构造超宽 Path。
                    val powerPath = if (isStaticFullscreen) {
                        fullscreenStaticLayout?.powerPath
                    } else if (hasVisiblePowerCurve) {
                        buildPowerPath(
                            points = activePowerPoints,
                            coords = coords,
                            valueSelector = powerValueSelector,
                            smooth = curveVisibility.powerCurveMode == PowerCurveMode.Fitted
                        )
                    } else {
                        null
                    }
                    val capacityPath = if (isStaticFullscreen) {
                        fullscreenStaticLayout?.capacityPath
                    } else if (curveVisibility.showCapacity) {
                        buildCapacityPath(renderFilteredPoints, coords) { it.capacity.toDouble() }
                    } else {
                        null
                    }
                    val tempPath = if (isStaticFullscreen) {
                        fullscreenStaticLayout?.tempPath
                    } else if (curveVisibility.showTemp) {
                        buildTempPath(renderFilteredPoints, coords) { it.temp.toDouble() }
                    } else {
                        null
                    }
                    val voltagePath = if (isStaticFullscreen) {
                        fullscreenStaticLayout?.voltagePath
                    } else if (curveVisibility.showVoltage) {
                        buildVoltagePath(renderFilteredPoints, coords) { it.voltage.toDouble() }
                    } else {
                        null
                    }

                    // 固定功率轴：垂直网格 + 主次刻度水平线 + 固定刻度标签
                    drawVerticalGridLines(
                        coords,
                        gridColor,
                        verticalGridSegments,
                        viewportStart,
                        viewportEnd
                    )
                    if (hasVisiblePowerCurve) {
                        drawFixedPowerGridLines(
                            coords,
                            gridColor,
                            powerAxisConfig.majorStepW,
                            powerAxisConfig.minorStepW
                        )
                        drawFixedPowerAxisLabels(
                            coords,
                            axisLabelColor,
                            powerAxisConfig.majorStepW,
                            powerAxisConfig.minorStepW,
                            if (isNegativeMode) -1 else 1
                        )
                    }
                    drawTimeAxisLabels(
                        coords, axisLabelColor, { value -> formatDateTime(value) },
                        timeLabelSegments, timeLabelStep, viewportStart, viewportEnd
                    )

                    clipRect(
                        left = paddingLeft,
                        top = paddingTop,
                        right = paddingLeft + chartWidth,
                        bottom = paddingTop + chartHeight
                    ) {
                        val contentOffset =
                            if (isStaticFullscreen) -fullscreenContentOffsetPx else 0f
                        // 只有真正依赖“内容坐标”的元素才跟随平移：
                        // 曲线、标记、图标会移动；坐标轴和刻度文本仍留在视口坐标系。
                        translate(left = contentOffset) {
                            voltagePath?.let { path ->
                                drawPath(
                                    path = path,
                                    color = voltageColor,
                                    style = Stroke(
                                        width = strokeWidth.toPx(),
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round
                                    )
                                )
                            }
                            tempPath?.let { path ->
                                drawPath(
                                    path = path,
                                    color = tempColor,
                                    style = Stroke(
                                        width = strokeWidth.toPx(),
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round
                                    )
                                )
                            }
                            powerPath?.let { path ->
                                drawPath(
                                    path = path,
                                    color = powerColor,
                                    style = Stroke(
                                        width = strokeWidth.toPx(),
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round
                                    )
                                )
                            }
                            capacityPath?.let { path ->
                                drawPath(
                                    path = path,
                                    color = capacityColor,
                                    style = Stroke(
                                        width = strokeWidth.toPx(),
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round
                                    )
                                )
                            }
                            if (activePowerPoints.size == 1) {
                                // 单点数据无法形成 path，这里补绘圆点，避免“有数据但图上什么都没有”。
                                val point = activePowerPoints.first()
                                val pointX = staticCoords.timeToX(point.timestamp)
                                if (curveVisibility.showVoltage && point.voltage > 0L) {
                                    drawCircle(
                                        voltageColor,
                                        radius = 2.8.dp.toPx(),
                                        center = Offset(
                                            pointX,
                                            staticCoords.voltageToY(point.voltage.toDouble())
                                        )
                                    )
                                }
                                if (curveVisibility.showTemp) {
                                    drawCircle(
                                        tempColor,
                                        radius = 2.8.dp.toPx(),
                                        center = Offset(
                                            pointX,
                                            staticCoords.tempToY(point.temp.toDouble())
                                        )
                                    )
                                }
                                if (hasVisiblePowerCurve) {
                                    drawCircle(
                                        powerColor,
                                        radius = 2.8.dp.toPx(),
                                        center = Offset(
                                            pointX,
                                            staticCoords.powerToY(powerValueSelector(point))
                                        )
                                    )
                                }
                                if (curveVisibility.showCapacity) {
                                    drawCircle(
                                        capacityColor,
                                        radius = 2.8.dp.toPx(),
                                        center = Offset(
                                            pointX,
                                            staticCoords.capacityToY(point.capacity.toDouble())
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // 峰值标注保持在网格之上、曲线之下，避免与主次刻度线互相遮挡。
                    // 全屏分支保留原地计算，是因为峰值标签位置依赖当前视口宽度而不是全时段内容宽度。
                    val peakPlotPowerW = if (hasVisiblePowerCurve) {
                        activePowerPoints.maxOfOrNull { powerValueSelector(it) }
                    } else {
                        null
                    }
                    if (peakPlotPowerW != null) {
                        val peakY = coords.powerToY(peakPlotPowerW)
                        drawLine(
                            color = peakLineColor.copy(alpha = 0.9f),
                            start = Offset(paddingLeft, peakY),
                            end = Offset(paddingLeft + chartWidth, peakY),
                            strokeWidth = 1.dp.toPx()
                        )

                        val label = String.format(
                            Locale.getDefault(),
                            "%.2f W",
                            if (isNegativeMode) -peakPlotPowerW else peakPlotPowerW
                        )
                        val labelPaint = createTextPaint(peakLineColor.toArgb(), 24f)
                        val labelWidth = labelPaint.measureText(label)
                        val plotRight = paddingLeft + chartWidth
                        val labelX = (plotRight + 4.dp.toPx())
                            .coerceAtMost(size.width - labelWidth - 4.dp.toPx())
                            .coerceAtLeast(plotRight + 2.dp.toPx())
                        val labelY = (peakY - 4.dp.toPx())
                            .coerceIn(
                                paddingTop + 12.dp.toPx(),
                                paddingTop + chartHeight - 4.dp.toPx()
                            )
                        drawContext.canvas.nativeCanvas.drawText(label, labelX, labelY, labelPaint)
                    }

                    if (capacityMarkers.isNotEmpty()) {
                        // 电量标记复用与曲线相同的内容偏移，保证标记点仍然对齐真实采样时间。
                        val markerCoords = if (isStaticFullscreen) staticCoords else coords
                        val markerOffset =
                            if (isStaticFullscreen) -fullscreenContentOffsetPx else 0f
                        clipRect(
                            left = paddingLeft,
                            top = paddingTop,
                            right = paddingLeft + chartWidth,
                            bottom = paddingTop + chartHeight
                        ) {
                            translate(left = markerOffset) {
                                drawCapacityMarkers(capacityMarkers, markerCoords, capacityColor)
                            }
                        }
                    }

                    if (curveVisibility.showTemp) {
                        // 温度极值属于“依赖曲线几何位置”的覆盖层，必须跟随内容平移，但仍压在曲线之上。
                        val tempCoords = if (isStaticFullscreen) staticCoords else coords
                        val tempOffset = if (isStaticFullscreen) -fullscreenContentOffsetPx else 0f
                        clipRect(
                            left = paddingLeft,
                            top = paddingTop,
                            right = paddingLeft + chartWidth,
                            bottom = paddingTop + chartHeight
                        ) {
                            translate(left = tempOffset) {
                                drawTempExtremeMarkers(tempMarkerPoints, tempCoords, tempColor)
                            }
                        }
                    }
                    if (curveVisibility.showVoltage) {
                        // 电压极值和温度一样依赖当前曲线几何位置，因此需要跟随内容层一起平移。
                        val voltageCoords = if (isStaticFullscreen) staticCoords else coords
                        val voltageOffset =
                            if (isStaticFullscreen) -fullscreenContentOffsetPx else 0f
                        clipRect(
                            left = paddingLeft,
                            top = paddingTop,
                            right = paddingLeft + chartWidth,
                            bottom = paddingTop + chartHeight
                        ) {
                            translate(left = voltageOffset) {
                                drawVoltageExtremeMarkers(
                                    voltageMarkerPoints,
                                    voltageCoords,
                                    voltageColor
                                )
                            }
                        }
                    }

                    if (renderRawPoints.isNotEmpty()) {
                        // 屏幕状态线保留在底部区域，但仅允许在图表横向范围内绘制
                        clipRect(
                            left = paddingLeft,
                            top = 0f,
                            right = paddingLeft + chartWidth,
                            bottom = size.height
                        ) {
                            translate(left = if (isStaticFullscreen) -fullscreenContentOffsetPx else 0f) {
                                drawScreenStateLine(
                                    renderRawPoints,
                                    if (isStaticFullscreen) staticCoords else coords,
                                    screenOnColor,
                                    screenOffColor,
                                    4.dp
                                )
                            }
                        }
                    }

                    clipRect(
                        left = paddingLeft,
                        top = paddingTop,
                        right = paddingLeft + chartWidth,
                        bottom = paddingTop + chartHeight
                    ) {
                        // 选中态不进入静态层：
                        // 它既要压住曲线和峰值线，又要随着点击/拖动即时刷新，单独放在这里能保持最低重绘成本。
                        selectedPointState.value
                            ?.takeIf { it.timestamp in viewportStart..viewportEnd }
                            ?.let { selectedPoint ->
                                val selectedX = coords.timeToX(selectedPoint.timestamp)

                                drawLine(
                                    color = gridColor.copy(alpha = 0.6f),
                                    start = Offset(selectedX, paddingTop),
                                    end = Offset(selectedX, paddingTop + chartHeight),
                                    strokeWidth = 1.dp.toPx()
                                )
                                if (hasVisiblePowerCurve) {
                                    val powerY = coords.powerToY(powerValueSelector(selectedPoint))
                                    drawCircle(
                                        powerColor,
                                        radius = 2.8.dp.toPx(),
                                        center = Offset(selectedX, powerY)
                                    )
                                }
                                if (curveVisibility.showCapacity) {
                                    val capacityY =
                                        coords.capacityToY(selectedPoint.capacity.toDouble())
                                    drawCircle(
                                        capacityColor,
                                        radius = 2.8.dp.toPx(),
                                        center = Offset(selectedX, capacityY)
                                    )
                                }
                                if (curveVisibility.showTemp) {
                                    val tempY = coords.tempToY(selectedPoint.temp.toDouble())
                                    drawCircle(
                                        tempColor,
                                        radius = 2.8.dp.toPx(),
                                        center = Offset(selectedX, tempY)
                                    )
                                }
                                if (curveVisibility.showVoltage && selectedPoint.voltage > 0L) {
                                    val voltageY =
                                        coords.voltageToY(selectedPoint.voltage.toDouble())
                                    drawCircle(
                                        voltageColor,
                                        radius = 2.8.dp.toPx(),
                                        center = Offset(selectedX, voltageY)
                                    )
                                }
                            }
                    }

                    if (showAppIcons) {
                        // 图标继续保持最后绘制，确保选中点圆点和图标都可见时，图标优先表达应用语义。
                        clipRect(
                            left = paddingLeft,
                            top = paddingTop,
                            right = paddingLeft + chartWidth,
                            bottom = paddingTop + chartHeight
                        ) {
                            translate(left = if (isStaticFullscreen) -fullscreenContentOffsetPx else 0f) {
                                appIconPlacements.forEach { placement ->
                                    val icon = appIcons[placement.packageName] ?: return@forEach
                                    translate(
                                        left = placement.topLeft.x,
                                        top = placement.topLeft.y
                                    ) {
                                        clipPath(appIconClipPath) {
                                            drawImage(
                                                image = icon,
                                                topLeft = Offset.Zero,
                                                alpha = APP_ICON_ALPHA
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(chartHeight)
                        .onSizeChanged { canvasSizeState.value = it }
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                // 普通模式底图使用离屏合成，避免裁剪与多层 Path 叠加时出现边缘污染。
                                compositingStrategy = CompositingStrategy.Offscreen
                            },
                        onDraw = normalStaticLayerDrawBlock
                    )
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(
                                selectablePoints,
                                paddingRightPx,
                                viewportStart,
                                viewportEnd
                            ) {
                                val paddingLeft = 32.dp.toPx()
                                val chartWidth = size.width - paddingLeft - paddingRightPx
                                val coords = ChartCoordinates(
                                    paddingLeft,
                                    0f,
                                    chartWidth,
                                    0f,
                                    viewportStart,
                                    viewportEnd,
                                    0.0,
                                    0.0,
                                    0.0,
                                    0.0,
                                    0.0,
                                    0.0,
                                    contentWidth = chartWidth,
                                    contentOffsetPx = 0f
                                )
                                detectTapGestures { offset ->
                                    selectedPointState.value =
                                        coords.findPointAtX(offset.x, selectablePoints)
                                }
                            }
                            .pointerInput(
                                selectablePoints,
                                paddingRightPx,
                                viewportStart,
                                viewportEnd
                            ) {
                                val paddingLeft = 32.dp.toPx()
                                val chartWidth = size.width - paddingLeft - paddingRightPx
                                val coords = ChartCoordinates(
                                    paddingLeft,
                                    0f,
                                    chartWidth,
                                    0f,
                                    viewportStart,
                                    viewportEnd,
                                    0.0,
                                    0.0,
                                    0.0,
                                    0.0,
                                    0.0,
                                    0.0,
                                    contentWidth = chartWidth,
                                    contentOffsetPx = 0f
                                )
                                detectDragGestures { change, _ ->
                                    change.consume()
                                    selectedPointState.value =
                                        coords.findPointAtX(change.position.x, selectablePoints)
                                }
                            }
                    ) {
                        val layout = normalStaticLayout ?: return@Canvas
                        // 上层 Canvas 只负责交互覆盖层，不重复绘制任何静态元素。
                        drawSelectedPointOverlay(
                            selectedPoint = selectedPointState.value,
                            coords = layout.coords,
                            viewportStart = viewportStart,
                            viewportEnd = viewportEnd,
                            gridColor = gridColor,
                            powerColor = powerColor,
                            capacityColor = capacityColor,
                            tempColor = tempColor,
                            voltageColor = voltageColor,
                            curveVisibility = curveVisibility,
                            hasVisiblePowerCurve = hasVisiblePowerCurve,
                            powerValueSelector = powerValueSelector
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            LegendItem(
                label = powerCurveModeLabel(curveVisibility.powerCurveMode),
                color = powerColor,
                enabled = hasVisiblePowerCurve,
                onClick = onTogglePowerVisibility
            )
            LegendItem(
                label = "电量",
                color = capacityColor,
                enabled = curveVisibility.showCapacity,
                onClick = onToggleCapacityVisibility
            )
            LegendItem(
                label = "温度",
                color = tempColor,
                enabled = curveVisibility.showTemp,
                onClick = onToggleTempVisibility
            )
            LegendItem(
                label = "电压",
                color = voltageColor,
                enabled = curveVisibility.showVoltage,
                onClick = onToggleVoltageVisibility
            )
            LegendItem(
                label = "应用",
                color = MaterialTheme.colorScheme.secondary,
                enabled = showAppIcons,
                onClick = onToggleAppIconsVisibility
            )
        }
    }
}

/**
 * 显示选中数据点的详细信息
 */
@Composable
private fun SelectedPointInfo(
    selected: RecordDetailChartPoint?,
    recordStartTime: Long,
    powerCurveMode: PowerCurveMode,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    startPadding: Dp,
) {
    val text = if (selected == null) {
        "时间点详细数据"
    } else {
        val offset = (selected.timestamp - recordStartTime).coerceAtLeast(0L)
        val timeText = formatRelativeTime(offset)
        val displayPowerW = selectPowerValueForDisplay(selected, powerCurveMode)
        val powerText = if (powerCurveMode == PowerCurveMode.Fitted) {
            String.format(LocalLocale.current.platformLocale, "%.2f W", displayPowerW)
        } else {
            String.format(LocalLocale.current.platformLocale, "%.2f W", displayPowerW)
        }
        val capacityText = "${selected.capacity}%"
        val tempText =
            if (selected.temp == 0) "" else " · ${
                String.format(
                    LocalLocale.current.platformLocale,
                    "%.1f ℃",
                    selected.temp / 10.0
                )
            }"
        val voltageText =
            if (selected.voltage == 0L) "" else " · ${
                String.format(
                    LocalLocale.current.platformLocale,
                    "%.2f V",
                    selected.voltage / 1_000_000.0
                )
            }"
        "$timeText · $powerText · $capacityText$tempText$voltageText"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = startPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        IconButton(
            onClick = onToggleFullscreen,
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                imageVector = if (isFullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                contentDescription = if (isFullscreen) "退出全屏" else "放大图表"
            )
        }
    }
}

/**
 * 图例项：圆点 + 标签
 */
@Composable
private fun LegendItem(
    label: String,
    color: Color,
    enabled: Boolean,
    onClick: (() -> Unit)?,
) {
    val disabledColor = MaterialTheme.colorScheme.outlineVariant
    val indicatorColor = if (enabled) color else disabledColor
    val textColor = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else disabledColor
    val interactionSource = remember { MutableInteractionSource() }
    val itemModifier = if (onClick != null) {
        Modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 4.dp, vertical = 2.dp)
    } else {
        Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
    }

    Row(
        modifier = itemModifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(modifier = Modifier.size(12.dp)) {
            drawCircle(color = indicatorColor, radius = size.minDimension / 2f)
        }
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = textColor
        )
    }
}

private fun slicePointsForViewport(
    points: List<RecordDetailChartPoint>,
    startTime: Long,
    endTime: Long
): List<RecordDetailChartPoint> {
    // 视口切片不是单纯过滤 in-range 点：
    // 为了让折线在边界处连续，会额外保留左右两侧最近的邻点。
    if (points.isEmpty()) return emptyList()
    val sorted = points.sortedBy { it.timestamp }
    if (startTime <= sorted.first().timestamp && endTime >= sorted.last().timestamp) return sorted

    val inRange = sorted.filter { it.timestamp in startTime..endTime }
    if (inRange.isEmpty()) {
        val previous = sorted.lastOrNull { it.timestamp < startTime }
        val next = sorted.firstOrNull { it.timestamp > endTime }
        return listOfNotNull(previous, next)
    }

    val previous = sorted.lastOrNull { it.timestamp < startTime }
    val next = sorted.firstOrNull { it.timestamp > endTime }
    return buildList {
        if (previous != null) add(previous)
        addAll(inRange)
        if (next != null) add(next)
    }
}

/**
 * 在后台线程预计算图表绘制所需的全部重数据，避免 Compose 主线程承担大块同步计算。
 *
 * @param request 图表预处理请求，收敛所有输入参数
 * @result 返回 Loading / Empty / Ready 三种状态，供 UI 直接切换到 loading 或绘制
 */
private fun prepareChartState(request: ChartPreparationRequest): ChartPreparationResult {
    if (request.canvasSize.width <= 0 || request.canvasSize.height <= 0) {
        return ChartPreparationResult.Loading
    }

    // points 是原始展示点；这里只有它需要应用“孤立息屏点过滤”。
    // trendPoints 已经在 ViewModel 侧基于过滤后的 points 分桶生成，图表层不能再二次过滤。
    val filteredPoints =
        normalizeRecordDetailChartPoints(request.points, request.recordScreenOffEnabled)
    if (filteredPoints.isEmpty()) return ChartPreparationResult.Empty

    val filteredTrendPoints = request.trendPoints.sortedBy { it.timestamp }
    // rawPoints 保留完整原始序列，主要用于屏幕状态线等需要逐点时间连续性的附加图层。
    val rawPoints = request.points.sortedBy { it.timestamp }
    val fullMinTime = filteredPoints.minOf { it.timestamp }
    val fullMaxTime = filteredPoints.maxOf { it.timestamp }
    val viewportStart =
        (request.visibleStartTime ?: fullMinTime).coerceIn(fullMinTime, fullMaxTime)
    val viewportEnd = (request.visibleEndTime ?: fullMaxTime).coerceIn(viewportStart, fullMaxTime)
    val viewportDurationMs = (viewportEnd - viewportStart).coerceAtLeast(1L)
    val isStaticFullscreen = request.isFullscreen

    // 视口切片会额外保留边界外最近点，避免缩放到局部后曲线在视口边缘被硬切断。
    val visibleFilteredPoints = slicePointsForViewport(filteredPoints, viewportStart, viewportEnd)
    val visibleTrendPoints = slicePointsForViewport(filteredTrendPoints, viewportStart, viewportEnd)
    val visibleRawPoints = slicePointsForViewport(rawPoints, viewportStart, viewportEnd)
    val renderFilteredPoints = if (visibleFilteredPoints.size >= 2) {
        if (isStaticFullscreen) filteredPoints else visibleFilteredPoints
    } else {
        filteredPoints
    }
    val renderTrendPoints = if (visibleTrendPoints.isNotEmpty()) {
        if (isStaticFullscreen) filteredTrendPoints else visibleTrendPoints
    } else {
        filteredTrendPoints
    }
    val renderRawPoints = if (visibleRawPoints.size >= 2) {
        if (isStaticFullscreen) rawPoints else visibleRawPoints
    } else {
        rawPoints
    }
    val activePowerPoints = if (request.curveVisibility.powerCurveMode == PowerCurveMode.Fitted) {
        renderTrendPoints
    } else {
        renderFilteredPoints
    }
    val selectablePoints = activePowerPoints.filter { it.timestamp in viewportStart..viewportEnd }
        .ifEmpty { activePowerPoints }

    // 固定功率轴配置：趋势模式单独使用 fittedPowerW，避免继续被原始尖峰绑定纵轴。
    val powerAxisPoints = if (request.curveVisibility.powerCurveMode == PowerCurveMode.Fitted) {
        filteredTrendPoints
    } else {
        filteredPoints
    }
    val maxObservedAbsW = when (request.fixedPowerAxisMode) {
        FixedPowerAxisMode.PositiveOnly -> when (request.curveVisibility.powerCurveMode) {
            PowerCurveMode.Fitted -> powerAxisPoints.maxOfOrNull { it.fittedPowerW } ?: 0.0
            PowerCurveMode.Raw, PowerCurveMode.Hidden -> powerAxisPoints.maxOfOrNull { it.rawPowerW }
                ?: 0.0
        }

        FixedPowerAxisMode.NegativeOnly -> when (request.curveVisibility.powerCurveMode) {
            PowerCurveMode.Fitted -> abs(powerAxisPoints.minOfOrNull { it.fittedPowerW } ?: 0.0)
            PowerCurveMode.Raw, PowerCurveMode.Hidden -> abs(powerAxisPoints.minOfOrNull { it.rawPowerW }
                ?: 0.0)
        }
    }
    val powerAxisConfig = computeFixedPowerAxisConfig(maxObservedAbsW, request.fixedPowerAxisMode)
    val isNegativeMode = request.fixedPowerAxisMode == FixedPowerAxisMode.NegativeOnly
    val hasVisiblePowerCurve = request.curveVisibility.powerCurveMode != PowerCurveMode.Hidden
    val minPower = powerAxisConfig.minValue
    val maxPower = powerAxisConfig.maxValue
    val powerValueSelector: (RecordDetailChartPoint) -> Double = { point ->
        selectPowerValueForChart(point, request.curveVisibility.powerCurveMode, isNegativeMode)
            .coerceIn(minPower, maxPower)
    }
    val (minTemp, maxTemp) = computeTempAxisRange(renderFilteredPoints)
    val (minVoltage, maxVoltage) = computeVoltageAxisRange()
    val capacityMarkerPoints = if (isStaticFullscreen) filteredPoints else renderFilteredPoints
    val capacityMarkers = if (request.curveVisibility.showCapacity) {
        computeCapacityMarkers(capacityMarkerPoints)
    } else {
        emptyList()
    }
    val tempMarkerPoints = if (isStaticFullscreen) filteredPoints else renderFilteredPoints
    val voltageMarkerPoints = if (isStaticFullscreen) filteredPoints else renderFilteredPoints
    val peakDisplay = if (!hasVisiblePowerCurve) {
        null
    } else {
        val peakPlotPowerW = activePowerPoints.maxOfOrNull(powerValueSelector)
        if (peakPlotPowerW == null) {
            null
        } else {
            PeakPowerDisplay(
                peakPlotPowerW = peakPlotPowerW,
                label = String.format(
                    Locale.getDefault(),
                    "%.2f W",
                    if (isNegativeMode) -peakPlotPowerW else peakPlotPowerW
                )
            )
        }
    }

    val paddingRightPx = if (peakDisplay == null) {
        with(request.density) { 32.dp.toPx() }
    } else {
        with(request.density) {
            val reservedPx =
                createTextPaint(0, 24f).measureText(peakDisplay.label) + 8.dp.toPx()
            reservedPx.coerceAtLeast(32.dp.toPx())
        }
    }
    val chartWidthPx =
        (request.canvasSize.width.toFloat() - request.paddingLeftPx - paddingRightPx).coerceAtLeast(
            0f
        )
    val chartHeightPx =
        (request.canvasSize.height.toFloat() - request.paddingTopPx - request.paddingBottomPx).coerceAtLeast(
            0f
        )
    if (chartWidthPx <= 0f || chartHeightPx <= 0f) {
        return ChartPreparationResult.Loading
    }

    val totalDurationMs = (fullMaxTime - fullMinTime).coerceAtLeast(1L)
    val fullscreenContentWidthPx = if (!isStaticFullscreen) {
        chartWidthPx
    } else {
        max(
            chartWidthPx,
            (chartWidthPx * (totalDurationMs.toDouble() / viewportDurationMs.toDouble())).toFloat()
        )
    }
    val fullscreenMaxOffsetPx = (fullscreenContentWidthPx - chartWidthPx).coerceAtLeast(0f)
    val fullscreenContentOffsetPx = if (!isStaticFullscreen || fullscreenMaxOffsetPx <= 0f) {
        0f
    } else {
        (((viewportStart - fullMinTime) / totalDurationMs.toDouble()) * fullscreenContentWidthPx)
            .toFloat()
            .coerceIn(0f, fullscreenMaxOffsetPx)
    }
    val packageFirstTimestamps = buildPackageFirstTimestamps(filteredPoints)
    val visibleAppPoints = if (!request.showAppIcons) {
        emptyList()
    } else if (isStaticFullscreen) {
        filteredPoints
    } else {
        filteredPoints.filter { it.timestamp in viewportStart..viewportEnd }
    }
    val visibleAppPackages = visibleAppPoints.asSequence()
        .mapNotNull { normalizePackageName(it.packageName) }
        .toSet()

    val normalStaticLayout = if (isStaticFullscreen) {
        null
    } else {
        // 普通模式的静态布局完全绑定当前视口：
        // 任何标记、Path 和屏幕状态线都按可见窗口预计算，后续只需直接重绘这一块区域。
        val coords = ChartCoordinates(
            paddingLeft = request.paddingLeftPx,
            paddingTop = request.paddingTopPx,
            chartWidth = chartWidthPx,
            chartHeight = chartHeightPx,
            minTime = viewportStart,
            maxTime = viewportEnd,
            minPower = minPower,
            maxPower = maxPower,
            minTemp = minTemp,
            maxTemp = maxTemp,
            minVoltage = minVoltage,
            maxVoltage = maxVoltage,
            contentWidth = chartWidthPx,
            contentOffsetPx = 0f
        )
        NormalStaticChartLayerLayout(
            coords = coords,
            powerPath = if (hasVisiblePowerCurve) {
                buildPowerPath(
                    points = activePowerPoints,
                    coords = coords,
                    valueSelector = powerValueSelector,
                    smooth = request.curveVisibility.powerCurveMode == PowerCurveMode.Fitted
                )
            } else {
                null
            },
            capacityPath = if (request.curveVisibility.showCapacity) {
                buildCapacityPath(renderFilteredPoints, coords) { it.capacity.toDouble() }
            } else {
                null
            },
            tempPath = if (request.curveVisibility.showTemp) {
                buildTempPath(renderFilteredPoints, coords) { it.temp.toDouble() }
            } else {
                null
            },
            voltagePath = if (request.curveVisibility.showVoltage) {
                buildVoltagePath(renderFilteredPoints, coords) { it.voltage.toDouble() }
            } else {
                null
            },
            appIconPlacements = if (request.showAppIcons) {
                computeAppIconPlacements(
                    points = visibleAppPoints,
                    viewportStart = viewportStart,
                    viewportEnd = viewportEnd,
                    chartLeft = request.paddingLeftPx,
                    chartTop = request.paddingTopPx,
                    chartWidth = chartWidthPx,
                    chartHeight = chartHeightPx,
                    iconSizePx = request.appIconSizePx.toFloat(),
                    packageFirstTimestamps = packageFirstTimestamps
                )
            } else {
                emptyList()
            },
            screenStatePaths = buildScreenStatePaths(renderRawPoints, coords, request.density),
            capacityMarkerLayouts = buildCapacityMarkerLayouts(
                capacityMarkers,
                coords,
                request.density
            ),
            tempMarkerLayouts = if (request.curveVisibility.showTemp) {
                buildTempMarkerLayouts(tempMarkerPoints, coords, request.density)
            } else {
                emptyList()
            },
            voltageMarkerLayouts = if (request.curveVisibility.showVoltage) {
                buildVoltageMarkerLayouts(voltageMarkerPoints, coords, request.density)
            } else {
                emptyList()
            },
            peakAnnotationLayout = peakDisplay?.let { peak ->
                buildPeakAnnotationLayout(
                    peakDisplay = peak,
                    coords = coords,
                    canvasWidthPx = request.canvasSize.width.toFloat(),
                    density = request.density
                )
            }
        )
    }
    val fullscreenStaticLayout = if (!isStaticFullscreen) {
        null
    } else {
        // 全屏静态布局按全时段内容宽度预建，用“内容坐标”表达所有几何。
        // 这样拖动只需要改 contentOffset，而不是在每一帧重算完整 Path。
        val baseCoords = ChartCoordinates(
            paddingLeft = request.paddingLeftPx,
            paddingTop = request.paddingTopPx,
            chartWidth = chartWidthPx,
            chartHeight = chartHeightPx,
            minTime = fullMinTime,
            maxTime = fullMaxTime,
            minPower = minPower,
            maxPower = maxPower,
            minTemp = minTemp,
            maxTemp = maxTemp,
            minVoltage = minVoltage,
            maxVoltage = maxVoltage,
            contentWidth = fullscreenContentWidthPx,
            contentOffsetPx = 0f
        )
        val staticPowerPoints =
            if (request.curveVisibility.powerCurveMode == PowerCurveMode.Fitted) {
                filteredTrendPoints
            } else {
                filteredPoints
            }
        FullscreenStaticChartLayout(
            baseCoords = baseCoords,
            powerPath = if (hasVisiblePowerCurve) {
                buildPowerPath(
                    points = staticPowerPoints,
                    coords = baseCoords,
                    valueSelector = powerValueSelector,
                    smooth = request.curveVisibility.powerCurveMode == PowerCurveMode.Fitted
                )
            } else {
                null
            },
            capacityPath = if (request.curveVisibility.showCapacity) {
                buildCapacityPath(filteredPoints, baseCoords) { it.capacity.toDouble() }
            } else {
                null
            },
            tempPath = if (request.curveVisibility.showTemp) {
                buildTempPath(filteredPoints, baseCoords) { it.temp.toDouble() }
            } else {
                null
            },
            voltagePath = if (request.curveVisibility.showVoltage) {
                buildVoltagePath(filteredPoints, baseCoords) { it.voltage.toDouble() }
            } else {
                null
            },
            appIconPlacements = if (request.showAppIcons) {
                computeAppIconPlacements(
                    points = filteredPoints,
                    viewportStart = fullMinTime,
                    viewportEnd = fullMaxTime,
                    chartLeft = request.paddingLeftPx,
                    chartTop = request.paddingTopPx,
                    chartWidth = fullscreenContentWidthPx,
                    chartHeight = chartHeightPx,
                    iconSizePx = request.appIconSizePx.toFloat(),
                    packageFirstTimestamps = packageFirstTimestamps
                )
            } else {
                emptyList()
            }
        )
    }
    val appIconPackages = if (!request.showAppIcons) {
        emptySet()
    } else if (isStaticFullscreen) {
        fullscreenStaticLayout?.appIconPlacements
            ?.asSequence()
            ?.map { it.packageName }
            ?.toSet()
            ?: emptySet()
    } else {
        visibleAppPackages
    }

    return ChartPreparationResult.Ready(
        PreparedChartState(
            fullMinTime = fullMinTime,
            fullMaxTime = fullMaxTime,
            viewportStart = viewportStart,
            viewportEnd = viewportEnd,
            viewportDurationMs = viewportDurationMs,
            isStaticFullscreen = isStaticFullscreen,
            renderFilteredPoints = renderFilteredPoints,
            renderRawPoints = renderRawPoints,
            activePowerPoints = activePowerPoints,
            selectablePoints = selectablePoints,
            capacityMarkers = capacityMarkers,
            tempMarkerPoints = tempMarkerPoints,
            voltageMarkerPoints = voltageMarkerPoints,
            powerAxisConfig = powerAxisConfig,
            hasVisiblePowerCurve = hasVisiblePowerCurve,
            isNegativeMode = isNegativeMode,
            minPower = minPower,
            maxPower = maxPower,
            minTemp = minTemp,
            maxTemp = maxTemp,
            minVoltage = minVoltage,
            maxVoltage = maxVoltage,
            peakDisplay = peakDisplay,
            paddingRightPx = paddingRightPx,
            chartWidthPx = chartWidthPx,
            chartHeightPx = chartHeightPx,
            fullscreenContentWidthPx = fullscreenContentWidthPx,
            fullscreenContentOffsetPx = fullscreenContentOffsetPx,
            normalStaticLayout = normalStaticLayout,
            fullscreenStaticLayout = fullscreenStaticLayout,
            appIconPackages = appIconPackages
        )
    )
}

private data class AppBucketUsage(
    val packageName: String,
    val count: Int,
)

private data class AppIconPlacement(
    val packageName: String,
    val topLeft: Offset,
)

@Composable
private fun rememberAppIconBitmaps(
    packageNames: Set<String>,
    iconSizePx: Int,
): Map<String, ImageBitmap> {
    val context = LocalContext.current
    val packageList = remember(packageNames) { packageNames.toList().sorted() }
    return produceState(
        initialValue = emptyMap(),
        key1 = context,
        key2 = packageList,
        key3 = iconSizePx
    ) {
        if (iconSizePx <= 0) {
            value = emptyMap()
            return@produceState
        }

        // 先同步当前已命中的全局缓存，避免视口切换时重复等待异步加载完成。
        val currentIcons = LinkedHashMap<String, ImageBitmap>()
        packageList.forEach { packageName ->
            AppIconMemoryCache.get(packageName, iconSizePx)?.let { bitmap ->
                currentIcons[packageName] = bitmap
            }
        }
        value = currentIcons.toMap()

        // 仅补齐当前视口缺失的图标；每成功加载一个包名就立刻刷新结果，让图表逐步显示。
        for (packageName in packageList) {
            if (currentIcons.containsKey(packageName)) continue
            if (!AppIconMemoryCache.shouldLoad(packageName, iconSizePx)) continue

            AppIconMemoryCache.loadAndCache(context, packageName, iconSizePx)?.let { bitmap ->
                currentIcons[packageName] = bitmap
                value = currentIcons.toMap()
            }
        }
    }.value
}

private fun normalizePackageName(packageName: String?): String? =
    packageName?.trim()?.takeIf { it.isNotEmpty() }

private fun buildPackageFirstTimestamps(points: List<RecordDetailChartPoint>): Map<String, Long> {
    if (points.isEmpty()) return emptyMap()

    val firstTimestamps = LinkedHashMap<String, Long>()
    for (point in points) {
        val packageName = normalizePackageName(point.packageName) ?: continue
        // 同频次下按首次出现时间稳定排序，避免同一列里图标顺序随着重组抖动。
        firstTimestamps.putIfAbsent(packageName, point.timestamp)
    }
    return firstTimestamps
}

private fun computeAppIconPlacements(
    points: List<RecordDetailChartPoint>,
    viewportStart: Long,
    viewportEnd: Long,
    chartLeft: Float,
    chartTop: Float,
    chartWidth: Float,
    chartHeight: Float,
    iconSizePx: Float,
    packageFirstTimestamps: Map<String, Long>,
): List<AppIconPlacement> {
    if (points.isEmpty() || chartWidth < iconSizePx || chartHeight < iconSizePx || iconSizePx <= 0f) {
        return emptyList()
    }

    val columnCount = (chartWidth / iconSizePx).toInt().coerceAtLeast(1)
    val maxRows = (chartHeight / iconSizePx).toInt().coerceAtLeast(1)
    val bucketWidth = chartWidth / columnCount
    val timeRange = (viewportEnd - viewportStart).coerceAtLeast(1L).toDouble()
    val bucketCounts = Array(columnCount) { LinkedHashMap<String, Int>() }

    for (point in points) {
        if (!point.isDisplayOn) continue
        val packageName = normalizePackageName(point.packageName) ?: continue
        // 图标只表达“当前时间段前台应用分布”，因此仅统计亮屏采样点。
        val bucketIndex = (((point.timestamp - viewportStart) / timeRange) * columnCount)
            .toInt()
            .coerceIn(0, columnCount - 1)
        val counts = bucketCounts[bucketIndex]
        counts[packageName] = (counts[packageName] ?: 0) + 1
    }

    return buildList {
        bucketCounts.forEachIndexed { columnIndex, counts ->
            if (counts.isEmpty()) return@forEachIndexed

            val columnUsages = counts.entries
                .map { (packageName, count) -> AppBucketUsage(packageName, count) }
                .sortedWith(
                    compareByDescending<AppBucketUsage> { it.count }
                        .thenBy { packageFirstTimestamps[it.packageName] ?: Long.MAX_VALUE }
                        .thenBy { it.packageName }
                )
                .take(maxRows)

            val columnLeft = chartLeft + bucketWidth * columnIndex + (bucketWidth - iconSizePx) / 2f
            columnUsages.forEachIndexed { rowIndex, usage ->
                add(
                    AppIconPlacement(
                        packageName = usage.packageName,
                        topLeft = Offset(
                            x = columnLeft,
                            y = chartTop + chartHeight - (rowIndex + 1) * iconSizePx
                        )
                    )
                )
            }
        }
    }
}

/**
 * 将 Compose Outline 转成 Path，供图标绘制时的 clipPath 裁剪复用。
 */
private fun outlineToPath(outline: Outline): Path {
    return when (outline) {
        is Outline.Generic -> outline.path
        is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
        is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
    }
}

/**
 * 构建功率曲线路径
 */
private fun buildPowerPath(
    points: List<RecordDetailChartPoint>,
    coords: ChartCoordinates,
    valueSelector: (RecordDetailChartPoint) -> Double,
    smooth: Boolean,
): Path {
    // 功率曲线是唯一同时支持“原始折线 / 趋势平滑线”两种绘制样式的曲线。
    if (!smooth) {
        return buildLinearPath(points, coords) { point -> coords.powerToY(valueSelector(point)) }
    }
    return buildSmoothPath(points, coords) { point -> coords.powerToY(valueSelector(point)) }
}

/**
 * 构建电量曲线路径（固定 0.9 缩放，避免与底部时间轴重叠）
 */
private fun buildCapacityPath(
    points: List<RecordDetailChartPoint>,
    coords: ChartCoordinates,
    valueSelector: (RecordDetailChartPoint) -> Double
): Path {
    return buildLinearPath(points, coords) { point -> coords.capacityToY(valueSelector(point)) }
}

/**
 * 构建温度曲线路径（使用 tempToY 映射，0.9 缩放）
 */
private fun buildTempPath(
    points: List<RecordDetailChartPoint>,
    coords: ChartCoordinates,
    valueSelector: (RecordDetailChartPoint) -> Double
): Path {
    return buildLinearPath(points, coords) { point -> coords.tempToY(valueSelector(point)) }
}

/**
 * 构建电压曲线路径（使用 voltageToY 映射，0.9 缩放）
 */
private fun buildVoltagePath(
    points: List<RecordDetailChartPoint>,
    coords: ChartCoordinates,
    valueSelector: (RecordDetailChartPoint) -> Double
): Path {
    val path = Path()
    var hasStartedSegment = false
    points.forEach { point ->
        val voltage = valueSelector(point)
        if (voltage <= 0.0) {
            hasStartedSegment = false
            return@forEach
        }
        val x = coords.timeToX(point.timestamp)
        val y = coords.voltageToY(voltage)
        if (!hasStartedSegment) {
            path.moveTo(x, y)
            hasStartedSegment = true
        } else {
            path.lineTo(x, y)
        }
    }
    return path
}

private fun buildLinearPath(
    points: List<RecordDetailChartPoint>,
    coords: ChartCoordinates,
    ySelector: (RecordDetailChartPoint) -> Float,
): Path {
    // 通用折线路径构建器，调用方只需要提供“这个点在当前轴上的 Y 值”。
    val path = Path()
    points.forEachIndexed { index, point ->
        val x = coords.timeToX(point.timestamp)
        val y = ySelector(point)
        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    return path
}

private fun buildSmoothPath(
    points: List<RecordDetailChartPoint>,
    coords: ChartCoordinates,
    ySelector: (RecordDetailChartPoint) -> Float,
): Path {
    // 使用相邻点推导三次贝塞尔控制点，让趋势曲线更适合表达低频走势。
    // 它只用于趋势点；原始点仍保持折线，避免把瞬时尖刺错误地视觉平滑成真实走势。
    val path = Path()
    if (points.isEmpty()) return path

    val pathPoints = points.map { point ->
        Offset(coords.timeToX(point.timestamp), ySelector(point))
    }
    path.moveTo(pathPoints.first().x, pathPoints.first().y)
    if (pathPoints.size == 1) return path

    for (index in 0 until pathPoints.lastIndex) {
        val previous = pathPoints.getOrElse(index - 1) { pathPoints[index] }
        val current = pathPoints[index]
        val next = pathPoints[index + 1]
        val nextNext = pathPoints.getOrElse(index + 2) { next }
        val controlPoint1 = Offset(
            x = current.x + (next.x - previous.x) / 6f,
            y = current.y + (next.y - previous.y) / 6f
        )
        val controlPoint2 = Offset(
            x = next.x - (nextNext.x - current.x) / 6f,
            y = next.y - (nextNext.y - current.y) / 6f
        )
        path.cubicTo(
            controlPoint1.x,
            controlPoint1.y,
            controlPoint2.x,
            controlPoint2.y,
            next.x,
            next.y
        )
    }
    return path
}

/**
 * 仅绘制垂直网格线
 */
private fun DrawScope.drawVerticalGridLines(
    coords: ChartCoordinates,
    gridColor: Color,
    verticalSegments: Int,
    visibleStartTime: Long,
    visibleEndTime: Long,
) {
    val cols = verticalSegments.coerceAtLeast(1)
    val visibleRange = max(1L, visibleEndTime - visibleStartTime).toDouble()
    val lineColor = gridColor.copy(alpha = 0.3f)
    val stroke = 1.dp.toPx()

    for (i in 0..cols) {
        val timestamp = visibleStartTime + (visibleRange * i / cols).toLong()
        val x = coords.timeToX(timestamp)
        drawLine(
            lineColor,
            Offset(x, coords.paddingTop),
            Offset(x, coords.paddingTop + coords.chartHeight),
            stroke
        )
    }
}

/**
 * 绘制固定功率轴的水平网格线（主刻度实线，次刻度虚线）
 */
private fun DrawScope.drawFixedPowerGridLines(
    coords: ChartCoordinates,
    gridColor: Color,
    majorStepW: Int,
    minorStepW: Int
) {
    val minW = coords.minPower.roundToInt()
    val maxW = coords.maxPower.roundToInt()
    val minor = minorStepW.coerceAtLeast(1)
    val major = majorStepW.coerceAtLeast(minor)
    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()), 0f)

    // 绘制主刻度（实线）和次刻度（虚线）
    var value = minW
    while (value <= maxW) {
        val isMajor = value % major == 0
        val y = coords.powerToY(value.toDouble())
        drawLine(
            color = gridColor.copy(alpha = if (isMajor) 0.35f else 0.18f),
            start = Offset(coords.paddingLeft, y),
            end = Offset(coords.paddingLeft + coords.chartWidth, y),
            strokeWidth = if (isMajor) 1.dp.toPx() else 0.8.dp.toPx(),
            pathEffect = if (isMajor) null else dashEffect
        )
        value += minor
    }
}

/**
 * 绘制固定功率轴的刻度标签
 */
private fun DrawScope.drawFixedPowerAxisLabels(
    coords: ChartCoordinates,
    labelColor: Color,
    majorStepW: Int,
    minorStepW: Int,
    labelSignMultiplier: Int,
) {
    val textPaint = createTextPaint(labelColor.toArgb(), 24f)
    val minW = coords.minPower.roundToInt()
    val maxW = coords.maxPower.roundToInt()
    val minor = minorStepW.coerceAtLeast(1)
    val major = majorStepW.coerceAtLeast(minor)
    val topBaseline = coords.paddingTop - textPaint.fontMetrics.ascent
    val bottomBaseline = coords.paddingTop + coords.chartHeight - textPaint.fontMetrics.descent

    // 仅绘制主刻度标签
    var value = minW
    while (value <= maxW) {
        if (value % major == 0) {
            val y = coords.powerToY(value.toDouble())
            val powerText = String.format(
                Locale.getDefault(),
                "%.0f W",
                (value * labelSignMultiplier).toDouble()
            )
            val powerWidth = textPaint.measureText(powerText)
            val baselineY = (y - 4.dp.toPx()).coerceIn(topBaseline, bottomBaseline)
            drawContext.canvas.nativeCanvas.drawText(
                powerText,
                coords.paddingLeft - powerWidth - 8.dp.toPx(),
                baselineY,
                textPaint
            )
        }
        value += minor
    }
}

/**
 * 绘制时间轴刻度标签
 */
private fun DrawScope.drawTimeAxisLabels(
    coords: ChartCoordinates,
    labelColor: Color,
    timeLabelFormatter: (Long) -> String,
    totalSegments: Int,
    labelStep: Int,
    visibleStartTime: Long,
    visibleEndTime: Long,
) {
    val textPaint = createTextPaint(labelColor.toArgb(), 24f)
    val cols = totalSegments.coerceAtLeast(1)
    val step = labelStep.coerceAtLeast(1)
    val visibleRange = max(1L, visibleEndTime - visibleStartTime).toDouble()

    for (i in 0..cols) {
        if (i % step != 0 && i != cols) continue
        val timeValue = visibleStartTime + (visibleRange * i / cols).toLong()
        val x = coords.timeToX(timeValue)
        val text = timeLabelFormatter(timeValue)
        val textWidth = textPaint.measureText(text)
        drawContext.canvas.nativeCanvas.drawText(
            text,
            x - textWidth / 2f,
            coords.paddingTop + coords.chartHeight + 24.dp.toPx(),
            textPaint
        )
    }
}

/**
 * 预构建峰值横线与标签布局，供普通模式和全屏模式复用。
 *
 * @param peakDisplay 峰值文本与峰值高度
 * @param coords 图表坐标系
 * @param canvasWidthPx 当前 Canvas 总宽度
 * @return 峰值注解布局；当宽度非法时返回 null
 */
private fun buildPeakAnnotationLayout(
    peakDisplay: PeakPowerDisplay,
    coords: ChartCoordinates,
    canvasWidthPx: Float,
    density: Density,
): PeakAnnotationLayout? {
    // 峰值标签放在绘图区右外侧保留区，而不是压进图内：
    // 这样既能和曲线保持对齐，又不会在峰值靠右时遮住终点附近的数据。
    if (canvasWidthPx <= 0f || coords.chartWidth <= 0f) return null
    val peakY = coords.powerToY(peakDisplay.peakPlotPowerW)
    val labelPaint = createTextPaint(0, 24f)
    val labelWidth = labelPaint.measureText(peakDisplay.label)
    val plotRight = coords.paddingLeft + coords.chartWidth
    val labelX = with(density) {
        (plotRight + 4.dp.toPx())
            .coerceAtMost(canvasWidthPx - labelWidth - 4.dp.toPx())
            .coerceAtLeast(plotRight + 2.dp.toPx())
    }
    val labelY = with(density) {
        (peakY - 4.dp.toPx()).coerceIn(
            coords.paddingTop + 12.dp.toPx(),
            coords.paddingTop + coords.chartHeight - 4.dp.toPx()
        )
    }
    return PeakAnnotationLayout(
        peakY = peakY,
        label = peakDisplay.label,
        labelX = labelX,
        labelBaselineY = labelY
    )
}

/**
 * 预构建容量标记点的几何布局，避免普通模式滚动时重复计算标签位置。
 *
 * @param markers 电量标记点集合
 * @param coords 图表坐标系
 * @return 预计算后的标记布局
 */
private fun buildCapacityMarkerLayouts(
    markers: List<CapacityMarker>,
    coords: ChartCoordinates,
    density: Density,
): List<TextPointMarkerLayout> {
    if (markers.isEmpty()) return emptyList()
    val textPaint = createTextPaint(0, 20f)
    val padding = with(density) { 6.dp.toPx() }
    val textHeight = -textPaint.fontMetrics.ascent
    val chartRight = coords.paddingLeft + coords.chartWidth
    val scaledChartHeight = coords.chartHeight * 0.9f
    val chartBottom = coords.paddingTop + scaledChartHeight

    return markers.map { marker ->
        val x = coords.timeToX(marker.timestamp)
        val y = coords.capacityToY(marker.capacity.toDouble())
        val labelWidth = textPaint.measureText(marker.label)
        var textX = x + padding
        if (textX + labelWidth > chartRight) textX = x - padding - labelWidth
        if (textX < coords.paddingLeft) textX = coords.paddingLeft

        var textY = y - padding
        if (textY - textHeight < coords.paddingTop) textY = y + textHeight + padding
        if (textY > chartBottom) textY = chartBottom

        TextPointMarkerLayout(
            center = Offset(x, y),
            label = marker.label,
            labelX = textX,
            labelBaselineY = textY
        )
    }
}

/**
 * 预构建温度极值标记的几何布局。
 *
 * @param points 当前图表点集合
 * @param coords 图表坐标系
 * @return 最高温/最低温标记布局；无有效温度时返回空集合
 */
private fun buildTempMarkerLayouts(
    points: List<RecordDetailChartPoint>,
    coords: ChartCoordinates,
    density: Density,
): List<TextPointMarkerLayout> {
    val validPoints = points.filter { it.temp > 0 }
    if (validPoints.size < 2) return emptyList()
    val maxPoint = validPoints.maxByOrNull { it.temp } ?: return emptyList()
    val minPoint = validPoints.minByOrNull { it.temp } ?: return emptyList()
    if (maxPoint.temp == minPoint.temp) return emptyList()

    val textPaint = createTextPaint(0, 20f)
    val padding = with(density) { 6.dp.toPx() }
    val chartRight = coords.paddingLeft + coords.chartWidth

    return listOf(maxPoint, minPoint).map { point ->
        val x = coords.timeToX(point.timestamp)
        val y = coords.tempToY(point.temp.toDouble())
        val label = String.format(Locale.getDefault(), "%.1f ℃", point.temp / 10.0)
        val labelWidth = textPaint.measureText(label)
        var textX = x + padding
        if (textX + labelWidth > chartRight) textX = x - padding - labelWidth
        if (textX < coords.paddingLeft) textX = coords.paddingLeft

        val isMax = point === maxPoint
        // 最高温贴点上方、最低温贴点下方，避免两个极值刚好接近时文字互相覆盖。
        val textY = if (isMax) {
            y - padding
        } else {
            y - textPaint.fontMetrics.ascent + padding
        }

        TextPointMarkerLayout(
            center = Offset(x, y),
            label = label,
            labelX = textX,
            labelBaselineY = textY
        )
    }
}

/**
 * 预构建电压极值标记的几何布局。
 *
 * @param points 当前图表点集合
 * @param coords 图表坐标系
 * @return 最高电压/最低电压标记布局；无有效电压时返回空集合
 */
private fun buildVoltageMarkerLayouts(
    points: List<RecordDetailChartPoint>,
    coords: ChartCoordinates,
    density: Density,
): List<TextPointMarkerLayout> {
    val validPoints = points.filter { it.voltage > 0L }
    if (validPoints.size < 2) return emptyList()
    val maxPoint = validPoints.maxByOrNull { it.voltage } ?: return emptyList()
    val minPoint = validPoints.minByOrNull { it.voltage } ?: return emptyList()
    if (maxPoint.voltage == minPoint.voltage) return emptyList()

    val textPaint = createTextPaint(0, 20f)
    val padding = with(density) { 6.dp.toPx() }
    val textHeight = -textPaint.fontMetrics.ascent
    val chartRight = coords.paddingLeft + coords.chartWidth
    val scaledChartHeight = coords.chartHeight * 0.9f
    val chartBottom = coords.paddingTop + scaledChartHeight

    return listOf(maxPoint, minPoint).map { point ->
        val x = coords.timeToX(point.timestamp)
        val y = coords.voltageToY(point.voltage.toDouble())
        val label = String.format(Locale.getDefault(), "%.2f V", point.voltage / 1_000_000.0)
        val labelWidth = textPaint.measureText(label)
        var textX = x + padding
        if (textX + labelWidth > chartRight) textX = x - padding - labelWidth
        if (textX < coords.paddingLeft) textX = coords.paddingLeft

        val isMax = point === maxPoint
        var textY = if (isMax) {
            y - padding
        } else {
            y - textPaint.fontMetrics.ascent + padding
        }
        if (textY - textHeight < coords.paddingTop) {
            textY = y + textHeight + padding
        }
        if (textY > chartBottom) {
            textY = (y - padding).coerceAtLeast(coords.paddingTop + textHeight)
        }

        TextPointMarkerLayout(
            center = Offset(x, y),
            label = label,
            labelX = textX,
            labelBaselineY = textY
        )
    }
}

/**
 * 预构建屏幕状态线 Path，供普通模式静态层直接复用。
 *
 * @param points 原始时间序列点
 * @param coords 图表坐标系
 * @return 亮屏/息屏 Path；无数据时返回 null
 */
private fun buildScreenStatePaths(
    points: List<RecordDetailChartPoint>,
    coords: ChartCoordinates,
    density: Density,
): ScreenStatePaths? {
    if (points.isEmpty()) return null
    // 亮屏/息屏轨道独立于主绘图区，预构建 Path 后可在普通模式直接复用，
    // 避免每次选中点变化都重新遍历整段原始屏幕状态序列。
    val y = coords.paddingTop + coords.chartHeight + with(density) { 8.dp.toPx() }
    val screenOnPath = Path()
    val screenOffPath = Path()
    var hasOnSegment = false
    var hasOffSegment = false

    if (points.size == 1) {
        val targetPath = if (points[0].isDisplayOn) screenOnPath else screenOffPath
        targetPath.moveTo(coords.paddingLeft, y)
        targetPath.lineTo(coords.paddingLeft + coords.chartWidth, y)
        if (points[0].isDisplayOn) {
            hasOnSegment = true
        } else {
            hasOffSegment = true
        }
        return ScreenStatePaths(
            screenOnPath = screenOnPath.takeIf { hasOnSegment },
            screenOffPath = screenOffPath.takeIf { hasOffSegment }
        )
    }

    var lastOnX = Float.NaN
    var lastOffX = Float.NaN
    for (index in 0 until points.lastIndex) {
        val current = points[index]
        val next = points[index + 1]
        val startX = coords.timeToX(current.timestamp)
        val endX = coords.timeToX(next.timestamp)
        if (current.isDisplayOn) {
            if (startX != lastOnX) screenOnPath.moveTo(startX, y)
            screenOnPath.lineTo(endX, y)
            lastOnX = endX
            hasOnSegment = true
        } else {
            if (startX != lastOffX) screenOffPath.moveTo(startX, y)
            screenOffPath.lineTo(endX, y)
            lastOffX = endX
            hasOffSegment = true
        }
    }

    return ScreenStatePaths(
        screenOnPath = screenOnPath.takeIf { hasOnSegment },
        screenOffPath = screenOffPath.takeIf { hasOffSegment }
    )
}

/**
 * 绘制预计算后的点标记与文本。
 *
 * @param layouts 预计算布局
 * @param color 标记颜色
 */
private fun DrawScope.drawTextPointMarkerLayouts(
    layouts: List<TextPointMarkerLayout>,
    color: Color
) {
    if (layouts.isEmpty()) return
    // 这里只消费“已经排版好的布局”，不再关心业务含义。
    // 这样电量标记与温度极值可以共用同一绘制器，同时把排版约束留在预计算阶段处理。
    val textPaint = createTextPaint(color.toArgb(), 20f)
    layouts.forEach { layout ->
        drawCircle(color, radius = 3.dp.toPx() * 0.65f, center = layout.center)
        drawContext.canvas.nativeCanvas.drawText(
            layout.label,
            layout.labelX,
            layout.labelBaselineY,
            textPaint
        )
    }
}

/**
 * 成组绘制峰值横线与右侧标签，保持它们处于同一绘制层级。
 *
 * @param layout 峰值布局
 * @param coords 图表坐标系
 * @param color 峰值颜色
 */
private fun DrawScope.drawPeakAnnotation(
    layout: PeakAnnotationLayout,
    coords: ChartCoordinates,
    color: Color
) {
    // 线和文字在同一 helper 中绘制，避免调用方只更新其一导致峰值横线与标签错层。
    drawLine(
        color = color.copy(alpha = 0.9f),
        start = Offset(coords.paddingLeft, layout.peakY),
        end = Offset(coords.paddingLeft + coords.chartWidth, layout.peakY),
        strokeWidth = 1.dp.toPx()
    )
    val textPaint = createTextPaint(color.toArgb(), 24f)
    drawContext.canvas.nativeCanvas.drawText(
        layout.label,
        layout.labelX,
        layout.labelBaselineY,
        textPaint
    )
}

/**
 * 绘制预构建的屏幕状态线 Path。
 *
 * @param paths 亮屏/息屏 Path
 * @param screenOnColor 亮屏颜色
 * @param screenOffColor 息屏颜色
 * @param strokeWidth 线宽
 */
private fun DrawScope.drawScreenStatePaths(
    paths: ScreenStatePaths,
    screenOnColor: Color,
    screenOffColor: Color,
    strokeWidth: Dp
) {
    val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Butt)
    paths.screenOnPath?.let { drawPath(it, screenOnColor, style = stroke) }
    paths.screenOffPath?.let { drawPath(it, screenOffColor, style = stroke) }
}

/**
 * 绘制选中点覆盖层。
 *
 * @param selectedPoint 当前选中的点
 * @param coords 图表坐标系
 * @param viewportStart 视口起点
 * @param viewportEnd 视口终点
 * @param gridColor 参考线颜色
 * @param powerColor 功率点颜色
 * @param capacityColor 电量点颜色
 * @param tempColor 温度点颜色
 * @param curveVisibility 曲线显隐配置
 * @param hasVisiblePowerCurve 当前是否展示功率曲线
 * @param powerValueSelector 功率取值函数
 */
private fun DrawScope.drawSelectedPointOverlay(
    selectedPoint: RecordDetailChartPoint?,
    coords: ChartCoordinates,
    viewportStart: Long,
    viewportEnd: Long,
    gridColor: Color,
    powerColor: Color,
    capacityColor: Color,
    tempColor: Color,
    voltageColor: Color,
    curveVisibility: RecordChartCurveVisibility,
    hasVisiblePowerCurve: Boolean,
    powerValueSelector: (RecordDetailChartPoint) -> Double
) {
    // 选中态只使用当前视口坐标，不接受内容层偏移；
    // 调用方必须先把点限制到可见窗口，避免在全屏平移时出现“屏外点仍在画辅助线”的错觉。
    clipRect(
        left = coords.paddingLeft,
        top = coords.paddingTop,
        right = coords.paddingLeft + coords.chartWidth,
        bottom = coords.paddingTop + coords.chartHeight
    ) {
        selectedPoint
            ?.takeIf { it.timestamp in viewportStart..viewportEnd }
            ?.let { point ->
                val selectedX = coords.timeToX(point.timestamp)
                drawLine(
                    color = gridColor.copy(alpha = 0.6f),
                    start = Offset(selectedX, coords.paddingTop),
                    end = Offset(selectedX, coords.paddingTop + coords.chartHeight),
                    strokeWidth = 1.dp.toPx()
                )
                if (hasVisiblePowerCurve) {
                    val powerY = coords.powerToY(powerValueSelector(point))
                    drawCircle(
                        powerColor,
                        radius = 2.8.dp.toPx(),
                        center = Offset(selectedX, powerY)
                    )
                }
                if (curveVisibility.showCapacity) {
                    val capacityY = coords.capacityToY(point.capacity.toDouble())
                    drawCircle(
                        capacityColor,
                        radius = 2.8.dp.toPx(),
                        center = Offset(selectedX, capacityY)
                    )
                }
                if (curveVisibility.showTemp) {
                    val tempY = coords.tempToY(point.temp.toDouble())
                    drawCircle(
                        tempColor,
                        radius = 2.8.dp.toPx(),
                        center = Offset(selectedX, tempY)
                    )
                }
                if (curveVisibility.showVoltage && point.voltage > 0L) {
                    val voltageY = coords.voltageToY(point.voltage.toDouble())
                    drawCircle(
                        voltageColor,
                        radius = 2.8.dp.toPx(),
                        center = Offset(selectedX, voltageY)
                    )
                }
            }
    }
}

/**
 * 绘制屏幕状态线（亮屏/息屏分色显示）
 */
private fun DrawScope.drawScreenStateLine(
    points: List<RecordDetailChartPoint>,
    coords: ChartCoordinates,
    screenOnColor: Color,
    screenOffColor: Color,
    strokeWidth: Dp
) {
    if (points.isEmpty()) return
    val y = coords.paddingTop + coords.chartHeight + 8.dp.toPx()

    if (points.size == 1) {
        drawLine(
            color = if (points[0].isDisplayOn) screenOnColor else screenOffColor,
            start = Offset(coords.paddingLeft, y),
            end = Offset(coords.paddingLeft + coords.chartWidth, y),
            strokeWidth = strokeWidth.toPx()
        )
        return
    }

    val screenOnPath = Path()
    val screenOffPath = Path()
    var lastOnX = Float.NaN
    var lastOffX = Float.NaN

    for (i in 0 until points.size - 1) {
        val current = points[i]
        val next = points[i + 1]
        val startX = coords.timeToX(current.timestamp)
        val endX = coords.timeToX(next.timestamp)

        if (current.isDisplayOn) {
            if (startX != lastOnX) screenOnPath.moveTo(startX, y)
            screenOnPath.lineTo(endX, y)
            lastOnX = endX
        } else {
            if (startX != lastOffX) screenOffPath.moveTo(startX, y)
            screenOffPath.lineTo(endX, y)
            lastOffX = endX
        }
    }

    val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Butt)
    if (!lastOnX.isNaN()) drawPath(screenOnPath, screenOnColor, style = stroke)
    if (!lastOffX.isNaN()) drawPath(screenOffPath, screenOffColor, style = stroke)
}

/**
 * 固定功率轴配置
 */
private data class FixedPowerAxisConfig(
    val minValue: Double,
    val maxValue: Double,
    val majorStepW: Int,
    val minorStepW: Int,
)

/**
 * 固定功率轴模式：正值（充电）或负值（放电）
 */
enum class FixedPowerAxisMode {
    PositiveOnly,
    NegativeOnly,
}

/** 温度轴范围：按数据自动扩展，20℃ 步进，无默认范围与硬限制。 */
private fun computeTempAxisRange(points: List<RecordDetailChartPoint>): Pair<Double, Double> {
    val validTemps = points.asSequence()
        .map { it.temp.toDouble() }
        .filter { it > 0.0 }
        .toList()
    if (validTemps.isEmpty()) return 0.0 to TEMP_EXPAND_STEP_TENTHS

    val observedMin = validTemps.min()
    val observedMax = validTemps.max()
    val minTemp = kotlin.math.floor(observedMin / TEMP_EXPAND_STEP_TENTHS) * TEMP_EXPAND_STEP_TENTHS
    val maxTemp = kotlin.math.ceil(observedMax / TEMP_EXPAND_STEP_TENTHS) * TEMP_EXPAND_STEP_TENTHS

    return if (maxTemp - minTemp < 1.0) {
        minTemp to (minTemp + TEMP_EXPAND_STEP_TENTHS)
    } else {
        minTemp to maxTemp
    }
}

/** 电压轴固定为 2.5V ~ 5.0V，保证所有记录详情图的纵轴一致。 */
private fun computeVoltageAxisRange(): Pair<Double, Double> {
    return VOLTAGE_AXIS_MIN_UV to VOLTAGE_AXIS_MAX_UV
}

/**
 * 根据最大功率值计算固定轴配置（自动选择合适的刻度范围）
 */
private fun computeFixedPowerAxisConfig(
    maxObservedAbsW: Double,
    mode: FixedPowerAxisMode
): FixedPowerAxisConfig {
    val axisMaxW = when {
        maxObservedAbsW > 200 -> 240
        maxObservedAbsW >= 150 -> 210
        maxObservedAbsW >= 120 -> 150
        maxObservedAbsW >= 100 -> 120
        maxObservedAbsW >= 80 -> 100
        maxObservedAbsW >= 60 -> 80
        maxObservedAbsW >= 45 -> 60
        maxObservedAbsW >= 30 -> 45
        maxObservedAbsW > 15 -> 30
        maxObservedAbsW > 10 -> 15
        else -> 15
    }

    val majorStepW = when (axisMaxW) {
        10 -> 5
        15 -> 5
        30 -> 10
        45 -> 15
        60, 80, 100, 120 -> 20
        150, 210, 240 -> 30
        else -> 20
    }

    val minorStepW = when {
        axisMaxW <= 15 -> 1
        axisMaxW <= 60 -> 5
        else -> 10
    }

    val minValue = 0.0
    val maxValue = axisMaxW.toDouble()

    return FixedPowerAxisConfig(
        minValue = minValue,
        maxValue = maxValue,
        majorStepW = majorStepW,
        minorStepW = minorStepW
    )
}

/**
 * 电量标记点
 */
private data class CapacityMarker(
    val timestamp: Long,
    val capacity: Int,
    val label: String,
)

/**
 * 计算电量标记点（起止点 + 整数倍刻度）
 */
private fun computeCapacityMarkers(points: List<RecordDetailChartPoint>): List<CapacityMarker> {
    if (points.isEmpty()) return emptyList()
    val sorted = points.sortedBy { it.timestamp }
    val startCapacity = sorted.first().capacity
    val endCapacity = sorted.last().capacity
    val delta = kotlin.math.abs(endCapacity - startCapacity)
    val step = if (delta <= 30) 5 else 10

    val minCap = minOf(startCapacity, endCapacity)
    val maxCap = maxOf(startCapacity, endCapacity)

    // 收集目标电量值：起止点 + 整数倍刻度
    val targets = LinkedHashSet<Int>()
    targets.add(startCapacity)
    targets.add(endCapacity)

    val firstMultiple = ((minCap + step - 1) / step) * step
    val lastMultiple = (maxCap / step) * step
    var value = firstMultiple
    while (value <= lastMultiple) {
        targets.add(value)
        value += step
    }

    // 为每个目标电量值找到最近的数据点，避免重复时间戳
    val usedTimestamps = HashSet<Long>()
    val markers = ArrayList<CapacityMarker>(targets.size)
    for (target in targets.toList().sorted()) {
        val nearest = sorted.minByOrNull { kotlin.math.abs(it.capacity - target) } ?: continue
        if (!usedTimestamps.add(nearest.timestamp)) continue
        markers.add(
            CapacityMarker(
                timestamp = nearest.timestamp,
                capacity = nearest.capacity,
                label = "$target%"
            )
        )
    }
    return markers.sortedBy { it.timestamp }
}

/**
 * 绘制电量标记点及标签
 */
private fun DrawScope.drawCapacityMarkers(
    markers: List<CapacityMarker>,
    coords: ChartCoordinates,
    capacityColor: Color
) {
    val textPaint = createTextPaint(capacityColor.toArgb(), 20f)
    val padding = 6.dp.toPx()
    val textHeight = -textPaint.fontMetrics.ascent
    val chartRight = coords.paddingLeft + coords.chartWidth
    val scaledChartHeight = coords.chartHeight * 0.9f
    val chartBottom = coords.paddingTop + scaledChartHeight

    markers.forEach { marker ->
        val x = coords.timeToX(marker.timestamp)
        val y = coords.capacityToY(marker.capacity.toDouble())

        drawCircle(capacityColor, radius = 3.dp.toPx() * 0.65f, center = Offset(x, y))

        // 智能定位标签：优先右侧，超出边界则左侧
        val labelWidth = textPaint.measureText(marker.label)
        var textX = x + padding
        if (textX + labelWidth > chartRight) textX = x - padding - labelWidth
        if (textX < coords.paddingLeft) textX = coords.paddingLeft

        var textY = y - padding
        if (textY - textHeight < coords.paddingTop) textY = y + textHeight + padding
        if (textY > chartBottom) textY = chartBottom

        drawContext.canvas.nativeCanvas.drawText(marker.label, textX, textY, textPaint)
    }
}

/**
 * 绘制温度极值标记（最高/最低点圆点+标签）
 */
private fun DrawScope.drawTempExtremeMarkers(
    points: List<RecordDetailChartPoint>,
    coords: ChartCoordinates,
    tempColor: Color,
) {
    val validPoints = points.filter { it.temp > 0 }
    if (validPoints.size < 2) return
    val maxPoint = validPoints.maxByOrNull { it.temp } ?: return
    val minPoint = validPoints.minByOrNull { it.temp } ?: return
    if (maxPoint.temp == minPoint.temp) return

    val textPaint = createTextPaint(tempColor.toArgb(), 20f)
    val padding = 6.dp.toPx()
    val textHeight = -textPaint.fontMetrics.ascent
    val chartRight = coords.paddingLeft + coords.chartWidth
    val scaledChartHeight = coords.chartHeight * 0.9f
    val chartBottom = coords.paddingTop + scaledChartHeight

    for (point in listOf(maxPoint, minPoint)) {
        val x = coords.timeToX(point.timestamp)
        val y = coords.tempToY(point.temp.toDouble())

        drawCircle(tempColor, radius = 3.dp.toPx() * 0.65f, center = Offset(x, y))

        val label = String.format(Locale.getDefault(), "%.1f ℃", point.temp / 10.0)
        val labelWidth = textPaint.measureText(label)
        var textX = x + padding
        if (textX + labelWidth > chartRight) textX = x - padding - labelWidth
        if (textX < coords.paddingLeft) textX = coords.paddingLeft

        val isMax = point === maxPoint
        val textY = if (isMax) y - padding else y + textHeight + padding

        drawContext.canvas.nativeCanvas.drawText(label, textX, textY, textPaint)
    }
}

private fun DrawScope.drawVoltageExtremeMarkers(
    points: List<RecordDetailChartPoint>,
    coords: ChartCoordinates,
    voltageColor: Color,
) {
    val validPoints = points.filter { it.voltage > 0L }
    if (validPoints.size < 2) return
    val maxPoint = validPoints.maxByOrNull { it.voltage } ?: return
    val minPoint = validPoints.minByOrNull { it.voltage } ?: return
    if (maxPoint.voltage == minPoint.voltage) return

    val textPaint = createTextPaint(voltageColor.toArgb(), 20f)
    val padding = 6.dp.toPx()
    val textHeight = -textPaint.fontMetrics.ascent
    val chartRight = coords.paddingLeft + coords.chartWidth
    val scaledChartHeight = coords.chartHeight * 0.9f
    val chartBottom = coords.paddingTop + scaledChartHeight

    for (point in listOf(maxPoint, minPoint)) {
        val x = coords.timeToX(point.timestamp)
        val y = coords.voltageToY(point.voltage.toDouble())

        drawCircle(voltageColor, radius = 3.dp.toPx() * 0.65f, center = Offset(x, y))

        val label = String.format(Locale.getDefault(), "%.2f V", point.voltage / 1_000_000.0)
        val labelWidth = textPaint.measureText(label)
        var textX = x + padding
        if (textX + labelWidth > chartRight) textX = x - padding - labelWidth
        if (textX < coords.paddingLeft) textX = coords.paddingLeft

        val isMax = point === maxPoint
        var textY = if (isMax) y - padding else y + textHeight + padding
        if (textY - textHeight < coords.paddingTop) {
            textY = y + textHeight + padding
        }
        if (textY > chartBottom) {
            textY = (y - padding).coerceAtLeast(coords.paddingTop + textHeight)
        }

        drawContext.canvas.nativeCanvas.drawText(label, textX, textY, textPaint)
    }
}

private fun selectPowerValueForChart(
    point: RecordDetailChartPoint,
    powerCurveMode: PowerCurveMode,
    isNegativeMode: Boolean,
): Double {
    // 图表坐标轴不接受“向下无限负值”的另一套逻辑，因此负轴模式统一翻成正高度。
    // 真正展示给用户的符号语义由 selectPowerValueForDisplay 负责保留。
    val powerValue = when (powerCurveMode) {
        PowerCurveMode.Raw -> point.rawPowerW
        PowerCurveMode.Fitted -> point.fittedPowerW
        PowerCurveMode.Hidden -> point.rawPowerW
    }
    return if (isNegativeMode) {
        (-powerValue).coerceAtLeast(0.0)
    } else {
        powerValue
    }
}

private fun selectPowerValueForDisplay(
    point: RecordDetailChartPoint,
    powerCurveMode: PowerCurveMode,
): Double {
    // 展示值不做负轴翻转，保证点选信息与用户当前配置下的功率语义一致。
    return when (powerCurveMode) {
        PowerCurveMode.Raw -> point.rawPowerW
        PowerCurveMode.Fitted -> point.fittedPowerW
        PowerCurveMode.Hidden -> point.rawPowerW
    }
}

private fun powerCurveModeLabel(mode: PowerCurveMode): String {
    // Hidden 仍显示“功耗”标签，目的是保留同一个点击入口，而不是让图例项消失。
    return when (mode) {
        PowerCurveMode.Raw -> "功耗"
        PowerCurveMode.Fitted -> "趋势"
        PowerCurveMode.Hidden -> "功耗"
    }
}
