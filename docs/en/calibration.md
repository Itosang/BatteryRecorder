# Calibration

## Entry

Open Settings. Under Battery calibration, you will find:

- Dual-cell series
- Prompt on suspected abnormal status
- Show discharge as positive
- Use mAh instead of Wh (discharge details only)
- Current unit calibration
- Battery status calibration

## When Calibration Is Needed

Check calibration first if you encounter any of these situations:

- **You are using the app for the first time**
- Power readings are clearly abnormal
- Power is always **0.00W**
- Current record always shows **Unknown** or **Not charging**
- No record files are created

## What Calibration Affects

Current calibration affects power conversion and display inside the app.
If the conversion scale is incorrect, none of the app's features can work correctly.

Battery status calibration affects how the recorder determines the battery state.
If the detected state is incorrect, record files may never be created.

## Options

### Dual-cell Series

Use this option to correct display conversion on dual-cell devices.
Do not enable it unless your device uses this battery configuration.

### Show Discharge as Positive

This option only changes how the sign is displayed for easier reading.
It does not change how samples are recorded.

### Current Unit Calibration

Open Current unit calibration. Connect the recording service, unplug the power supply, and tap Start calibration. The page uses 10 new discharge samples to determine the current unit and direction. It saves the multiplier when measurement succeeds and shows a retry message if measurement fails.

Use the plus and minus buttons to adjust the multiplier manually, or tap Reset to restore the default. Changes are saved immediately and reflected in the live power preview. With the correct calibration, charging shows a positive value and discharging shows a negative value.

### Battery Status Calibration

This option changes how the recorder determines the battery state.
If no record files are created, or Current record always shows **Unknown** or **Not charging**, adjust this option while the problem is present to correct the detected state.
