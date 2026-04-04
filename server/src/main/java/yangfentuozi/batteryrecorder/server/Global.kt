package yangfentuozi.batteryrecorder.server

import android.system.ErrnoException
import android.system.Os
import yangfentuozi.batteryrecorder.shared.util.LoggerX
import java.io.File

object Global {

    private const val TAG = "Global"

    fun changeOwner(file: File, uid: Int) {
        try {
            Os.chown(file.absolutePath, uid, uid)
        } catch (e: ErrnoException) {
            LoggerX.e(
                TAG,
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

    var appSourceDir = ""
    var appUid = -1
}

