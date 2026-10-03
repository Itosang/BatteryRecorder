package yangfentuozi.batteryrecorder.data.bh

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import yangfentuozi.batteryrecorder.shared.config.SharedSettings

/**
 * 「查看厂商系统原生日志」功能可用性检测。
 *
 * 功能入口同时满足以下条件时开放：
 * 1. 系统为小米澎湃OS（读取 HyperOS / MIUI 系统属性判定）；
 * 2. battery-history 数据源目录中检测到 .bh 文件；
 * 3. 设置开关（[yangfentuozi.batteryrecorder.shared.config.SettingsConstants.vendorSystemLogEnabled]，默认开启）未被关闭。
 *
 * 检测结果在进程内缓存；数据源检测可能触发 root 授权（su），统一在 IO 线程执行。
 */
object BhFeature {

    @Volatile private var hyperOsCache: Boolean? = null
    @Volatile private var dataCache: Boolean? = null

    /** 读取系统属性（SystemProperties 为隐藏 API，反射调用；失败返回空串）。 */
    private fun getSystemProperty(key: String): String = try {
        Class.forName("android.os.SystemProperties")
            .getMethod("get", String::class.java)
            .invoke(null, key) as? String ?: ""
    } catch (_: Throwable) {
        ""
    }

    /** 是否为小米澎湃OS（HyperOS 系统属性存在；兼容回退 MIUI 属性）。 */
    fun isHyperOs(): Boolean {
        hyperOsCache?.let { return it }
        val versionName = getSystemProperty("ro.mi.os.version.name").ifBlank {
            getSystemProperty("ro.miui.ui.version.name")
        }
        return versionName.isNotBlank().also { hyperOsCache = it }
    }

    /** battery-history 数据源中是否检测到 .bh 文件（结果缓存；[force] 时重新检测）。 */
    fun hasBhFiles(context: Context, force: Boolean = false): Boolean {
        if (!force) {
            dataCache?.let { return it }
        }
        val dir = BhSyncManager.resolveSourceDir(context)
        if (dir == null) {
            dataCache = false
            return false
        }
        val entries = BhRootSource.listEntries(dir)
        if (entries == null) {
            dataCache = false
            return false
        }
        return entries.isNotEmpty().also { dataCache = it }
    }

    /** 设置开关是否开启（仅读设置，不做数据源检测）。 */
    fun isEnabledBySetting(context: Context): Boolean =
        SharedSettings.readAppSettings(context).vendorSystemLogEnabled

    /**
     * 功能当前是否可用：设置开启 + 澎湃OS + 检测到数据文件。
     * 供首页入口、记录详情等处门控使用。
     */
    suspend fun isEnabled(context: Context, force: Boolean = false): Boolean =
        withContext(Dispatchers.IO) {
            if (!isEnabledBySetting(context)) return@withContext false
            isHyperOs() && hasBhFiles(context, force)
        }

    /**
     * 仅数据可用性检测（澎湃OS + 数据文件，不含设置开关）。
     * 供设置页判断是否展示「查看厂商系统原生日志」选项。
     */
    suspend fun isDataAvailable(context: Context, force: Boolean = false): Boolean =
        withContext(Dispatchers.IO) {
            isHyperOs() && hasBhFiles(context, force)
        }
}
