# 功能提案：接入 HyperOS 系统电池历史（.bh）

> 编写：ZCode（协作整理）· 2026-10-02
> 样本来源：Redmi K90（HyperOS 4.0.0.34）`/data/system/battery-history/` 共 244 段会话
> 样本备份：仓库 `bh-samples/` 目录（可直接用于测试）

## 一、.bh 是什么

小米 HyperOS 会把系统 BatteryStats 的电池历史按会话切片保存为 `.bh`：

```
文件 = "GZIP" 头(8B) + gzip 流
解压后 = [u32 记录数][u32 起始uptime_ms][0][u32 结束uptime_ms][...32B 头] + BatteryStats 事件流
事件名 = UTF-16LE 明文（ALARM_ACTION / display=... / Abort:... / sensor:... 等）
文件名 = 会话起始 uptime 毫秒（自首次开机累计）
```

一段会话通常 30~90 分钟，包含数百条事件，覆盖：

| 事件族 | 示例 | 对本 App 的价值 |
|---|---|---|
| 屏幕状态机 | `display=0 state=ON reason=KEY` / `state=DOZE reason=DRAW_WAKE_LOCK` | 精确到“为什么亮/灭”，比采样 `isDisplayOn` 更细 |
| 应用定时唤醒 | `137a963 ALARM_ACTION(10000)/u0/0x1055`、`com.xiaomi.push.PING_TIMER` | 解释息屏耗电：谁在定时起来干活 |
| 唤醒锁 | `MSF:WakeLock:MsgPush`（QQ 消息保活） | 与记录到的耗电尖峰做归因 |
| **内核挂起失败** | `Abort: Pending Wakeup Sources: [timerfd]`、`Device a88000.spi failed to suspend: error -16` | **直接给出“睡眠被打断”的原因**——这正是 README 里“探索未知场景”缺失的一块 |
| 网络/传感器 | `NR MID/HIGH`、`sensor:0x...`、`gnss` | 场景标注（5G 切换、定位拉活） |

## 二、可以补齐的功能（按价值排序）

1. **系统电池历史浏览页（已实现骨架）**
   `系统电池历史` 入口 → 会话列表（起始/时长/事件计数：屏幕·唤醒·挂起失败·应用）→ 会话详情（分类着色的事件时间线）。
2. **挂起失败统计**：把 `Abort:` 事件聚合为“睡眠质量”指标，按会话展示（次数、原因 Top）。
3. **应用级唤醒归因**：从 `ALARM_ACTION` / `WakeLock` 提取包名，按会话统计“谁唤醒了多少次”。
4. **与自有功率记录交叉对齐**（后续）：同一时段 recorder 的功率曲线 + bh 事件叠加，标注尖峰对应事件。
5. **导出**：单向导入或与历史记录一起打包导出。

## 三、本仓库已包含的实现（当前定稿架构）

> 以下为 2026-10-03 定稿后的文件清单；第七、十一节等历史迭代记录保留在文末，仅作过程存档。

数据层（`app/src/main/java/yangfentuozi/batteryrecorder/data/bh/`）：
- `BhHistoryParser.kt` 解析器：gzip 解壳、头部解析、UTF-16LE 事件抽取、按类别
  （屏幕/唤醒/挂起失败/网络/传感器/应用）分类、连续重复合并、图例过滤；
- `BhParserV3.kt` 精确解码：AOSP BatteryStatsHistory delta 格式，还原每条事件的精确时间戳；
- `BhEventDb.kt` 事件库：SQLite 单表时间轴 + 增量账本；
- `BhSyncManager.kt` 静默增量同步（App 启动 / 打开页面时触发）；
- `BhRootSource.kt` root 读取通道；`BhRangeStats.kt` 范围统计；`BhEventHumanizer.kt` 事件翻译；
- `BhTimeline.kt` 时间轴分割（按息屏段/小时/固定分钟）。

UI 层（`app/src/main/java/yangfentuozi/batteryrecorder/ui/screens/bh/`）：
- `BhRecordsScreen.kt` 全量记录页（按日期分类 + 小时级筛选 + 统计 + 明细分段）；
- `BhDigest.kt` / `BhEventViews.kt` 解读与共享视图。

