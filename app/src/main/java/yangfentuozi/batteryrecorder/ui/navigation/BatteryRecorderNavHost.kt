package yangfentuozi.batteryrecorder.ui.navigation

import android.net.Uri
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import yangfentuozi.batteryrecorder.shared.data.BatteryStatus
import yangfentuozi.batteryrecorder.shared.data.RecordsFile
import yangfentuozi.batteryrecorder.ui.screens.bh.BhRecordsScreen
import yangfentuozi.batteryrecorder.ui.screens.history.HistoryListScreen
import yangfentuozi.batteryrecorder.ui.screens.history.RecordDetailScreen
import yangfentuozi.batteryrecorder.ui.screens.home.HomeScreen
import yangfentuozi.batteryrecorder.ui.screens.prediction.PredictionDetailScreen
import yangfentuozi.batteryrecorder.ui.screens.settings.SettingsScreen
import yangfentuozi.batteryrecorder.ui.viewmodel.HistorySharedViewModel
import yangfentuozi.batteryrecorder.ui.viewmodel.MainViewModel
import yangfentuozi.batteryrecorder.ui.viewmodel.SettingsViewModel

private const val ANIMATION_DURATION = 300
private const val SCALE_FACTOR = 0.95f

private val animationSpec = tween<Float>(
    durationMillis = ANIMATION_DURATION,
    easing = FastOutSlowInEasing
)

private val defaultEnterTransition: EnterTransition = scaleIn(
    initialScale = SCALE_FACTOR,
    animationSpec = animationSpec
) + fadeIn(animationSpec = animationSpec)

private val defaultExitTransition: ExitTransition = fadeOut(animationSpec = animationSpec)

private val defaultPopEnterTransition: EnterTransition = fadeIn(animationSpec = animationSpec)

private val defaultPopExitTransition: ExitTransition = scaleOut(
    targetScale = SCALE_FACTOR,
    animationSpec = animationSpec
) + fadeOut(animationSpec = animationSpec)

