package yangfentuozi.batteryrecorder.server

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import yangfentuozi.batteryrecorder.server.recorder.IRecordListener
import yangfentuozi.batteryrecorder.server.recorder.Monitor
import yangfentuozi.batteryrecorder.server.recorder.sampler.DumpsysSampler
import yangfentuozi.batteryrecorder.server.recorder.sampler.SysfsSampler
import yangfentuozi.batteryrecorder.server.writer.PowerRecordWriter
import yangfentuozi.batteryrecorder.shared.Constants
import yangfentuozi.batteryrecorder.shared.config.ConfigUtil
import yangfentuozi.batteryrecorder.shared.config.SettingsConstants
import yangfentuozi.batteryrecorder.shared.config.dataclass.ServerSettings
import yangfentuozi.batteryrecorder.shared.data.BatteryStatus.Charging
import yangfentuozi.batteryrecorder.shared.data.BatteryStatus.Discharging
import yangfentuozi.batteryrecorder.shared.data.RecordsFile
import yangfentuozi.batteryrecorder.shared.sync.PfdFileSender
import yangfentuozi.batteryrecorder.shared.util.Handlers
import yangfentuozi.batteryrecorder.shared.util.LoggerX
import yangfentuozi.hiddenapi.compat.ActivityManagerCompat
import yangfentuozi.hiddenapi.compat.PackageManagerCompat
import yangfentuozi.hiddenapi.compat.ServiceManagerCompat
import java.io.File
import java.io.FileDescriptor
import java.io.IOException
import java.nio.file.Files
import java.util.Scanner
import kotlin.system.exitProcess

private const val TAG = "Server"

class Server internal constructor() : IService.Stub() {
    private var monitor: Monitor
    private var writer: PowerRecordWriter

    private var appDataDir: File
    private var appConfigFile: File
    private var appPowerDataDir: File
    private var shellDataDir: File
    private var shellPowerDataDir: File

    override fun stopService() {
        Handlers.main.postDelayed({ exitProcess(0) }, 100)
    }

    override fun getVersion(): Int {
        return BuildConfig.VERSION
    }

    override fun getCurrRecordsFile(): RecordsFile? {
        return RecordsFile.fromFile(
            when (writer.lastStatus) {
                Charging -> writer.chargeDataWriter.getCurrFile(writer.lastStatus != Charging)
                Discharging -> writer.dischargeDataWriter.getCurrFile(writer.lastStatus != Discharging)
                else -> null
            } ?: return null
        )
    }

    override fun registerRecordListener(listener: IRecordListener) {
        monitor.registerRecordListener(listener)
    }

    override fun unregisterRecordListener(listener: IRecordListener) {
        monitor.unregisterRecordListener(listener)
    }

    override fun updateConfig(settings: ServerSettings) {
        Handlers.common.post {
            LoggerX.d(
                TAG,
                "updateConfig: 应用配置, intervalMs=${settings.recordIntervalMs} writeLatencyMs=${settings.writeLatencyMs} batchSize=${settings.batchSize} screenOffRecord=${settings.screenOffRecordEnabled} segmentDurationMin=${settings.segmentDurationMin} logLevel=${settings.logLevel} polling=${settings.alwaysPollingScreenStatusEnabled}"
            )
            LoggerX.maxHistoryDays = settings.maxHistoryDays
            LoggerX.logLevel = settings.logLevel

            unlockOPlusSampleTimeLimit(settings.recordIntervalMs.coerceAtLeast(200))

            monitor.alwaysPollingScreenStatusEnabled = settings.alwaysPollingScreenStatusEnabled
            monitor.recordIntervalMs = settings.recordIntervalMs
            monitor.screenOffRecord = settings.screenOffRecordEnabled
            monitor.notifyLock()

            writer.flushIntervalMs = settings.writeLatencyMs
            writer.batchSize = settings.batchSize
            writer.maxSegmentDurationMs = settings.segmentDurationMin * 60 * 1000L
        }
    }