导航：`NavRoute.BhRecords`；入口见第十三节（首页 + 记录详情）。

## 四、数据放置方式（在非 root 环境使用）

```
adb push xxx.bh /sdcard/Android/data/yangfentuozi.batteryrecorder/files/battery-history/
```

真机（KernelSU root）也可以直接把 App 加进 root 授权后读取 `/data/system/battery-history`；
或由 root server 增加一个导出子命令（后续工作）。

## 五、已知边界

- 每条记录内部的**精确时刻**是 delta 增量编码，当前版本只还原【会话级时间】+【事件顺序】；
  逐条时间戳解码作为后续增强（解码后可输出“几点几分被谁唤醒”的完整账单）。
- 会话起止 uptime → 挂钟时间的换算需要“首次开机时刻”基准；当前 UI 以「开机后 X 天 X 小时」展示，
  避免猜测挂钟时间。

## 六、实机验证记录（2026-10-02）

在 MuMu 模拟器（Android 15/API 35）中完成端到端验证：

1. 模拟器以真机同路径部署 244 个 `.bh`（`/data/system/battery-history/`）+ 真机 BR 日志（`/data/local/tmp/batteryrecorder_logs/`）。
2. debug APK 编译（JDK 21 + NDK 29 + CMake 3.22.1）安装启动，ADB 方式拉起 root server 后连接成功。
3. **首页 →「系统电池历史」→ 243 段会话列表 → 会话详情时间线** 全部正常工作。

截图存于 `docs/screenshots/`：
- `bh-list.png`：会话列表（每条含 屏幕/唤醒/挂起失败/应用 分类计数）
- `bh-detail.png`：详情时间线（可见 `ALARM_ACTION/u999`——**微信分身**的定时唤醒被直接归因）
- `bh-abort.png`：`挂起失败` 红色事件：`Abort: Pending Wakeup Sources: smp2p-sleepstate`
- `home-entry.png`：首页入口卡片

## 七、常规记录集成（2026-10-03 完成）

**目标**：.bh 的全部内容不只停留在独立浏览页，而是**并入常规电量记录**。

实现（`RecordDetailScreen.kt`）：

1. 打开任意常规记录详情时，用该记录的 `RecordsStats.startTime/endTime`（epoch 毫秒）作为窗口；
2. `BhHistoryParser.scanOverlapping()` 先用文件 mtime 廉价预筛（mtime = 会话结束时刻），
   再解析并精确判断窗口相交（最长会话按 8 小时估上界防漏）；
3. **挂钟时间锚定**：`boot 锚点 = mtime - endUptime` → 会话起止时间换算为真实挂钟时间
   （实测 00:25 结束、时长 45 分钟的会话还原为 "10-02 23:39 ~ 10-03 00:25"，与文件时间完全一致）；
4. 详情页底部新增「系统事件（BatteryStats）」区：
   - 标题 + 会话挂钟时间段 + 事件总数
   - **分类统计行**：`挂起失败 N · 唤醒 N · 屏幕 N · 应用 N · 传感器 N`
   - 按时间顺序的事件列表（分类着色：挂起失败=红、唤醒=橙、屏幕=主色；上限 80 条/会话，其余提示去浏览页看全量）
5. BhHistoryScreen 列表与详情同步显示挂钟时间段。

验证截图：`docs/screenshots/record-events-header.png`（标题+统计行+事件流）、
`record-events-1/2/3.png`（事件明细：`Abort: Pending Wakeup Sources [timerfd]`、
`Abort: One or more tasks refusing to freeze`、`Abort: late suspend of 0000:01:00.0 device failed` 等）。

## 九、Android 17（API 37）兼容性修复（2026-10-03，真机验证）

**症状**：Redmi K90（HyperOS / Android 17）上后端服务器启动数秒即崩溃，引导页卡在"启动服务"：

```
Server crashed
java.lang.AbstractMethodError:
  abstract method "void android.hardware.display.IDisplayManagerCallback.onDisplaySnapshot(int[], int[])"
```

