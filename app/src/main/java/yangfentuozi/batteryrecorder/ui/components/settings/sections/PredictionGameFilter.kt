package yangfentuozi.batteryrecorder.ui.components.settings.sections

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import yangfentuozi.batteryrecorder.ui.components.settings.SettingsItem
import java.io.File

private data class AppEntry(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    val isSystem: Boolean,
    val isGameDetected: Boolean
)

private val GAME_ENGINE_LIBS = setOf(
    "libunity.so", "libil2cpp.so", "libue4.so", "libue.so",
    "libunreal.so", "libcocos2djs.so", "libcocos2dcpp.so"
)

private val LANDSCAPE_ORIENTATIONS = setOf(0, 6, 8, 11)

// 预设误判 blacklist
private val PRESET_BLACKLIST = setOf(
    "com.tencent.mobileqq",
    "com.miui.securitycenter",
    "com.miui.securityadd",
    "tv.danmaku.bilibilihd",
    "com.qiyi.video.pad",
    "com.mihoyo.hyperion",
    "com.handsgo.jiakao.android",
    "com.heytap.speechassist",
    "com.miHoYo.cloudgames.ys",
    "com.android.launcher",
    "com.youdao.note",
    "com.sf.activity",
    "com.qiyi.video",
    "com.slanissue.apps.mobile.erge",
    "com.miui.hybrid",
    "com.nearme.gamecenter"
)

@Composable
fun PredictionGameFilterItem(
    gamePackages: Set<String>,
    gameBlacklist: Set<String>,
    onGamePackagesChange: (selected: Set<String>, detectedGamePkgs: Set<String>) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    val summary = if (gamePackages.isEmpty()) "未选择" else "已选 ${gamePackages.size} 个"

    SettingsItem(
        title = "排除高负载 App",
        summary = summary
    ) { showDialog = true }

    if (showDialog) {
        PredictionGameFilterDialog(
            currentSelection = gamePackages,
            userBlacklist = gameBlacklist,
            onDismiss = { showDialog = false },
            onConfirm = { selected, detected ->
                onGamePackagesChange(selected, detected)
                showDialog = false
            }
        )
    }
}

@Suppress("DEPRECATION")
private fun isGameApp(appInfo: ApplicationInfo, pm: PackageManager): Boolean {
    // 1. CATEGORY_GAME 或 FLAG_IS_GAME
    if (appInfo.category == ApplicationInfo.CATEGORY_GAME) return true
    if (appInfo.flags and 0x02000000 != 0) return true

    // 2. 游戏引擎 so
    val nativeDir = appInfo.nativeLibraryDir
    if (nativeDir != null) {
        val dir = File(nativeDir)
        if (dir.isDirectory) {
            val libs = dir.list()
            if (libs != null && libs.any { it in GAME_ENGINE_LIBS }) return true
        }
    }

    // 3. 启动 Activity 横屏取值
    runCatching {
        val launchIntent = pm.getLaunchIntentForPackage(appInfo.packageName)
        if (launchIntent?.component != null) {
            val actInfo = pm.getActivityInfo(launchIntent.component!!, 0)
            if (actInfo.screenOrientation in LANDSCAPE_ORIENTATIONS) return true
        }
    }

    return false
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PredictionGameFilterDialog(
    currentSelection: Set<String>,
    userBlacklist: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (selected: Set<String>, detectedGamePkgs: Set<String>) -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var showSystemApps by remember { mutableStateOf(false) }
    var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val selectedPackages =
        remember { mutableStateListOf<String>().apply { addAll(currentSelection) } }
    // 记录本次检测到的游戏包名，供 confirm 时回传
    var detectedGamePkgs by remember { mutableStateOf<Set<String>>(emptySet()) }

    val combinedBlacklist = remember(userBlacklist) { PRESET_BLACKLIST + userBlacklist }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val installed = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            installed.map { info ->
                AppEntry(
                    packageName = info.packageName,
                    label = info.loadLabel(pm).toString(),
                    icon = runCatching { info.loadIcon(pm) }.getOrNull(),
                    isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                    isGameDetected = isGameApp(info, pm)
                )
            }.sortedBy { it.label }
        }
        // 自动勾选：检测到的游戏，排除 blacklist，且不在当前已选中（避免重复添加）
        val detected = apps.filter { it.isGameDetected }.map { it.packageName }.toSet()
        detectedGamePkgs = detected
        val autoAdd = detected - combinedBlacklist - currentSelection
        for (pkg in autoAdd) {
            selectedPackages.add(pkg)
        }
        isLoading = false
    }

    val filteredApps by remember(searchQuery, showSystemApps, apps) {
        derivedStateOf {
            apps.filter { app ->
                (showSystemApps || !app.isSystem) &&
                        (searchQuery.isBlank() ||
                                app.label.contains(searchQuery, ignoreCase = true) ||
                                app.packageName.contains(searchQuery, ignoreCase = true))
            }.sortedByDescending { it.packageName in selectedPackages }
        }
    }

    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "排除高负载 App",
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = "已选 App 不参与首页续航预测统计",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("搜索应用名或包名") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = null)
                            }
                        }
                    },
                    singleLine = true
                )

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = showSystemApps,
                        onCheckedChange = { showSystemApps = it }
                    )
                    Text(
                        text = "显示系统应用",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.clickable { showSystemApps = !showSystemApps }
                    )
                }

                Spacer(Modifier.height(8.dp))

                if (isLoading) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        LoadingIndicator()
                    }
                } else if (filteredApps.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("未找到应用")
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(filteredApps, key = { it.packageName }) { app ->
                            val isSelected = app.packageName in selectedPackages
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (isSelected) selectedPackages.remove(app.packageName)
                                        else selectedPackages.add(app.packageName)
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (app.icon != null) {
                                    val bitmap = remember(app.packageName) {
                                        app.icon.toBitmap(128, 128).asImageBitmap()
                                    }
                                    Image(
                                        bitmap = bitmap,
                                        contentDescription = null,
                                        modifier = Modifier.size(40.dp)
                                    )
                                } else {
                                    Card(modifier = Modifier.size(40.dp)) {}
                                }

                                Spacer(Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = app.label,
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = app.packageName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = {
                                        if (isSelected) selectedPackages.remove(app.packageName)
                                        else selectedPackages.add(app.packageName)
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消")
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = {
                        onConfirm(
                            selectedPackages.toSet(),
                            detectedGamePkgs
                        )
                    }) {
                        Text("确定 (${selectedPackages.size})")
                    }
                }
            }
        }
    }
}
