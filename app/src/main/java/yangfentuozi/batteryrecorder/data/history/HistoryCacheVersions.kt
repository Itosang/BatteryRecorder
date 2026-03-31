package yangfentuozi.batteryrecorder.data.history

/**
 * 历史统计缓存共享版本号。
 *
 * 只要 AppStats / SceneStats / powerStats 的缓存格式或 cache key 组成发生变化，就统一提升这个版本。
 */
internal const val HISTORY_STATS_CACHE_VERSION = 13
