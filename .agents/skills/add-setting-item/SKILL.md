---
name: add-setting-item
description: 为 BatteryRecorder 仓库新增单个设置项的固定流程。用于需要修改设置链路、区分 AppSettings/StatisticsSettings/ServerSettings，尤其是新增 ServerSettings 项并检查 SharedSettings、ServerSettingsMapper、Config、ConfigProvider、ConfigUtil、Server.updateConfig 等同步节点时。
---

# add-setting-item

1. 先判断设置项归属：`AppSettings`、`StatisticsSettings`、`ServerSettings`。不要把 `Config` 当成设置真值。
2. 先读取 `references/setting-flow.md`，确认当前分层、映射和同步链路。
3. 实施前再对照 `references/setting-checklist.md`，逐项补齐读写、规范化和同步节点。
4. 只改本次设置项直接相关的文件；不要顺手重构整条设置系统。
5. 新增 `ServerSettings` 项时，必须完整检查 IPC 同步链路，不要只改 App 侧。
6. 不要恢复零散 `SharedPreferences` 直读直写，也不要把默认值/裁剪逻辑散落到 UI 或 IPC 层。
7. Android 项目不要自行 build；完成后明确告知用户手动测试。
