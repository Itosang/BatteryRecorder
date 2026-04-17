package yangfentuozi.batteryrecorder.data.history

import java.io.File

private const val CACHE_VERSION_DIR = "v$HISTORY_STATS_CACHE_VERSION"

internal fun getAppStatsCacheFile(cacheRoot: File, key: String): File =
    File(File(cacheRoot, "app_stats/$CACHE_VERSION_DIR"), "$key.cache")

internal fun getSceneStatsCacheFile(cacheRoot: File, key: String): File =
    File(File(cacheRoot, "scene_stats/$CACHE_VERSION_DIR"), "$key.cache")

internal fun getPowerStatsCacheFile(cacheRoot: File, logicalRecordName: String): File =
    File(File(cacheRoot, "power_stats/$CACHE_VERSION_DIR"), "$logicalRecordName.cache")
