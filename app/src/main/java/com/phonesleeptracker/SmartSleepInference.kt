package com.phonesleeptracker

import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Multi-signal phone-only sleep estimator.
 *
 * This produces an *estimate* from behavioral proxies. It does **not** measure
 * sleep stages, heart rate, SpO2, or any clinical metric.
 *
 * Pipeline:
 * 1. Collect & sort signals
 * 2. Extract meaningful activity timestamps
 * 3. Generate inactivity candidates (4h–14h)
 * 4. Reject impossible / low-quality candidates
 * 5. Score remaining candidates with transparent features
 * 6. Merge brief interruptions inside likely sleep periods
 * 7. Return the highest-confidence session
 */
object SmartSleepInference {

    private const val MIN_GAP_MINUTES = 240L          // 4 hours
    private const val MAX_GAP_MINUTES = 14L * 60L     // 14 hours
    private const val MAX_MERGE_INTERRUPTION_MINUTES = 8L
    private const val MIN_MERGE_CONFIDENCE = 55

    data class Result(
        val session: SleepSession,
        val scoreBreakdown: Map<String, Int>
    )

    data class PersonalStats(
        val typicalBedtime: LocalTime? = null,
        val typicalWakeTime: LocalTime? = null,
        val meanDurationMinutes: Long? = null
    )

    fun infer(
        signals: List<PhoneSignal>,
        typicalSleepStart: LocalTime = LocalTime.of(21, 0),
        typicalWakeTime: LocalTime = LocalTime.of(10, 0),
        personalStats: PersonalStats = PersonalStats()
    ): Result? {
        if (signals.size < 2) return null

        val sorted = signals.sortedBy { it.time }
        val activityTimes = sorted
            .filter { it.type == PhoneSignal.Type.APP_RESUMED || it.type == PhoneSignal.Type.SCREEN_ON }
            .map { it.time }
            .distinct()
            .sorted()

        if (activityTimes.size < 2) return null

        // 1. Generate raw candidates from consecutive activity pairs
        val rawCandidates = activityTimes.zipWithNext().mapNotNull { (prev, next) ->
            val minutes = Duration.between(prev, next).toMinutes()
            if (minutes in MIN_GAP_MINUTES..MAX_GAP_MINUTES) {
                Candidate(prev, next, minutes)
            } else null
        }

        if (rawCandidates.isEmpty()) return null

        // 2. Merge brief interruptions that sit inside likely sleep windows
        val merged = mergeFragmentedSessions(rawCandidates, sorted)

        // 3. Score every surviving candidate
        val scored = merged.map { candidate ->
            val nearby = sorted.filter {
                !it.time.isBefore(candidate.start.minusMinutes(45)) &&
                    !it.time.isAfter(candidate.end.plusMinutes(45))
            }
            val breakdown = scoreWindow(
                start = candidate.start,
                end = candidate.end,
                nearby = nearby,
                defaultSleepStart = typicalSleepStart,
                defaultWakeTime = typicalWakeTime,
                personalStats = personalStats
            )
            val score = breakdown.values.sum().coerceIn(0, 100)
            Result(SleepSession(candidate.start, candidate.end, score), breakdown)
        }

        // 4. Prefer the highest confidence; break ties by longer duration
        return scored.maxWithOrNull(
            compareBy<Result> { it.session.confidence }
                .thenBy { it.session.durationMinutes }
        )
    }

    private data class Candidate(
        val start: LocalDateTime,
        val end: LocalDateTime,
        val durationMinutes: Long
    )

