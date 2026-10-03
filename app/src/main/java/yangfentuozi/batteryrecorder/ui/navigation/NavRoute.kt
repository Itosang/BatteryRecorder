package yangfentuozi.batteryrecorder.ui.navigation

/**
 * 应用内导航路由定义。
 *
 * 统一集中在这里，避免字符串散落在各 Screen / NavHost 调用点。
 */
sealed class NavRoute(val route: String) {
    object Home : NavRoute("home")
    object Settings : NavRoute("settings")

    // 应用预测详情页无入参，直接复用固定 route。
    object PredictionDetail : NavRoute("prediction")
    object HistoryList : NavRoute("history/{type}") {
        fun createRoute(type: String): String = "history/$type"
    }

    object RecordDetail : NavRoute("record/{type}/{name}") {
        fun createRoute(type: String, name: String): String = "record/$type/$name"
    }

    /** 全量电池事件记录（可选带入时间窗，来自充放电记录详情）。 */
    object BhRecords : NavRoute("bh_records?start={start}&end={end}") {
        fun createRoute(startMs: Long = 0L, endMs: Long = 0L): String =
            "bh_records?start=$startMs&end=$endMs"
    }
}
