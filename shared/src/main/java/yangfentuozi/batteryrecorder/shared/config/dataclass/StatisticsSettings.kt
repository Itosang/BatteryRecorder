package yangfentuozi.batteryrecorder.shared.config.dataclass

import yangfentuozi.batteryrecorder.shared.config.SettingsConstants

/**
 * 历史统计与续航预测设置。
 *
 * 这组字段直接供统计、扫描、预测链路消费，不再额外翻译成通用请求对象。
 */
data class StatisticsSettings(
    /** 用户确认属于游戏场景的包名集合。 */
    val gamePackages: Set<String> = emptySet(),
    /** 用户明确排除的高负载包名集合。 */
    val gameBlacklist: Set<String> = emptySet(),
    /** 统计与预测使用的最近放电文件数量。 */
    val sceneStatsRecentFileCount: Int = SettingsConstants.sceneStatsRecentFileCount.def,
    /** 是否启用“当次记录加权”。 */
    val predCurrentSessionWeightEnabled: Boolean =
        SettingsConstants.predCurrentSessionWeightEnabled.def,
    /** 当次记录加权的最大倍率，使用 x100 整数存储。 */
    val predCurrentSessionWeightMaxX100: Int =
        SettingsConstants.predCurrentSessionWeightMaxX100.def,
    /** 当次记录加权的半衰期，单位为分钟。 */
    val predCurrentSessionWeightHalfLifeMin: Long =
        SettingsConstants.predCurrentSessionWeightHalfLifeMin.def
)
