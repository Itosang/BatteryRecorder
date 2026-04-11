package yangfentuozi.batteryrecorder.server.stream

object StreamProtocol {
    // 4 bytes 标志位
    const val MAGIC: Int = 0x4C524544
    const val VERSION: Int = 1
    const val FLAG_DATA = 1
    
}