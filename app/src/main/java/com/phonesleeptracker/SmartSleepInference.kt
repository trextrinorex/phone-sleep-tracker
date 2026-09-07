package com.phonesleeptracker

import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Multi-signal phone-only sleep estimator.
 *
 * Produces an *estimate* from behavioral proxies. It does **not** measure
 * sleep stages, heart rate, SpO₂, or any clinical metric.
 *
 * Pipeline:
 * 1. Collect & sort signals
 * 2. Extract meaningful activity timestamps
 * 3. Generate inactivity candidates (4h–14h)
 * 4. Merge brief interruptions
 * 5. Score with transparent, distribution-aware features
 * 6. Return the highest-confidence session + full breakdown
 */
object SmartSleepInference {

    private const val MIN_GAP_MINUTES = 240L
    private const val MAX_GAP_MINUTES = 14L * 60L
    private const val MAX_MERGE_INTERRUPTION_MINUTES = 8L

    data class Result(
        val session: SleepSession,
        val scoreBreakdown: Map<String, Int>,
        val maxPossible: Map<String, Int> = DEFAULT_MAX
    ) {
        val totalScore: Int get() = session.confidence
        val confidenceBand: String
            get() = when {
                session.confidence >= 80 -> "High"
                session.confidence >= 60 -> "Moderate"
                else -> "Low"
            }
    }

    /**
     * Robust personal statistics derived from recent nights.
     * Prefer median + median absolute deviation (MAD) over mean/std for resistance to outliers.
     */
    data class PersonalStats(
        val medianBedtime: LocalTime? = null,
        val bedtimeMadMinutes: Int = 0,
        val medianWakeTime: LocalTime? = null,
        val wakeMadMinutes: Int = 0,
        val medianDurationMinutes: Long? = null,
        val durationMadMinutes: Int = 0,
        val sampleSize: Int = 0
    ) {
        val hasEnoughHistory: Boolean get() = sampleSize >= 5
    }

    private val DEFAULT_MAX = mapOf(
        "duration" to 30,
        "nighttime" to 25,
        "bedtime_match" to 15,
        "wake_match" to 12,
        "screen_quiet" to 10,
        "charging" to 8,
        "pattern_match" to 5
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

        val rawCandidates = activityTimes.zipWithNext().mapNotNull { (prev, next) ->
            val minutes = Duration.between(prev, next).toMinutes()
            if (minutes in MIN_GAP_MINUTES..MAX_GAP_MINUTES) Candidate(prev, next, minutes) else null
        }
        if (rawCandidates.isEmpty()) return null

        val merged = mergeFragmentedSessions(rawCandidates)

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

    private fun mergeFragmentedSessions(candidates: List<Candidate>): List<Candidate> {
        if (candidates.size < 2) return candidates
        val result = mutableListOf<Candidate>()
        var i = 0
        while (i < candidates.size) {
            var current = candidates[i]
            var j = i + 1
            while (j < candidates.size) {
                val next = candidates[j]
                val gapMinutes = Duration.between(current.end, next.start).toMinutes()
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

        // Duration (max 30)
        val durationScore = when {
            duration >= 480 -> 30
            duration >= 420 -> 26
            duration >= 360 -> 22
            duration >= 300 -> 16
            else -> 10
        }

        // Nighttime (max 25) – broad window ~21:00–10:00
        val nighttimeScore = if (isLikelyNighttime(startTime, defaultSleepStart, defaultWakeTime)) 25 else 5

        // Bedtime match against personal median ± MAD (max 15)
        val bedtimeScore = scoreTimeMatch(
            candidate = startTime,
            median = personalStats.medianBedtime,
            madMinutes = personalStats.bedtimeMadMinutes,
            hasHistory = personalStats.hasEnoughHistory,
            maxScore = 15
        )

        // Wake match (max 12)
        val wakeScore = scoreTimeMatch(
            candidate = endTime,
            median = personalStats.medianWakeTime,
            madMinutes = personalStats.wakeMadMinutes,
            hasHistory = personalStats.hasEnoughHistory,
            maxScore = 12
        )

        // Screen quiet (max 10)
        val hadScreenOn = nearby.any { it.type == PhoneSignal.Type.SCREEN_ON }
        val screenQuietScore = if (!hadScreenOn) 10 else 2

        // Charging present in or near the window (max 8)
        val charged = nearby.any { it.type == PhoneSignal.Type.CHARGING_START }
        val chargingScore = if (charged) 8 else 0

        // Pattern / duration consistency with personal median (max 5)
        val patternScore = if (personalStats.hasEnoughHistory && personalStats.medianDurationMinutes != null) {
            val diff = abs(duration - personalStats.medianDurationMinutes)
            val mad = personalStats.durationMadMinutes.coerceAtLeast(20)
            when {
                diff <= mad -> 5
                diff <= mad * 2 -> 3
                else -> 0
            }
        } else 2

        return mapOf(
            "duration" to durationScore,
            "nighttime" to nighttimeScore,
            "bedtime_match" to bedtimeScore,
            "wake_match" to wakeScore,
            "screen_quiet" to screenQuietScore,
            "charging" to chargingScore,
            "pattern_match" to patternScore
        )
    }

    /**
     * Score how well a candidate time matches the personal distribution.
     * Uses median + MAD. Falls back to a mild neutral score when history is insufficient.
     */
    private fun scoreTimeMatch(
        candidate: LocalTime,
        median: LocalTime?,
        madMinutes: Int,
        hasHistory: Boolean,
        maxScore: Int
    ): Int {
        if (!hasHistory || median == null) return (maxScore * 0.45).roundToInt() // neutral

        val diff = minutesBetween(candidate, median)
        val mad = madMinutes.coerceAtLeast(15)

        return when {
            diff <= mad -> maxScore                    // inside 1 MAD → strong
            diff <= mad * 2 -> (maxScore * 0.65).roundToInt() // 1–2 MAD → moderate
            diff <= mad * 3 -> (maxScore * 0.3).roundToInt()  // 2–3 MAD → weak
            else -> 0
        }
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

    private fun minutesBetween(a: LocalTime, b: LocalTime): Long {
        val diff = Duration.between(a, b).toMinutes()
        val absDiff = abs(diff)
        return minOf(absDiff, 1440 - absDiff)
    }
}
