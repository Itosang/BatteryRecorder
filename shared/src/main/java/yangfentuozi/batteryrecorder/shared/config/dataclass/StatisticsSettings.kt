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
    /** 是否启用首页/应用预测使用的加权算法。 */
    val predWeightedAlgorithmEnabled: Boolean =
        SettingsConstants.predWeightedAlgorithmEnabled.def,
    /** 首页预测里当前文件可影响结果的最大比例，按百分比整数存储。 */
    val predWeightedAlgorithmAlphaMaxX100: Int =
        SettingsConstants.predWeightedAlgorithmAlphaMaxX100.def
)