    /**
     * If a short interruption occurs inside what would otherwise be a long
     * high-probability sleep window, merge the surrounding periods.
     */
    private fun mergeFragmentedSessions(
        candidates: List<Candidate>,
        allSignals: List<PhoneSignal>
    ): List<Candidate> {
        if (candidates.size < 2) return candidates

        val result = mutableListOf<Candidate>()
        var i = 0
        while (i < candidates.size) {
            var current = candidates[i]
            var j = i + 1
            while (j < candidates.size) {
                val next = candidates[j]
                val gapMinutes = Duration.between(current.end, next.start).toMinutes()
                // Only merge very short interruptions that sit between two solid gaps
                if (gapMinutes in 1..MAX_MERGE_INTERRUPTION_MINUTES) {
                    val mergedDuration = Duration.between(current.start, next.end).toMinutes()
                    if (mergedDuration <= MAX_GAP_MINUTES) {
                        current = Candidate(current.start, next.end, mergedDuration)
                        j++
                        continue
                    }
                }
                break
            }
            result += current
            i = j
        }
        return result
    }

    private fun scoreWindow(
        start: LocalDateTime,
        end: LocalDateTime,
        nearby: List<PhoneSignal>,
        defaultSleepStart: LocalTime,
        defaultWakeTime: LocalTime,
        personalStats: PersonalStats
    ): Map<String, Int> {
        val duration = Duration.between(start, end).toMinutes()
        val startTime = start.toLocalTime()
        val endTime = end.toLocalTime()

        // Duration score (max 30)
        val durationScore = when {
            duration >= 480 -> 30   // 8h+
            duration >= 420 -> 26   // 7h+
            duration >= 360 -> 22   // 6h+
            duration >= 300 -> 16   // 5h+
            else -> 10             // 4h+
        }

        // Nighttime score (max 28) – broader window ~21:00–10:00
        val nighttimeScore = if (isLikelyNighttime(startTime, defaultSleepStart, defaultWakeTime)) 28 else 6

        // Historical bedtime proximity (max 15)
        val bedtimeScore = personalStats.typicalBedtime?.let { typical ->
            val diffMinutes = minutesBetween(startTime, typical)
            when {
                diffMinutes <= 30 -> 15
                diffMinutes <= 60 -> 10
                diffMinutes <= 90 -> 5
                else -> 0
            }
        } ?: 8 // neutral when no history yet

        // Historical wake proximity (max 12)
        val wakeScore = personalStats.typicalWakeTime?.let { typical ->
            val diffMinutes = minutesBetween(endTime, typical)
            when {
                diffMinutes <= 30 -> 12
                diffMinutes <= 60 -> 8
                diffMinutes <= 90 -> 4
                else -> 0
            }
        } ?: 6

        // Screen quiet (max 10)
        val hadScreenOn = nearby.any { it.type == PhoneSignal.Type.SCREEN_ON }
        val screenQuietScore = if (!hadScreenOn) 10 else 2

        // Charging present (max 10)
        val charged = nearby.any { it.type == PhoneSignal.Type.CHARGING_START }
        val chargingScore = if (charged) 10 else 0

        // Duration consistency with personal mean (max 5)
        val consistencyScore = personalStats.meanDurationMinutes?.let { mean ->
            val diff = kotlin.math.abs(duration - mean)
            when {
                diff <= 45 -> 5
                diff <= 90 -> 3
                else -> 0
            }
        } ?: 2

        return mapOf(
            "duration" to durationScore,
            "nighttime" to nighttimeScore,
            "historical_bedtime" to bedtimeScore,
            "historical_wake" to wakeScore,
            "screen_quiet" to screenQuietScore,
            "charging" to chargingScore,
            "duration_consistency" to consistencyScore
        )
    }

    private fun isLikelyNighttime(
        time: LocalTime,
        sleepStart: LocalTime,
        wakeTime: LocalTime
    ): Boolean {
        return if (sleepStart <= wakeTime) {
            !time.isBefore(sleepStart) && !time.isAfter(wakeTime)
        } else {
            // crosses midnight
            !time.isBefore(sleepStart) || !time.isAfter(wakeTime)
        }
    }

    /** Absolute minutes between two LocalTimes, handling day wrap. */
    private fun minutesBetween(a: LocalTime, b: LocalTime): Long {
        val diff = Duration.between(a, b).toMinutes()
        val abs = kotlin.math.abs(diff)
        return minOf(abs, 1440 - abs)
    }
}
