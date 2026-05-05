package yangfentuozi.batteryrecorder.server.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.Icon
import yangfentuozi.batteryrecorder.shared.Constants
import yangfentuozi.batteryrecorder.shared.util.LoggerX
import java.util.Locale

private const val TAG = "NotificationProtocol"

const val CHANNEL_NAME = "BatteryRecorder"
const val CHANNEL_ID = "batteryrecorder_notification"
const val NOTIFICATION_TAG = "batteryrecorder_notification"
const val NOTIFICATION_ID = 10086
const val NOTIFICATION_TITLE = "BatteryRecorder"
const val NOTIFICATION_CONTENT_FORMAT = "%.2f W | %.1f℃ | %d%%"

private fun buildSmallIcon(context: Context): Icon {
    val defaultIcon = Icon.createWithResource("android", android.R.drawable.stat_notify_sync)
    return try {
        val appInfo = context.packageManager.getApplicationInfo(Constants.APP_PACKAGE_NAME, 0)
        val iconResId = appInfo.icon
        if (iconResId == 0) {
            LoggerX.w(TAG, "buildSmallIcon: 应用图标资源为空，回退系统图标")
            defaultIcon
        } else {
            Icon.createWithResource(Constants.APP_PACKAGE_NAME, iconResId)
        }
    } catch (e: PackageManager.NameNotFoundException) {
        LoggerX.w(TAG, "buildSmallIcon: 未找到应用包，回退系统图标", tr = e)
        defaultIcon
    } catch (e: Throwable) {
        LoggerX.e(TAG, "buildSmallIcon: 读取应用图标失败，回退系统图标", tr = e)
        defaultIcon
    }
}

fun createBaseBuilder(context: Context): Notification.Builder {
    if (!::cachedPendingIntent.isInitialized) {
        cachedPendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent().apply {
                component = ComponentName(
                    Constants.APP_PACKAGE_NAME,
                    "yangfentuozi.batteryrecorder.ui.MainActivity"
                )
                flags =
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("open_current_record_detail", true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
    if (!::cachedIcon.isInitialized) {
        cachedIcon = buildSmallIcon(context)
    }
    return Notification.Builder(context, CHANNEL_ID)
        .setSmallIcon(cachedIcon)
        .setContentTitle(NOTIFICATION_TITLE)
        .setShowWhen(false)
        .setCategory(Notification.CATEGORY_SERVICE)
        .setVisibility(Notification.VISIBILITY_PUBLIC)
        .setOnlyAlertOnce(true)
        .setOngoing(true)
        .setAutoCancel(false)
        .setContentIntent(cachedPendingIntent)
}

fun buildNotification(
    context: Context,
    info: NotificationInfo,
    reusableBuilder: Notification.Builder,
    compatibilityModeEnabled: Boolean,
    iconCompatibilityModeEnabled: Boolean
): Notification {
    val contentText = String.format(
        Locale.getDefault(),
        NOTIFICATION_CONTENT_FORMAT,
        info.power,
        info.temp / 10.0,
        info.capacity
    )
    val builder = if (compatibilityModeEnabled) createBaseBuilder(context) else reusableBuilder
    return builder.setContentText(contentText)
        .setTicker(contentText)
        .build().apply {
            @Suppress("DEPRECATION")
            if (iconCompatibilityModeEnabled) {
                contentView = null
                bigContentView = null
                headsUpContentView = null
                color = Color.TRANSPARENT
            }
        }
}

fun buildNotificationChannel(): NotificationChannel =
    NotificationChannel(
        CHANNEL_ID,
        CHANNEL_NAME,
        NotificationManager.IMPORTANCE_LOW
    ).apply {
        enableLights(false)
        enableVibration(false)
    }

private lateinit var cachedPendingIntent: PendingIntent
private lateinit var cachedIcon: Icon