# 设置链路

## 1. 分层现状

- `SettingsConstants`：设置项 key、默认值、范围常量的定义处。
- `SharedSettings.kt`：设置系统核心，负责 `AppSettings / StatisticsSettings / ServerSettings` 分层、SharedPreferences 读写、规范化、以及 `ServerSettingsMapper` 映射。
- `ConfigUtil.kt`：只负责 root/shell 场景下的设置来源适配，把外部来源接回 `ServerSettings` / `ServerConfigDto`。
- `ServerConfigDto`：仅用于 AIDL 与 `ConfigProvider` 的 IPC 边界 DTO，不是设置真值。
- `SettingsViewModel`：面向 UI 暴露三层设置状态；Server 设置统一走 `updateServerSettings(...)` 持久化并下发。

## 2. 三类设置项入口

### AppSettings

链路通常是：

`SettingsConstants -> SharedSettings(AppSettingKeys/AppSettings/read，与按需写入辅助) -> SettingsViewModel -> UI`

适用于只在 App 进程内消费的设置项。

### StatisticsSettings

链路通常是：

`SettingsConstants -> SharedSettings(StatisticsSettingKeys/StatisticsSettings/read/normalize) -> SettingsViewModel -> 统计/预测相关调用方`

适用于历史统计、场景统计、预测展示等设置项。

### ServerSettings

链路必须完整检查：

`SettingsConstants -> SharedSettings(ServerSettingKeys/ServerSettings/read/write/normalize) -> ServerSettingsMapper -> ServerConfigDto -> Service.updateConfig(...)`

同时确认这些同步节点是否需要更新：

- `ConfigProvider`
- `ConfigUtil`
- `Server.updateConfig()`

## 3. 关键原则

- `ServerSettings` 才是服务端设置领域模型，`ServerConfigDto` 只是 IPC 边界薄包装。
- 默认值、范围裁剪、枚举解析统一收敛在 `SharedSettings.kt`，不要把规则散落到 UI、Provider 或 Server。
- `ConfigUtil.kt` 只处理“从哪里读到设置”，不负责定义设置语义。
- 新增设置项时，优先参考同层现有字段的完整链路，不要只看单个调用点。

## 4. 最容易漏的点

新增 `ServerSettings` 项时，优先检查：

- `ServerSettings` 数据类字段
- `SharedSettings.readServerSettings(...)`
- `SharedSettings.writeServerSettings(...)`
- `SharedSettings.normalizeServerSettings(...)` / `serverSettingsFromStoredValues(...)`
- `ServerSettingsMapper.toServerConfigDto(...)` / `fromServerConfigDto(...)`
- `ServerConfigDto` DTO 字段
- `ConfigProvider`
- `ConfigUtil`
- `Server.updateConfig()`
