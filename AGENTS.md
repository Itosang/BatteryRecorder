本文件用于指导代码代理在本仓库中进行检索、设计、修改与交付；当前 `CLAUDE.md` 通过 `@AGENTS.md` 直接复用本文件内容。

## 文档定位

- 这是**仓库工作说明**，不是面向最终用户的使用文档
- 目标是让代理快速理解当前项目结构、关键链路与修改约束
- 当项目新增关键模块、入口、缓存格式或数据链路时，必须同步更新本文件
- 文档应优先记录**真实现状**；若代码实现与理想约束不一致，必须如实写明，不得继续保留过时描述

## 项目概述

BatteryRecorder 是一个 Android 电池功率记录 App。

- App 进程负责 UI、配置、历史数据展示、日志导出、续航预测与 IPC 客户端
- Server 进程以 root/shell 权限运行，低开销采集电池数据并写入记录文件
- 采样优先走 JNI sysfs 读取；不可用时回退到 dumpsys/batteryproperties 方案
- 历史数据支持图表查看、应用维度统计、场景维度统计、记录详情统计与续航预测
- 应用启动阶段还负责首次文档引导与更新检查；首页同时提供 Root/ADB 启动入口与日志导出

## 构建约束

不要自主构建。修改完成后直接告知用户测试。

- 签名配置从根目录 `signing.properties` 读取；缺失时回退到 debug keystore
- APK 输出文件名格式为：`batteryrecorder-v{versionName}-{variant}.apk`
- APK 默认输出到：`app/build/outputs/apk/{debug,release}/`
- Release APK 会复制到根目录 `out/apk/`
- Release mapping 会复制到根目录 `out/mapping/`
- `versionCode` 由 git commit 数量动态生成，需要完整 git history

## 技术栈

| 项     | 值                                                |
|-------|--------------------------------------------------|
| 语言    | Kotlin（主）, Java（hiddenapi）, C/C++（JNI）           |
| UI    | Jetpack Compose + Material 3，无 XML Layout        |
| 架构    | MVVM（ViewModel + StateFlow + Compose）            |
| 构建    | Gradle 8.13 Kotlin DSL, AGP 8.13.2, Kotlin 2.3.0 |
| JDK   | 21（app/server/shared）, 11（hiddenapi）             |
| NDK   | 29.0.14206865                                    |
| CMake | 3.22.1                                           |
| SDK   | minSdk 31, targetSdk/compileSdk 36               |
| 依赖管理  | Version Catalog（`gradle/libs.versions.toml`）     |

## 模块结构与依赖

```text
:app                 -> 主应用，UI + IPC 客户端 + 历史/预测 + 日志导出
:server              -> 独立 Server 进程，采样 + 写文件 + AIDL 服务端
:shared              -> 公共配置、数据模型、文件解析、同步协议、日志基础设施
:hiddenapi:stub      -> Hidden API stub 声明（compileOnly）
:hiddenapi:compat    -> Hidden API 兼容封装
```

依赖方向：

- `app -> shared, server`
- `server -> shared, hiddenapi:compat`
- `shared -> hiddenapi:compat`
- `hiddenapi:compat -> hiddenapi:stub (compileOnly)`

## 核心架构

### 双进程 IPC 模型

```text
App 进程 (UI)  <->  AIDL Binder  <->  Server 进程 (root/shell)
```

- Server 不是 Android Service，而是独立进程内直接创建 `Server()` 并进入 `Looper.loop()`
- 进程入口为 `server/.../Main.kt`
- `Main.kt` 负责设置进程名与 `oom_score_adj`，随后启动 `Server`
- Server 启动后通过 `ActivityManagerCompat.contentProviderCall()` 将 Binder 推送给 App 的 `BinderProvider`
- App 通过 `IService` AIDL 与 Server 通信，核心能力包括停止服务、注册监听、更新配置、同步数据
- Server 读取配置时：
  - root 权限：直接读 App 的 SharedPreferences XML
  - shell 权限：通过 `ConfigProvider`

### 数据采样链路

```text
Sampler -> SysfsSampler / DumpsysSampler -> Monitor -> PowerRecordWriter -> CSV
```

