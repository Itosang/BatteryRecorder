package yangfentuozi.batteryrecorder.startup

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import yangfentuozi.batteryrecorder.R

object BootAutoStartNotification {
    private const val CHANNEL_ID = "boot_auto_start_reminder"
    private const val NOTIFICATION_ID_PERMISSION_HINT = 10001
    private const val NOTIFICATION_ID_BOOT_RESULT = 10002

    fun permissionHintText(context: Context): String =
        context.getString(R.string.boot_autostart_permission_hint)

    @Suppress("DEPRECATION")
    fun notifyEnabled(context: Context) {
        val appContext = context.applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val manager = appContext.getSystemService(NotificationManager::class.java) ?: return
        ensureChannel(appContext, manager)
        val notification = Notification.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(appContext.getString(R.string.boot_autostart_enabled_title))
            .setContentText(appContext.getString(R.string.boot_autostart_permission_hint))
            .setCategory(Notification.CATEGORY_REMINDER)
            .setPriority(Notification.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        runCatching {
            manager.notify(NOTIFICATION_ID_PERMISSION_HINT, notification)
        }
    }

    @Suppress("DEPRECATION")
    fun notifyBootAutoStartResult(context: Context, started: Boolean) {
        val appContext = context.applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val manager = appContext.getSystemService(NotificationManager::class.java) ?: return
        ensureChannel(appContext, manager)
        val (title, text) = if (started) {
            appContext.getString(R.string.boot_autostart_success_title) to
                appContext.getString(R.string.boot_autostart_success_text)
        } else {
            appContext.getString(R.string.boot_autostart_failed_title) to
                appContext.getString(R.string.boot_autostart_failed_text)
        }
        val notification = Notification.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(Notification.CATEGORY_STATUS)
            .setPriority(Notification.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        runCatching {
            manager.notify(NOTIFICATION_ID_BOOT_RESULT, notification)
        }
    }

    private fun ensureChannel(context: Context, manager: NotificationManager) {
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.boot_autostart_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.boot_autostart_channel_description)
            setShowBadge(true)
        }
        manager.createNotificationChannel(channel)
    }
}
