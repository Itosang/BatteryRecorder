# 仓库代理工作说明

本文件同时适用于 `AGENTS.md` 与 `CLAUDE.md`，用于指导代码代理在本仓库中进行检索、设计、修改与交付。

## 文档定位

- 这是**仓库工作说明**，不是面向最终用户的使用文档
- 目标是让代理快速理解当前项目结构、关键链路与修改约束
- 当项目新增关键模块、入口、缓存格式或数据链路时，必须同步更新本文件与 `CLAUDE.md`

## 项目概述

BatteryRecorder 是一个 Android 电池功率记录 App。

- App 进程负责 UI、配置、历史数据展示、续航预测与 IPC 客户端
- Server 进程以 root/shell 权限运行，低开销采集电池数据并写入记录文件
- 采样优先走 JNI sysfs 读取；不可用时回退到 dumpsys/batteryproperties 方案
- 历史数据支持图表查看、应用维度统计、场景维度统计与续航预测

## 构建约束

不要自主构建。修改完成后直接告知用户测试。

- 签名配置从根目录 `signing.properties` 读取；缺失时回退到 debug keystore
- APK 输出路径：`app/build/outputs/apk/{release,debug}/batteryrecorder-v*-{variant}.apk`
- Release 混淆产物会复制到根目录 `out/apk/`
- Release mapping 会复制到根目录 `out/mapping/`
- `versionCode` 由 git commit 数量动态生成，需要完整 git history

## 技术栈

| 项 | 值 |
|---|---|
| 语言 | Kotlin（主）, Java（hiddenapi）, C（JNI） |
| UI | Jetpack Compose + Material 3，无 XML Layout |
| 架构 | MVVM（ViewModel + StateFlow + Compose） |
| 构建 | Gradle 8.13 Kotlin DSL, AGP 8.13.2, Kotlin 2.3.0 |
| JDK | 21（app/server/shared）, 11（hiddenapi） |
| NDK | 29.0.14206865 |
| CMake | 3.22.1 |
| SDK | minSdk 31, targetSdk/compileSdk 36 |
| 依赖管理 | Version Catalog（`gradle/libs.versions.toml`） |

## 模块结构与依赖

