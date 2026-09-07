package com.phonesleeptracker

import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class SleepTrackerRepository(
    private val activityReader: UsageActivityReader
) {

    fun inferFromActivity(activityTimes: List<LocalDateTime>): SleepSession? {
        if (activityTimes.size < 2) return null
        return activityTimes.sorted().zipWithNext()
            .mapNotNull { (previous, next) -> SleepInferenceEngine.infer(previous, next) }
            .maxWithOrNull(compareBy<SleepSession> { it.confidence }.thenBy { it.durationMinutes })
    }

    /**
     * Main entry point used by the background worker.
     * Looks at the last ~40 hours of signals and returns the best sleep estimate.
     */
    fun inferRecentSleep(
        endMillis: Long = System.currentTimeMillis(),
        personalStats: SmartSleepInference.PersonalStats = SmartSleepInference.PersonalStats()
    ): SleepSession? {
        val startMillis = endMillis - 40L * 60L * 60L * 1000L
        val signals = activityReader.phoneSignals(startMillis, endMillis)

        val smart = SmartSleepInference.infer(
            signals = signals,
            personalStats = personalStats
        )
        if (smart != null) return smart.session

        // Fallback to the simpler engine
        return inferFromActivity(activityReader.foregroundActivityTimes(startMillis, endMillis))
    }

    companion object {
        /**
         * Build simple personal statistics from previously stored sessions.
         * Uses the most recent N nights. Returns empty stats when history is insufficient.
         */
        fun computePersonalStats(sessions: List<SleepSessionEntity>): SmartSleepInference.PersonalStats {
            if (sessions.size < 3) return SmartSleepInference.PersonalStats()

            val zone = ZoneId.systemDefault()
            val bedtimes = sessions.mapNotNull {
                try {
                    LocalDateTime.ofInstant(
                        java.time.Instant.ofEpochMilli(it.startEpochMillis), zone
                    ).toLocalTime()
                } catch (_: Exception) { null }
            }
            val wakeTimes = sessions.mapNotNull {
                try {
                    LocalDateTime.ofInstant(
                        java.time.Instant.ofEpochMilli(it.endEpochMillis), zone
                    ).toLocalTime()
                } catch (_: Exception) { null }
            }
            val durations = sessions.map { it.durationMinutes }

            fun averageTime(times: List<LocalTime>): LocalTime? {
                if (times.isEmpty()) return null
                // Convert to minutes past midnight, average, convert back
                val minutes = times.map { it.toSecondOfDay() / 60 }
                val avg = minutes.average().toInt()
                return LocalTime.of(avg / 60, avg % 60)
            }

            return SmartSleepInference.PersonalStats(
                typicalBedtime = averageTime(bedtimes),
                typicalWakeTime = averageTime(wakeTimes),
                meanDurationMinutes = durations.average().toLong()
            )
        }
    }
}
