package com.phonesleeptracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        assertTrue((result?.session?.confidence ?: 0) >= 80)
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

    @Test
    fun rejectsDaytimeLongGapAsLowConfidenceOrNull() {
        val base = LocalDateTime.of(2026, 9, 6, 13, 0)
        val signals = listOf(
            PhoneSignal(base, PhoneSignal.Type.APP_RESUMED),
            PhoneSignal(base.plusHours(6), PhoneSignal.Type.APP_RESUMED)
        )
        val result = SmartSleepInference.infer(signals)
        // Daytime should score significantly lower than night
        assertTrue((result?.session?.confidence ?: 0) < 60)
    }

    @Test
    fun mergesBriefInterruptionInsideSleepWindow() {
        val start = LocalDateTime.of(2026, 9, 6, 23, 0)
        val signals = listOf(
            PhoneSignal(start, PhoneSignal.Type.APP_RESUMED),
            PhoneSignal(start.plusHours(3).plusMinutes(30), PhoneSignal.Type.APP_RESUMED), // brief wake
            PhoneSignal(start.plusHours(3).plusMinutes(32), PhoneSignal.Type.APP_RESUMED),
            PhoneSignal(start.plusHours(8), PhoneSignal.Type.APP_RESUMED)
        )
        val result = SmartSleepInference.infer(signals)
        // Should prefer the full overnight window after merging
        assertTrue((result?.session?.durationMinutes ?: 0) >= 470)
        assertTrue((result?.session?.confidence ?: 0) >= 70)
    }
}