```text
:app                 -> 主应用，UI + IPC 客户端 + 历史/预测
:server              -> 独立 Server 进程，采样 + 写文件 + AIDL 服务端
:shared              -> 公共配置、数据模型、文件解析、同步协议
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
- `DumpsysSampler` 是 sysfs/JNI 不可用时的回退实现
- `Monitor` 按配置间隔循环采样，并监听前台应用与屏幕状态
- `PowerRecordWriter` 分充电/放电两路写入 CSV，支持批量缓冲、延迟 flush、分段落盘
- 当前记录格式：
  `timestamp,power,packageName,capacity,isDisplayOn,status,temp,voltage,current`

### 数据同步链路

- Server 以 shell 权限运行时，记录文件落在 `com.android.shell` 数据目录
- App 通过 `sync()` AIDL 拿到 `ParcelFileDescriptor`
- 传输协议由 `PfdFileSender` / `PfdFileReceiver` 实现
- 同步结束后，已传输的 shell 侧旧文件会按当前文件排除规则清理

### 功率显示与预测链路

- 原始功率值展示必须统一经过 `FormatUtil.computePowerW()`
- 放电显示正值逻辑只能在 ViewModel 层通过 `PowerDisplayMapper` 或等价映射处理，UI 层不做正负转换
- 预测统计层一律按功耗幅值计算；展示层保留原始正负语义
- `PredictionDetailViewModel` 仅暴露原始统计值，最终展示由 `PredictionDetailScreen` 结合设置项决定

### 记录详情链路

- `HistoryViewModel` 统一产出 `RecordDetailChartUiState`
- `RecordDetailScreen` 直接消费 `recordChartUiState`
- 详情页图表偏好通过独立的 `record_detail_chart` SharedPreferences 持久化，不进入业务配置
- 详情页同时支持：
  - 原始/趋势/隐藏三种功率曲线模式
  - 电量/温度/应用图标显隐切换
  - 图表说明弹窗
  - 单记录导出
  - 单记录删除
  - 应用维度详情统计

## 关键数据与展示约定

### 功率转换

- 所有原始功率值转瓦特都必须通过 `FormatUtil.computePowerW()`
- 禁止在 Screen、Chart、Repository 中重复拼装换算公式

### 放电显示正值

- 只允许在 ViewModel 展示层统一处理
- UI 组件不关心放电显示正负配置

### 记录详情图表

- `RecordDetailChartPoint` 同时承载原始点与趋势点字段
- 趋势点必须基于**过滤后的展示点**重新分桶
- 分桶结果取中位数作为趋势功率值
- 平滑绘制是图表层职责，不在仓库层落地“平滑后数据”

### 记录详情应用统计

- `RecordAppStatsComputer` 负责单条放电记录内的应用维度统计
- 统计维度包含：
  - 应用包名
  - 息屏详情
  - 平均原始功率
  - 平均温度
  - 最高温度
  - 持续时长

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
  - 掉电不少于 1%
- `endTs` 取自当前文件最后一条有效记录时间戳，不使用 `System.currentTimeMillis()`

### 预测失败原因链路

- `DischargeRecordScanner` 负责区分文件级过滤原因
- `SceneStatsComputer` 透传 `insufficientReason`
- `MainViewModel` 与 `BatteryPredictor` 继续向上透传
- 首页 `PredictionCard` 直接展示具体原因

### 缓存约定

- `AppStatsComputer`、`SceneStatsComputer` 与记录详情 `power_stats` 共用 `HistoryCacheVersions.HISTORY_STATS_CACHE_VERSION`
- 任一历史统计缓存格式或 key 组成变化时，统一提升该版本
- 缓存命名统一通过 `HistoryCacheNaming.kt`
- 记录详情缓存命中时，还必须校验缓存内 `sourceLastModified` 与源文件 `lastModified()` 一致

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
├── ui/
│   ├── BatteryRecorderApp.kt
│   ├── MainActivity.kt
│   ├── BaseActivity.kt
│   ├── navigation/
│   │   ├── NavRoute.kt
│   │   └── BatteryRecorderNavHost.kt
│   ├── screens/
│   │   ├── home/HomeScreen.kt
│   │   ├── settings/SettingsScreen.kt
│   │   ├── prediction/PredictionDetailScreen.kt
│   │   └── history/
│   │       ├── HistoryListScreen.kt
│   │       └── RecordDetailScreen.kt
│   ├── components/
│   │   ├── global/
│   │   ├── home/
│   │   ├── settings/
│   │   └── charts/PowerCapacityChart.kt
│   ├── dialog/
│   │   ├── home/
│   │   ├── settings/
│   │   └── history/ChartGuideDialog.kt
│   ├── model/
│   ├── theme/
│   └── viewmodel/
├── data/
│   ├── model/
│   └── history/
│       ├── HistoryRepository.kt
│       ├── BatteryPredictor.kt
│       ├── DischargeRecordScanner.kt
│       ├── SceneStatsComputer.kt
│       ├── AppStatsComputer.kt
│       ├── RecordAppStatsComputer.kt
│       ├── HistoryCacheNaming.kt
│       ├── HistoryCacheVersions.kt
│       ├── StatisticsRequest.kt
│       └── SyncUtil.kt
├── ipc/
├── startup/
└── utils/
```

### Server 模块

```text
server/src/main/
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
├── aidl/
└── jni/power_reader.c
```

### Shared 模块

```text
shared/src/main/
├── java/yangfentuozi/batteryrecorder/shared/
│   ├── config/
│   ├── data/
│   ├── sync/
│   ├── util/
│   ├── writer/
│   └── Constants.kt
└── aidl/
```

## 关键路径索引

