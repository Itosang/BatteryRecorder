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
import android.os.PowerManager
import android.os.RemoteCallbackList
import android.os.RemoteException
import android.os.ServiceManager
import android.system.Os
import androidx.annotation.Keep
import yangfentuozi.batteryrecorder.server.fakecontext.FakeContext
import yangfentuozi.batteryrecorder.server.notification.LocalNotificationUtil
import yangfentuozi.batteryrecorder.server.notification.NotificationInfo
import yangfentuozi.batteryrecorder.server.notification.NotificationUtil
import yangfentuozi.batteryrecorder.server.notification.RemoteNotificationUtil
import yangfentuozi.batteryrecorder.server.notification.server.ChildServerBridge
import yangfentuozi.batteryrecorder.server.sampler.Sampler
import yangfentuozi.batteryrecorder.server.writer.PowerRecordWriter
import yangfentuozi.batteryrecorder.shared.config.SettingsConstants
import yangfentuozi.batteryrecorder.shared.data.LineRecord
import yangfentuozi.batteryrecorder.shared.util.Handlers
import yangfentuozi.batteryrecorder.shared.util.LoggerX
import yangfentuozi.hiddenapi.compat.ServiceManagerCompat
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class Monitor(
    private val writer: PowerRecordWriter,
    private val sampler: Sampler,
    private val bridge: ChildServerBridge?
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

    private val tag = "Monitor"

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
        set(value) {
            val oldValue = field
            if (oldValue == value) return
            field = value
            LoggerX.d(tag, "screenOffRecord: 配置变更, $oldValue -> $value")
            updatePreciseScreenOffWakeLockState()
        }

    @Volatile
    var preciseScreenOffRecordEnabled: Boolean = SettingsConstants.preciseScreenOffRecordEnabled.def
        set(value) {
            val oldValue = field
            if (oldValue == value) return
            field = value
            LoggerX.d(tag, "preciseScreenOffRecordEnabled: 配置变更, $oldValue -> $value")
            updatePreciseScreenOffWakeLockState()
        }

    @Volatile
    var notificationUtil: NotificationUtil? = null

    @Volatile
    var notificationPowerMultiplier: Double = computeNotificationPowerMultiplier(
        dualCellEnabled = SettingsConstants.dualCellEnabled.def,
        calibrationValue = SettingsConstants.calibrationValue.def,
    )

    @Volatile
    private var notificationEnabled = SettingsConstants.notificationEnabled.def
    @Volatile
    private var notificationCompatModeEnabled = SettingsConstants.notificationCompatModeEnabled.def

    private var mAlwaysPollingScreenStatusEnabled: Boolean =
        SettingsConstants.alwaysPollingScreenStatusEnabled.def
    var alwaysPollingScreenStatusEnabled: Boolean
        get() = mAlwaysPollingScreenStatusEnabled
        set(value) {
            val oldValue = mAlwaysPollingScreenStatusEnabled
            if (value != oldValue) {
                mAlwaysPollingScreenStatusEnabled = value
                LoggerX.d(tag, "alwaysPollingScreenStatusEnabled: 配置变更, $oldValue -> $value")
                if (value) {
                    LoggerX.d(tag, "alwaysPollingScreenStatusEnabled: 切到轮询模式, 清空 DisplayCallback 引用")
                    unregisterDisplayEventCallback()
                } else {
                    LoggerX.d(tag, "alwaysPollingScreenStatusEnabled: 切到回调模式, 注册 DisplayCallback")
                    registerDisplayEventCallback()
                }
            }
        }

    @Volatile
    private var paused = false

    @Volatile
    private var stopped = false
    private var preciseScreenOffWakeLock: PowerManager.WakeLock? = null
    private var lastPreciseScreenOffWakeLockDecisionReason: String? = null
    private val lock = ReentrantLock()
    private val condition = lock.newCondition()
    private val callbackHandler: Handler
        get() = Handlers.getHandler("CallbackThread")
    private var thread = Thread({
        lock.withLock {
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
                            LoggerX.d(tag, "@thread: 亮屏状态变化, $oldIsInteractive -> $latestIsInteractive")
                        }
                        isInteractive = latestIsInteractive
                        updatePreciseScreenOffWakeLockState()
                    }
                    val record = LineRecord(
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
                    val writeResult = writer.write(record)
                    if (isInteractive) {
                        notificationUtil?.updateNotification(
                            NotificationInfo(
                                power = notificationPowerMultiplier * power,
                                temp = temp,
                                capacity = sample.capacity
                            )
                        )
                    }

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
                                LoggerX.e(tag,
                                    "@callbackHandlerPost: 记录回调失败",
                                    tr = e
                                )
                            }
                        }
                        callbacks.finishBroadcast()
                    }
                } catch (e: IOException) {
                    LoggerX.e(tag, "@thread: 读取功耗数据失败", tr = e)
                }

                if (isInteractive || screenOffRecord) {
                    if (paused) {
                        LoggerX.d(tag, "@thread: 恢复采样, isInteractive=$isInteractive screenOffRecord=$screenOffRecord")
                    }
                    paused = false
                    condition.await(recordIntervalMs, TimeUnit.MILLISECONDS)
                } else {
                    paused = true
                    if (alwaysPollingScreenStatusEnabled) {
                        LoggerX.d(tag, "@thread: 暂停采样, 等待轮询亮屏")

                        while (!stopped && !screenOffRecord && !isInteractive && alwaysPollingScreenStatusEnabled) {
                            condition.await(recordIntervalMs, TimeUnit.MILLISECONDS)
                            isInteractive = iPowerManager.isInteractive
                            updatePreciseScreenOffWakeLockState()
                        }
                    } else {
                        LoggerX.d(tag, "@thread: 暂停采样, 等待亮屏事件")
                        condition.await()
                    }
                }
            }
        }
        Thread.currentThread().interrupt()
    }, "MonitorThread")

    fun start() {
        LoggerX.d(tag,
            "start: alwaysPollingScreenStatusEnabled=$alwaysPollingScreenStatusEnabled screenOffRecord=$screenOffRecord preciseScreenOffRecordEnabled=$preciseScreenOffRecordEnabled"
        )
        preparePreciseScreenOffWakeLock()
        try {
            iActivityTaskManager.registerTaskStackListener(taskStackListener)
            if (!alwaysPollingScreenStatusEnabled) {
                LoggerX.d(tag, "start: 分支命中, 注册 DisplayCallback")
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
        updatePreciseScreenOffWakeLockState()
        LoggerX.d(tag, "start: initial isInteractive=$isInteractive")
        thread.start()
    }

    /**
     * 在 Server 主线程预创建精确息屏记录的唤醒锁实例，避免首次息屏回调落在 Binder 线程时
     * 触发 `FakeContext.systemContext` 的惰性初始化，进而命中无 Looper 线程创建 ActivityThread 的崩溃。
     *
     * @return 无；预创建失败时仅记日志，保留后续再次尝试的机会。
     */
    private fun preparePreciseScreenOffWakeLock() {
        runCatching { requirePreciseScreenOffWakeLock() }
            .onSuccess {
                LoggerX.d(tag, "preparePreciseScreenOffWakeLock: 唤醒锁实例预创建完成")
            }
            .onFailure { error ->
                LoggerX.e(tag, "preparePreciseScreenOffWakeLock: 唤醒锁实例预创建失败", tr = error)
            }
    }

    private fun registerDisplayEventCallback() {
        if (displayCallbackRegistered) {
            LoggerX.v(tag, "registerDisplayEventCallback: 已注册, skip")
            return
        }
        LoggerX.d(tag, "registerDisplayEventCallback: 注册 DisplayCallback")
        displayCallback = object : IDisplayManagerCallback.Stub() {
            @Keep
            override fun onDisplayEvent(displayId: Int, event: Int) {
                if (alwaysPollingScreenStatusEnabled) {
                    LoggerX.v(tag, "onDisplayEvent: 已忽略, 当前为轮询模式, displayId=$displayId event=$event")
                    return
                }
                val oldIsInteractive = isInteractive
                val latestIsInteractive = iPowerManager.isInteractive
                isInteractive = latestIsInteractive
                updatePreciseScreenOffWakeLockState()
                LoggerX.d(tag,
                    "onDisplayEvent: displayId=$displayId event=$event interactive $oldIsInteractive -> $latestIsInteractive paused=$paused"
                )
                if (isInteractive && paused) {
                    LoggerX.d(tag, "onDisplayEvent: 收到亮屏事件, 唤醒采样线程")
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
        LoggerX.v(tag, "unregisterDisplayEventCallback: 清空 DisplayCallback 引用")
        displayCallback = null
        displayCallbackRegistered = false
    }

    fun stop() {
        stopped = true
        notifyLock()
        releasePreciseScreenOffWakeLockIfHeld("服务停止")
        try {
            iActivityTaskManager.unregisterTaskStackListener(taskStackListener)
        } catch (e: RemoteException) {
            LoggerX.e(tag, "stop: 注销任务栈监听失败", tr = e)
        }
        disableNotification()
    }

    fun notifyLock() {
        lock.withLock {
            condition.signalAll()
        }
    }

    fun registerRecordListener(callback: IRecordListener) {
        LoggerX.v(tag, "registerRecordListener: 注册记录回调")
        callbacks.register(callback)
    }

    fun unregisterRecordListener(callback: IRecordListener) {
        LoggerX.v(tag, "unregisterRecordListener: 注销记录回调")
        callbacks.unregister(callback)
    }

    private fun onFocusedAppChanged(taskInfo: TaskInfo) {
        val componentName = taskInfo.topActivity ?: return
        val packageName = componentName.packageName

        currForegroundApp = packageName
    }

    // 耗时操作
    private fun enableNotification() {
        lock.withLock {
            notificationUtil?.cancelNotification()
            notificationUtil = if (Os.getuid() == 0) {
                RemoteNotificationUtil(
                    bridge = bridge!!,
                    initialCompatibilityModeEnabled = notificationCompatModeEnabled
                )
            }
            else {
                ServiceManagerCompat.waitService("notification")
                LocalNotificationUtil(notificationCompatModeEnabled)
            }
        }
    }

    private fun disableNotification() {
        lock.withLock {
            notificationUtil?.cancelNotification()
            notificationUtil = null
            notificationEnabled = false
        }
    }

    /**
     * 更新通知兼容模式。
     *
     * @param enabled `true` 表示每次更新通知都新建 Builder；`false` 表示继续复用 Builder。
     * @return 无；若通知已启用，则会立即同步到当前通知实现。
     */
    fun setNotificationCompatModeEnabled(enabled: Boolean) {
        lock.withLock {
            if (notificationCompatModeEnabled == enabled) return
            LoggerX.d(
                tag,
                "setNotificationCompatModeEnabled: $notificationCompatModeEnabled -> $enabled"
            )
            notificationCompatModeEnabled = enabled
            notificationUtil?.setCompatibilityModeEnabled(enabled)
        }
    }

    fun setNotificationEnabled(enabled: Boolean) {
        if (enabled == notificationEnabled) return
        if (enabled) {
            enableNotification()
            notificationEnabled = true
        } else {
            disableNotification()
        }
    }

    /**
     * 按当前屏幕状态与配置同步精确息屏记录的唤醒锁状态。
     *
     * 这里只在“开启精确息屏记录 + 开启息屏记录 + 当前确实处于息屏”时持锁，
     * 避免把亮屏阶段也变成常驻保活，额外抬高非目标场景功耗。
     *
     * @return 无；状态不满足时会主动释放已持有的唤醒锁。
     */
    @Synchronized
    private fun updatePreciseScreenOffWakeLockState() {
        val shouldHoldWakeLock =
            preciseScreenOffRecordEnabled &&
                screenOffRecord &&
                !isInteractive &&
                !stopped
        val currentReason = when {
            stopped -> "服务停止"
            isInteractive -> "屏幕亮起"
            !screenOffRecord -> "息屏记录关闭"
            !preciseScreenOffRecordEnabled -> "精确息屏记录关闭"
            else -> "满足持锁条件"
        }

        if (shouldHoldWakeLock) {
            val wakeLock = requirePreciseScreenOffWakeLock()
            if (!wakeLock.isHeld) {
                LoggerX.d(
                    tag,
                    "updatePreciseScreenOffWakeLockState: 准备持有唤醒锁, preciseScreenOffRecordEnabled=$preciseScreenOffRecordEnabled screenOffRecord=$screenOffRecord isInteractive=$isInteractive stopped=$stopped"
                )
                wakeLock.acquire()
                if (wakeLock.isHeld) {
                    LoggerX.i(tag, "updatePreciseScreenOffWakeLockState: 已持有唤醒锁")
                } else {
                    LoggerX.w(tag, "updatePreciseScreenOffWakeLockState: acquire() 返回后仍未持有唤醒锁")
                }
            } else {
                LoggerX.d(tag, "updatePreciseScreenOffWakeLockState: 唤醒锁已处于持有状态，跳过重复 acquire")
            }
            lastPreciseScreenOffWakeLockDecisionReason = currentReason
            return
        }

        val wakeLock = preciseScreenOffWakeLock
        if (wakeLock?.isHeld == true) {
            releasePreciseScreenOffWakeLockIfHeld(currentReason)
            lastPreciseScreenOffWakeLockDecisionReason = currentReason
            return
        }
        if (lastPreciseScreenOffWakeLockDecisionReason != currentReason) {
            LoggerX.d(
                tag,
                "updatePreciseScreenOffWakeLockState: 当前不持有唤醒锁, reason=$currentReason preciseScreenOffRecordEnabled=$preciseScreenOffRecordEnabled screenOffRecord=$screenOffRecord isInteractive=$isInteractive stopped=$stopped"
            )
            lastPreciseScreenOffWakeLockDecisionReason = currentReason
        }
    }

    /**
     * 释放当前已持有的精确息屏记录唤醒锁。
     *
     * @param reason 本次释放的直接原因，用于日志定位。
     * @return 无；若尚未创建或当前未持锁则直接返回。
     */
    @Synchronized
    private fun releasePreciseScreenOffWakeLockIfHeld(reason: String) {
        val wakeLock = preciseScreenOffWakeLock ?: return
        if (!wakeLock.isHeld) return
        LoggerX.d(tag, "releasePreciseScreenOffWakeLockIfHeld: 准备释放唤醒锁, reason=$reason")
        wakeLock.release()
        if (!wakeLock.isHeld) {
            LoggerX.i(tag, "releasePreciseScreenOffWakeLockIfHeld: 已释放唤醒锁, reason=$reason")
        } else {
            LoggerX.w(tag, "releasePreciseScreenOffWakeLockIfHeld: release() 返回后仍处于持有状态, reason=$reason")
        }
    }

    /**
     * 获取精确息屏记录使用的部分唤醒锁实例。
     *
     * @return 返回单例 `PARTIAL_WAKE_LOCK`；若系统 `PowerManager` 不可用则直接抛错暴露问题。
     */
    private fun requirePreciseScreenOffWakeLock(): PowerManager.WakeLock {
        preciseScreenOffWakeLock?.let {
            LoggerX.d(
                tag,
                "requirePreciseScreenOffWakeLock: 复用已有唤醒锁实例, held=${it.isHeld}"
            )
            return it
        }
        try {
            LoggerX.d(tag, "requirePreciseScreenOffWakeLock: 开始创建唤醒锁实例")
            LoggerX.d(tag, "requirePreciseScreenOffWakeLock: 开始获取 systemContext")
            val systemContext = FakeContext.systemContext
            LoggerX.d(tag, "requirePreciseScreenOffWakeLock: 获取 systemContext 成功, context=${systemContext.javaClass.name}")
            LoggerX.d(tag, "requirePreciseScreenOffWakeLock: 开始获取 PowerManager")
            val powerManager = systemContext.getSystemService(PowerManager::class.java)
                ?: throw IllegalStateException("获取 PowerManager 失败")
            LoggerX.d(tag, "requirePreciseScreenOffWakeLock: 获取 PowerManager 成功, service=${powerManager.javaClass.name}")
            LoggerX.d(tag, "requirePreciseScreenOffWakeLock: 开始调用 newWakeLock, tag=$PRECISE_SCREEN_OFF_WAKE_LOCK_TAG")
            return powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                PRECISE_SCREEN_OFF_WAKE_LOCK_TAG
            ).apply {
                setReferenceCounted(false)
                preciseScreenOffWakeLock = this
                LoggerX.d(
                    tag,
                    "requirePreciseScreenOffWakeLock: 唤醒锁实例创建完成, tag=$PRECISE_SCREEN_OFF_WAKE_LOCK_TAG held=$isHeld"
                )
            }
        } catch (t: Throwable) {
            LoggerX.e(tag, "requirePreciseScreenOffWakeLock: 创建唤醒锁失败", tr = t)
            throw t
        }
    }

    companion object {
        private const val POWER_SCALE_DIVISOR = 1_000_000_000_000.0
        private const val PRECISE_SCREEN_OFF_WAKE_LOCK_TAG = "BatteryRecorder:PreciseScreenOffRecord"

        fun computeNotificationPowerMultiplier(
            dualCellEnabled: Boolean,
            calibrationValue: Int
        ): Double {
            val cellMultiplier = if (dualCellEnabled) 2.0 else 1.0
            return cellMultiplier * calibrationValue / POWER_SCALE_DIVISOR
        }
    }
}
