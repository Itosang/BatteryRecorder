package yangfentuozi.batteryrecorder.server.notification

import android.app.INotificationManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.RemoteException
import android.os.ServiceManager
import yangfentuozi.batteryrecorder.server.fakecontext.FakeContext
import yangfentuozi.batteryrecorder.shared.Constants
import yangfentuozi.batteryrecorder.shared.config.SettingsConstants
import yangfentuozi.batteryrecorder.shared.util.LoggerX
import yangfentuozi.hiddenapi.compat.NotificationManagerCompat
import java.util.Locale

class LocalNotificationUtil(
    initialCompatibilityModeEnabled: Boolean =
        SettingsConstants.notificationCompatModeEnabled.def
) : NotificationUtil {

    private val tag = "LocalNotificationUtil"

    private val lock = Any()
    private val notificationManager: INotificationManager =
        INotificationManager.Stub.asInterface(ServiceManager.getService("notification"))
            ?: throw IllegalStateException("notification 服务未就绪")
    private val context = FakeContext()

    @Volatile
    private var channelCreated = false
    @Volatile
    private var compatibilityModeEnabled = initialCompatibilityModeEnabled

    init {
        synchronized(lock) {
            if (channelCreated) return@synchronized
            try {
                NotificationManagerCompat.createChannel(
                    notificationManager,
                    SHELL_PACKAGE_NAME,
                    SHELL_UID,
                    NotificationChannel(
                        CHANNEL_ID,
                        CHANNEL_NAME,
                        NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        enableLights(false)
                        enableVibration(false)
                    }
                )
                channelCreated = true
                LoggerX.i(tag, "init: 通知渠道创建成功")
            } catch (e: RemoteException) {
                LoggerX.e(tag, "init: 通知渠道创建失败", tr = e)
            }
        }
    }

    /**
     * 更新当前通知实现是否复用 Builder。
     *
     * @param enabled `true` 表示每次更新通知都新建 Builder；`false` 表示继续复用单个 Builder。
     * @return 无。
     */
    override fun setCompatibilityModeEnabled(enabled: Boolean) {
        synchronized(lock) {
            if (compatibilityModeEnabled == enabled) return
            LoggerX.i(tag, "setCompatibilityModeEnabled: $compatibilityModeEnabled -> $enabled")
            compatibilityModeEnabled = enabled
        }
    }

    override fun updateNotification(info: NotificationInfo) {
        synchronized(lock) {
            try {
                NotificationManagerCompat.enqueueNotification(
                    notificationManager,
                    SHELL_PACKAGE_NAME,
                    NOTIFICATION_TAG,
                    NOTIFICATION_ID,
                    buildNotification(info),
                    0
                )
            } catch (e: RemoteException) {
                LoggerX.e(tag, "updateNotification: 发送通知失败", tr = e)
            }
        }
    }

    override fun cancelNotification() {
        synchronized(lock) {
            NotificationManagerCompat.cancelNotification(
                notificationManager,
                SHELL_PACKAGE_NAME,
                NOTIFICATION_TAG,
                NOTIFICATION_ID,
                0
            )
        }
    }

    private fun buildNotification(info: NotificationInfo): Notification {
        val contentText = String.format(
            Locale.getDefault(),
            NOTIFICATION_CONTENT_FORMAT,
            info.power,
            info.temp / 10.0,
            info.capacity
        )
        val builder = if (compatibilityModeEnabled) createBaseBuilder() else reusableBuilder
        return builder.setContentText(contentText)
            .setTicker(contentText)
            .build()
    }

    private val cachedIcon = buildSmallIcon()

    private fun buildSmallIcon(): Icon {
        val defaultIcon = Icon.createWithResource("android", android.R.drawable.stat_notify_sync)
        return try {
            val appInfo = context.packageManager.getApplicationInfo(Constants.APP_PACKAGE_NAME, 0)
            val iconResId = appInfo.icon
            if (iconResId == 0) {
                LoggerX.w(tag, "buildSmallIcon: 应用图标资源为空，回退系统图标")
                defaultIcon
            } else {
                Icon.createWithResource(Constants.APP_PACKAGE_NAME, iconResId)
            }
        } catch (e: PackageManager.NameNotFoundException) {
            LoggerX.w(tag, "buildSmallIcon: 未找到应用包，回退系统图标", tr = e)
            defaultIcon
        } catch (e: Throwable) {
            LoggerX.e(tag, "buildSmallIcon: 读取应用图标失败，回退系统图标", tr = e)
            defaultIcon
        }
    }

    private fun createBaseBuilder(): Notification.Builder =
        Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(cachedIcon)
            .setContentTitle(NOTIFICATION_TITLE)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setAutoCancel(false)

    private val reusableBuilder by lazy(LazyThreadSafetyMode.NONE) { createBaseBuilder() }

    companion object {
        private const val SHELL_PACKAGE_NAME = "com.android.shell"
        private const val SHELL_UID = 2000
        private const val CHANNEL_ID = "batteryrecorder_notification"
        private const val CHANNEL_NAME = "BatteryRecorder"
        private const val NOTIFICATION_TAG = "batteryrecorder_notification"
        private const val NOTIFICATION_ID = 10086
        private const val NOTIFICATION_TITLE = "BatteryRecorder"
        private const val NOTIFICATION_CONTENT_FORMAT = "%.2f W | %.1f℃ | %d%%"
    }

}