- `Sampler` 是采样抽象
- `SysfsSampler` 负责加载 JNI 动态库并读取 `/sys/class/power_supply/battery/`
- `DumpsysSampler` 是 sysfs/JNI 不可用时的回退实现，JNI 侧同时依赖 `dump_parser.cpp`
- `Monitor` 按配置间隔循环采样，并监听前台应用与屏幕状态
- `PowerRecordWriter` 分充电/放电两路写入 CSV，支持批量缓冲、延迟 flush、分段落盘
- 当前记录格式：
  `timestamp,power,packageName,capacity,isDisplayOn,status,temp,voltage,current`

### 数据同步链路

- Server 以 shell 权限运行时，记录文件落在 `com.android.shell` 数据目录
- App 通过 `sync()` AIDL 拿到 `ParcelFileDescriptor`
- 传输协议由 `PfdFileSender` / `PfdFileReceiver` / `SyncConstants` 实现
- 同步结束后，已传输的 shell 侧旧文件会按当前文件排除规则清理

### 首页统计与预测链路

- `BatteryRecorderApp` 创建并下传 `MainViewModel`、`SettingsViewModel`
- `MainViewModel` 负责首页汇总统计、当前记录切段等待态与实时曲线缓冲
- 首页当前记录链路已收敛到 `MainViewModel`，`HomeScreen` 不再局部创建 `LiveRecordViewModel`
- `CurrentRecordCard` 直接消费 `MainViewModel.currentRecordUiState`
- 首页的“续航预测卡片”和“场景统计卡片”都定义在 `ui/components/home/PredictionCard.kt`
- 首页统计刷新参数统一来自 `SettingsViewModel.statisticsSettings`，服务端采样间隔单独来自 `SettingsViewModel.recordIntervalMs`
- 首页支持 Root 启动卡片、ADB 引导、日志导出、关于弹窗、首次文档引导与启动更新检查

### UI 沉浸与 Insets 链路

- `BaseActivity` 统一调用 `enableEdgeToEdge(...)`，并关闭 `window.isNavigationBarContrastEnforced`
- 页面级 `Scaffold` 统一通过 `ui/EdgeToEdgeInsets.kt` 中的 `batteryRecorderScaffoldInsets()` 只消费顶部与水平安全区
- 底部导航手势区不再交给 `Scaffold` 一刀切处理，而是由页面内容层按需使用 `navigationBarBottomPadding()` 单独追加
- 当前首页、设置页、历史列表页、记录详情页、预测详情页都已接入上述规则
- `RecordDetailScreen` 的沉浸结构与其他页面不同：外层 `Box` 负责铺满沉浸背景，内层滚动 `Column` 只按内容高度展开，避免 `fillMaxSize()` 的滚动列把手势区表现成常驻大空白

### 记录详情链路

- `HistoryViewModel` 在 `BatteryRecorderNavHost` 中创建单个共享实例，供 `HistoryListScreen` 与 `RecordDetailScreen` 共用
- `HistoryViewModel` 统一产出 `RecordDetailChartUiState`
- `RecordDetailScreen` 直接消费 `recordChartUiState`
- `RecordDetailPowerStatsComputer` 负责记录详情页功耗统计
- 详情页图表偏好通过独立的 `record_detail_chart` SharedPreferences 持久化，不进入业务配置
- 详情页同时支持：
  - 原始/趋势/隐藏三种功率曲线模式
  - 电量/温度/应用图标显隐切换
  - 图表说明弹窗
  - 单记录导出
  - 单记录删除
  - 应用维度详情统计

### 应用预测详情链路

- `PredictionDetailScreen` 局部创建 `PredictionDetailViewModel`
- `PredictionDetailViewModel` 负责同步记录、聚合应用维度预测并产出 `PredictionDetailUiState`
- `PredictionDetailScreen` 结合 `SettingsViewModel` 的展示设置处理功率符号/格式化，并按需加载应用图标

## 关键数据与展示约定

### 功率转换

- 所有原始功率值转瓦特都必须通过 `FormatUtil.computePowerW()`
- 禁止在 Screen、Chart、Repository 中重复拼装换算公式

### 放电显示正值

- 只允许在 ViewModel 或明确的展示映射层统一处理
- Compose UI 不应各自散落处理正负转换

### 记录详情图表

- `RecordDetailChartPoint` 同时承载原始点与趋势点字段
- 趋势点必须基于**过滤后的展示点**重新分桶
- 分桶结果取中位数作为趋势功率值
- 平滑绘制是图表层职责，不在仓库层落地“平滑后数据”

