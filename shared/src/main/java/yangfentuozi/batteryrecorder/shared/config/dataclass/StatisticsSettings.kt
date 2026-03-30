package yangfentuozi.batteryrecorder.shared.config.dataclass

import yangfentuozi.batteryrecorder.shared.config.SettingsConstants

/** 历史统计与预测设置。 */
data class StatisticsSettings(
    val gamePackages: Set<String> = emptySet(),
    val gameBlacklist: Set<String> = emptySet(),
    val sceneStatsRecentFileCount: Int = SettingsConstants.sceneStatsRecentFileCount.def,
    val predCurrentSessionWeightEnabled: Boolean =
        SettingsConstants.predCurrentSessionWeightEnabled.def,
    val predCurrentSessionWeightMaxX100: Int =
        SettingsConstants.predCurrentSessionWeightMaxX100.def,
    val predCurrentSessionWeightHalfLifeMin: Long =
        SettingsConstants.predCurrentSessionWeightHalfLifeMin.def
)
