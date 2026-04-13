package yangfentuozi.batteryrecorder.shared.config

import android.content.SharedPreferences
import yangfentuozi.batteryrecorder.shared.config.dataclass.ServerSettings

private interface ServerSettingsValueSource {
    fun boolean(key: String): Boolean?
    fun int(key: String): Int?
    fun long(key: String): Long?
}

private class SharedPreferencesServerSettingsSource(
    private val prefs: SharedPreferences
) : ServerSettingsValueSource {
    override fun boolean(key: String): Boolean? =
        if (prefs.contains(key)) prefs.getBoolean(key, false) else null

    override fun int(key: String): Int? =
        if (prefs.contains(key)) prefs.getInt(key, 0) else null

    override fun long(key: String): Long? =
        if (prefs.contains(key)) prefs.getLong(key, 0L) else null
}

private class StringValueMapServerSettingsSource(
    private val values: Map<String, String>
) : ServerSettingsValueSource {
    override fun boolean(key: String): Boolean? =
        values[key]?.toBooleanStrictOrNull()

    override fun int(key: String): Int? =
        values[key]?.toIntOrNull()

    override fun long(key: String): Long? =
        values[key]?.toLongOrNull()
}

/**
 * `ServerSettings` 的唯一字段映射入口。
 *
 * `SharedPreferences`、XML 等来源只负责提供原始键值，
 * 具体如何组装为 `ServerSettings` 与如何写回持久化层，都统一收敛到这里。
 */
object ServerSettingsCodec {
    /**
     * 从 SharedPreferences 读取服务端配置。
     *
     * @param prefs 已定位好的设置存储。
     * @return 组装后的服务端配置；缺字段时回退默认值。
     */
    fun readFromPreferences(prefs: SharedPreferences): ServerSettings =
        readFromSource(SharedPreferencesServerSettingsSource(prefs))

    /**
     * 从字符串键值对读取服务端配置。
     *
     * @param values 原始字符串键值表；通常来自 SharedPreferences XML。
     * @return 组装后的服务端配置；缺字段或字段解析失败时回退默认值。
     */
    fun readFromStringValues(values: Map<String, String>): ServerSettings =
        readFromSource(StringValueMapServerSettingsSource(values))

    /**
     * 将服务端配置写入 SharedPreferences.Editor。
     *
     * @param editor 目标 Editor。
     * @param settings 需要落盘的服务端配置。
     * @return 无返回值。
     */
    fun writeToPreferences(editor: SharedPreferences.Editor, settings: ServerSettings) {
        editor.putBoolean(SettingsConstants.notificationEnabled.key, settings.notificationEnabled)
        editor.putBoolean(SettingsConstants.dualCellEnabled.key, settings.dualCellEnabled)
        editor.putInt(SettingsConstants.calibrationValue.key, settings.calibrationValue)
        editor.putLong(SettingsConstants.recordIntervalMs.key, settings.recordIntervalMs)
        editor.putInt(SettingsConstants.batchSize.key, settings.batchSize)
        editor.putLong(SettingsConstants.writeLatencyMs.key, settings.writeLatencyMs)
        editor.putBoolean(
            SettingsConstants.screenOffRecordEnabled.key,
            settings.screenOffRecordEnabled
        )
        editor.putLong(SettingsConstants.segmentDurationMin.key, settings.segmentDurationMin)
        editor.putLong(SettingsConstants.logMaxHistoryDays.key, settings.maxHistoryDays)
        editor.putInt(
            SettingsConstants.logLevel.key,
            SettingsConstants.logLevel.converter.toValue(settings.logLevel)
        )
        editor.putBoolean(
            SettingsConstants.alwaysPollingScreenStatusEnabled.key,
            settings.alwaysPollingScreenStatusEnabled
        )
    }

    private fun readFromSource(source: ServerSettingsValueSource): ServerSettings =
        ServerSettings(
            notificationEnabled =
                source.boolean(SettingsConstants.notificationEnabled.key)
                    ?: SettingsConstants.notificationEnabled.def,
            dualCellEnabled =
                source.boolean(SettingsConstants.dualCellEnabled.key)
                    ?: SettingsConstants.dualCellEnabled.def,
            calibrationValue =
                source.int(SettingsConstants.calibrationValue.key)
                    ?: SettingsConstants.calibrationValue.def,
            recordIntervalMs =
                source.long(SettingsConstants.recordIntervalMs.key)
                    ?: SettingsConstants.recordIntervalMs.def,
            batchSize =
                source.int(SettingsConstants.batchSize.key)
                    ?: SettingsConstants.batchSize.def,
            writeLatencyMs =
                source.long(SettingsConstants.writeLatencyMs.key)
                    ?: SettingsConstants.writeLatencyMs.def,
            screenOffRecordEnabled =
                source.boolean(SettingsConstants.screenOffRecordEnabled.key)
                    ?: SettingsConstants.screenOffRecordEnabled.def,
            segmentDurationMin =
                source.long(SettingsConstants.segmentDurationMin.key)
                    ?: SettingsConstants.segmentDurationMin.def,
            maxHistoryDays =
                source.long(SettingsConstants.logMaxHistoryDays.key)
                    ?: SettingsConstants.logMaxHistoryDays.def,
            logLevel =
                source.int(SettingsConstants.logLevel.key)
                    ?.let(SettingsConstants.logLevel.converter::fromValue)
                    ?: SettingsConstants.logLevel.def,
            alwaysPollingScreenStatusEnabled =
                source.boolean(SettingsConstants.alwaysPollingScreenStatusEnabled.key)
                    ?: SettingsConstants.alwaysPollingScreenStatusEnabled.def
        )
}
