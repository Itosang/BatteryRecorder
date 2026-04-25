package yangfentuozi.batteryrecorder.usecase.prediction

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import yangfentuozi.batteryrecorder.data.history.AppStatsComputer
import yangfentuozi.batteryrecorder.data.history.BatteryPredictor
import yangfentuozi.batteryrecorder.data.history.HistoryRepository
import yangfentuozi.batteryrecorder.data.history.SyncUtil
import yangfentuozi.batteryrecorder.ipc.Service
import yangfentuozi.batteryrecorder.shared.config.dataclass.StatisticsSettings
import yangfentuozi.batteryrecorder.shared.data.BatteryStatus
import yangfentuozi.batteryrecorder.ui.model.PredictionDetailUiEntry
import yangfentuozi.batteryrecorder.usecase.common.ResolveInstalledAppLabelUseCase
import kotlin.math.roundToInt

/**
 * 加载预测详情页应用维度数据。
 */
internal object LoadPredictionDetailUseCase {

    /**
     * 读取并组装预测详情页需要的应用明细。
     *
     * @param context 应用上下文。
     * @param request 当前统计设置。
     * @param recordIntervalMs 当前采样间隔。
     * @return 返回预测详情页列表项；仅保留当前仍已安装的应用。
     */
    suspend fun execute(
        context: Context,
        request: StatisticsSettings,
        recordIntervalMs: Long
    ): List<PredictionDetailUiEntry> = withContext(Dispatchers.IO) {
        SyncUtil.sync(context)

        val latestDischargeFile = HistoryRepository
            .listRecordFiles(context, BatteryStatus.Discharging)
            .firstOrNull()
        val latestDischargeRecord = latestDischargeFile?.let { file ->
            HistoryRepository.loadStats(context, file, needCaching = false)
        }
        val currentSoc = readCurrentBatteryCapacityPercent(context)
            ?: latestDischargeRecord?.stats?.endCapacity
        val currentDischargeFileName = Service.service?.currRecordsFile
            ?.takeIf { it.type == BatteryStatus.Discharging }
            ?.name
        val packageManager = context.packageManager
        val appStats = AppStatsComputer.compute(
            context = context,
            request = request,
            recordIntervalMs = recordIntervalMs,
            currentDischargeFileName = currentDischargeFileName
        )
        appStats.entries.mapNotNull { entry ->
            val resolved = ResolveInstalledAppLabelUseCase.execute(
                packageManager = packageManager,
                packageName = entry.packageName
            )
            if (!resolved.isInstalled) {
                return@mapNotNull null
            }
            PredictionDetailUiEntry(
                packageName = resolved.packageName,
                appLabel = resolved.label,
                averagePowerRaw = entry.rawAvgPowerRaw,
                currentHours = currentSoc?.let { BatteryPredictor.predictAppCurrentHours(entry, it) }
            )
        }
    }

    /**
     * 读取系统当前电量百分比。
     *
     * @param context 应用上下文。
     * @return 当前电量百分比；系统广播缺少 level 或 scale 时返回空值。
     */
    private fun readCurrentBatteryCapacityPercent(context: Context): Int? {
        val intent: Intent = context.applicationContext.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        ) ?: return null
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return null
        return (level * 100f / scale).roundToInt()
    }
}