    private fun unlockOPlusSampleTimeLimit(intervalMs: Long) {
        fun readFd(fd: FileDescriptor): String {
            val buffer = ByteArray(1024)
            val len = Os.read(fd, buffer, 0, buffer.size)
            return String(buffer, 0, len)
        }

        fun writeFd(fd: FileDescriptor, content: String) {
            val buffer = content.toByteArray()
            var toWrite = buffer.size
            var offset = 0
            while (toWrite > 0) {
                val len = Os.write(fd, buffer, offset, toWrite)
                toWrite -= len
                offset += len
            }
        }

        val forceActive = "/proc/oplus-votable/GAUGE_UPDATE/force_active"
        val forceVal = "/proc/oplus-votable/GAUGE_UPDATE/force_val"
        val perm = "666".toInt(8)

        var forceActiveFd: FileDescriptor? = null
        var forceValFd: FileDescriptor? = null

        try {
            if (try {
                    Os.access(forceActive, OsConstants.F_OK)
                } catch (_: ErrnoException) {
                    false
                }
            ) {
                LoggerX.i(TAG, "unlockOPlusSampleTimeLimit: 欧加功率采样频率解限文件存在")

                Os.chmod(forceActive, perm)
                Os.chmod(forceVal, perm)

                forceActiveFd = Os.open(forceActive, OsConstants.O_RDWR, perm)
                forceValFd = Os.open(forceVal, OsConstants.O_RDWR, perm)

                val nowValue = readFd(forceValFd).trim().toLong()
                val nowActive = readFd(forceActiveFd).trim().toInt() == 1
                if (!nowActive || nowValue > intervalMs || nowValue == 0L) {
                    LoggerX.i(TAG, 
                        "unlockOPlusSampleTimeLimit: 解锁欧加功率采样频率, target=${intervalMs}ms nowValue=${nowValue}ms nowActive=$nowActive"
                    )
                    writeFd(forceValFd, "$intervalMs\n")
                    writeFd(forceActiveFd, "1\n")
                }
            }
        } catch (e: Exception) {
            LoggerX.w(TAG, "unlockOPlusSampleTimeLimit: 解锁欧加功率采样频率限制失败", tr = e)
        } finally {
            if (forceActiveFd != null) Os.close(forceActiveFd)
            if (forceValFd != null) Os.close(forceValFd)
        }
    }

    override fun sync(): ParcelFileDescriptor? {
        writer.flushBuffer()
        if (Os.getuid() == 0) {
            LoggerX.d(TAG, "sync: root 模式不需要同步文件, return null")
            return null
        }

        val pipe = ParcelFileDescriptor.createPipe()
        val readEnd = pipe[0]
        val writeEnd = pipe[1]
        LoggerX.i(TAG, "sync: 开始同步 shell 记录目录, dir=${shellPowerDataDir.absolutePath}")

        // 服务端在后台线程写入（发送）
        Thread {
            try {
                val currChargeDataPath =
                    if (writer.chargeDataWriter.needStartNewSegment(writer.lastStatus != Charging)) null
                    else writer.chargeDataWriter.segmentFile?.toPath()

                val currDischargeDataPath =
                    if (writer.dischargeDataWriter.needStartNewSegment(writer.lastStatus != Discharging)) null
                    else writer.dischargeDataWriter.segmentFile?.toPath()
                var sentCount = 0

                PfdFileSender.sendFile(
                    writeEnd,
                    shellPowerDataDir
                ) { file ->
                    sentCount += 1
                    LoggerX.d(TAG, "@sendFileCallback: 文件已发送, file=${file.name}")
                    if ((currChargeDataPath == null || !Files.isSameFile(
                            file.toPath(),
                            currChargeDataPath
                        )) &&
                        (currDischargeDataPath == null || !Files.isSameFile(
                            file.toPath(),
                            currDischargeDataPath
                        ))
                    ) file.delete()
                }
                LoggerX.i(TAG, "sync: 同步完成, sentCount=$sentCount")
            } catch (e: Exception) {
                LoggerX.e(TAG, "sync: 后台同步失败", tr = e)
                try {
                    writeEnd.close()
                } catch (_: Exception) {
                }
            }
        }.start()

        // 返回给客户端用于读取
        return readEnd
    }

    private fun stopServiceImmediately() {
        monitor.stop()

        try {
            writer.flushBuffer()
        } catch (e: IOException) {
            LoggerX.e(TAG, "stopServiceImmediately: flushBuffer 失败", tr = e)
        }
        writer.close()

    }

    private fun sendBinder() {
        LoggerX.d(TAG, "sendBinder: 开始向 App 推送 Binder")
        try {
            val reply = ActivityManagerCompat.contentProviderCall(
                "yangfentuozi.batteryrecorder.binderProvider",
                "setBinder",
                null,
                Bundle().apply {
                    putBinder("binder", this@Server)
                }
            )
            if (reply == null) {
                LoggerX.w(TAG, "sendBinder: Binder 推送失败, reply == null")
            } else {
                LoggerX.i(TAG, "sendBinder: Binder 推送成功")
            }
        } catch (e: RemoteException) {
            LoggerX.w(TAG, "sendBinder: Binder 推送失败", tr = e)
        }
    }

