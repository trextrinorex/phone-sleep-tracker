package com.phonesleeptracker

import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Lightweight fallback phone-only sleep inference.
 * Produces an estimate, not a medical measurement.
 * Prefer SmartSleepInference for multi-signal scoring.
 */
object SleepInferenceEngine {
    private const val MIN_INACTIVITY_MINUTES = 240L
    private const val HIGH_CONFIDENCE_MINUTES = 360L

    fun infer(
        lastActivity: LocalDateTime,
        firstActivity: LocalDateTime,
        typicalSleepStart: LocalTime = LocalTime.of(21, 0),
        typicalWakeTime: LocalTime = LocalTime.of(10, 0)
    ): SleepSession? {
        val duration = Duration.between(lastActivity, firstActivity).toMinutes()
        if (duration < MIN_INACTIVITY_MINUTES || duration > 14 * 60) return null

        val nighttime = isLikelyNighttime(
            lastActivity.toLocalTime(),
            typicalSleepStart,
            typicalWakeTime
        )
        val confidence = when {
            nighttime && duration >= HIGH_CONFIDENCE_MINUTES -> 88
            nighttime -> 75
            duration >= 480 -> 55
            else -> 40
        }
        return SleepSession(lastActivity, firstActivity, confidence)
    }

    private fun isLikelyNighttime(
        time: LocalTime,
        sleepStart: LocalTime,
        wakeTime: LocalTime
    ): Boolean {
        return if (sleepStart <= wakeTime) {
            !time.isBefore(sleepStart) && !time.isAfter(wakeTime)
        } else {
            !time.isBefore(sleepStart) || !time.isAfter(wakeTime)
        }
    }
}