### 记录详情统计

- `RecordAppStatsComputer` 负责单条放电记录内的应用维度统计
- `RecordDetailPowerStatsComputer` 负责记录详情页平均功耗统计
- 记录详情缓存命中时，必须校验缓存内 `sourceLastModified` 与源文件 `lastModified()` 一致

### 图标缓存

- `AppIconMemoryCache` 仅按包名与尺寸缓存已请求图标
- 只按当前视口需要的包名加载
- 禁止恢复启动期预热或全量历史扫描

## 续航预测约定

- 预测算法：能量比例法，`k = ΔSOC_total / E_total`
- 不依赖电池容量 Wh
- 场景分类：
  - 息屏
  - 亮屏日常
  - 游戏
- 三类场景均参与 `E_total` 与 `ΔSOC_total` 统计
- 预测层按功耗幅值计算，不因放电原始值为负而忽略有效样本

### 当次记录加权

- 只对**当前放电文件**启用指数衰减加权
- 其余历史文件固定 `w = 1`
- 加权同时作用于：
  - `energyRawMs`
  - `dt`
  - `capDelta`
- 启用门槛：
  - 记录时长不少于 10 分钟
  - 掉电不少于 2%
- `endTs` 取自当前文件最后一条有效记录时间戳，不使用 `System.currentTimeMillis()`
- 当前设置项已从旧的“当次记录加权 + 最大倍率/半衰期”切换为：
  - `pred_weighted_algorithm_enabled`
  - `pred_weighted_algorithm_alpha_max_x100`
- `pred_weighted_algorithm_enabled` 默认开启
- 读取侧已移除旧 key `pred_current_session_weight_enabled` 的兼容分支

### 预测失败原因链路

- `DischargeRecordScanner` 负责区分文件级过滤原因
- `SceneStatsComputer` 透传 `insufficientReason`
- `MainViewModel` 与 `BatteryPredictor` 继续向上透传
- 首页 `PredictionCard` 直接展示具体原因

### 缓存约定

- `AppStatsComputer`、`SceneStatsComputer` 与记录详情 `power_stats` 共用 `HistoryCacheVersions.HISTORY_STATS_CACHE_VERSION`
- 任一历史统计缓存格式或 key 组成变化时，统一提升该版本
- 缓存命名统一通过 `HistoryCacheNaming.kt`

## 目录索引

### 根模块

```text
app/
server/
shared/
hiddenapi/
docs/
```

### App 模块

```text
app/src/main/java/yangfentuozi/batteryrecorder/
├── data/
│   ├── history/
│   │   ├── HistoryRepository.kt
│   │   ├── BatteryPredictor.kt
│   │   ├── DischargeRecordScanner.kt
│   │   ├── SceneStatsComputer.kt
│   │   ├── AppStatsComputer.kt
│   │   ├── RecordAppStatsComputer.kt
│   │   ├── RecordDetailPowerStatsComputer.kt
│   │   ├── HistoryCacheNaming.kt
│   │   ├── HistoryCacheVersions.kt
│   │   ├── StatisticsRequest.kt
│   │   └── SyncUtil.kt
│   ├── log/
│   │   └── LogRepository.kt
│   ├── model/
│   └── ...
├── ipc/
├── startup/
├── ui/
│   ├── BatteryRecorderApp.kt
│   ├── MainActivity.kt
│   ├── BaseActivity.kt
│   ├── EdgeToEdgeInsets.kt
│   ├── navigation/
│   ├── screens/
│   │   ├── home/HomeScreen.kt
│   │   ├── settings/SettingsScreen.kt
│   │   ├── prediction/PredictionDetailScreen.kt
│   │   └── history/
│   │       ├── HistoryListScreen.kt
│   │       └── RecordDetailScreen.kt
│   ├── components/
│   │   ├── charts/PowerCapacityChart.kt
│   │   ├── global/
│   │   │   ├── LazySplicedColumnGroup.kt
│   │   │   ├── MarkdownText.kt
│   │   │   └── ...
│   │   ├── home/
│   │   │   ├── BatteryRecorderTopAppBar.kt
│   │   │   ├── CurrentRecordCard.kt
│   │   │   ├── PredictionCard.kt  (同时包含 SceneStatsCard)
│   │   │   ├── StartServerCard.kt
│   │   │   └── StatsCard.kt
│   │   └── settings/sections/
│   ├── dialog/
│   │   ├── history/ChartGuideDialog.kt
│   │   ├── home/
│   │   │   ├── AboutDialog.kt
│   │   │   ├── AdbGuideDialog.kt
│   │   │   ├── DocsIntroDialog.kt
│   │   │   └── UpdateDialog.kt
│   │   └── settings/
│   │       ├── WeightedAlgorithmDialog.kt
│   │       ├── SceneStatsRecentFileCountDialog.kt
│   │       └── ...
│   ├── model/
│   ├── theme/
│   └── viewmodel/
│       ├── MainViewModel.kt
│       ├── SettingsViewModel.kt
│       ├── HistoryViewModel.kt
│       ├── PredictionDetailViewModel.kt
│       └── PowerDisplayMapper.kt
└── utils/
    ├── AppIconMemoryCache.kt
    ├── FormatUtil.kt
    └── UpdateUtil.kt
```

