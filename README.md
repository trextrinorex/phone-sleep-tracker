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

## Current status (v0.4.0)

### Build & CI foundation
- Gradle Wrapper configuration (Gradle 8.11.1)
- GitHub Actions CI: unit tests + `assembleDebug` on every push/PR to `main`
- Debug APK uploaded as CI artifact

### Inference engine
- Multi-signal candidate detection (4–14 h inactivity)
- Brief interruption merging
- Transparent multi-factor scoring with full breakdown
- BatteryManager charging signal as *supporting evidence only*
- Robust 14-night personalization using **median + MAD** (median absolute deviation)
- Overlap-based duplicate protection
- Confidence bands: High / Moderate / Low

### Evaluation
- Synthetic scenario suite (`InferenceEvaluationTest`) covering normal nights, late nights, early wake, daytime inactivity, short interruptions, long gaps, and personalization cases
- Baseline accuracy gate on controlled scenarios (raise the bar as the engine improves)

### Privacy
- On-device inference only
- Usage Access timestamps + battery status; no message, photo, microphone, or contact access

## How the MVP works

1. User grants Usage Access.
2. User enables Automatic Tracking.
3. WorkManager periodically reads activity timestamps (~40 h lookback) and current battery state.
4. `SmartSleepInference` generates candidates, merges short interruptions, scores with personal distributional stats, and keeps the best session.
5. High-confidence results are saved locally (with overlap deduplication).
6. Dashboard shows estimated sleep with a simple confidence band.

## Confidence breakdown (internal)

Each candidate produces a transparent score map, for example:

```
Duration          28/30
Nighttime         25/25
Bedtime match     14/15
Wake match         9/12
Screen quiet      10/10
Charging           8/8
Pattern match      4/5
-----------------------
Total             98/100  → High
```

The UI currently shows only the band ("High") while the full breakdown is available in logs and for future detail screens.

## Building

```bash
# Preferred: Android Studio (Hedgehog or newer)
# Or, after generating the full wrapper locally:
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

If the binary `gradle-wrapper.jar` is missing, either:
- Let Android Studio generate the wrapper, or
- Run `gradle wrapper --gradle-version 8.11.1` if you have Gradle installed, or
- Rely on the CI workflow which bootstraps Gradle via `setup-gradle`.

Minimum SDK 26, target / compile SDK 35, Java 17.

## Known limitations (honest)

- Charging history is currently limited to the present state observed at inference time. Full POWER_CONNECTED / DISCONNECTED persistence is a planned improvement.
- Phone left elsewhere, turned off, or dead battery still produces artificial inactivity.
- Long intentional phone-free periods (travel, studying) can look like sleep; the nighttime + personalization filters reduce but do not eliminate this.
- First phone interaction after waking is only a proxy for wake time.

## Recommended next milestones (in order)

1. Persist real charging start/stop events (BroadcastReceiver + small Room table) for true historical overlap.
2. Expand the synthetic evaluation suite to hundreds of generated scenarios and track false-positive / false-negative rates, bedtime error, duration error.
3. Optional detail UI that surfaces the confidence breakdown for power users / debugging.
4. Only after a strong, measurable heuristic baseline: consider an on-device model that must demonstrably beat the current algorithm.

## License

Experimental research / personal project software.
