# Phone Sleep Tracker v1.0.0

Privacy-first Android app that **estimates** sleep periods using only your smartphone.

No smartwatch, fitness band, ring, or external sensor required.

## Disclaimer

This application produces **estimated** sleep windows derived from passive phone behavior.  
It is **not** a medical device and does **not** measure sleep stages, heart rate, or blood oxygen.

Confidence scores are heuristic, not scientifically validated accuracy percentages.

## What works in v1.0.0

- Clean Material 3 UI with permanent non-medical disclaimer
- Usage Access onboarding
- Background tracking via WorkManager
- Multi-signal sleep inference (duration, nighttime, charging support, screen quiet, personal history)
- Robust 14-night personalization (median + MAD)
- Session merging for short interruptions
- Overlap-based duplicate protection
- Local Room database
- Transparent confidence breakdown (internal)
- Unit tests + synthetic evaluation suite
- GitHub Actions CI that builds both debug and release APKs

## Install on your phone (recommended ways)

### Method 1 – Android Studio (most reliable)
1. Install Android Studio (Hedgehog or newer).
2. Open this repository.
3. Let it sync Gradle.
4. Connect your phone (USB debugging enabled).
5. Click the green **Run** button.

### Method 2 – Download APK from GitHub Actions
1. Go to the **Actions** tab of this repository.
2. Open the latest green (successful) workflow run.
3. Download the **`app-debug`** artifact.
4. Unzip → transfer the `.apk` to your phone.
5. Open the APK and allow installation from unknown sources.

> The first CI run after this release should produce downloadable APKs automatically.

### After installing
1. Open the app.
2. Tap **Grant Usage Access** and enable the permission for Phone Sleep Tracker.
3. Return to the app and tap **Start tracking**.
4. Leave the app running in the background overnight.
5. Check the history the next morning.

## Privacy

- All inference runs on-device.
- Only activity timestamps and battery state are used.
- No messages, photos, microphone, contacts, or passwords are accessed.
- Sleep history is stored only in the local Room database.

## Tech stack

- Kotlin
- Jetpack Compose + Material 3
- Room
- WorkManager
- Android UsageStats + BatteryManager
- JUnit

## Building from source

```bash
gradle :app:testDebugUnitTest
gradle :app:assembleDebug
gradle :app:assembleRelease   # unsigned
```

Minimum SDK 26 · Target / Compile SDK 35 · Java 17

## Known limitations

- Charging history is currently based on present state at inference time (full historical POWER_CONNECTED persistence is planned).
- Long intentional phone-free periods can still produce false candidates; nighttime + personalization filters reduce this.
- First phone interaction after waking is only a proxy for true wake time.

## License

Experimental personal / research software. Use at your own risk.