@Composable
fun BatteryRecorderNavHost(
    navController: NavHostController,
    mainViewModel: MainViewModel,
    settingsViewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val historyViewModel: HistorySharedViewModel = viewModel()

    NavHost(
        navController = navController,
        startDestination = NavRoute.Home.route,
        modifier = modifier
    ) {
        composable(
            route = NavRoute.Home.route,
            exitTransition = {
                // 首页推入二级页时轻微左移，保留“主页面退后”的层级感。
                slideOutHorizontally(targetOffsetX = { fullWidth -> -fullWidth / 4 }) +
                        fadeOut()
            },
            popEnterTransition = {
                // 返回首页时反向平移，与前进动画保持镜像。
                slideInHorizontally(initialOffsetX = { fullWidth -> -fullWidth / 4 }) +
                        fadeIn()
            },

            enterTransition = { null },
//            exitTransition = { defaultExitTransition },
//            popEnterTransition = { defaultPopEnterTransition },
            popExitTransition = { null }
        ) {
            HomeScreen(
                viewModel = mainViewModel,
                settingsViewModel = settingsViewModel,
                onNavigateToSettings = {
                    navController.navigate(NavRoute.Settings.route)
                },
                onNavigateToHistoryList = { type ->
                    navController.navigate(NavRoute.HistoryList.createRoute(type.dataDirName))
                },
                onNavigateToRecordDetail = { type, name ->
                    navController.navigate(
                        NavRoute.RecordDetail.createRoute(
                            type.dataDirName,
                            Uri.encode(name)
                        )
                    )
                },
                onNavigateToPredictionDetail = {
                    // 预测详情页无参数，直接走固定 route。
                    navController.navigate(NavRoute.PredictionDetail.route)
                },
                onNavigateToBhRecords = {
                    navController.navigate(NavRoute.BhRecords.createRoute())
                }
            )
        }
        composable(
            route = NavRoute.BhRecords.route,
            arguments = listOf(
                navArgument("start") { type = NavType.LongType; defaultValue = 0L },
                navArgument("end") { type = NavType.LongType; defaultValue = 0L }
            ),
            enterTransition = { defaultEnterTransition },
            exitTransition = { defaultExitTransition },
            popEnterTransition = { defaultPopEnterTransition },
            popExitTransition = { defaultPopExitTransition }
        ) {
            BhRecordsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                initialStartMs = it.arguments?.getLong("start") ?: 0L,
                initialEndMs = it.arguments?.getLong("end") ?: 0L
            )
        }
        composable(
            route = NavRoute.Settings.route,
            enterTransition = { defaultEnterTransition },
            exitTransition = { defaultExitTransition },
            popEnterTransition = { defaultPopEnterTransition },
            popExitTransition = { defaultPopExitTransition }
        ) {
            SettingsScreen(
                settingsViewModel = settingsViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        composable(
            route = NavRoute.PredictionDetail.route,
            enterTransition = { defaultEnterTransition },
            exitTransition = { defaultExitTransition },
            popEnterTransition = { defaultPopEnterTransition },
            popExitTransition = { defaultPopExitTransition }
        ) {
            // 预测详情页依赖 SettingsViewModel 提供统计请求与展示配置。
            PredictionDetailScreen(
                settingsViewModel = settingsViewModel
            )
        }
        composable(
            route = NavRoute.HistoryList.route,
            arguments = listOf(navArgument("type") { type = NavType.StringType }),
            enterTransition = { defaultEnterTransition },
            exitTransition = {
                if (targetState.destination.route == NavRoute.RecordDetail.route) {
                    slideOutHorizontally(targetOffsetX = { fullWidth -> -fullWidth / 4 }) +
                            fadeOut()
                } else {
                    defaultExitTransition
                }
            },
            popEnterTransition = {
                if (initialState.destination.route == NavRoute.RecordDetail.route) {
                    slideInHorizontally(initialOffsetX = { fullWidth -> -fullWidth / 4 }) +
                            fadeIn()
                } else {
                    defaultPopEnterTransition
                }
            },
            popExitTransition = { defaultPopExitTransition }
        ) { backStackEntry ->
            val typeArg =
                backStackEntry.arguments?.getString("type") ?: BatteryStatus.Charging.dataDirName
            val batteryStatus = if (typeArg == BatteryStatus.Discharging.dataDirName) {
                BatteryStatus.Discharging
            } else {
                BatteryStatus.Charging
            }
            HistoryListScreen(
                batteryStatus = batteryStatus,
                viewModel = historyViewModel,
                onNavigateToRecordDetail = { type, name ->
                    navController.navigate(
                        NavRoute.RecordDetail.createRoute(type.dataDirName, Uri.encode(name))
                    )
                },
                settingsViewModel = settingsViewModel
            )
        }
        composable(
            route = NavRoute.RecordDetail.route,
            arguments = listOf(
                navArgument("type") { type = NavType.StringType },
                navArgument("name") { type = NavType.StringType }
            ),
            enterTransition = { defaultEnterTransition },
            exitTransition = { defaultExitTransition },
            popEnterTransition = { defaultPopEnterTransition },
            popExitTransition = { defaultPopExitTransition }
        ) { backStackEntry ->
            val typeArg =
                backStackEntry.arguments?.getString("type") ?: BatteryStatus.Charging.dataDirName
            val nameArg = backStackEntry.arguments?.getString("name") ?: ""
            val batteryStatus = if (typeArg == BatteryStatus.Discharging.dataDirName) {
                BatteryStatus.Discharging
            } else {
                BatteryStatus.Charging
            }
            RecordDetailScreen(
                recordsFile = RecordsFile(batteryStatus, Uri.decode(nameArg)),
                viewModel = historyViewModel,
                settingsViewModel = settingsViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToBhRecords = { startMs, endMs ->
                    navController.navigate(NavRoute.BhRecords.createRoute(startMs, endMs))
                }
            )
        }
    }
}