**根因**：隐藏 API 接口方法表不完整。`hiddenapi:stub` 是 **compileOnly**（不打进 APK），
运行期 `android.hardware.display.IDisplayManagerCallback` 由 bootclasspath 解析为系统真实类；
本仓库的 AIDL 桩只声明了 `onDisplayEvent`，而 Android 16+ 的真实接口新增了
`onDisplaySnapshot(int[], int[])`、`onTopologyChanged(DisplayTopology)`。
系统事务派发到未实现的抽象方法 → AbstractMethodError → 进程崩溃。

**修复**（从设备 framework.jar 用 dexdump 抠出真实方法表后对齐）：

1. `hiddenapi/stub/.../IDisplayManagerCallback.aidl`：补齐三个方法（保持方法集一致）；
2. 新增编译桩 `hiddenapi/stub/.../DisplayTopology.java`（Parcelable 占位，仅编译期）；
3. `Monitor.kt`：实现 `onDisplaySnapshot` / `onTopologyChanged`（复用亮灭屏刷新逻辑）。

**真机验证**（Redmi K90，无线调试）：
- 服务器稳定运行，日志出现 `onDisplaySnapshot: displays=0 ...` 而不再崩溃；
- 引导页走通（服务已连接 → 校准页"强制跳过"→ 完成引导）；
- 首页实时记录正常（充电记录 11W）；
- 「系统电池历史」列出 **243 段会话**（挂钟时间 + 挂起失败计数）；
- 记录详情页底部「系统事件（BatteryStats）」正常合并显示（见
  `docs/screenshots/real-device-record-events.png`）。

**遗留说明**：
- 每次启动 App 会弹"发现新版本 2.2.0"（本构建版本号 2.1.1），点取消即可；如需消除可自行改
  `app/build.gradle.kts` 的 `baseVersionName`。
- 官方原版 APK 备份在 `backup-official/batteryrecorder-official-2.2.2-alpha1062.apk`。
- 服务器由 `adb shell <lib>/libstarter.so --apk=<apk>/base.apk`（root）拉起；开机自启依赖
  原有 batteryrecorder_starter 模块。

## 十一、2026-10-03 第二轮改版（真机反馈落实）

按用户反馈完成四项改造，真机（Redmi K90）验证通过：

1. **数据源改为真机路径**：`/data/system/battery-history`（root 读取）。
   新增 `data/bh/BhRootSource.kt`：直接可读时走 File IO；否则 `su -c` 通道
   （KernelSU 首次弹一次授权；`stat -c '%n|%Y|%s'` 取文件名/修改时间/大小，
   `cat` 读原始字节）。应用目录导入仍作为无 root 时的兜底。
2. **解析缓存（解决"244 个文件全量解析很慢"）**：新增 `data/bh/BhRepository.kt`。
   - 内存缓存：name@mtime → BhSession（容量 32）
   - 磁盘缓存：`cacheDir/bh_cache/<name>.<mtime>.json`（事件文本序列化）；
     命中缓存零解压零解析；mtime 变化自动失效并清理过期文件
   - 列表渐进加载：先出缓存行，未解析的后台补（界面显示"解析进度 x/y（已缓存的秒开）"）
3. **UI 对齐 App 原生风格**：解读页改用 `SplicedColumnGroup`（primary 标题 + 圆角分组）
   + `StatRow`（左标签右数值）重排：`这段时间发生了什么`（总结+提示）、
   `休眠被谁阻止（共 N 次）`、`后台唤醒排行`、`应用活动排行`、`屏幕使用`；
   原始事件流折叠在"查看原始事件流"之后。会话列表卡片改为时间区间标题 + 时长 + 分类计数。
4. **融合进充放电记录**：移除首页独立入口；改为记录详情页底部
   `系统事件解读 · <时间段>` 分组（总结+提示+休眠被打断 Top3），
   附"查看完整事件明细"入口跳转浏览页（`onNavigateToBhHistory` 回调贯通 NavHost）。

