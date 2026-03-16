package yangfentuozi.batteryrecorder.server.recorder

import android.app.ActivityManager.RunningTaskInfo
import android.app.IActivityTaskManager
import android.app.ITaskStackListener
import android.app.TaskInfo
import android.app.TaskStackListener
import android.hardware.display.IDisplayManager
import android.hardware.display.IDisplayManagerCallback
import android.os.Handler
import android.os.IPowerManager
import android.os.RemoteCallbackList
import android.os.RemoteException
import android.os.ServiceManager
import androidx.annotation.Keep
import yangfentuozi.batteryrecorder.server.recorder.sampler.Sampler
import yangfentuozi.batteryrecorder.server.writer.PowerRecordWriter
import yangfentuozi.batteryrecorder.shared.Constants
import yangfentuozi.batteryrecorder.shared.config.ConfigConstants
import yangfentuozi.batteryrecorder.shared.data.LineRecord
import yangfentuozi.batteryrecorder.shared.util.Handlers
import yangfentuozi.batteryrecorder.shared.util.LoggerX
import java.io.IOException

class Monitor(
    private val writer: PowerRecordWriter,
    private val sendBinder: (() -> Unit),
    private val sampler: Sampler
) {

    private val iActivityTaskManager =
        IActivityTaskManager.Stub.asInterface(ServiceManager.getService("activity_task"))
    private val iDisplayManager =
        IDisplayManager.Stub.asInterface(ServiceManager.getService("display"))
    private val iPowerManager = IPowerManager.Stub.asInterface(ServiceManager.getService("power"))
    private val taskStackListener: ITaskStackListener = object : TaskStackListener() {
        @Keep
        override fun onTaskMovedToFront(taskInfo: RunningTaskInfo) {
            onFocusedAppChanged(taskInfo)
        }
    }

    private var displayCallback: IDisplayManagerCallback? = null

    @Volatile
    private var currForegroundApp: String? = null

    @Volatile
    private var isInteractive = true

    private val callbacks: RemoteCallbackList<IRecordListener> = RemoteCallbackList()

    @Volatile
    var recordIntervalMs: Long = ConfigConstants.DEF_RECORD_INTERVAL_MS

    @Volatile
    var screenOffRecord: Boolean = ConfigConstants.DEF_SCREEN_OFF_RECORD_ENABLED

    private var mAlwaysPollingScreenStatusEnabled: Boolean = ConfigConstants.DEF_ALWAYS_POLLING_SCREEN_STATUS_ENABLED
    var alwaysPollingScreenStatusEnabled: Boolean
        get() = mAlwaysPollingScreenStatusEnabled
        set(value) {
            if (value != mAlwaysPollingScreenStatusEnabled) {
                mAlwaysPollingScreenStatusEnabled = value
                if (value) {
                    unregisterDisplayEventCallback()
                } else {
                    registerDisplayEventCallback()
                }
            }
        }

    @Volatile
    private var paused = false

    @Volatile
    private var stopped = false
    private val lock = Object()
    private val callbackHandler: Handler
        get() = Handlers.getHandler("CallbackThread")
    private var thread = Thread({
        synchronized(lock) {
            while (!stopped) {
                try {
                    val timestamp = System.currentTimeMillis()
                    val sample = sampler.sample()
                    val power = sample.voltage * sample.current
                    val status = sample.status
                    val temp = sample.temp
                    if (alwaysPollingScreenStatusEnabled) isInteractive = iPowerManager.isInteractive
                    writer.write(
                        LineRecord(
                            timestamp,
                            power,
                            currForegroundApp,
                            sample.capacity,
                            if (isInteractive) 1 else 0,
                            sample.status,
                            sample.temp,
                            sample.voltage,
                            sample.current
                        )
                    )

                    callbackHandler.post {
                        // 回调 app
                        val n: Int = callbacks.beginBroadcast()
                        for (i in 0..<n) {
                            try {
                                callbacks.getBroadcastItem(i)
                                    .onRecord(timestamp, power, status, temp)
                            } catch (e: RemoteException) {
                                LoggerX.e<Monitor>("MonitorThread -> callbackHandler@post: 回调失败", tr = e)
                            }
                        }
                        callbacks.finishBroadcast()
                    }
                } catch (e: IOException) {
                    LoggerX.e<Monitor>("MonitorThread: 读取功耗数据失败", tr = e)
                }

                if (isInteractive || screenOffRecord) {
                    paused = false
                    lock.wait(recordIntervalMs)
                } else {
                    paused = true
                    lock.wait()
                }
            }
        }
        Thread.currentThread().interrupt()
    }, "MonitorThread")

    fun start() {
        try {
            iActivityTaskManager.registerTaskStackListener(taskStackListener)
            if (alwaysPollingScreenStatusEnabled) registerDisplayEventCallback()
        } catch (e: RemoteException) {
            throw RuntimeException("start: 注册任务栈监听失败", e)
        }

        try {
            onFocusedAppChanged(iActivityTaskManager.getFocusedRootTaskInfo())
        } catch (e: RemoteException) {
            throw RuntimeException("start: 获取当前焦点任务信息失败", e)
        }
        isInteractive = iPowerManager.isInteractive

        thread.start()
        writer.onChangedCurrRecordsFile = {
            callbackHandler.post {
                // 回调 app
                val n: Int = callbacks.beginBroadcast()
                for (i in 0..<n) {
                    try {
                        callbacks.getBroadcastItem(i).onChangedCurrRecordsFile()
                    } catch (e: RemoteException) {
                        LoggerX.e<Monitor>("writer.onChangedCurrRecordsFile -> callbackHandler@post: 回调失败", tr = e)
                    }
                }
                callbacks.finishBroadcast()
            }
        }
    }

    private fun registerDisplayEventCallback() {
        displayCallback = object : IDisplayManagerCallback.Stub() {
            @Keep
            override fun onDisplayEvent(displayId: Int, event: Int) {
                isInteractive = iPowerManager.isInteractive
                if (isInteractive && paused) {
                    notifyLock()
                }
            }
        }
        iDisplayManager.registerCallback(displayCallback)
    }

    private fun unregisterDisplayEventCallback() {
        displayCallback = null
    }

    fun stop() {
        writer.onChangedCurrRecordsFile = null
        stopped = true
        notifyLock()
        try {
            iActivityTaskManager.unregisterTaskStackListener(taskStackListener)
        } catch (e: RemoteException) {
            LoggerX.e<Monitor>( "stop: 注销任务栈监听失败", tr = e)
        }
    }

    fun notifyLock() {
        synchronized(lock) {
            lock.notifyAll()
        }
    }

    fun registerRecordListener(callback: IRecordListener) {
        callbacks.register(callback)
    }

    fun unregisterRecordListener(callback: IRecordListener) {
        callbacks.unregister(callback)
    }

    private fun onFocusedAppChanged(taskInfo: TaskInfo) {
        val componentName = taskInfo.topActivity ?: return
        val packageName = componentName.packageName
        if (packageName == Constants.APP_PACKAGE_NAME) {
            sendBinder()
        }
        currForegroundApp = packageName
    }
}
