# BatteryRecorder

English | [简体中文](README.md)

## Introduction

A battery power recording app designed to capture more accurate power data with lower CPU overhead and provide reliable battery life estimates.

## Features

- Fine-grained recording settings to suit your needs
- Raw and trend power charts for different analysis scenarios
- Hide individual chart curves
- Screen-off power recording to explore less familiar usage scenarios

## [Documentation](https://battrec.itosang.com/#/en/)

## Contributing

The BatteryRecorder repository contains the source code for an older version. To obtain the latest source code, contact the maintainer using the contact information in the latest app, or open an issue in this repository.

## ToDo

### app

- [ ] Improve home screen card colors
- [ ] Use coulomb counter readings instead of state of charge (SOC) as the basis for battery life prediction
- [ ] Add a Shizuku startup guide
- [ ] Fix power normalization

### server

- [ ] Add an option to display units with power values in notifications and the status bar
- [ ] Add persistent recording filters

> User request: `充放电记录能否设置一个常开的过滤规则：比如我的场景下，开了充电保护95就停止充电，然后上班时又是丢在无线充上面的，经常用一会放一会，记录就会一堆个位数的充电记录`
> Translated: “Could charging and discharging records have a persistent filter? Charging protection stops charging at 95%, and I leave my phone on a wireless charger at work. Repeated short periods of charging and discharging produce many charging records with only single-digit battery percentage changes.”
>
> The details still need to be discussed.

- [ ] Record coulomb counter readings
- [ ] Make the floating window size adjustable
- [ ] ~~Use screen brightness to determine whether the screen is on~~

### ext

- [ ] Boot power curve

## Download

- [GitHub Releases](https://github.com/Itosang/BatteryRecorder/releases)

## Feedback

- [GitHub Issues](https://github.com/Itosang/BatteryRecorder/issues)
- [Telegram](https://t.me/BatteryRecorder)
- [QQ Channel](https://pd.qq.com/s/9atqtprl6)
- Contact the maintainer directly from the app’s About page (**not recommended**)

## Acknowledgements

- [RikkaW/HiddenApi](https://github.com/RikkaW/HiddenApi)
