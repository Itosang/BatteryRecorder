package yangfentuozi.batteryrecorder

import android.app.Application
import androidx.annotation.StringRes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import yangfentuozi.batteryrecorder.data.bh.BhSyncManager
import yangfentuozi.batteryrecorder.shared.Constants
import yangfentuozi.batteryrecorder.shared.config.SharedSettings
import yangfentuozi.batteryrecorder.shared.util.LoggerX
import java.io.File

private const val TAG = "App"

class App: Application() {
    companion object {
        lateinit var instance: App
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        val settings = SharedSettings.readServerSettings(this)
        LoggerX.d(TAG, 
            "[应用] SharedPreferences 配置读取完成: intervalMs=${settings.recordIntervalMs} " +
                "screenOffRecord=${settings.screenOffRecordEnabled} preciseScreenOffRecord=${settings.preciseScreenOffRecordEnabled} polling=${settings.alwaysPollingScreenStatusEnabled}"
        )
        LoggerX.maxHistoryDays = settings.maxHistoryDays
        LoggerX.logLevel = settings.logLevel
        LoggerX.logDir = File(cacheDir, Constants.APP_LOG_DIR_PATH)
        LoggerX.i(TAG, 
            "[应用] 日志初始化完成: level=${settings.logLevel} dir=${File(cacheDir, Constants.APP_LOG_DIR_PATH).absolutePath} " +
                "maxDays=${settings.maxHistoryDays}"
        )
        // 静默分析：启动后后台增量同步 .bh 事件库（不阻塞 UI；仅在功能开关开启时执行）
        if (SharedSettings.readAppSettings(this).vendorSystemLogEnabled) {
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try {
                    val r = BhSyncManager.sync(this@App)
                    LoggerX.i(
                        TAG,
                        "[电池事件库] 静默同步完成: 扫描 ${r.scanned} · 新增/更新 ${r.imported} · " +
                                "跳过 ${r.skipped} · 失败 ${r.failed} · 共 ${r.totalEvents} 事件"
                    )
                } catch (t: Throwable) {
                    LoggerX.w(TAG, "[电池事件库] 静默同步失败: ${t.message}")
                }
            }
        }
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            LoggerX.a(thread.name, "App crashed", tr = throwable)
            LoggerX.writer?.close()
        }
    }
}

fun appString(@StringRes resId: Int, vararg formatArgs: Any): String =
    App.instance.getString(resId, *formatArgs)
