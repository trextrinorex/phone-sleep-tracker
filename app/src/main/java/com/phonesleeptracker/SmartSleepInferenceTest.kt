package com.phonesleeptracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class SmartSleepInferenceTest {
    @Test
    fun ignoresShortGaps() {
        val base = LocalDateTime.of(2026, 9, 6, 22, 0)
        val signals = listOf(
            PhoneSignal(base, PhoneSignal.Type.APP_RESUMED),
            PhoneSignal(base.plusHours(3), PhoneSignal.Type.APP_RESUMED)
        )
        assertNull(SmartSleepInference.infer(signals))
    }

    @Test
    fun scoresLongQuietNightHighly() {
        val base = LocalDateTime.of(2026, 9, 6, 22, 0)
        val signals = listOf(
            PhoneSignal(base, PhoneSignal.Type.APP_RESUMED),
            PhoneSignal(base.plusMinutes(1), PhoneSignal.Type.CHARGING_START),
            PhoneSignal(base.plusHours(8), PhoneSignal.Type.APP_RESUMED)
        )
        val result = SmartSleepInference.infer(signals)
        assertEquals(100, result?.session?.confidence)
        assertEquals(480L, result?.session?.durationMinutes)
    }

    @Test
    fun rejectsUnrealisticallyLongInactivityWindow() {
        val base = LocalDateTime.of(2026, 9, 6, 21, 0)
        val signals = listOf(
            PhoneSignal(base, PhoneSignal.Type.APP_RESUMED),
            PhoneSignal(base.plusHours(15), PhoneSignal.Type.APP_RESUMED)
        )
        assertNull(SmartSleepInference.infer(signals))
    }
}
