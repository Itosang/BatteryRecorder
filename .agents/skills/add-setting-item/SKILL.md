---
name: add-setting-item
description: 为 BatteryRecorder 仓库新增单个设置项的固定流程。用于需要修改设置链路、区分 AppSettings/StatisticsSettings/ServerSettings，尤其是新增 ServerSettings 项并检查 SharedSettings、ConfigProvider、ConfigUtil、IService.aidl、Server.updateConfig 等同步节点时。
---

# add-setting-item

1. 先判断设置项归属：`AppSettings`、`StatisticsSettings`、`ServerSettings`。不要沿用旧 `ServerConfigDto`、`ServerSettingsMapper` 或 `coerceConfigValue` 的心智模型找入口。
2. 先读取 `references/setting-flow.md`，确认当前分层、映射和同步链路。
3. 实施前再对照 `references/setting-checklist.md`，逐项补齐读写、下发和来源适配节点。
4. 只改本次设置项直接相关的文件；不要顺手重构整条设置系统。
5. 新增 `ServerSettings` 项时，必须完整检查 IPC 同步链路，不要只改 App 侧。
6. 不要恢复零散 `SharedPreferences` 直读直写，也不要把默认值或数值范围散落到 IPC 层；当前规则是 UI 限制输入、数值 setter 用 `coerce()` 轻量收口、读取侧只做缺字段默认值回退。
7. `ConfigUtil` 当前职责是“从不同来源读到 `ServerSettings`”，不是继续维护 DTO、映射器或统一合法化包装层。
8. Android 项目不要自行 build；完成后明确告知用户手动测试。
