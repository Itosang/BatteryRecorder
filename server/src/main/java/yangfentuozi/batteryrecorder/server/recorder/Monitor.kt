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
import yangfentuozi.batteryrecorder.shared.config.SettingsConstants
import yangfentuozi.batteryrecorder.shared.data.LineRecord
import yangfentuozi.batteryrecorder.shared.util.Handlers
import yangfentuozi.batteryrecorder.shared.util.LoggerX
import java.io.IOException

private const val TAG = "Monitor"

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
    private var displayCallbackRegistered = false

    @Volatile
    private var currForegroundApp: String? = null

    @Volatile
    private var isInteractive = true

    private val callbacks: RemoteCallbackList<IRecordListener> = RemoteCallbackList()

    @Volatile
    var recordIntervalMs: Long = SettingsConstants.recordIntervalMs.def

    @Volatile
    var screenOffRecord: Boolean = SettingsConstants.screenOffRecordEnabled.def

    private var mAlwaysPollingScreenStatusEnabled: Boolean =
        SettingsConstants.alwaysPollingScreenStatusEnabled.def
    var alwaysPollingScreenStatusEnabled: Boolean
        get() = mAlwaysPollingScreenStatusEnabled
        set(value) {
            val oldValue = mAlwaysPollingScreenStatusEnabled
            if (value != oldValue) {
                mAlwaysPollingScreenStatusEnabled = value
                LoggerX.d(TAG, "alwaysPollingScreenStatusEnabled: 配置变更, $oldValue -> $value")
                if (value) {
                    LoggerX.d(TAG, "alwaysPollingScreenStatusEnabled: 切到轮询模式, 清空 DisplayCallback 引用")
                    unregisterDisplayEventCallback()
                } else {
                    LoggerX.d(TAG, "alwaysPollingScreenStatusEnabled: 切到回调模式, 注册 DisplayCallback")
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
                    if (alwaysPollingScreenStatusEnabled) {
                        val oldIsInteractive = isInteractive
                        val latestIsInteractive = iPowerManager.isInteractive
                        if (oldIsInteractive != latestIsInteractive) {
                            LoggerX.d(TAG, "@thread: 亮屏状态变化, $oldIsInteractive -> $latestIsInteractive")
                        }
                        isInteractive = latestIsInteractive
                    }
                    val writeResult = writer.write(
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
                        // 回调 app：先同步当前记录文件切换，再下发已进入当前记录的实时样本。
                        val n: Int = callbacks.beginBroadcast()
                        for (i in 0..<n) {
                            try {
                                val callback = callbacks.getBroadcastItem(i)
                                when (writeResult) {
                                    is PowerRecordWriter.WriteResult.Changed -> {
                                        callback.onChangedCurrRecordsFile(writeResult.recordsFile)
                                        callback.onRecord(timestamp, power, status, temp)
                                    }

                                    PowerRecordWriter.WriteResult.Accepted -> {
                                        callback.onRecord(timestamp, power, status, temp)
                                    }

                                    PowerRecordWriter.WriteResult.Rejected -> Unit
                                }
                            } catch (e: RemoteException) {
                                LoggerX.e(TAG, 
                                    "@callbackHandlerPost: 记录回调失败",
                                    tr = e
                                )
                            }
                        }
                        callbacks.finishBroadcast()
                    }
                } catch (e: IOException) {
                    LoggerX.e(TAG, "@thread: 读取功耗数据失败", tr = e)
                }

                if (isInteractive || screenOffRecord) {
                    if (paused) {
                        LoggerX.d(TAG, "@thread: 恢复采样, isInteractive=$isInteractive screenOffRecord=$screenOffRecord")
                    }
                    paused = false
                    lock.wait(recordIntervalMs)
                } else {
                    paused = true
                    if (alwaysPollingScreenStatusEnabled) {
                        LoggerX.d(TAG, "@thread: 暂停采样, 等待轮询亮屏")

                        while (!stopped && !screenOffRecord && !isInteractive && alwaysPollingScreenStatusEnabled) {
                            lock.wait(recordIntervalMs)
                            isInteractive = iPowerManager.isInteractive
                        }
                    } else {
                        LoggerX.d(TAG, "@thread: 暂停采样, 等待亮屏事件")
                        lock.wait()
                    }
                }
            }
        }
        Thread.currentThread().interrupt()
    }, "MonitorThread")

    fun start() {
        LoggerX.d(TAG, 
            "start: alwaysPollingScreenStatusEnabled=$alwaysPollingScreenStatusEnabled screenOffRecord=$screenOffRecord"
        )
        try {
            iActivityTaskManager.registerTaskStackListener(taskStackListener)
            if (!alwaysPollingScreenStatusEnabled) {
                LoggerX.d(TAG, "start: 分支命中, 注册 DisplayCallback")
                registerDisplayEventCallback()
            }
        } catch (e: RemoteException) {
            throw RuntimeException("start: 注册任务栈监听失败", e)
        }

        try {
            onFocusedAppChanged(iActivityTaskManager.getFocusedRootTaskInfo())
        } catch (e: RemoteException) {
            throw RuntimeException("start: 获取当前焦点任务信息失败", e)
        }
        isInteractive = iPowerManager.isInteractive
        LoggerX.d(TAG, "start: initial isInteractive=$isInteractive")
        thread.start()
    }

    private fun registerDisplayEventCallback() {
        if (displayCallbackRegistered) {
            LoggerX.v(TAG, "registerDisplayEventCallback: 已注册, skip")
            return
        }
        LoggerX.d(TAG, "registerDisplayEventCallback: 注册 DisplayCallback")
        displayCallback = object : IDisplayManagerCallback.Stub() {
            @Keep
            override fun onDisplayEvent(displayId: Int, event: Int) {
                if (alwaysPollingScreenStatusEnabled) {
                    LoggerX.v(TAG, "onDisplayEvent: 已忽略, 当前为轮询模式, displayId=$displayId event=$event")
                    return
                }
                val oldIsInteractive = isInteractive
                val latestIsInteractive = iPowerManager.isInteractive
                isInteractive = latestIsInteractive
                LoggerX.d(TAG, 
                    "onDisplayEvent: displayId=$displayId event=$event interactive $oldIsInteractive -> $latestIsInteractive paused=$paused"
                )
                if (isInteractive && paused) {
                    LoggerX.d(TAG, "onDisplayEvent: 收到亮屏事件, 唤醒采样线程")
                    notifyLock()
                }
            }
        }
        iDisplayManager.registerCallback(displayCallback)
        displayCallbackRegistered = true
    }

    /**
     * 自实现注销屏幕事件回调，系统服务端检测进程退出自动处理
     */
    private fun unregisterDisplayEventCallback() {
        LoggerX.v(TAG, "unregisterDisplayEventCallback: 清空 DisplayCallback 引用")
        displayCallback = null
        displayCallbackRegistered = false
    }

    fun stop() {
        stopped = true
        notifyLock()
        try {
            iActivityTaskManager.unregisterTaskStackListener(taskStackListener)
        } catch (e: RemoteException) {
            LoggerX.e(TAG, "stop: 注销任务栈监听失败", tr = e)
        }
    }

    fun notifyLock() {
        synchronized(lock) {
            lock.notifyAll()
        }
    }

    fun registerRecordListener(callback: IRecordListener) {
        LoggerX.v(TAG, "registerRecordListener: 注册记录回调")
        callbacks.register(callback)
    }

    fun unregisterRecordListener(callback: IRecordListener) {
        LoggerX.v(TAG, "unregisterRecordListener: 注销记录回调")
        callbacks.unregister(callback)
    }

    private fun onFocusedAppChanged(taskInfo: TaskInfo) {
        val componentName = taskInfo.topActivity ?: return
        val packageName = componentName.packageName
        if (packageName == Constants.APP_PACKAGE_NAME) {
            LoggerX.d(TAG, "onFocusedAppChanged: 焦点回到 App, 尝试重新发送 Binder")
            sendBinder()
        }
        if (currForegroundApp != packageName) {
            LoggerX.d(TAG, "onFocusedAppChanged: 应用切换, $currForegroundApp -> $packageName")
        }
        currForegroundApp = packageName
    }
}
