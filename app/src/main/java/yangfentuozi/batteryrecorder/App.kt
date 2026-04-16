package yangfentuozi.batteryrecorder

import android.app.Application
import androidx.annotation.StringRes
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
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            LoggerX.a(thread.name, "App crashed", tr = throwable)
            LoggerX.writer?.close()
        }
    }
}

fun appString(@StringRes resId: Int, vararg formatArgs: Any): String =
    App.instance.getString(resId, *formatArgs)