### Server 模块

```text
server/src/main/
├── aidl/
├── java/yangfentuozi/batteryrecorder/server/
│   ├── Main.kt
│   ├── Server.kt
│   ├── recorder/
│   │   ├── Monitor.kt
│   │   └── sampler/
│   │       ├── Sampler.kt
│   │       ├── SysfsSampler.kt
│   │       └── DumpsysSampler.kt
│   └── writer/
│       └── PowerRecordWriter.kt
└── jni/
    ├── CMakeLists.txt
    ├── dump_parser.cpp
    └── power_reader.c
```

### Shared 模块

```text
shared/src/main/
├── aidl/
└── java/yangfentuozi/batteryrecorder/shared/
    ├── config/
    │   ├── ConfigItems.kt
    │   ├── SettingsConstants.kt
    │   ├── ConfigUtil.kt
    │   ├── SharedSettings.kt
    │   └── dataclass/
    ├── data/
    │   ├── BatteryStatus.kt
    │   ├── LineRecord.kt
    │   ├── RecordFileParser.kt
    │   ├── RecordsFile.kt
    │   └── RecordsStats.kt
    ├── sync/
    │   ├── PfdFileSender.kt
    │   ├── PfdFileReceiver.kt
    │   └── SyncConstants.kt
    ├── util/
    │   ├── Handlers.kt
    │   └── LoggerX.kt
    ├── writer/
    │   └── AdvancedWriter.kt
    └── Constants.kt
```

## 关键路径索引

