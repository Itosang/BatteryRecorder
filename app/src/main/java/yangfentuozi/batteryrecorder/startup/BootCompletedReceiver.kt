package yangfentuozi.batteryrecorder.startup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.content.edit
import yangfentuozi.batteryrecorder.shared.config.SettingsConstants
import yangfentuozi.batteryrecorder.shared.config.SharedSettings
import yangfentuozi.batteryrecorder.shared.util.LoggerX

private const val TAG = "BootCompletedReceiver"

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        Thread(
            {
                try {
                    LoggerX.d(TAG, "[BOOT] 收到开机广播")
                    val prefs = appContext.getSharedPreferences(
                        SettingsConstants.PREFS_NAME,
                        Context.MODE_PRIVATE
                    )
                    val autoStartEnabled =
                        SharedSettings.readAppSettings(appContext).rootBootAutoStartEnabled
                    if (!autoStartEnabled) {
                        LoggerX.i(TAG, "[BOOT] 开机 ROOT 自启动未开启")
                        return@Thread
                    }

                    val currentBootCount = runCatching {
                        Settings.Global.getInt(
                            appContext.contentResolver,
                            Settings.Global.BOOT_COUNT
                        )
                    }.getOrElse {
                        LoggerX.w(TAG, "[BOOT] 读取 boot_count 失败，跳过本次去重", tr = it)
                        Int.MIN_VALUE
                    }
                    if (currentBootCount != Int.MIN_VALUE) {
                        val lastBootCount =
                            SettingsConstants.rootBootAutoStartLastBootCount.readFromSP(prefs)
                        if (lastBootCount == currentBootCount) {
                            LoggerX.i(TAG, "[BOOT] 命中 boot_count 去重，跳过自启动，boot_count=$currentBootCount")
                            return@Thread
                        }
                        prefs.edit {
                            SettingsConstants.rootBootAutoStartLastBootCount.writeToSP(
                                this,
                                currentBootCount
                            )
                        }
                        LoggerX.d(TAG, "[BOOT] 已记录 boot_count 去重标记，boot_count=$currentBootCount")
                    }

                    LoggerX.i(TAG, "[BOOT] 满足自启动条件，准备拉起服务")
                    val started = RootServerStarter.start(
                        context = appContext,
                        source = RootServerStarter.Source.BOOT
                    )
                    BootAutoStartNotification.notifyBootAutoStartResult(appContext, started)
                } catch (e: Throwable) {
                    LoggerX.e(TAG, "[BOOT] 开机自启动执行失败", tr = e)
                } finally {
                    pendingResult.finish()
                }
            },
            "BootAutoStart"
        ).start()
    }
}
