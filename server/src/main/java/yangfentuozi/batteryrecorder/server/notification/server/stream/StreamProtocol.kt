package yangfentuozi.batteryrecorder.server.notification.server.stream

object StreamProtocol {
    // 4 bytes 标志位
    const val MAGIC: Int = 0x4C524543 // "LREC"
    const val FLAG_DATA = 1
    const val FLAG_STOP = 2
    const val FLAG_CANCEL = 3

    class StopException: Exception()
    class CancelNotificationException: Exception()
}