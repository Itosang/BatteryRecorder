package yangfentuozi.batteryrecorder.server.notification.server

import android.net.LocalServerSocket
import android.net.LocalSocket
import android.os.Looper
import android.system.Os
import yangfentuozi.batteryrecorder.server.notification.LocalNotificationUtil
import yangfentuozi.batteryrecorder.server.notification.NotificationUtil
import yangfentuozi.batteryrecorder.server.notification.server.stream.StreamProtocol
import yangfentuozi.batteryrecorder.server.notification.server.stream.StreamReader
import yangfentuozi.batteryrecorder.shared.util.Handlers
import yangfentuozi.batteryrecorder.shared.util.LoggerX
import yangfentuozi.hiddenapi.compat.ServiceManagerCompat
import java.io.IOException
import kotlin.system.exitProcess

private const val TAG = "NotificationServer"

class NotificationServer {
    @Volatile
    var isStopped = false

    @Volatile
    private var cleanedUp = false

    val notificationUtil: NotificationUtil
    var socket: LocalSocket? = null
    var reader: StreamReader? = null
    val serverSocket: LocalServerSocket
    val serverThread = Thread({
        try {
            LoggerX.i(TAG, "@serverRunnable: 等待客户端")
            socket = serverSocket.accept()
            LoggerX.i(TAG, "@serverRunnable: 接受客户端")
            reader = StreamReader(socket!!.inputStream)
            while (!isStopped) {
                try {
                    val info = reader!!.readNext() ?: break
                    notificationUtil.updateNotification(info)
                } catch (_: StreamProtocol.CancelNotificationException) {
                    notificationUtil.cancelNotification()
                }
            }
        } catch (e: IOException) {
            if (!isStopped) LoggerX.e(TAG, "@serverRunnable: 处理客户端请求时出现异常", tr = e)
        } catch (_: StreamProtocol.StopException) {
            isStopped = true
            Handlers.main.post { exitProcess(0) }
        } finally {
            reader?.let { runCatching { it.close() } }
            socket?.let { runCatching { it.close() } }
        }
        reader = null
        socket = null
        notificationUtil.cancelNotification()
        exitProcess(0)
    }, "ServerSocketThread")

    init {
        LoggerX.i(
            TAG,
            "init: NotificationServer 初始化开始, uid=${Os.getuid()}"
        )
        if (Looper.getMainLooper() == null) {
            @Suppress("DEPRECATION")
            Looper.prepareMainLooper()
        }
        Handlers.initMainThread()

        if (Os.getuid() != 2000) {
            LoggerX.i(TAG, "init: uid 不为 2000, 执行降权")
            @Suppress("DEPRECATION")
            Os.setuid(2000)
        }

        LoggerX.i(TAG, "init: 等待 notification, activity 服务")
        ServiceManagerCompat.waitService("notification")
        ServiceManagerCompat.waitService("activity")
        notificationUtil = LocalNotificationUtil()

        LoggerX.i(TAG, "init: 创建 LocalServerSocket 通信服务")
        serverSocket = LocalServerSocket(SOCKET_NAME)

        Runtime.getRuntime().addShutdownHook(Thread { this.stopServiceImmediately() })
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            LoggerX.a(thread.name, "NotificationServer crashed", tr = throwable)
            LoggerX.writer?.close()
        }
        serverThread.start()
        Looper.loop()
    }

    private fun stopServiceImmediately() {
        if (cleanedUp) return
        LoggerX.i(TAG, "停止服务")
        cleanedUp = true
        isStopped = true
        runCatching {
            serverThread.interrupt()
            reader?.let { runCatching { it.close() } }
            socket?.let { runCatching { it.close() } }
            notificationUtil.cancelNotification()
            serverSocket.close()
        }
    }

    companion object {
        const val SOCKET_NAME = "BatteryRecorder_NotificationServer"
    }
}
