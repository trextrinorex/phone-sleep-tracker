package com.phonesleeptracker

import java.time.LocalDateTime

class SleepTrackerRepository(
    private val activityReader: UsageActivityReader
) {
    fun inferFromActivity(activityTimes: List<LocalDateTime>): SleepSession? {
        if (activityTimes.size < 2) return null

        val sorted = activityTimes.sorted()
        var best: SleepSession? = null

        sorted.zipWithNext().forEach { (previous, next) ->
            val candidate = SleepInferenceEngine.infer(previous, next)
            if (candidate != null && (best == null || candidate.durationMinutes > best!!.durationMinutes)) {
                best = candidate
            }
        }
        return best
    }

    fun inferRecentSleep(endMillis: Long = System.currentTimeMillis()): SleepSession? {
        val startMillis = endMillis - 36L * 60L * 60L * 1000L
        return inferFromActivity(activityReader.foregroundActivityTimes(startMillis, endMillis))
    }
}
