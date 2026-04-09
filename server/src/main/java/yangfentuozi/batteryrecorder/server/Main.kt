package yangfentuozi.batteryrecorder.server

import android.ddm.DdmHandleAppName
import android.system.Os
import androidx.annotation.Keep
import yangfentuozi.batteryrecorder.server.notification.server.NotificationServer
import yangfentuozi.batteryrecorder.shared.Constants
import yangfentuozi.batteryrecorder.shared.util.LoggerX
import java.io.File
import java.io.IOException


@Keep
object Main {

    private const val TAG = "Main"
    private const val SERVER_PROCESS_NAME = "batteryrecorder_server"
    private val SERVER_CGROUP_DIRS = listOf(
        "/acct",
        "/dev/cg2_bpf",
        "/sys/fs/cgroup",
        "/dev/memcg/apps"
    )

    @Keep
    @JvmStatic
    fun main(args: Array<String>) {
        val isNotificationServer = args.isNotEmpty() && args[0] == "--notification-server"
        DdmHandleAppName.setAppName(SERVER_PROCESS_NAME, 0)

        // 配置 LoggerX
        LoggerX.logDirPath = "${Constants.SHELL_DATA_DIR_PATH}/${Constants.SHELL_LOG_DIR_PATH}"
        if (isNotificationServer) LoggerX.suffix = "-notification-server"
        LoggerX.d(
            TAG,
            "main: LoggerX 配置完成, dir=${LoggerX.logDirPath}, suffix=${LoggerX.suffix}"
        )

        if (!isNotificationServer) {
            switchCgroupIfNeeded()
        }

        // 设置OOM保活
        setSelfOomScoreAdj()

        if (isNotificationServer) {
            LoggerX.i(TAG, "main: 初始化 NotificationServer")
            NotificationServer()
        } else {
            LoggerX.i(TAG, "main: 初始化 Server")
            Server()
        }
    }

    private fun setSelfOomScoreAdj() {
        val oomScoreAdjFile = File("/proc/self/oom_score_adj")
        val oomScoreAdjValue = -1000
        try {
            oomScoreAdjFile.writeText("$oomScoreAdjValue\n")
            val actualValue: String = oomScoreAdjFile.readText().trim()
            if (oomScoreAdjValue.toString() != actualValue) {
                LoggerX.e(TAG, 
                    "setSelfOomScoreAdj: 设置 oom_score_adj 失败, expected=$oomScoreAdjValue actual=$actualValue"
                )
                return
            }
            LoggerX.i(TAG, "setSelfOomScoreAdj: 设置 oom_score_adj 成功, actual=$oomScoreAdjValue")
        } catch (e: IOException) {
            LoggerX.e(TAG, "setSelfOomScoreAdj: 设置 oom_score_adj 失败", tr = e)
        } catch (e: RuntimeException) {
            LoggerX.e(TAG, "setSelfOomScoreAdj: 设置 oom_score_adj 失败", tr = e)
        }
    }

    private fun switchCgroupIfNeeded() {
        val selfPid = Os.getpid()
        for (dir in SERVER_CGROUP_DIRS) {
            val procsFile = File(dir, "cgroup.procs")
            if (!procsFile.exists()) continue

            try {
                procsFile.appendText("$selfPid\n")
                LoggerX.i(TAG, "switchCgroupIfNeeded: 切换 cgroup 成功, path=${procsFile.path}")
                return
            } catch (e: Exception) {
                LoggerX.w(
                    TAG,
                    "switchCgroupIfNeeded: 切换 cgroup 失败, path=${procsFile.path}",
                    tr = e
                )
            }
        }

        LoggerX.w(TAG, "switchCgroupIfNeeded: 未找到可用 cgroup")
    }
}
