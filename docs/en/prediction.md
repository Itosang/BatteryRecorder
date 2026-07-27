# Battery Life Prediction

## Entry

The Battery life prediction card on the home screen is the prediction entry.
Tap the card to open prediction details.

> The prediction card displays a score that reflects the reliability of the current result.

To adjust prediction behavior, open the Prediction section in Settings and review:

- Exclude high-load apps
- Sample count
- Enable weighted algorithm
- Weight strength

## Why "Insufficient Data" Appears

Battery life prediction requires at least `3` valid records before it can produce a result.
If there are not enough historical samples, the home screen displays Insufficient data.

> Per-app predictions require at least `20` minutes of total records for that app.

## Why More Samples Improve Stability

Predictions are based on historical discharge records.
With fewer records, a single unusual session, a short high-load period, or an incomplete sample has a greater effect on the result.

A result being available does not mean it is already stable. It usually becomes more reliable as more samples accumulate.

## What Affects Predictions

The following factors significantly affect prediction results:

- The number of valid historical records
- Whether the device is currently discharging
- Whether high-load apps have been excluded
- How closely current usage matches the historical samples
