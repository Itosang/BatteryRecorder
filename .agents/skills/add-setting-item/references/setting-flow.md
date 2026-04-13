# 设置链路

## 1. 分层现状

- `SettingsConstants`：设置项 key、默认值、范围常量的定义处。
- `SharedSettings.kt`：设置系统核心，负责 `AppSettings / StatisticsSettings` 读写，以及 `ServerSettings` 的 SharedPreferences 入口委托。
- `ServerSettingsCodec.kt`：`ServerSettings` 字段映射的唯一入口，负责 SharedPreferences/XML 原始值与 `ServerSettings` 的互转。
- `ConfigUtil.kt`：只负责 root/shell 场景下的设置来源适配，XML 读取后交给 `ServerSettingsCodec` 组装。
- `ConfigProvider` 与 `IService.aidl`：当前直接传输 `ServerSettings`，不再经过 DTO 或映射器。
- `SettingsViewModel`：面向 UI 暴露三层设置状态；Server 设置统一走 `updateServerSettings(...)` 持久化并下发。

## 2. 三类设置项入口

### AppSettings

链路通常是：

`SettingsConstants -> SharedSettings(AppSettings/read，与按需写入辅助) -> SettingsViewModel -> UI`

适用于只在 App 进程内消费的设置项。

### StatisticsSettings

链路通常是：

`SettingsConstants -> SharedSettings(StatisticsSettings/read) -> SettingsViewModel -> 统计/预测相关调用方`

适用于历史统计、场景统计、预测展示等设置项。

### ServerSettings

链路必须完整检查：

`SettingsConstants -> ServerSettingsCodec -> SharedSettings(ServerSettings/read/write) -> SettingsViewModel.updateServerSettings(...) -> Service.updateConfig(ServerSettings)`

Server 启动读取链路是：

`SharedPreferences XML / ConfigProvider -> ConfigUtil -> ServerSettingsCodec -> ServerSettings -> Server.updateConfig(ServerSettings)`

同时确认这些同步节点是否需要更新：

- `ConfigProvider`
- `ConfigUtil`
- `ServerSettingsCodec`
- `Server.updateConfig()`

## 3. 关键原则

- `ServerSettings` 是服务端设置领域模型，也是当前 IPC 边界对象。
- 默认值定义和数值范围定义在 `SettingsConstants`；不要把这些规则散落到 Provider、Server 或其他来源适配层。
- 当前规则是：UI 限制非法输入，`SettingsViewModel` 的数值 setter 用 `SettingsConstants.xxx.coerce(...)` 做轻量收口，`SharedSettings.writeServerSettings(...)` 纯写入，读取侧只保留缺字段默认值回退。
- `ServerSettingsCodec.kt` 负责唯一的字段映射；新增 `ServerSettings` 项时，优先改这里，不要在 `SharedSettings` / `ConfigUtil` 再展开一份字段清单。
- `ConfigUtil.kt` 只处理“从哪里读到设置”，不负责统一裁剪或兼容旧 DTO 心智模型。
- 新增设置项时，优先参考同层现有字段的完整链路，不要只看单个调用点。

## 4. 最容易漏的点

新增 `ServerSettings` 项时，优先检查：

- `ServerSettings` 数据类字段
- `ServerSettingsCodec`
- `SharedSettings.readServerSettings(...)`
- `SharedSettings.writeServerSettings(...)`
- `SettingsViewModel` 对应 setter / `updateServerSettings(...)`
- `ConfigProvider`
- `ConfigUtil`
- `IService.aidl`
- `Server.updateConfig()`