| 功能                        | 路径                                                                                                 |
|---------------------------|----------------------------------------------------------------------------------------------------|
| App 入口 Composable         | `app/.../ui/BatteryRecorderApp.kt`                                                                 |
| Activity Edge-to-Edge 入口  | `app/.../ui/BaseActivity.kt`                                                                       |
| 页面级 Insets 公共方法           | `app/.../ui/EdgeToEdgeInsets.kt`                                                                   |
| 导航路由                      | `app/.../ui/navigation/NavRoute.kt`                                                                |
| 导航宿主与历史页共享 ViewModel      | `app/.../ui/navigation/BatteryRecorderNavHost.kt`                                                  |
| 首页                        | `app/.../ui/screens/home/HomeScreen.kt`                                                            |
| 首页当前记录卡片                  | `app/.../ui/components/home/CurrentRecordCard.kt`                                                  |
| 首页 Root 启动卡片              | `app/.../ui/components/home/StartServerCard.kt`                                                    |
| 首页汇总卡片                    | `app/.../ui/components/home/StatsCard.kt`                                                          |
| 设置页                       | `app/.../ui/screens/settings/SettingsScreen.kt`                                                    |
| 历史列表                      | `app/.../ui/screens/history/HistoryListScreen.kt`                                                  |
| 记录详情页                     | `app/.../ui/screens/history/RecordDetailScreen.kt`                                                 |
| 预测详情页                     | `app/.../ui/screens/prediction/PredictionDetailScreen.kt`                                          |
| 预测详情 ViewModel            | `app/.../ui/viewmodel/PredictionDetailViewModel.kt`                                                |
| 首页预测/场景卡片                 | `app/.../ui/components/home/PredictionCard.kt`                                                     |
| 图表说明弹窗                    | `app/.../ui/dialog/history/ChartGuideDialog.kt`                                                    |
| ViewModel                 | `app/.../ui/viewmodel/`                                                                            |
| 记录详情图表状态                  | `app/.../ui/viewmodel/HistoryViewModel.kt`                                                         |
| 放电显示映射                    | `app/.../ui/viewmodel/PowerDisplayMapper.kt`                                                       |
| IPC Binder 持有             | `app/.../ipc/Service.kt`                                                                           |
| Binder 接收 Provider        | `app/.../ipc/BinderProvider.kt`                                                                    |
| 配置 Provider               | `app/.../ipc/ConfigProvider.kt`                                                                    |
| 开机自启动                     | `app/.../startup/BootCompletedReceiver.kt`, `RootServerStarter.kt`, `BootAutoStartNotification.kt` |
| 历史仓库                      | `app/.../data/history/HistoryRepository.kt`                                                        |
| 单记录应用统计                   | `app/.../data/history/RecordAppStatsComputer.kt`                                                   |
| 记录详情功耗统计                  | `app/.../data/history/RecordDetailPowerStatsComputer.kt`                                           |
| 应用预测统计                    | `app/.../data/history/AppStatsComputer.kt`                                                         |
| 场景统计                      | `app/.../data/history/SceneStatsComputer.kt`                                                       |
| 放电扫描                      | `app/.../data/history/DischargeRecordScanner.kt`                                                   |
| 续航预测                      | `app/.../data/history/BatteryPredictor.kt`                                                         |
| 缓存命名与版本                   | `app/.../data/history/HistoryCacheNaming.kt`, `HistoryCacheVersions.kt`                            |
| 日志导出                      | `app/.../data/log/LogRepository.kt`                                                                |
| 图表组件                      | `app/.../ui/components/charts/PowerCapacityChart.kt`                                               |
| 图标缓存                      | `app/.../utils/AppIconMemoryCache.kt`                                                              |
| 功率换算/格式化                  | `app/.../utils/FormatUtil.kt`                                                                      |
| 更新检查工具（对象名 `UpdateUtils`） | `app/.../utils/UpdateUtil.kt`                                                                      |
| Server 进程入口               | `server/.../Main.kt`                                                                               |
| Server Binder 实现          | `server/.../Server.kt`                                                                             |
| 采样循环                      | `server/.../recorder/Monitor.kt`                                                                   |
| 采样抽象                      | `server/.../recorder/sampler/Sampler.kt`                                                           |
| sysfs/JNI 采样              | `server/.../recorder/sampler/SysfsSampler.kt`                                                      |
| dumpsys 回退采样              | `server/.../recorder/sampler/DumpsysSampler.kt`                                                    |
| 写文件                       | `server/.../writer/PowerRecordWriter.kt`                                                           |
| JNI 原生代码                  | `server/src/main/jni/power_reader.c`, `server/src/main/jni/dump_parser.cpp`                        |
| AIDL 接口                   | `server/src/main/aidl/`                                                                            |
| 共享配置                      | `shared/.../config/`                                                                               |
| 共享数据模型与解析                 | `shared/.../data/`                                                                                 |
| 共享配置核心文件                  | `shared/.../config/SharedSettings.kt`, `shared/.../config/ConfigUtil.kt`                           |
| 同步协议                      | `shared/.../sync/`                                                                                 |
| 日志工具                      | `shared/.../util/LoggerX.kt`                                                                       |

## 架构约定

