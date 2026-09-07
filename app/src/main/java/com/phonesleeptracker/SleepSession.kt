package com.phonesleeptracker

import java.time.Duration
import java.time.LocalDateTime

/** An inferred period in which the user was probably asleep. */
data class SleepSession(
    val start: LocalDateTime,
    val end: LocalDateTime,
    val confidence: Int
) {
    val durationMinutes: Long
        get() = Duration.between(start, end).toMinutes()
}
