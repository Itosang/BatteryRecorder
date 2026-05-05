package yangfentuozi.batteryrecorder.server.appservice

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.ParcelFileDescriptor
import yangfentuozi.batteryrecorder.server.Server
import yangfentuozi.batteryrecorder.server.notification.NOTIFICATION_ID
import yangfentuozi.batteryrecorder.server.notification.NotificationInfo
import yangfentuozi.batteryrecorder.server.notification.buildNotification
import yangfentuozi.batteryrecorder.server.notification.buildNotificationChannel
import yangfentuozi.batteryrecorder.server.notification.createBaseBuilder
import yangfentuozi.batteryrecorder.server.sampler.SysfsSampler
import yangfentuozi.batteryrecorder.shared.Constants
import yangfentuozi.batteryrecorder.shared.config.SettingsConstants
import java.io.File

class RecordService: Service() {
    private lateinit var server: Server
    private lateinit var notificationManager: NotificationManager

    @Volatile
    private var compatibilityModeEnabled: Boolean =
        SettingsConstants.notificationCompatModeEnabled.def

    @Volatile
    private var iconCompatibilityModeEnabled: Boolean =
        SettingsConstants.notificationIconCompatModeEnabled.def

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NotificationManager::class.java)
        server = object : Server(
            powerDir = File(dataDir, Constants.APP_POWER_DATA_PATH),
            writerStatusData = null,
            fixFileOwner = {},
            sampler = SysfsSampler
        ) {
            override fun restartServer(nativeLibraryDir: String?) {
                TODO("Not yet implemented")
            }

            override fun stopServer() {
                server.onStop()
                stopSelf()
            }

            override fun sync(): ParcelFileDescriptor? {
                writer.flushBuffer()
                return null
            }

            override fun exportLogs(): ParcelFileDescriptor? {
                return null
            }
        }

        // 初始化 + 启动你的 Server
        server.onStart()

        startAsForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 可根据 intent 控制 server 行为
        when (intent?.action) {
            ACTION_START -> server.onStart()
            ACTION_STOP -> {
                server.onStop()
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        server.onStop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder = server.asBinder()

    private fun startAsForeground() {
        notificationManager.createNotificationChannel(buildNotificationChannel())

        val notification = buildNotification(
            this,
            NotificationInfo(0.0,0,0),
            reusableBuilder,
            compatibilityModeEnabled,
            iconCompatibilityModeEnabled
        )

        startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    private val reusableBuilder by lazy(LazyThreadSafetyMode.NONE) { createBaseBuilder(this) }

    companion object {
        const val ACTION_START = "action.START"
        const val ACTION_STOP = "action.STOP"
    }
}