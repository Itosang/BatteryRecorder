package yangfentuozi.batteryrecorder.server

import android.os.FileObserver
import java.io.File

class AppSourceDirObserver(private val checkAppReinstall: (String) -> Boolean) :
    FileObserver(
        File(Global.appSourceDir), MODIFY or ATTRIB or CLOSE_WRITE
                or CLOSE_NOWRITE or OPEN or MOVED_FROM or MOVED_TO or DELETE or CREATE
                or DELETE_SELF or MOVE_SELF
    ) {

    override fun onEvent(event: Int, path: String?) {
        if (!checkAppReinstall("AppSourceDirObserver")) {
            stopWatching()
        }
    }
}