真机验证截图：`docs/screenshots/real-device-bh-root-list.png`（来源显示
`/data/system/battery-history`，解析进度 170/242）、`real-device-digest-final.png`
（解读页 SplicedColumnGroup 样式）、`real-device-record-events.png`（记录详情集成）。

## 十二、后续增强建议

1. **delta 时间戳解码** → 秒级事件挂钟时间（当前为会话级时间 + 事件顺序）。
2. **挂起失败排行**：跨会话聚合 `Abort:` 原因 Top N（睡眠质量指标）。
3. **应用唤醒归因排行**：按 u0/u999 + 包名统计各应用定时唤醒次数。
4. **图表标注**：把挂起失败事件画成功率曲线上的标记点。


## 十三、事件库架构（2026-10-03 定稿，用户需求驱动）

**核心变化：从"按文件浏览"改为"一张时间轴数据库"。**

### 数据层
- `data/bh/BhEventDb.kt`：SQLite（App 内部存储 `databases/bh_events.db`）
  - `bh_events(wall_ms 索引, text, category, code, uid, level, approx, file, file_mtime)`
  - `bh_files(file, file_mtime, ...)`：增量账本
- `data/bh/BhSyncManager.kt`：静默增量同步
  - 触发：App 启动（App.kt 后台协程）+ 打开记录详情/全量页
  - 账本比对 name+mtime：未变跳过；变化/新增 → V3 精确解析（失败回退 v2 近似，`approx=1`）
  - 事务化替换（delete→insert→账本更新），可安全重跑
- 时间轴：所有事件 `wall_ms = eventUptime + (文件mtime − 会话endUptime)`，入库即按时间排序，
  **不再保留原始文件分段**作为浏览维度

### UI（两个入口，无原始文件视图）
- **入口 A**：首页底部「电池事件记录」→ `BhRecordsScreen`
  - 默认**按日期分类**（每天：事件数 + 起止时间）→ 点选进入
  - **小时级筛选**：日期下拉 + 从/到小时下拉 → 统计结果（总数/精确近似/屏幕/休眠被阻止/唤醒/传感器）
    + 排行（休眠被谁阻止 Top、唤醒排行）+ **详细信息**（按息屏段/小时/每30/每10 分钟重新分割，
    事件行带 HH:mm:ss 与"≈"近似标记）
- **入口 B**：充放电记录详情底部「系统事件统计（记录开始~截止）」+「查看详细记录」
  - 统计范围严格 = 记录 startTime~endTime（DB 窗口查询）
  - 详细记录入口把同一窗口带入入口 A 的筛选

### 验证（真机 49867 事件 / 7.7MB / 4 天跨度；MuMu 52284 事件）
- 增量：`扫描 247 · 新增/更新 1 · 跳过 245`（无重复解析）✓
- 日期分类：09-30(15041)/10-01(27672)/10-02(9565) ✓
- 小时筛选 + 统计 + 明细（含分段切换）✓
- 入口 B：`系统事件统计（10-03 09:03 ~ 10-03 10:03）` 精确等于记录窗口 ✓

## 十四、可见性门控与入口 B 调整（2026-10-03）

**功能可见性检测**（`data/bh/BhFeature.kt`）：功能入口仅在同时满足以下条件时开放，
检测结果进程内缓存：

1. 系统为小米澎湃OS（`ro.mi.os.version.name`，兼容回退 `ro.miui.ui.version.name`）；
2. battery-history 数据源目录中检测到 `.bh` 文件（真机 root / 导入目录均可）；
3. 设置开关未被关闭。

**设置开关**：设置页新增「厂商系统原生日志」分组（仅检测通过后展示），开关
「查看厂商系统原生日志」默认开启；关闭后隐藏首页入口与记录详情的系统事件区，
并跳过 App 启动时的静默同步。

**入口 B（充放电记录详情 → 电池事件详情）**：时间窗由记录固定，移除日期/小时筛选，
仅保留「详细信息（xx 分段）」的重新分割；入口 A（首页）保留按日期分类与小时级筛选。

**文案原则**：事件翻译层只收录含义明确的词条，含义不确定的事件一律原样显示；
不输出任何建议类、推测类文案。
