# Phone Sleep Tracker

Privacy-first Android app that **estimates** sleep periods using only the smartphone.

No smartwatch, fitness band, ring, or external sensor is required.

## Important disclaimer

This application produces **estimated** sleep windows derived from passive phone behavior.
It is **not** a medical device and does **not** measure:

- REM / deep / light sleep stages
- Heart rate
- Blood oxygen (SpO₂)
- Respiratory rate

Never treat the numbers as clinical truth. Confidence scores are heuristic, not scientifically validated accuracy percentages.

## What the MVP currently does

- Jetpack Compose UI with clear onboarding and permanent non-medical disclaimer
- Reliable Usage Access permission detection and settings deep-link
- Passive collection of foreground-app and screen-interactive timestamps via UsageStats
- Multi-signal sleep inference engine (`SmartSleepInference`):
  - Detects 4–14 hour inactivity candidates
  - Transparent scoring (duration, nighttime, charging, screen quiet, historical proximity)
  - Merges brief interruptions inside likely sleep windows
  - Rejects unrealistically long gaps and strongly prefers nighttime windows
- Simple on-device personalization from recent stored sessions (typical bedtime / wake / duration)
- Local Room database with overlap-based duplicate protection
- Periodic WorkManager background processing
- Sleep history dashboard showing estimated duration, time window, and confidence band
- Unit tests for core inference rules

## How it works (high level)

1. User grants Usage Access (special Android setting).
2. User enables Automatic Tracking.
3. WorkManager periodically reads lightweight activity timestamps from the previous ~40 hours.
4. The inference engine builds candidate inactivity windows, scores them, merges short interruptions, and keeps the highest-confidence nightly session.
5. High-confidence results are saved locally (with overlap deduplication).
6. The dashboard displays estimated sleep sessions.

All inference runs on-device. The app does not need message contents, keyboard input, photos, microphone, or contacts.

## Tech stack

- Kotlin
- Jetpack Compose + Material 3
- Android UsageStats APIs
- Room
- WorkManager
- JUnit

## Privacy design principles

- Prefer metadata timestamps over content
- Keep raw signals and inference on-device
- Store only aggregated sleep-session results by default
- Never request message, photo, microphone, or contact permissions for the core feature

## Known limitations (honest)

- Phone left in another room / turned off / dead battery creates artificial inactivity → can produce false positives or false negatives.
- Long intentional phone-free periods (studying, travel, meetings) can look like sleep.
- First-phone-interaction after waking is only a proxy for wake time; true physiological wake may be earlier.
- Charging signals improve confidence when available, but UsageEvents does not always surface them reliably on every OEM.
- Personalization needs several nights of data before it becomes useful.

## Building

Open the project in Android Studio (Hedgehog or newer recommended). Gradle wrapper is not yet checked in; use Android Studio’s built-in Gradle or generate the wrapper locally.

```bash
./gradlew :app:assembleDebug
```

Minimum SDK 26, target / compile SDK 35.

## Next milestones

- Stronger signal collection (BatteryManager charging broadcasts, interactive state)
- Richer personalization (rolling mean + std-dev of bedtime/wake)
- Better handling of travel / multi-day gaps
- Weekly consistency metrics and simple insights
- Optional on-device ML model once sufficient labeled data exists
- Data export / delete controls
- Gradle wrapper + CI

## License

This is an early research / personal project. Treat it as experimental software.
