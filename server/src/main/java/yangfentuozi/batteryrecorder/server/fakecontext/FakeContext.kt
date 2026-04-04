package yangfentuozi.batteryrecorder.server.fakecontext

import android.annotation.SuppressLint
import android.content.AttributionSource
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import android.system.Os
import android.util.Log
import androidx.annotation.Keep
import java.lang.reflect.Constructor

@Keep
class FakeContext : ContextWrapper(systemContext) {
    private val packageContext: Context = systemContext ?: this

    override fun getPackageName(): String {
        return PACKAGE_NAME
    }

    override fun getOpPackageName(): String {
        return PACKAGE_NAME
    }

    override fun getAttributionSource(): AttributionSource {
        val builder = AttributionSource.Builder(Os.getuid())
        builder.setPackageName(PACKAGE_NAME)
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

    @SuppressLint("DiscouragedPrivateApi")
    companion object {
        var PACKAGE_NAME: String = if (Os.getuid() == 0) "root" else "com.android.shell"

        private var ACTIVITY_THREAD_CLASS: Class<*>? = null
        private var ACTIVITY_THREAD: Any? = null

        init {
            try {
                ACTIVITY_THREAD_CLASS = Class.forName("android.app.ActivityThread")
                val activityThreadConstructor: Constructor<*>? =
                    ACTIVITY_THREAD_CLASS?.getDeclaredConstructor()
                activityThreadConstructor?.isAccessible = true
                ACTIVITY_THREAD = activityThreadConstructor?.newInstance()

                val sCurrentActivityThreadField =
                    ACTIVITY_THREAD_CLASS?.getDeclaredField("sCurrentActivityThread")
                sCurrentActivityThreadField?.isAccessible = true
                sCurrentActivityThreadField?.set(null, ACTIVITY_THREAD)
            } catch (e: Exception) {
                throw AssertionError(e)
            }
        }

        val systemContext: Context?
            get() {
                try {
                    val getSystemContextMethod =
                        ACTIVITY_THREAD_CLASS!!.getDeclaredMethod("getSystemContext")
                    return getSystemContextMethod.invoke(ACTIVITY_THREAD) as Context?
                } catch (throwable: Throwable) {
                    Log.e(
                        "FakeContext",
                        "Workarounds: Failed to get system context: ${throwable.stackTraceToString()}",
                        throwable
                    )
                    return null
                }
            }
    }
}
