package com.phonesleeptracker

import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * First-pass phone-only sleep inference. This produces an estimate, not a
 * medical measurement. Later versions can replace scoring with on-device ML.
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
        if (duration < MIN_INACTIVITY_MINUTES) return null

        val nighttime = isLikelyNighttime(lastActivity.toLocalTime(), typicalSleepStart, typicalWakeTime)
        val confidence = when {
            nighttime && duration >= HIGH_CONFIDENCE_MINUTES -> 90
            nighttime -> 78
            duration >= 480 -> 62
            else -> 45
        }
        return SleepSession(lastActivity, firstActivity, confidence)
    }

    private fun isLikelyNighttime(time: LocalTime, sleepStart: LocalTime, wakeTime: LocalTime): Boolean {
        return if (sleepStart <= wakeTime) {
            time >= sleepStart && time <= wakeTime
        } else {
            time >= sleepStart || time <= wakeTime
        }
    }
}