- `MainViewModel` 与 `SettingsViewModel` 在 `BatteryRecorderApp` 创建并向下传递
- `SettingsViewModel.init(context)` 在应用入口阶段完成 SharedPreferences 初始化
- `HistoryViewModel` 在 `BatteryRecorderNavHost` 创建共享实例，不是“每个历史页面各建一个”
- 首页当前记录卡片、实时曲线与等待态统一由 `MainViewModel.currentRecordUiState` 提供
- `PredictionDetailViewModel` 在 `PredictionDetailScreen` 局部创建
- `PredictionDetailViewModel.load()` 会先执行同步，再读取最近放电记录并聚合应用维度预测
- 当前实现中，`HomeScreen` 会直接访问 `Service.service` 注册/反注册 `IRecordListener`；修改该链路时必须同时检查生命周期与监听释放
- `HistoryRepository` 负责文件 I/O、解析、缓存和统计，不承载 Compose 展示逻辑
- 详情页图表状态统一收敛到 `RecordDetailChartUiState`
- 图表本地展示偏好不写入业务配置
- 应用图标请求只基于当前视口包名集合触发
- 页面级沉浸规则是：`Scaffold` 只吃顶部/水平安全区，底部手势区由内容层自行处理
- 页面外层 margin 当前统一按 16.dp 收敛；若看到 24.dp，需要先确认那是不是组件内部排版或图表绘制留白，而不是页面 margin
- 充电历史页底部筛选栏需要单独处理导航栏底部 inset，不能直接套用普通滚动页的底部 padding 逻辑
- ROOT 启动统一经过 `RootServerStarter.start(context, source)`
- 当前设置系统按 `AppSettings`、`StatisticsSettings`、`ServerSettings` 分层；`SharedSettings.kt` 负责三类设置的 SharedPreferences 读写，以及 `logLevel` 编解码
- 当前预测设置已收敛到 `StatisticsSettings.predWeightedAlgorithmEnabled` 与 `StatisticsSettings.predWeightedAlgorithmAlphaMaxX100`
- `SharedSettings.readStatisticsSettings(...)` 当前只读取新 key，不再兼容旧的 `pred_current_session_weight_enabled`
- `ConfigUtil.kt` 只负责 root/shell 场景下的设置来源适配；root 直接解析 SharedPreferences XML，shell 通过 `ConfigProvider` 读取 `ServerSettings`
- `ConfigProvider` 与 `IService.aidl` 当前直接使用 `ServerSettings` 作为 IPC 边界对象，不再经过 `ServerConfigDto` / `ServerSettingsMapper`
- Server 设置同步链路为：`SettingsConstants -> SharedSettings(ServerSettings) -> SettingsViewModel.updateServerSettings(...) -> Service.updateConfig(ServerSettings)`
- Server 启动读取链路为：`SharedPreferences XML / ConfigProvider -> ConfigUtil -> ServerSettings -> Server.updateConfig(ServerSettings)`
- 当前规则是：UI 限制非法输入，数值 setter 用 `SettingsConstants.xxx.coerce(...)` 做轻量收口，`writeServerSettings(...)` 纯写入，读取侧只保留缺字段默认值回退
- 新增 Server 设置项时，最容易漏的是：`ServerSettings` 数据类、`SharedSettings` 读写、`SettingsViewModel` setter/下发、`ConfigProvider`、`ConfigUtil`、`IService.aidl`、`Server.updateConfig()`

## 编码约定

- 所有注释、提交信息、文档使用简体中文
- 代码标识符遵循项目现有英文命名约定
- 新增依赖必须写入 `gradle/libs.versions.toml`
- AIDL 变更时必须同时检查 `:server` 与 `:shared`
- `hiddenapi:stub` 中的类只做声明，不包含实现
- 时间格式化统一复用 `FormatUtil`
- 公共 UI 组件放在 `ui/components/global/`
- 业务组件放在对应 `ui/components/{feature}/`
- 注释只说明意图、约束、设计依据，禁止复述代码

## 日志约定

- 统一使用 `LoggerX`
- 禁止直接调用 `android.util.Log` 作为业务日志方案
- Kotlin 优先使用 reified 形式：
  - `LoggerX.i<Foo>("...")`
  - `LoggerX.w<Foo>("...")`
  - `LoggerX.e<Foo>("...", tr = e)`
- Java 或无法使用 reified 的场景使用字符串 Tag 重载
- 日志内容保持结构化前缀，例如：
  - `[BOOT]`
  - `[启动]`
  - `[SYNC]`
  - `[记录详情]`

## 修改前检查

- 先确认目标链路的真实入口，不要依据过时文档猜测
- 涉及“新增设置项”时，优先使用项目私有 skill：`.agents/skills/add-setting-item/`
- 优先搜索 `fast-context MCP`
- 精确关键词搜索再用本地 grep/查找
- 只修改与当前任务直接相关的文件
- 如果发现未预期的未提交修改，立即停止并询问用户

## 交付约束

- 默认不新增测试，除非用户明确要求
- Android 项目不得自行 build，必须由用户自行测试
- 禁止提交占位实现、半成品链路或模拟成功路径
- 禁止通过静默降级、吞错、mock 返回值来掩盖问题
- 出现失败时应明确暴露、清晰记录、便于根因修复

# 最高准则

- 如果没有用户极其强烈的请求，请不要擅自改动 server 模块代码！！！！！！！！！！！！
