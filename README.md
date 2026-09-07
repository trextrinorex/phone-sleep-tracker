# Phone Sleep Tracker

Privacy-first phone-only sleep tracking app that estimates sleep duration from passive smartphone activity, without requiring a smartwatch or wearable.

## What is working now

- Android app shell with Jetpack Compose UI
- Usage Access onboarding and permission re-check
- Reliable Android UsageStats permission detection
- Passive foreground-app activity timestamp collection
- Sleep inference from 4+ hour inactivity gaps
- Broader nighttime confidence window (about 9 PM–10 AM)
- Automatic rejection of low-confidence daytime candidates for background saving
- Local Room database for estimated sleep sessions
- Periodic WorkManager background inference
- Sleep history dashboard with duration, time window, and confidence
- Unit tests for the core inference rules

## How the MVP works

1. User grants Usage Access.
2. User turns on Automatic Tracking.
3. WorkManager periodically reads lightweight foreground activity timestamps from the previous 36 hours.
4. The inference engine searches for long inactivity windows and scores them using duration and time of day.
5. High-confidence candidates are saved locally.
6. The dashboard displays estimated sleep sessions.

The app does **not** claim to measure sleep stages, heart rate, or other medical signals. It estimates likely sleep from phone behavior.

## Tech stack

- Kotlin
- Android
- Jetpack Compose
- Android UsageStats APIs
- Room
- WorkManager
- JUnit
- On-device inference architecture

## Privacy

The MVP is designed around data minimization. It uses activity timestamps rather than reading message contents, passwords, or other private content. Sleep-session data is stored locally in the app database by default.

## Next milestones

- Improve sleep-session merging and duplicate handling
- Add screen-interactive/non-interactive signals
- Personalize sleep/wake windows from user history
- Add a richer weekly analytics dashboard
- Add onboarding, privacy controls, and data export/delete
- Evaluate an on-device ML model against the rule-based baseline
