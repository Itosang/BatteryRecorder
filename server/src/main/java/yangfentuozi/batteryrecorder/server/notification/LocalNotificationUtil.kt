package yangfentuozi.batteryrecorder.server.notification

import android.app.INotificationManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.RemoteException
import android.os.ServiceManager
import yangfentuozi.batteryrecorder.server.fakecontext.FakeContext
import yangfentuozi.batteryrecorder.shared.config.SettingsConstants
import yangfentuozi.batteryrecorder.shared.config.dataclass.ServerSettings
import yangfentuozi.batteryrecorder.shared.util.LoggerX
import yangfentuozi.hiddenapi.compat.NotificationManagerCompat

private const val TAG = "LocalNotificationUtil"

class LocalNotificationUtil : NotificationUtil {

    private val lock = Any()
    private val notificationManager: INotificationManager =
        INotificationManager.Stub.asInterface(ServiceManager.getService("notification"))
            ?: throw IllegalStateException("notification 服务未就绪")
    private val context = FakeContext()

    @Volatile
    private var compatibilityModeEnabled: Boolean =
        SettingsConstants.notificationCompatModeEnabled.def

    @Volatile
    private var iconCompatibilityModeEnabled: Boolean =
        SettingsConstants.notificationIconCompatModeEnabled.def

    init {
        synchronized(lock) {
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
                LoggerX.i(TAG, "init: 通知渠道创建成功")
            } catch (e: RemoteException) {
                LoggerX.e(TAG, "init: 通知渠道创建失败", tr = e)
            }
        }
    }

    override fun syncSettings(settings: ServerSettings) {
        synchronized(lock) {
            if (iconCompatibilityModeEnabled == settings.notificationIconCompatModeEnabled) {
                LoggerX.i(
                    TAG,
                    "onSettingsUpdate: iconCompatibilityModeEnabled $iconCompatibilityModeEnabled -> ${settings.notificationIconCompatModeEnabled}"
                )
                iconCompatibilityModeEnabled = settings.notificationIconCompatModeEnabled
            }

            if (compatibilityModeEnabled != settings.notificationCompatModeEnabled) {
                LoggerX.i(
                    TAG,
                    "onSettingsUpdate: compatibilityModeEnabled $compatibilityModeEnabled -> ${settings.notificationCompatModeEnabled}"
                )
                compatibilityModeEnabled = settings.notificationCompatModeEnabled
            }
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
                    buildNotification(
                        context,
                        info,
                        reusableBuilder,
                        compatibilityModeEnabled,
                        iconCompatibilityModeEnabled
                    ),
                    0
                )
            } catch (e: RemoteException) {
                LoggerX.e(TAG, "updateNotification: 发送通知失败", tr = e)
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

    private val reusableBuilder by lazy(LazyThreadSafetyMode.NONE) { createBaseBuilder(context) }

    companion object {
        private const val SHELL_PACKAGE_NAME = "com.android.shell"
        private const val SHELL_UID = 2000
    }

}
