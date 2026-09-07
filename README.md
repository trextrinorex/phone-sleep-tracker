# Phone Sleep Tracker

Privacy-first phone-only sleep tracking app that estimates sleep duration from passive smartphone activity, without requiring a smartwatch or wearable.

## Vision

Turn ordinary smartphone activity into a useful estimate of when a person likely slept, while keeping sensitive activity data private.

## MVP

- Start/stop tracking from the app
- Observe lightweight phone-usage signals
- Detect long inactivity windows
- Distinguish likely nighttime sleep from ordinary phone inactivity
- Record estimated sleep sessions
- Show daily and weekly sleep history
- Keep tracking data on-device by default

## Current status

Early Android MVP scaffold. The first screen is in place; usage-event collection and sleep inference are the next implementation steps.

## Tech stack

- Kotlin
- Android
- Jetpack Compose
- Android UsageStats APIs
- Room (planned)
- On-device inference (planned)

## Privacy

The app should avoid collecting message contents, passwords, or unnecessary personal data. Sleep estimates should be clearly labeled as estimates rather than medical measurements.
