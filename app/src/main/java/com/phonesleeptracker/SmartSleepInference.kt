package com.phonesleeptracker

import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

/** Multi-signal sleep estimator. It estimates sleep; it does not measure sleep stages. */
object SmartSleepInference {
    private const val MIN_GAP_MINUTES = 240L
    private const val MAX_GAP_MINUTES = 14L * 60L

    data class Result(val session: SleepSession, val scoreBreakdown: Map<String, Int>)

    fun infer(
        signals: List<PhoneSignal>,
        typicalSleepStart: LocalTime = LocalTime.of(21, 0),
        typicalWakeTime: LocalTime = LocalTime.of(10, 0)
    ): Result? {
        if (signals.size < 2) return null
        val sorted = signals.sortedBy { it.time }
        val activity = sorted.filter { it.type == PhoneSignal.Type.APP_RESUMED }
        if (activity.size < 2) return null

        var best: Result? = null
        activity.zipWithNext().forEach { (previous, next) ->
            val minutes = Duration.between(previous.time, next.time).toMinutes()
            if (minutes !in MIN_GAP_MINUTES..MAX_GAP_MINUTES) return@forEach
            val nearby = sorted.filter {
                !it.time.isBefore(previous.time.minusMinutes(30)) &&
                    !it.time.isAfter(next.time.plusMinutes(30))
            }
            val breakdown = scoreWindow(previous.time, next.time, nearby, typicalSleepStart, typicalWakeTime)
            val score = breakdown.values.sum().coerceIn(0, 100)
            val candidate = Result(SleepSession(previous.time, next.time, score), breakdown)
            if (best == null || candidate.session.confidence > best!!.session.confidence ||
                (candidate.session.confidence == best!!.session.confidence && candidate.session.durationMinutes > best!!.session.durationMinutes)) {
                best = candidate
            }
        }
        return best
    }

    private fun scoreWindow(
        start: LocalDateTime,
        end: LocalDateTime,
        nearby: List<PhoneSignal>,
        sleepStart: LocalTime,
        wakeTime: LocalTime
    ): Map<String, Int> {
        val duration = Duration.between(start, end).toMinutes()
        val nighttime = isNight(start.toLocalTime(), sleepStart, wakeTime)
        val screenQuiet = nearby.none { it.type == PhoneSignal.Type.SCREEN_ON }
        val charged = nearby.any { it.type == PhoneSignal.Type.CHARGING_START }
        return mapOf(
            "duration" to when { duration >= 480 -> 35; duration >= 360 -> 28; else -> 20 },
            "nighttime" to if (nighttime) 30 else 8,
            "screen_quiet" to if (screenQuiet) 20 else 4,
            "charging" to if (charged) 15 else 0
        )
    }

    private fun isNight(time: LocalTime, start: LocalTime, wake: LocalTime): Boolean =
        if (start <= wake) time >= start && time <= wake else time >= start || time <= wake
}
