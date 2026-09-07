package com.phonesleeptracker

import java.time.LocalDateTime

class SleepTrackerRepository(private val activityReader: UsageActivityReader) {
    fun inferFromActivity(activityTimes: List<LocalDateTime>): SleepSession? {
        if (activityTimes.size < 2) return null
        return activityTimes.sorted().zipWithNext()
            .mapNotNull { (previous, next) -> SleepInferenceEngine.infer(previous, next) }
            .maxWithOrNull(compareBy<SleepSession> { it.confidence }.thenBy { it.durationMinutes })
    }

    fun inferRecentSleep(endMillis: Long = System.currentTimeMillis()): SleepSession? {
        val startMillis = endMillis - 36L * 60L * 60L * 1000L
        val result = SmartSleepInference.infer(activityReader.phoneSignals(startMillis, endMillis))
        return result?.session ?: inferFromActivity(activityReader.foregroundActivityTimes(startMillis, endMillis))
    }
}
