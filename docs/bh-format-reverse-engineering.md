# .bh 文件格式逆向笔记（BatteryStatsHistory delta 格式）

> 逆向对象：Redmi K90 / HyperOS 4.0.0.34 的 `/data/system/battery-history/*.bh`
> 参照实现：AOSP `frameworks/base` → `BatteryStatsHistory.java` / `BatteryStatsHistoryIterator.java` /
> `BatteryStats$HistoryItem` / `PowerStats.java`（android-36 分支逐行核对）
> 状态（2026-10-03）：**记录/事件/精确时间戳已全部解出**；扩展块（per-uid 功耗统计）内部结构待完成。

## 1. 容器

```
文件 = "GZIP" + 版本字节(2) + 2 字节 + gzip 流（偏移 8 处为 1f 8b）
解压后:
  [0..31] 头部 32 字节:
    u32 @0   标签池计数（不是记录数！样本恒为 214；等于该文件 writer 会话中已分配 tag 数）
    u32 @4   会话起始 uptime 毫秒
    u32 @8   0
    u32 @12  会话结束 uptime 毫秒
    u32 @16  0
    u32 @20  常量 65965816
    u32 @24  0
    u32 @28  数据区长度
  [32..]   记录流（Parcel 字节流；u32 无对齐、字符串 4 字节对齐）
```

## 2. firstToken（每条记录的第一个 u32）

| 位 | 含义 |
|---|---|
| bit0-18 | 时间增量 dt（ms）。`0x7FFFD`=本条为 ABS 完整记录；`0x7FFFE`=后跟 i32 增量；`0x7FFFF`=后跟 i64 增量 |
| bit19 | BATT：后跟 batteryLevelInt（bit1=电压/温度溢出；bit0=后跟 17×u32 StepDetails + string8） |
| bit20 | STATE：后跟 stateInt（电池状态/健康/充电器 + 高频 states 高位） |
| bit21 | STATE2：后跟 states2（bit17=后面有"扩展块"） |
| bit22 | WAKELOCK：后跟索引对（低16=wakelock、高16=wakeReason；索引带 0x8000=内联 tag=string16+u32 uid；0xFFFF=无） |
| bit23 | EVENT：后跟 codeAndIndex（低16=事件码、高16=tag 索引，同样 0x8000 内联规则） |
| bit24 | CHARGE：后跟 u32 |
| 之后 | 恒有 modemRail(f64) + wifiRail(f64)（各 8 字节） |

## 3. ABS 记录（firstToken==0x7FFFD）

```
time u64 | bat u32(cmd 低8位、batteryLevel 位8-15、status/health/plug 位16-27、三枚"tag存在"标志位28-30)
| bat2 u32(temp 低16/volt 高16) | batteryChargeUah u32 | modemRail f64 | wifiRail f64
| states u32 | states2 u32
| [wakelock tag 内联 string16+uid] [wakeReason tag 内联] [事件: u32 code + tag 内联]
| cmd∈{5,7} 时再跟 u64 currentTime
```

## 4. 字符串与事件

- `string16` = `[i32 字符数][UTF-16LE][u16 NUL]`，**整体 4 字节对齐**（不足补 2 字节）。
  ⚠️ 对齐规则是解析成败的关键：procstate-change（16 字符）需补 2 字节，CONNECTED（9 字符）恰好对齐不需要。
- `string8` = `[i32 字节数][UTF-8][u8 NUL]` 再对齐 4（StepDetails 的 statSubsystemPowerState 用）。
- 内联 tag = `string16 + u32 uid`；非首次出现只写池索引（文字不在本文件，需回退到池）。
- 事件码示例：0x0009=CONNECTED、0x000E=procstate-change、0x000F=mark.via、
  0x8011/0x4011/0x8006/0x4006=START/FINISH 类（0x8000=START、0x4000=FINISH 位）。
  事件 tag 字符串本身就是可读文本（如 `137a963 ALARM_ACTION(10000)/u0/0x1055`）。

## 5. 扩展块（states2 bit17 置位时）

```
[u32 extFlags]
  bit0 描述符： [u32 firstWord(版本/数组长度位域)] [u32 组件ID] [string16 name]
              [u32 stateLabels 数] ×(u32 key + string16 label)
              extras： [u32 长度][BNDL 数据…]（内部为 Bundle 序列化；含 format-* 图例键）
  bit1 功耗统计（PowerStats，格式见 AOSP PowerStats.writeToParcel：长度前缀 + 组件ID + duration +
               varint 数组；varint 以 4 字节字打包、结尾余字节丢弃）
  bit2 进程状态变更：[u32 bits][大uid 时再 u32]
```

✅ **已解决（2026-10-03）**：
- Bundle（BNDL）是**自定界**的：[len][BNDL][版本][条目…]，**0 长度键为终止符**；
  之前"8 字节谜团"是因为按 len 字段粗略跳跃时会停在 bundle 中间——正确做法是从
  "BNDL" 魔数开始按条目解析到终止符（嵌套 MAP/LIST/BUNDLE 递归），不要依赖 len 字段；
- 终止符之后紧跟 `[u32 长度][PowerStats 内容]`（内容 = 组件ID + duration + varint 数组）；
- descriptor 仅在**该组件首次出现**时写入，后续扩展块只有 extFlags/procstate；
- 未知类型（parcelable 等）会抛错，由外层"软恢复"（时间窗校验 + 重同步）兜底。

## 6. 验证情况（2026-10-03）

- ✅ `244572094.bh`：全文件无扩展块样本，**完美解码**：911 条记录、417 个命名事件、末条时间与头部 end 完全一致。
- ✅ 早期记录（ALARM_ACTION/CONNECTED/procstate-change/mark.via）时间戳与字节级反推完全一致（+0/+4984/+405/+9864ms）。
- ⚠️ 含扩展块的会话：在扩展处会丢失尾部（重同步后可继续但时间线不完整）——待完成第 5 节。
- 解析器实现：`app/.../data/bh/BhParserV3.kt`；测试：`app/src/test/.../BhParserV3Test.kt`
  （`./gradlew :app:testDebugUnitTest --tests "*BhParserV3*"`）。
- 真机会话完成率：设备样本 **7/8**，入库端点实测 **182/240** 会话完整解码（其余按近似回退）。

## 7. 下一步（时间线功能的前置）

1. 定论扩展块 8 字节之谜（对同一文件的两次扩展块做差分，或补全 Bundle 尾部语义）→ 全量文件完美解码。
2. 会话事件池补充：非内联 tag 的事件需跨文件维护 tag 池（同一 boot 会话内文件连续）。
3. 合并时间线 + 自定义分割（按小时 / 按息屏段 / 每 N 分钟）：数据层 `BhTimeline`，UI 在系统电池历史页加"时间线"标签。
