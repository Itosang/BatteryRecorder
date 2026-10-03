package yangfentuozi.batteryrecorder.server.recorder

import android.annotation.SuppressLint
import android.app.ActivityManager.RunningTaskInfo
import android.app.ActivityTaskManager.RootTaskInfo
import android.app.IActivityTaskManager
import android.app.ITaskStackListener
import android.app.TaskInfo
import android.app.TaskStackListener
import android.graphics.Rect
import android.hardware.display.DisplayTopology
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
import yangfentuozi.batteryrecorder.shared.config.dataclass.ServerSettings
import yangfentuozi.batteryrecorder.shared.data.LineRecord
import yangfentuozi.batteryrecorder.shared.util.Handlers
import yangfentuozi.batteryrecorder.shared.util.LoggerX
import yangfentuozi.hiddenapi.compat.TaskInfoCompat
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private const val TAG = "Monitor"

class Monitor(
    private val writer: PowerRecordWriter,
    private val sampler: Sampler,
) {
    private val iActivityTaskManager =
        IActivityTaskManager.Stub.asInterface(ServiceManager.getService("activity_task"))
    private val iDisplayManager =
        IDisplayManager.Stub.asInterface(ServiceManager.getService("display"))
    private val iPowerManager = IPowerManager.Stub.asInterface(ServiceManager.getService("power"))
    private val taskStackListener: ITaskStackListener = object : TaskStackListener() {
        @Keep
        override fun onTaskMovedToFront(taskInfo: RunningTaskInfo) {
            onFocusedAppChanged(taskInfo, getFocusedRootTaskInfoOrNull(), "task-moved")
        }
    }

    var bridge: ChildServerBridge? = null

    private var displayCallback: IDisplayManagerCallback? = null

    @Volatile
    private var displayCallbackRegistered = false

    @Volatile
    private var currForegroundApp: String? = null

    @Volatile
    private var lastSampleLoggedForegroundApp: String? = null

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
            LoggerX.d(TAG, "screenOffRecord: 配置变更, $oldValue -> $value")
            updatePreciseScreenOffWakeLockState()
        }

    @Volatile
    var preciseScreenOffRecordEnabled: Boolean = SettingsConstants.preciseScreenOffRecordEnabled.def
        set(value) {
            val oldValue = field
            if (oldValue == value) return
            field = value
            LoggerX.d(TAG, "preciseScreenOffRecordEnabled: 配置变更, $oldValue -> $value")
            if (!oldValue && value) {
                preparePreciseScreenOffWakeLock()
            }
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
    private var notificationEnabled = false // 默认状态一定是关

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
                    LoggerX.d(
                        TAG,
                        "alwaysPollingScreenStatusEnabled: 切到轮询模式, 清空 DisplayCallback 引用"
                    )
                    unregisterDisplayEventCallback()
                } else {
                    LoggerX.d(
                        TAG,
                        "alwaysPollingScreenStatusEnabled: 切到回调模式, 注册 DisplayCallback"
                    )
                    registerDisplayEventCallback()
                }
            }
        }

    @Volatile
    private var paused = false

    @Volatile
    private var isStopped = false
    private var preciseScreenOffWakeLock: PowerManager.WakeLock? = null

    @Volatile
    private var preciseScreenOffWakeLockDisabledForCurrentServer = false
    private var lastPreciseScreenOffWakeLockDecisionReason: String? = null
    private val lock = ReentrantLock()
    private val condition = lock.newCondition()
    private val callbackHandler: Handler
        get() = Handlers.getHandler("CallbackThread")
    private var thread = Thread({
        lock.withLock {
            while (!isStopped) {
                try {
                    val timestamp = System.currentTimeMillis()
                    val sample = sampler.sample()
                    val power = sample.voltage * sample.current
                    val status = sample.status
                    val temp = sample.temp
                    val foregroundApp = currForegroundApp
                    if (foregroundApp != lastSampleLoggedForegroundApp) {
                        LoggerX.v(
                            TAG,
                            "foreground:sample-foreground-changed timestamp=$timestamp pkg=$foregroundApp"
                        )
                        lastSampleLoggedForegroundApp = foregroundApp
                    }
                    if (alwaysPollingScreenStatusEnabled) {
                        val oldIsInteractive = isInteractive
                        val latestIsInteractive = iPowerManager.isInteractive
                        if (oldIsInteractive != latestIsInteractive) {
                            LoggerX.d(
                                TAG,
                                "@thread: 亮屏状态变化, $oldIsInteractive -> $latestIsInteractive"
                            )
                        }
                        isInteractive = latestIsInteractive
                        updatePreciseScreenOffWakeLockState()
                    }
                    val record = LineRecord(
                        timestamp,
                        power,
                        foregroundApp,
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
                                LoggerX.e(
                                    TAG,
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
                        LoggerX.d(
                            TAG,
                            "@thread: 恢复采样, isInteractive=$isInteractive screenOffRecord=$screenOffRecord"
                        )
                    }
                    paused = false
                    condition.await(recordIntervalMs, TimeUnit.MILLISECONDS)
                } else {
                    paused = true
                    if (alwaysPollingScreenStatusEnabled) {
                        LoggerX.d(TAG, "@thread: 暂停采样, 等待轮询亮屏")

                        while (!isStopped && !screenOffRecord && !isInteractive && alwaysPollingScreenStatusEnabled) {
                            condition.await(recordIntervalMs, TimeUnit.MILLISECONDS)
                            isInteractive = iPowerManager.isInteractive
                            updatePreciseScreenOffWakeLockState()
                        }
                    } else {
                        LoggerX.d(TAG, "@thread: 暂停采样, 等待亮屏事件")
                        condition.await()
                    }
                }
            }
        }
        Thread.currentThread().interrupt()
    }, "MonitorThread")

    fun start() {
        LoggerX.d(
            TAG,
            "start: alwaysPollingScreenStatusEnabled=$alwaysPollingScreenStatusEnabled screenOffRecord=$screenOffRecord preciseScreenOffRecordEnabled=$preciseScreenOffRecordEnabled"
        )
        if (preciseScreenOffRecordEnabled) {
            preparePreciseScreenOffWakeLock()
        }
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
            val focusedRootTaskInfo = iActivityTaskManager.getFocusedRootTaskInfo()
            onFocusedAppChanged(focusedRootTaskInfo, focusedRootTaskInfo, "init")
        } catch (e: RemoteException) {
            throw RuntimeException("start: 获取当前焦点任务信息失败", e)
        }
        isInteractive = iPowerManager.isInteractive
        updatePreciseScreenOffWakeLockState()
        LoggerX.d(TAG, "start: initial isInteractive=$isInteractive")
        thread.start()
    }

    /**
     * 在非 Binder 回调线程预创建精确息屏记录的唤醒锁实例，避免首次息屏回调落在 Binder 线程时
     * 触发 `FakeContext.systemContext` 的惰性初始化，进而命中无 Looper 线程创建 ActivityThread 的崩溃。
     *
     * @return 无；预创建失败时会在当前 Server 生命周期内静默禁用该功能。
     */
    private fun preparePreciseScreenOffWakeLock() {
        val wakeLock = requirePreciseScreenOffWakeLock()
        if (wakeLock != null) {
            LoggerX.d(TAG, "preparePreciseScreenOffWakeLock: 唤醒锁实例预创建完成")
        }
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
                    LoggerX.v(
                        TAG,
                        "onDisplayEvent: 已忽略, 当前为轮询模式, displayId=$displayId event=$event"
                    )
                    return
                }
                refreshInteractiveState("onDisplayEvent", "displayId=$displayId event=$event")
            }

            /**
             * Android 16+ (API 36+) 新增回调：显示集合快照变化。
             *
             * 必须实现，否则系统事务派发会抛 AbstractMethodError（API 37 实测崩溃点）。
             * 语义上仍属于屏幕状态变化，复用与 onDisplayEvent 一致的刷新逻辑。
             */
            @Keep
            override fun onDisplaySnapshot(displayIds: IntArray?, displayStates: IntArray?) {
                if (alwaysPollingScreenStatusEnabled) {
                    LoggerX.v(
                        TAG,
                        "onDisplaySnapshot: 已忽略, 当前为轮询模式, displays=${displayIds?.size}"
                    )
                    return
                }
                refreshInteractiveState("onDisplaySnapshot", "displays=${displayIds?.size}")
            }

            /**
             * Android 16+ (API 36+) 新增回调：显示拓扑变化（投屏/折叠/扩展屏等）。
             *
             * 必须实现，理由同上；拓扑变化时同样刷新一次交互状态即可。
             */
            @Keep
            override fun onTopologyChanged(topology: DisplayTopology?) {
                if (alwaysPollingScreenStatusEnabled) {
                    LoggerX.v(TAG, "onTopologyChanged: 已忽略, 当前为轮询模式")
                    return
                }
                refreshInteractiveState("onTopologyChanged")
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

    /**
     * Display 回调共用的刷新逻辑：拉取最新交互状态，亮屏且采样暂停时唤醒采样线程。
     *
     * @param source 回调名（用于日志定位）。
     * @param detail 附加到日志的回调参数摘要；null 表示该回调无有用参数。
     */
    private fun refreshInteractiveState(source: String, detail: String? = null) {
        val oldIsInteractive = isInteractive
        val latestIsInteractive = iPowerManager.isInteractive
        isInteractive = latestIsInteractive
        updatePreciseScreenOffWakeLockState()
        val detailText = detail?.let { "$it " } ?: ""
        LoggerX.d(
            TAG,
            "$source: ${detailText}interactive $oldIsInteractive -> $latestIsInteractive paused=$paused"
        )
        if (isInteractive && paused) {
            LoggerX.d(TAG, "$source: 收到亮屏事件, 唤醒采样线程")
            notifyLock()
        }
    }

    fun onStop() {
        isStopped = true
        notifyLock()
        releasePreciseScreenOffWakeLockIfHeld("服务停止")
        try {
            iActivityTaskManager.unregisterTaskStackListener(taskStackListener)
        } catch (e: RemoteException) {
            LoggerX.e(TAG, "stop: 注销任务栈监听失败", tr = e)
        }
        notificationUtil?.cancelNotification()
        notificationUtil = null
        bridge?.stop()
    }

    fun notifyLock() {
        lock.withLock {
            condition.signalAll()
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

    /**
     * 记录当前聚焦任务对应的前台应用诊断日志，并在满足记录条件时更新缓存包名。
     *
     * @param taskInfo 当前收到的任务栈信息，用于提取任务 ID 与顶部 Activity。
     * @param focusedRootTaskInfo 当前聚焦 RootTask 信息，用于读取最大窗口边界。
     * @param source 本次前台变更的事件来源，便于区分初始化与任务切换场景。
     * @return 无；当顶部 Activity 为空或命中小窗规则时仅输出日志，不更新当前前台应用缓存。
     */
    private fun onFocusedAppChanged(
        taskInfo: TaskInfo,
        focusedRootTaskInfo: RootTaskInfo?,
        source: String
    ) {
        val oldForegroundApp = currForegroundApp
        val componentName = taskInfo.topActivity
        val bounds = try {
            TaskInfoCompat.getBoundsOrNull(iActivityTaskManager, taskInfo, focusedRootTaskInfo)
        } catch (e: RemoteException) {
            LoggerX.e(TAG, "前台应用检测: 获取任务窗口边界失败, 跳过本次前台应用更新", tr = e)
            return
        }
        val maxBounds = TaskInfoCompat.getMaxBoundsOrNull(focusedRootTaskInfo)
        val boundsText = formatRect(bounds)
        val maxBoundsText = formatRect(maxBounds)
        if (componentName == null) {
            LoggerX.v(
                TAG,
                "前台应用检测: source=%s taskId=%d 当前应用包名=无 是否小窗=未知 是否切换前台应用=否 原因=当前任务没有顶部Activity 当前窗口=%s 最大窗口=%s 旧前台应用=%s",
                source,
                taskInfo.taskId,
                boundsText,
                maxBoundsText,
                oldForegroundApp
            )
            return
        }
        val packageName = componentName.packageName
        val className = componentName.className
        val isSmallWindow = isSmallWindow(bounds, maxBounds)
        if (isSmallWindow) {
            LoggerX.d(
                TAG,
                "前台应用检测: source=%s taskId=%d 当前应用包名=%s 当前Activity=%s 是否小窗=是 是否切换前台应用=否 原因=命中小窗规则 当前窗口=%s 最大窗口=%s 旧前台应用=%s",
                source,
                taskInfo.taskId,
                packageName,
                className,
                boundsText,
                maxBoundsText,
                oldForegroundApp
            )
            return
        }
        val changedForegroundApp = oldForegroundApp != packageName
        LoggerX.v(
            TAG,
            "前台应用检测: source=%s taskId=%d 当前应用包名=%s 当前Activity=%s 是否小窗=否 是否切换前台应用=%s 原因=%s 当前窗口=%s 最大窗口=%s 旧前台应用=%s 新前台应用=%s",
            source,
            taskInfo.taskId,
            packageName,
            className,
            if (changedForegroundApp) "是" else "否",
            if (changedForegroundApp) "命中非小窗且包名发生变化" else "命中非小窗但包名未变化",
            boundsText,
            maxBoundsText,
            oldForegroundApp,
            packageName
        )
        if (changedForegroundApp) {
            currForegroundApp = packageName
        }
    }

    /**
     * 当前只把“窗口左上角不贴边，且右下角也没有铺满最大边界”的任务视为小窗。
     *
     * 这个规则是为当前 ROM 的小窗形态收敛出来的，不额外兼顾分屏或其他多窗口模式。
     *
     * @param bounds 任务当前窗口边界。
     * @param maxBounds 任务可达到的最大窗口边界。
     * @return 命中小窗规则返回 `true`，否则返回 `false`。
     */
    private fun isSmallWindow(bounds: Rect?, maxBounds: Rect?): Boolean {
        if (bounds == null || maxBounds == null) return false
        return bounds.left != 0 &&
                bounds.top != 0 &&
                bounds.right != maxBounds.right &&
                bounds.bottom != maxBounds.bottom
    }

    /**
     * 将窗口边界格式化为紧凑坐标串，便于日志对比与脚本处理。
     *
     * @param rect 要格式化的窗口边界。
     * @return 成功时返回 `[left,top,right,bottom]`，为空时返回 `unavailable`。
     */
    private fun formatRect(rect: Rect?): String {
        if (rect == null) return "unavailable"
        return "[${rect.left},${rect.top},${rect.right},${rect.bottom}]"
    }

    private fun getFocusedRootTaskInfoOrNull(): RootTaskInfo? = try {
        iActivityTaskManager.getFocusedRootTaskInfo()
    } catch (e: RemoteException) {
        LoggerX.e(TAG, "前台应用检测: 获取当前焦点 RootTask 信息失败, 跳过本次前台应用更新", tr = e)
        null
    }

    fun syncSettings(settings: ServerSettings) {
        notificationPowerMultiplier = computeNotificationPowerMultiplier(
            dualCellEnabled = settings.dualCellEnabled,
            calibrationValue = settings.calibrationValue,
        )
        notificationUtil?.cancelNotification()
        if (settings.notificationEnabled) {
            notificationUtil = if (Os.getuid() == 0) RemoteNotificationUtil(bridge!!)
            else LocalNotificationUtil()
            notificationUtil!!.syncSettings(settings)
            notificationEnabled = true
        } else {
            notificationUtil = null
            notificationEnabled = false
        }
        alwaysPollingScreenStatusEnabled = settings.alwaysPollingScreenStatusEnabled
        recordIntervalMs = settings.recordIntervalMs
        screenOffRecord = settings.screenOffRecordEnabled
        preciseScreenOffRecordEnabled = settings.preciseScreenOffRecordEnabled
        notifyLock()
    }

    /**
     * 按当前屏幕状态与配置同步精确息屏记录的唤醒锁状态。
     *
     * 这里只在“开启精确息屏记录 + 开启息屏记录 + 当前确实处于息屏”时持锁，
     * 避免把亮屏阶段也变成常驻保活，额外抬高非目标场景功耗。
     *
     * @return 无；状态不满足时会主动释放已持有的唤醒锁。
     */
    @SuppressLint("WakelockTimeout")
    @Synchronized
    private fun updatePreciseScreenOffWakeLockState() {
        val shouldHoldWakeLock =
            preciseScreenOffRecordEnabled &&
                    !preciseScreenOffWakeLockDisabledForCurrentServer &&
                    screenOffRecord &&
                    !isInteractive &&
                    !isStopped
        val currentReason = when {
            isStopped -> "服务停止"
            preciseScreenOffWakeLockDisabledForCurrentServer -> "当前服务已禁用精确息屏记录"
            isInteractive -> "屏幕亮起"
            !screenOffRecord -> "息屏记录关闭"
            !preciseScreenOffRecordEnabled -> "精确息屏记录关闭"
            else -> "满足持锁条件"
        }

        val wakeLock = preciseScreenOffWakeLock
        if (shouldHoldWakeLock) {
            if (wakeLock == null) {
                LoggerX.e(
                    TAG,
                    "updatePreciseScreenOffWakeLockState: 满足持锁条件但唤醒锁尚未初始化"
                )
                lastPreciseScreenOffWakeLockDecisionReason = "唤醒锁未初始化"
                return
            }
            if (!wakeLock.isHeld) {
                LoggerX.d(
                    TAG,
                    "updatePreciseScreenOffWakeLockState: 准备持有唤醒锁, preciseScreenOffRecordEnabled=$preciseScreenOffRecordEnabled screenOffRecord=$screenOffRecord isInteractive=$isInteractive stopped=$isStopped"
                )
                wakeLock.acquire()
                if (wakeLock.isHeld) {
                    LoggerX.i(TAG, "updatePreciseScreenOffWakeLockState: 已持有唤醒锁")
                } else {
                    LoggerX.w(
                        TAG,
                        "updatePreciseScreenOffWakeLockState: acquire() 返回后仍未持有唤醒锁"
                    )
                }
            } else {
                LoggerX.d(
                    TAG,
                    "updatePreciseScreenOffWakeLockState: 唤醒锁已处于持有状态，跳过重复 acquire"
                )
            }
            lastPreciseScreenOffWakeLockDecisionReason = currentReason
            return
        }

        if (wakeLock?.isHeld == true) {
            releasePreciseScreenOffWakeLockIfHeld(currentReason)
            lastPreciseScreenOffWakeLockDecisionReason = currentReason
            return
        }
        if (lastPreciseScreenOffWakeLockDecisionReason != currentReason) {
            LoggerX.d(
                TAG,
                "updatePreciseScreenOffWakeLockState: 当前不持有唤醒锁, reason=$currentReason preciseScreenOffRecordEnabled=$preciseScreenOffRecordEnabled screenOffRecord=$screenOffRecord isInteractive=$isInteractive stopped=$isStopped"
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
        LoggerX.d(TAG, "releasePreciseScreenOffWakeLockIfHeld: 准备释放唤醒锁, reason=$reason")
        wakeLock.release()
        if (!wakeLock.isHeld) {
            LoggerX.i(TAG, "releasePreciseScreenOffWakeLockIfHeld: 已释放唤醒锁, reason=$reason")
        } else {
            LoggerX.w(
                TAG,
                "releasePreciseScreenOffWakeLockIfHeld: release() 返回后仍处于持有状态, reason=$reason"
            )
        }
    }

    /**
     * 获取精确息屏记录使用的部分唤醒锁实例。
     *
     * 初始化失败时仅记录错误，并在当前 Server 生命周期内静默禁用该功能，避免在回调线程反复抛异常。
     *
     * @return 返回单例 `PARTIAL_WAKE_LOCK`；当前服务已禁用该能力时返回 `null`。
     */
    private fun requirePreciseScreenOffWakeLock(): PowerManager.WakeLock? {
        if (preciseScreenOffWakeLockDisabledForCurrentServer) {
            LoggerX.d(
                TAG,
                "requirePreciseScreenOffWakeLock: 当前服务已禁用精确息屏记录，跳过唤醒锁初始化"
            )
            return null
        }
        preciseScreenOffWakeLock?.let {
            LoggerX.d(
                TAG,
                "requirePreciseScreenOffWakeLock: 复用已有唤醒锁实例, held=${it.isHeld}"
            )
            return it
        }
        try {
            LoggerX.d(TAG, "requirePreciseScreenOffWakeLock: 开始创建唤醒锁实例")
            LoggerX.d(TAG, "requirePreciseScreenOffWakeLock: 开始获取 systemContext")
            val systemContext = FakeContext.systemContext
            LoggerX.d(
                TAG,
                "requirePreciseScreenOffWakeLock: 获取 systemContext 成功, context=${systemContext.javaClass.name}"
            )
            LoggerX.d(TAG, "requirePreciseScreenOffWakeLock: 开始获取 PowerManager")
            val powerManager = systemContext.getSystemService(PowerManager::class.java)
                ?: throw IllegalStateException("获取 PowerManager 失败")
            LoggerX.d(
                TAG,
                "requirePreciseScreenOffWakeLock: 获取 PowerManager 成功, service=${powerManager.javaClass.name}"
            )
            LoggerX.d(
                TAG,
                "requirePreciseScreenOffWakeLock: 开始调用 newWakeLock, tag=$PRECISE_SCREEN_OFF_WAKE_LOCK_TAG"
            )
            return powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                PRECISE_SCREEN_OFF_WAKE_LOCK_TAG
            ).apply {
                setReferenceCounted(false)
                preciseScreenOffWakeLock = this
                LoggerX.d(
                    TAG,
                    "requirePreciseScreenOffWakeLock: 唤醒锁实例创建完成, tag=$PRECISE_SCREEN_OFF_WAKE_LOCK_TAG held=$isHeld"
                )
            }
        } catch (t: Throwable) {
            LoggerX.e(TAG, "requirePreciseScreenOffWakeLock: 创建唤醒锁失败", tr = t)
            preciseScreenOffWakeLockDisabledForCurrentServer = true
            preciseScreenOffWakeLock = null
            LoggerX.w(TAG, "requirePreciseScreenOffWakeLock: 当前服务已静默禁用精确息屏记录")
            return null
        }
    }

    companion object {
        private const val POWER_SCALE_DIVISOR = 1_000_000_000_000.0
        private const val PRECISE_SCREEN_OFF_WAKE_LOCK_TAG =
            "BatteryRecorder:PreciseScreenOffRecord"

        fun computeNotificationPowerMultiplier(
            dualCellEnabled: Boolean,
            calibrationValue: Int
        ): Double {
            val cellMultiplier = if (dualCellEnabled) 2.0 else 1.0
            return cellMultiplier * calibrationValue / POWER_SCALE_DIVISOR
        }
    }
}
