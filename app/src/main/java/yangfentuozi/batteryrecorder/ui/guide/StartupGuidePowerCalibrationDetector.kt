package yangfentuozi.batteryrecorder.ui.guide

import android.content.SharedPreferences
import androidx.core.content.edit
import yangfentuozi.batteryrecorder.shared.data.BatteryStatus
import kotlin.math.abs

internal const val STARTUP_PROMPT_PREFS = "startup_prompt"
internal const val KEY_STARTUP_GUIDE_COMPLETED_V2 = "startup_guide_completed_v2"

private const val KEY_STARTUP_POWER_CALIBRATION_DETECTED_V1 = "startup_power_calibration_detected_v1"
private const val DISCHARGE_POWER_THRESHOLD = 40_000_000_000L
private const val APPLY_STABLE_COUNT = 4
private const val COMPLETE_STABLE_COUNT = 5

internal enum class StartupPowerCalibrationPhase {
    WaitingForService,
    RemoveCharger,
    WaitingForDischarge,
    Detecting,
    Completed
}

internal data class StartupPowerCalibrationUiState(
    val lastStatus: BatteryStatus = BatteryStatus.Unknown,
    val candidate: Int? = null,
    val stableCount: Int = 0,
    val isCompleted: Boolean = false
)

internal fun StartupPowerCalibrationUiState.withObservedStatus(
    status: BatteryStatus
): StartupPowerCalibrationUiState {
    if (lastStatus == status) return this
    return copy(lastStatus = status)
}

/**
 * 解析当前自动探测 UI 应展示的阶段。
 *
 * @param serviceConnected 当前 Binder 是否已经连接。
 * @return 返回引导第三页的自动探测阶段。
 */
internal fun StartupPowerCalibrationUiState.resolvePhase(
    serviceConnected: Boolean
): StartupPowerCalibrationPhase {
    return when {
        isCompleted -> StartupPowerCalibrationPhase.Completed
        !serviceConnected -> StartupPowerCalibrationPhase.WaitingForService
        lastStatus == BatteryStatus.Charging || lastStatus == BatteryStatus.Full ->
            StartupPowerCalibrationPhase.RemoveCharger

        lastStatus == BatteryStatus.Discharging -> StartupPowerCalibrationPhase.Detecting
        else -> StartupPowerCalibrationPhase.WaitingForDischarge
    }
}

/**
 * 首次引导第三页使用的功率倍率自动探测器。
 *
 * 它只负责根据放电态 `power` 样本推断 `calibrationValue`，并在稳定后落库完成标记。
 *
 * @param prefs 首次引导共用的 SharedPreferences。
 */
internal class StartupGuidePowerCalibrationDetector(
    private val prefs: SharedPreferences
) {
    private var state = StartupPowerCalibrationUiState(
        isCompleted = prefs.getBoolean(KEY_STARTUP_POWER_CALIBRATION_DETECTED_V1, false)
    )

    /**
     * 读取当前探测状态快照。
     *
     * @return 返回当前内存态对应的 UI 状态。
     */
    fun snapshot(): StartupPowerCalibrationUiState = state

    /**
     * 消费一条实时功率样本并推进探测状态。
     *
     * 只有放电态样本才参与推断；其余状态会清空当前连续计数，但不会回滚完成标记。
     *
     * @param status 当前电池状态。
     * @param power 当前回调里的原始功率值。
     * @param currentCalibrationValue 当前已保存的校准倍率。
     * @return 返回本次是否需要自动写入倍率；最新状态通过 [snapshot] 读取。
     */
    fun onSample(
        status: BatteryStatus,
        power: Long,
        currentCalibrationValue: Int
    ): Int? {
        if (state.isCompleted) {
            state = state.copy(lastStatus = status)
            return null
        }

        if (status != BatteryStatus.Discharging || power == 0L) {
            state = state.copy(
                lastStatus = status,
                candidate = null,
                stableCount = 0
            )
            return null
        }

        val candidate = inferCalibrationValue(power)
        val nextStableCount = if (state.candidate == candidate) {
            state.stableCount + 1
        } else {
            1
        }
        val completedNow = nextStableCount == COMPLETE_STABLE_COUNT
        if (completedNow) {
            prefs.edit {
                putBoolean(KEY_STARTUP_POWER_CALIBRATION_DETECTED_V1, true)
            }
        }
        state = StartupPowerCalibrationUiState(
            lastStatus = status,
            candidate = candidate,
            stableCount = nextStableCount,
            isCompleted = completedNow
        )
        return candidate.takeIf {
            nextStableCount == APPLY_STABLE_COUNT && currentCalibrationValue != it
        }
    }

    /**
     * 根据原始功率值推断校准倍率。
     *
     * 约定：
     * 1. 放电态原始功率为正时，展示倍率应为负。
     * 2. 小于放电阈值时按 `1000` 级联放大倍率，直到达到目标量级。
     *
     * @param power 当前放电样本的原始功率值。
     * @return 返回推断出的校准倍率。
     */
    private fun inferCalibrationValue(power: Long): Int {
        var candidate = if (power > 0L) -1 else 1
        var absPower = abs(power)
        while (absPower in 1 until DISCHARGE_POWER_THRESHOLD) {
            absPower *= 1000L
            candidate *= 1000
        }
        return candidate
    }
}
