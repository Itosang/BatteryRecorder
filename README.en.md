[English](README.en.md) | [简体中文](README.md)

# BatteryRecorder

## Introduction

A battery power recording app designed to capture more accurate power data with lower CPU overhead and provide relatively accurate battery life predictions.

## Features

- Fine-grained recording configuration to better match your needs
- Raw and trend power charts for different analysis scenarios
- Custom curve hiding
- Screen-off power recording to explore unknown scenarios

## Documentation

- [Docs](https://battrec.itosang.com/)

## ToDo

### app

- [x] ADB startup user guide
- [x] Per-app battery life prediction
- [x] Per-scene battery life prediction
- [x] Chart zoom in and zoom out
- [x] Temporarily hide a specific curve
- [x] Auto start on `BOOT_COMPLETED`
- [x] Log export

### server

- [x] Fix Monitor wake lock issue (actually caused by callback blocking)
- [x] Monitor app installation and restart Server at the proper time
- [x] Restore the previous Server state after restarting server
- [x] Additional voltage recording
- [x] Record battery temperature from `/sys/class/power_supply/battery/temp`
- ~~[ ] Change screen-on detection to use screen brightness~~
- [x] Log export
- [ ] Optimize `needDeleteSegment` logic

### ext

- [ ] Boot power curve
- ~~[ ] Reset on charging~~

## Download

- [GitHub Releases](https://github.com/Itosang/BatteryRecorder/releases)
- [GitHub Actions](https://github.com/Itosang/BatteryRecorder/actions)

## Donate

If this project helps you, you are welcome to support its maintenance through the QR code below. The income will be used for domain renewal and daily maintenance.

<img src="app/src/main/res/drawable-nodpi/donate_qr.jpg" alt="Donation QR code" width="320" />

## Feedback

- [QQ Group](https://qm.qq.com/q/6q5etoYAuc)
- [GitHub Issues](https://github.com/Itosang/BatteryRecorder/issues) (recommended)

## Acknowledgements

- [RikkaW/HiddenApi](https://github.com/RikkaW/HiddenApi)
