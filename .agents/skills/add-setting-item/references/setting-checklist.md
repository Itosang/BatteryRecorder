# 新增设置项检查清单

## 通用

- 先确认该设置项属于 `AppSettings`、`StatisticsSettings` 还是 `ServerSettings`
- 确认 `SettingsConstants` 是否需要新增 key、默认值、范围常量
- 确认命名、默认值、类型与同层现有字段保持一致
- 不要把默认值、裁剪逻辑或枚举转换散落到 UI / IPC / Server 侧
- 数值设置项记得补 `min/max`，并让 UI 与 setter 沿用现有同层风格

## AppSettings

- 补齐 `AppSettings` 字段
- 补齐 `SharedSettings.readAppSettings(...)`
- 若该层已有对应写入辅助，补齐 `SharedSettings.writeAppSettings(...)`
- 补齐 `SettingsViewModel` 对应状态与 setter
- 检查实际 UI/调用方是否已经接入新字段

## StatisticsSettings

- 补齐 `StatisticsSettings` 字段
- 补齐 `SharedSettings.readStatisticsSettings(...)`
- 补齐 `SettingsViewModel` 对应状态与 setter
- 当前该层通常由 `SettingsViewModel` 直接写 prefs；新增项保持同层现有风格一致
- 检查统计/预测调用方是否已经使用新字段

## ServerSettings

- 补齐 `ServerSettings` 字段
- 补齐 `SharedSettings.readServerSettings(...)`
- 补齐 `SharedSettings.writeServerSettings(...)`
- 检查 `ConfigProvider` 是否需要透传新字段
- 检查 `ConfigUtil` 的 XML / ContentProvider 来源适配是否需要更新，保持它直接回到 `ServerSettings`
- 检查 `SettingsViewModel.updateServerSettings(...)` 相关调用是否已接入新字段
- 检查 `IService.aidl` 的 `ServerSettings` parcelable 是否已覆盖新字段
- 检查 `Server.updateConfig()` 是否真正消费并应用了新字段

## 收尾

- 搜索新 key / 新字段名，确认没有只改半条链路
- 搜索 `ServerConfigDto`、`ServerSettingsMapper`、`normalizeServerSettings`、`serverSettingsFromStoredValues` 等旧入口引用，确认文档和实现都没有继续把它们当成当前推荐路径
- 若设置链路或关键入口发生变化，同步更新 `AGENTS.md`
- 不要自行 build Android 项目；改完后提示用户手动测试
