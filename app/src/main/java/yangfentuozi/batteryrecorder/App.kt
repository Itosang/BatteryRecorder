package yangfentuozi.batteryrecorder

import android.app.Application
import yangfentuozi.batteryrecorder.shared.Constants
import yangfentuozi.batteryrecorder.shared.config.SharedSettings
import yangfentuozi.batteryrecorder.shared.util.LoggerX
import java.io.File

private const val TAG = "App"

class App: Application() {
    override fun onCreate() {
        super.onCreate()
        val settings = SharedSettings.readServerSettings(this)
        LoggerX.d(TAG, 
            "[应用] SharedPreferences 配置读取完成: intervalMs=${settings.recordIntervalMs} " +
                "screenOffRecord=${settings.screenOffRecordEnabled} polling=${settings.alwaysPollingScreenStatusEnabled}"
        )
        LoggerX.maxHistoryDays = settings.maxHistoryDays
        LoggerX.logLevel = settings.logLevel
        LoggerX.logDir = File(cacheDir, Constants.APP_LOG_DIR_PATH)
        LoggerX.i(TAG, 
            "[应用] 日志初始化完成: level=${settings.logLevel} dir=${File(cacheDir, Constants.APP_LOG_DIR_PATH).absolutePath} " +
                "maxDays=${settings.maxHistoryDays}"
        )
    }
}
