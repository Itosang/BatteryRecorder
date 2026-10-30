# BatteryRecorder

[English](README.en.md) | 简体中文

## 介绍

一个电池功率记录 App，旨在使用更低的 CPU 开销来记录更精确的功率数据，并为用户提供较为精准的续航预测。

## 功能

- 精细化记录调参，更切合你的需求
- 原始、趋势功耗图，各种需求都能覆盖
- 自定义曲线隐藏
- 息屏功耗记录，探索🧐未知场景

## [文档](https://battrec.itosang.com/)

## 贡献

`BatteryRecorder` 仓库存放的是旧版本源码，如需要最新版源码，请在最新版 app 找联系方式联系维护者，或者直接在本仓库提 Issue

## ToDo

### app

- [ ] 主页卡片配色优化
- [ ] 以库仑计代 SOC 为基准的的续航预测
- [ ] 接入 Shizuku 启动引导
- [ ] 修复功率 normalize

### server

- [ ] 通知栏/状态栏功率可选带单位
- [ ] 常开记录过滤
> 用户描述：`充放电记录能否设置一个常开的过滤规则：比如我的场景下，开了充电保护95就停止充电，然后上班时又是丢在无线充上面的，经常用一会放一会，记录就会一堆个位数的充电记录`
> 具体细节有待沟通
- [ ] 库仑记录
- [ ] 悬浮窗大小调节
- [ ] ~~亮屏判断改为以屏幕亮度为准~~

### ext

- [ ] 开机功耗曲线

## 下载

- [GitHub Releases](https://github.com/Itosang/BatteryRecorder/releases)

## 反馈

- [GitHub Issues](https://github.com/Itosang/BatteryRecorder/issues)
- [Telegram](https://t.me/BatteryRecorder)
- [QQ 频道](https://pd.qq.com/s/9atqtprl6)
- App 关于页直接联系维护者 (**不推荐**)

## 鸣谢

- [RikkaW/HiddenApi](https://github.com/RikkaW/HiddenApi)
