# Getting Started

## 1. Start the Service First

BatteryRecorder relies on a separate service process to collect data.
If the home screen is not connected to the service, follow its instructions and choose a startup mode:

- Start (Root)
- Start (ADB), preferably with the `nohup` command
- Start (Accessibility)

> [!NOTE]
> - The Accessibility service uses system APIs. It offers strong compatibility, but has higher overhead and is harder to keep alive.
>
> - The Root and ADB services access low-level APIs directly. They have lower overhead, run in a privileged process, and do not require the app to remain active in the background.
>
> **Only use Start (Accessibility) when the Root or ADB service cannot access the required low-level APIs.**

## 2. Confirm Recording on the Home Screen

After the service starts, the home screen displays:

- Current record
- Charging summary or Discharging summary
- Battery life prediction card

> The battery life prediction entry is hidden while the device is charging.

## 3. Required Setup

Open Settings and check these items first:

- Calibration-related options under General; see [Calibration](calibration.md)
- Exclude high-load apps under Prediction; see [Excluding High-load Apps](high-load-apps.md)

> The first group determines whether **power readings** are correct, while the second keeps **battery life predictions** accurate.

## 4. Where to Go Next

- To adjust power display, read [Calibration](calibration.md)
- To exclude games or other high-load apps, read [Excluding High-load Apps](high-load-apps.md)
- To adjust sampling, writing, or segmentation, open the Server section in Settings
- If screen state or foreground app detection is incorrect, open Compatibility settings from the Server section and enable the corresponding polling option
- If Realtime power notification is enabled but no notification appears, tap Manage Shell notification in the Server section and check whether the system disabled Shell notifications automatically
- To adjust log retention or log level, open the Logs section
- To understand prediction results, read [Battery Life Prediction](prediction.md)
- To manage history files, read [History](history.md)
- To learn chart interactions, read [Record Detail Charts](record-detail.md)
