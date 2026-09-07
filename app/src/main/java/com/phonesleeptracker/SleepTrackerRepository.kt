package com.phonesleeptracker

import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.abs

class SleepTrackerRepository(
    private val activityReader: UsageActivityReader
) {

    fun inferFromActivity(activityTimes: List<LocalDateTime>): SleepSession? {
        if (activityTimes.size < 2) return null
        return activityTimes.sorted().zipWithNext()
            .mapNotNull { (previous, next) -> SleepInferenceEngine.infer(previous, next) }
            .maxWithOrNull(compareBy<SleepSession> { it.confidence }.thenBy { it.durationMinutes })
    }

    fun inferRecentSleep(
        endMillis: Long = System.currentTimeMillis(),
        personalStats: SmartSleepInference.PersonalStats = SmartSleepInference.PersonalStats()
    ): SmartSleepInference.Result? {
        val startMillis = endMillis - 40L * 60L * 60L * 1000L
        val signals = activityReader.phoneSignals(startMillis, endMillis)

        val smart = SmartSleepInference.infer(
            signals = signals,
            personalStats = personalStats
        )
        if (smart != null) return smart

        // Fallback to the simpler engine (no breakdown)
        val simple = inferFromActivity(activityReader.foregroundActivityTimes(startMillis, endMillis))
        return simple?.let {
            SmartSleepInference.Result(
                session = it,
                scoreBreakdown = mapOf("fallback" to it.confidence)
            )
        }
    }

    companion object {
        private const val HISTORY_LIMIT = 14

        /**
         * Build robust personal statistics from previously stored sessions.
         * Uses median + median absolute deviation (MAD) for resistance to outlier nights.
         */
        fun computePersonalStats(sessions: List<SleepSessionEntity>): SmartSleepInference.PersonalStats {
            val recent = sessions.take(HISTORY_LIMIT)
            if (recent.size < 3) return SmartSleepInference.PersonalStats(sampleSize = recent.size)

            val zone = ZoneId.systemDefault()

            fun toLocalTime(epoch: Long): LocalTime? = try {
                LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(epoch), zone).toLocalTime()
            } catch (_: Exception) { null }

            val bedtimes = recent.mapNotNull { toLocalTime(it.startEpochMillis) }
            val wakeTimes = recent.mapNotNull { toLocalTime(it.endEpochMillis) }
            val durations = recent.map { it.durationMinutes }

            return SmartSleepInference.PersonalStats(
                medianBedtime = medianTime(bedtimes),
                bedtimeMadMinutes = madMinutes(bedtimes),
                medianWakeTime = medianTime(wakeTimes),
                wakeMadMinutes = madMinutes(wakeTimes),
                medianDurationMinutes = medianLong(durations),
                durationMadMinutes = madLong(durations),
                sampleSize = recent.size
            )
        }

        private fun medianTime(times: List<LocalTime>): LocalTime? {
            if (times.isEmpty()) return null
            // Convert to minutes past midnight, take median, convert back
            val minutes = times.map { it.toSecondOfDay() / 60 }.sorted()
            val mid = minutes[minutes.size / 2]
            return LocalTime.of(mid / 60, mid % 60)
        }

        private fun madMinutes(times: List<LocalTime>): Int {
            if (times.size < 2) return 30 // default spread
            val median = medianTime(times) ?: return 30
            val deviations = times.map { minutesBetween(it, median).toInt() }.sorted()
            return deviations[deviations.size / 2].coerceAtLeast(15)
        }

        private fun medianLong(values: List<Long>): Long? {
            if (values.isEmpty()) return null
            val sorted = values.sorted()
            return sorted[sorted.size / 2]
        }

        private fun madLong(values: List<Long>): Int {
            if (values.size < 2) return 40
            val med = medianLong(values) ?: return 40
            val deviations = values.map { abs(it - med).toInt() }.sorted()
            return deviations[deviations.size / 2].coerceAtLeast(20)
        }

        private fun minutesBetween(a: LocalTime, b: LocalTime): Long {
            val diff = java.time.Duration.between(a, b).toMinutes()
            val absDiff = abs(diff)
            return minOf(absDiff, 1440 - absDiff)
        }
    }
}
