package yangfentuozi.batteryrecorder.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import yangfentuozi.batteryrecorder.BuildConfig
import yangfentuozi.batteryrecorder.shared.util.LoggerX
import yangfentuozi.batteryrecorder.ui.dialog.home.DocsIntroDialog
import yangfentuozi.batteryrecorder.ui.dialog.home.UpdateDialog
import yangfentuozi.batteryrecorder.ui.navigation.BatteryRecorderNavHost
import yangfentuozi.batteryrecorder.ui.viewmodel.MainViewModel
import yangfentuozi.batteryrecorder.ui.viewmodel.SettingsViewModel
import yangfentuozi.batteryrecorder.utils.AppUpdate
import yangfentuozi.batteryrecorder.utils.UpdateUtils

private const val TAG = "BatteryRecorderApp"

private const val STARTUP_PROMPT_PREFS = "startup_prompt"
private const val KEY_DOCS_INTRO_SHOWN = "docs_intro_shown"

@Composable
fun BatteryRecorderApp(
    mainViewModel: MainViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val appSettings by settingsViewModel.appSettings.collectAsState()
    val initialized by settingsViewModel.initialized.collectAsState()
    var hasCheckedUpdateOnStartup by rememberSaveable { mutableStateOf(false) }
    var pendingUpdate by remember { mutableStateOf<AppUpdate?>(null) }
    var showDocsIntro by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(settingsViewModel, context) {
        settingsViewModel.init(context)
        val startupPrefs = context.getSharedPreferences(STARTUP_PROMPT_PREFS, Context.MODE_PRIVATE)
        val docsIntroShown = startupPrefs.getBoolean(KEY_DOCS_INTRO_SHOWN, false)
        if (!docsIntroShown) {
            LoggerX.v(TAG, "首次进入，展示使用文档引导弹窗")
            showDocsIntro = true
        }

    }

    LaunchedEffect(initialized, appSettings.checkUpdateOnStartup, hasCheckedUpdateOnStartup, context) {
        if (!initialized || hasCheckedUpdateOnStartup) return@LaunchedEffect
        hasCheckedUpdateOnStartup = true
        if (!appSettings.checkUpdateOnStartup) {
            LoggerX.d(TAG, "启动更新检测已关闭，跳过检查")
            return@LaunchedEffect
        }
        LoggerX.d(TAG, "启动更新检测开始，请求最新 release")

        val update = UpdateUtils.fetchUpdate() ?: run {
            LoggerX.w(TAG, "启动更新检测失败，未获取到可用更新信息")
            Toast.makeText(context, "检测更新失败", Toast.LENGTH_SHORT).show()
            return@LaunchedEffect
        }
        if (BuildConfig.VERSION_CODE >= update.versionCode) {
            LoggerX.i(
                TAG,
                "启动更新检测完成，无需更新，remote=${update.versionCode} local=${BuildConfig.VERSION_CODE}"
            )
            return@LaunchedEffect
        }

        LoggerX.i(
            TAG,
            "启动更新检测完成，发现新版本，remote=${update.versionCode} local=${BuildConfig.VERSION_CODE}"
        )
        pendingUpdate = update
    }

    val navController = rememberNavController()
    BatteryRecorderNavHost(
        navController = navController,
        mainViewModel = mainViewModel,
        settingsViewModel = settingsViewModel
    )

    pendingUpdate?.let { update ->
        if (showDocsIntro) return@let
        UpdateDialog(
            update = update,
            onDismiss = { pendingUpdate = null }
        )
    }

    if (showDocsIntro) {
        DocsIntroDialog(
            onOpenDocs = {
                context.getSharedPreferences(STARTUP_PROMPT_PREFS, Context.MODE_PRIVATE)
                    .edit {
                        putBoolean(KEY_DOCS_INTRO_SHOWN, true)
                    }
                LoggerX.v(TAG, "已打开使用文档，关闭首次引导弹窗")
                showDocsIntro = false
            }
        )
    }
}
