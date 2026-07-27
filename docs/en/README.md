# BatteryRecorder User Guide

BatteryRecorder is an app focused on power recording and battery life prediction.
It aims to record power accurately with minimal overhead and provide the most accurate battery life predictions possible.

## What You Can Find Here

- How to start recording
- When calibration is needed
- How to exclude high-load apps from predictions
- Why battery life predictions may be temporarily unavailable
- How to use the history list and record details

## Before You Start

- Start the service before expecting BatteryRecorder to record data.
- If power readings are inaccurate, check the Calibration guide first.
- If screen state or foreground app detection is incorrect, open Compatibility settings from the Server section and enable the corresponding polling option.
- Battery life prediction requires at least `3` valid records, and becomes more stable as more samples are collected.
- Tap the Battery life prediction card on the home screen to open prediction details.
- Swipe right on a history item to export it, or swipe left to delete it.
- The first three legends at the bottom of Record details are interactive. The Power legend cycles through `Raw -> Fitted -> Hidden`, while the Battery and Temperature legends toggle their curves.

## Settings Sections

The Settings screen currently contains four main sections:

- General
- Server
- Logs
- Prediction

Battery life prediction settings are mainly located under Prediction.
Power display and calibration settings are mainly located under General.

## Contents

1. [Getting Started](getting-started.md)
2. [Calibration](calibration.md)
3. [Excluding High-load Apps](high-load-apps.md)
4. [Battery Life Prediction](prediction.md)
5. [History](history.md)
6. [Record Detail Charts](record-detail.md)
