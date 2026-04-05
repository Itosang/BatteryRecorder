package yangfentuozi.batteryrecorder.server.notification

import yangfentuozi.batteryrecorder.server.notification.server.ChildServerBridge

class RemoteNotificationUtil(val bridge: ChildServerBridge): NotificationUtil {

    private val lock = Any()

    override fun updateNotification(info: NotificationInfo) {
        synchronized(lock) {
            bridge.writer?.write(info)
        }
    }

    override fun cancelNotification() {
        synchronized(lock) {
            bridge.writer?.writeCancel()
        }
    }
}