| 功能 | 路径 |
|---|---|
| App 入口 Composable | `app/.../ui/BatteryRecorderApp.kt` |
| 导航路由 | `app/.../ui/navigation/NavRoute.kt` |
| 首页 | `app/.../ui/screens/home/HomeScreen.kt` |
| 设置页 | `app/.../ui/screens/settings/SettingsScreen.kt` |
| 历史列表 | `app/.../ui/screens/history/HistoryListScreen.kt` |
| 记录详情页 | `app/.../ui/screens/history/RecordDetailScreen.kt` |
| 预测详情页 | `app/.../ui/screens/prediction/PredictionDetailScreen.kt` |
| 图表说明弹窗 | `app/.../ui/dialog/history/ChartGuideDialog.kt` |
| ViewModel | `app/.../ui/viewmodel/` |
| 记录详情图表状态 | `app/.../ui/viewmodel/HistoryViewModel.kt` |
| 放电显示映射 | `app/.../ui/viewmodel/PowerDisplayMapper.kt` |
| IPC Binder 持有 | `app/.../ipc/Service.kt` |
| Binder 接收 Provider | `app/.../ipc/BinderProvider.kt` |
| 配置 Provider | `app/.../ipc/ConfigProvider.kt` |
| 开机自启动 | `app/.../startup/BootCompletedReceiver.kt`, `RootServerStarter.kt`, `BootAutoStartNotification.kt` |
| 历史仓库 | `app/.../data/history/HistoryRepository.kt` |
| 单记录应用统计 | `app/.../data/history/RecordAppStatsComputer.kt` |
| 应用预测统计 | `app/.../data/history/AppStatsComputer.kt` |
| 场景统计 | `app/.../data/history/SceneStatsComputer.kt` |
| 放电扫描 | `app/.../data/history/DischargeRecordScanner.kt` |
| 续航预测 | `app/.../data/history/BatteryPredictor.kt` |
| 缓存命名与版本 | `app/.../data/history/HistoryCacheNaming.kt`, `HistoryCacheVersions.kt` |
| 图表组件 | `app/.../ui/components/charts/PowerCapacityChart.kt` |
| 图标缓存 | `app/.../utils/AppIconMemoryCache.kt` |
| 功率换算/格式化 | `app/.../utils/FormatUtil.kt` |
| 更新检查 | `app/.../utils/UpdateUtil.kt` |
| Server 进程入口 | `server/.../Main.kt` |
| Server Binder 实现 | `server/.../Server.kt` |
| 采样循环 | `server/.../recorder/Monitor.kt` |
| 采样抽象 | `server/.../recorder/sampler/Sampler.kt` |
| sysfs/JNI 采样 | `server/.../recorder/sampler/SysfsSampler.kt` |
| dumpsys 回退采样 | `server/.../recorder/sampler/DumpsysSampler.kt` |
| 写文件 | `server/.../writer/PowerRecordWriter.kt` |
| JNI 原生代码 | `server/src/main/jni/power_reader.c` |
| AIDL 接口 | `server/src/main/aidl/` |
| 共享配置 | `shared/.../config/` |
| 共享数据模型 | `shared/.../data/` |
| 同步协议 | `shared/.../sync/` |
| 日志工具 | `shared/.../util/LoggerX.kt` |

## 架构约定

- `SettingsViewModel` 统一在 `BatteryRecorderApp` 初始化
- UI 组件不直接依赖 `Service.service`
- `HistoryRepository` 只负责文件 I/O、解析和统计，不包含 UI 展示逻辑
- `MainViewModel` 与 `SettingsViewModel` 在应用入口统一创建后向下传递
- `HistoryViewModel`、`LiveRecordViewModel` 在各自页面局部创建
- 详情页图表状态统一收敛到 `RecordDetailChartUiState`
- 图表本地展示偏好不写入业务配置
- 应用图标请求只基于当前视口包名集合触发
- ROOT 启动统一经过 `RootServerStarter.start(context, source)`
- `SettingsViewModel.loadSettings()` 中构造 `serverConfig` 时，必须传入 `Config` 的所有字段；新增 `Config` 字段后若遗漏，App 进程重启而 Server 存活时，修改任意其他设置项会通过 `serverConfig.copy(...)` 将遗漏字段以默认值静默推送给 Server

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
