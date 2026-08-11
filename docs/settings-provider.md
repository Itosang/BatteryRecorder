# 设置 Provider

BatteryRecorder 提供了一个 ContentProvider，用于通过 ADB、Root 或系统级自动化工具读取和修改应用设置。

> [!NOTE]
> 此接口仅允许 Shell、Root、系统 UID 与 BatteryRecorder 自身访问。普通第三方应用不能直接调用。

## 地址

设置集合地址：

```text
content://yangfentuozi.batteryrecorder.settings/preferences
```

单个设置地址是在末尾追加设置 key，例如：

```text
content://yangfentuozi.batteryrecorder.settings/preferences/notification_enabled
```

## 读取设置

读取全部设置：

```bash
adb shell content query \
  --uri content://yangfentuozi.batteryrecorder.settings/preferences
```

读取单个设置：

```bash
adb shell content query \
  --uri content://yangfentuozi.batteryrecorder.settings/preferences/record_interval_ms
```

查询结果包含 `key`、`type`、`value` 三列。`StringSet` 的 `value` 为 JSON 数组字符串。

## 修改设置

建议使用单个设置地址，并传入 `value`：

```bash
adb shell content update \
  --uri content://yangfentuozi.batteryrecorder.settings/preferences/notification_enabled \
  --bind value:b:true
```

也可以向集合地址批量写入：

```bash
adb shell content update \
  --uri content://yangfentuozi.batteryrecorder.settings/preferences \
  --bind notification_enabled:b:true \
  --bind record_interval_ms:l:2000 \
  --bind power_overlay_opacity:f:0.8
```

常用类型：

| 类型 | 含义 | 示例 |
| --- | --- | --- |
| `b` | Boolean | `value:b:true` |
| `s` | String | `value:s:example` |
| `i` | Int | `value:i:1` |
| `l` | Long | `value:l:2000` |
| `f` | Float | `value:f:0.8` |

`adb shell content` 不能传递 `StringArrayList`，因此不要用它直接覆盖 `StringSet` 类型设置。

删除单个设置会使其回退到默认值：

```bash
adb shell content delete \
  --uri content://yangfentuozi.batteryrecorder.settings/preferences/notification_enabled
```

App 正在运行时，修改后的偏好会自动刷新设置界面。

## 刷新 Server 配置

普通读写操作**不会**自动同步 Server。修改服务端相关设置后，显式调用：

```bash
adb shell content call \
  --uri content://yangfentuozi.batteryrecorder.settings/preferences \
  --method syncSettings
```

该命令最多阻塞等待 2 秒 Server 建连。返回结果中的 `success=true` 表示已连接 Server 且同步请求已成功提交；`success=false` 表示连接超时或下发失败。
