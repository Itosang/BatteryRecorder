# Settings Provider

BatteryRecorder exposes a ContentProvider for reading and changing app settings through ADB, Root, or system-level automation tools.

> [!NOTE]
> Only Shell, Root, system UIDs, and BatteryRecorder itself may use this interface. Regular third-party apps cannot call it directly.

## URIs

Collection URI:

```text
content://yangfentuozi.batteryrecorder.settings/preferences
```

Append a setting key for a single preference, for example:

```text
content://yangfentuozi.batteryrecorder.settings/preferences/notification_enabled
```

## Read Settings

Read all settings:

```bash
adb shell content query \
  --uri content://yangfentuozi.batteryrecorder.settings/preferences
```

Read one setting:

```bash
adb shell content query \
  --uri content://yangfentuozi.batteryrecorder.settings/preferences/record_interval_ms
```

Query results contain `key`, `type`, and `value`. The `value` of a `StringSet` is a JSON array string.

## Change Settings

Use the single-preference URI with `value` when possible:

```bash
adb shell content update \
  --uri content://yangfentuozi.batteryrecorder.settings/preferences/notification_enabled \
  --bind value:b:true
```

The collection URI also accepts batched writes:

```bash
adb shell content update \
  --uri content://yangfentuozi.batteryrecorder.settings/preferences \
  --bind notification_enabled:b:true \
  --bind record_interval_ms:l:2000 \
  --bind power_overlay_opacity:f:0.8
```

Common types:

| Type | Meaning | Example |
| --- | --- | --- |
| `b` | Boolean | `value:b:true` |
| `s` | String | `value:s:example` |
| `i` | Int | `value:i:1` |
| `l` | Long | `value:l:2000` |
| `f` | Float | `value:f:0.8` |

`adb shell content` cannot pass a `StringArrayList`, so do not use it to overwrite a `StringSet` preference directly.

Delete a single preference to restore its default value:

```bash
adb shell content delete \
  --uri content://yangfentuozi.batteryrecorder.settings/preferences/notification_enabled
```

If the app is running, changed preferences automatically refresh the Settings UI.

## Refresh Server Settings

Regular reads and writes do **not** synchronize settings to the Server. After changing a server-related preference, call:

```bash
adb shell content call \
  --uri content://yangfentuozi.batteryrecorder.settings/preferences \
  --method syncSettings
```

The command blocks for up to two seconds while waiting for the Server to connect. `success=true` means a Server connection was available and the synchronization request was submitted; `success=false` means the connection timed out or dispatch failed.
