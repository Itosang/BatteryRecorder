# Excluding High-load Apps

## Entry

Open Settings, then tap Exclude high-load apps under Prediction.

Here, "exclude" means that selected apps do not participate in battery life prediction statistics.

## When Apps Should Be Excluded

Exclude apps that keep power consumption high for long periods but should not affect your normal daily-use prediction, such as:

- Games
- Stress-testing tools
- Other specific long-running, high-load apps

## What This Setting Affects

This setting changes the statistical samples used for battery life prediction. It does not control whether individual power records are saved.

In other words:

- Records are still collected normally
- Foreground power consumption from selected apps is excluded from prediction statistics

## Recommendations

- Start with apps that you know regularly produce sustained high load
- After changing the selection, check whether the home-screen prediction and prediction details better match your normal usage

## Additional Information

The page automatically detects some apps that may be games, but only the apps you ultimately select are excluded.
