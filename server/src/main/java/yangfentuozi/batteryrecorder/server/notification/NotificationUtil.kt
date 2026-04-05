package yangfentuozi.batteryrecorder.server.notification

interface NotificationUtil {
    fun updateNotification(info: NotificationInfo)
    fun cancelNotification()
}
