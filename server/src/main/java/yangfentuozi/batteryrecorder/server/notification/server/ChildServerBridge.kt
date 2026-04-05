package yangfentuozi.batteryrecorder.server.notification.server

import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.system.Os
import yangfentuozi.batteryrecorder.server.Global
import yangfentuozi.batteryrecorder.server.Main
import yangfentuozi.batteryrecorder.server.notification.server.stream.StreamWriter
import yangfentuozi.batteryrecorder.shared.util.Handlers
import yangfentuozi.batteryrecorder.shared.util.LoggerX
import java.io.IOException

class ChildServerBridge {
    private val tag = "Bridge"

    private var retryCount = 0
    private val retryMaxTimes = 5

    private var isStopped = false

    lateinit var process: Process
        private set
    private val handler = Handlers.getHandler("WaitNotificationServerExitThread")
    private val waitRunnable = Runnable {
        try {
            while (process.isAlive) {
                Thread.sleep(1000)
                if (!socket.isConnected) {
                    for (index in 1..10) {
                        if (!process.isAlive) {
                            LoggerX.w(
                                tag,
                                "NotificationServer 在建连前已退出, exitValue=${runCatching { process.exitValue() }.getOrNull()}",
                            )
                            break
                        }

                        socket = LocalSocket()
                        try {
                            socket.connect(LocalSocketAddress(NotificationServer.SOCKET_NAME))
                            writer = StreamWriter(socket.outputStream)
                            LoggerX.i(
                                tag,
                                "connectSocket: 已连接 NotificationServer, attempt=${index + 1}"
                            )
                            break
                        } catch (_: IOException) {
                            runCatching { socket.close() }
                            if (index < 9) {
                                Thread.sleep(500)
                            }
                        }
                    }

                    if (!socket.isConnected && process.isAlive)
                        process.destroyForcibly()
                }
            }
            retryCount++
            startNotificationServer()
        } catch (_: InterruptedException) {
        }
    }

    private var socket: LocalSocket = LocalSocket()
    var writer: StreamWriter? = null
        private set

    init {
        startNotificationServer()
    }

    fun startNotificationServer() {
        if (isStopped) return
        if (retryCount > retryMaxTimes) throw RuntimeException("NotificationServer 多次启动失败")
        process = ProcessBuilder(
            "app_process",
            "-Djava.class.path=${Global.appSourceDir}",
            "/system/bin",
            Main::class.java.name,
            "--notification-server",
            Os.getpid().toString()
        ).start()

        handler.removeCallbacks(waitRunnable)
        handler.post(waitRunnable)
    }

    fun stop() {
        isStopped = true
        handler.removeCallbacks(waitRunnable)
        Handlers.interrupt("WaitNotificationServerExitThread")
        writer?.writeClose()
        writer?.close()
        writer = null
        socket.close()
        Thread.sleep(100)
        process.destroyForcibly()
    }
}