    init {
        LoggerX.i(TAG, "init: Server 初始化开始, uid=${Os.getuid()}")
        if (Looper.getMainLooper() == null) {
            Looper.prepareMainLooper()
        }

        Handlers.initMainThread()
        Runtime.getRuntime().addShutdownHook(Thread { this.stopServiceImmediately() })
        ServiceManagerCompat.waitService("activity_task")
        ServiceManagerCompat.waitService("display")
        ServiceManagerCompat.waitService("power")

        fun getAppInfo(packageName: String): ApplicationInfo {
            try {
                return PackageManagerCompat.getApplicationInfo(packageName, 0L, 0)
            } catch (e: RemoteException) {
                throw RuntimeException(
                    "Failed to get application info for package: $packageName",
                    e
                )
            } catch (e: PackageManager.NameNotFoundException) {
                throw RuntimeException("$packageName is not installed", e)
            }
        }

        val appInfo = getAppInfo(Constants.APP_PACKAGE_NAME)
        appDataDir = File(appInfo.dataDir)
        appConfigFile = File("${appInfo.dataDir}/shared_prefs/${SettingsConstants.PREFS_NAME}.xml")
        appPowerDataDir = File("${appInfo.dataDir}/${Constants.APP_POWER_DATA_PATH}")

        val sampler = if (SysfsSampler.init(appInfo)) SysfsSampler else DumpsysSampler()
        LoggerX.i(TAG, "init: 采样器选择完成, sampler=${sampler::class.java.simpleName}")

        shellDataDir = File(Constants.SHELL_DATA_DIR_PATH)
        shellPowerDataDir =
            File("${Constants.SHELL_DATA_DIR_PATH}/${Constants.SHELL_POWER_DATA_PATH}")

        if (Os.getuid() == 0) {
            shellPowerDataDir.let { shellPowerDataDir ->
                appPowerDataDir.let { appPowerDataDir ->
                    if (shellPowerDataDir.exists() && shellPowerDataDir.isDirectory) {
                        LoggerX.i(TAG, "init: root 模式迁移 shell 历史记录到 app 目录")
                        shellPowerDataDir.copyRecursively(
                            target = appPowerDataDir,
                            overwrite = true
                        )
                        shellPowerDataDir.deleteRecursively()
                        changeOwnerRecursively(appPowerDataDir, appInfo.uid)
                    }
                }
            }

            LoggerX.fixFileOwner = {
                changeOwnerRecursively(it, 2000)
            }
        }

        try {
            writer = if (Os.getuid() == 0)
                PowerRecordWriter(appPowerDataDir) { changeOwnerRecursively(it, appInfo.uid) }
            else
                PowerRecordWriter(shellPowerDataDir) {}
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
        LoggerX.i(TAG, 
            "init: Writer 初始化完成, targetDir=${if (Os.getuid() == 0) appPowerDataDir.absolutePath else shellPowerDataDir.absolutePath}"
        )

        monitor = Monitor(
            writer = writer,
            sendBinder = this::sendBinder,
            sampler
        )
        LoggerX.d(TAG, "init: Monitor 初始化完成")

        val serverSettings = if (Os.getuid() == 0) {
            LoggerX.i(TAG, "init: 通过 SharedPreferences XML 读取配置, path=${appConfigFile.absolutePath}")
            ConfigUtil.readServerSettingsByReading(appConfigFile)
        } else {
            LoggerX.i(TAG, "init: 通过 ConfigProvider 读取配置")
            ConfigUtil.getServerSettingsByContentProvider()
        }
        serverSettings?.let(::updateConfig) ?: LoggerX.w(TAG, "init: 未读取到配置, 使用当前默认值")

        monitor.start()
        LoggerX.i(TAG, "init: Monitor 已启动, 进入消息循环")

        Thread({
            try {
                val scanner = Scanner(System.`in`)
                var line: String
                while ((scanner.nextLine().also { line = it }) != null) {
                    if (line.trim { it <= ' ' } == "exit") {
                        stopService()
                    }
                }
                scanner.close()
            } catch (_: Throwable) {
            }
        }, "InputHandler").start()
        Looper.loop()
    }

    companion object {

        fun changeOwner(file: File, uid: Int) {
            try {
                Os.chown(file.absolutePath, uid, uid)
            } catch (e: ErrnoException) {
                LoggerX.e(TAG, 
                    "changeOwner: 设置文件(夹)所有者和组失败, path=${file.absolutePath}",
                    tr = e
                )
            }
        }

        fun changeOwnerRecursively(file: File, uid: Int) {
            changeOwner(file, uid)
            if (file.isDirectory()) {
                val files = file.listFiles()
                if (files != null) {
                    for (child in files) {
                        changeOwnerRecursively(child, uid)
                    }
                }
            }
        }
    }
}
