package com.phonesleeptracker

import java.time.LocalDateTime

/** Lightweight, privacy-preserving phone signals used by the inference engine. */
data class PhoneSignal(
    val time: LocalDateTime,
    val type: Type
) {
    enum class Type {
        APP_RESUMED,
        SCREEN_ON,
        SCREEN_OFF,
        CHARGING_START,
        CHARGING_STOP
    }
}
