package yangfentuozi.batteryrecorder.server.fakecontext

import android.app.ActivityThread
import android.app.IActivityManager
import android.content.AttributionSource
import android.content.ContentResolver
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import android.os.Binder
import android.os.ServiceManager
import android.system.Os
import androidx.annotation.Keep

@Keep
class FakeContext : ContextWrapper(systemContext) {
    private val packageContext: Context = systemContext

    override fun getPackageName(): String {
        return if (Os.getuid() == 0) "root" else "com.android.shell"
    }

    override fun getOpPackageName(): String {
        return packageName
    }

    override fun getAttributionSource(): AttributionSource {
        val builder = AttributionSource.Builder(Os.getuid())
        builder.setPackageName(packageName)
        return builder.build()
    }

    override fun getDeviceId(): Int {
        return 0
    }

    override fun getApplicationContext(): Context {
        return this
    }

    @Keep
    fun createApplicationContext(
        application: ApplicationInfo,
        flags: Int
    ): Context {
        return packageContext
    }

    override fun createPackageContext(
        packageName: String,
        flags: Int
    ): Context {
        return packageContext
    }

    override fun getContentResolver(): ContentResolver {
        return externalContentResolver
    }

    companion object {
        private val providerToken = Binder()

        private val activityManager: IActivityManager by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            IActivityManager.Stub.asInterface(ServiceManager.getService("activity"))
                ?: throw IllegalStateException("activity 服务未就绪")
        }

        val systemContext: Context by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            val activityThread = ActivityThread.currentActivityThread()
                ?: ActivityThread.systemMain()
                ?: throw IllegalStateException("获取 system ActivityThread 失败")
            activityThread.systemContext as? Context
                ?: throw IllegalStateException("获取 systemContext 失败")
        }

        private val externalContentResolver by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            ExternalProviderResolver(systemContext, activityManager, providerToken)
        }
    }
}
