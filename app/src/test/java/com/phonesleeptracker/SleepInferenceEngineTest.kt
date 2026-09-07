package com.phonesleeptracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class SleepInferenceEngineTest {
    @Test
    fun rejectsGapsShorterThanFourHours() {
        val start = LocalDateTime.of(2026, 9, 6, 22, 0)
        val end = start.plusHours(3).plusMinutes(59)
        assertNull(SleepInferenceEngine.infer(start, end))
    }

    @Test
    fun sixHourNightGapGetsHighConfidence() {
        val start = LocalDateTime.of(2026, 9, 6, 23, 0)
        val end = start.plusHours(6)
        val result = SleepInferenceEngine.infer(start, end)
        assertEquals(88, result?.confidence)
        assertEquals(360, result?.durationMinutes)
    }

    @Test
    fun longDaytimeGapIsLowerConfidence() {
        val start = LocalDateTime.of(2026, 9, 6, 12, 0)
        val end = start.plusHours(8)
        val confidence = SleepInferenceEngine.infer(start, end)?.confidence ?: 0
        assertTrue(confidence in 40..60)
    }

    @Test
    fun overnightWindowRecognizesAfterMidnight() {
        val start = LocalDateTime.of(2026, 9, 7, 1, 0)
        val end = start.plusHours(6)
        assertEquals(88, SleepInferenceEngine.infer(start, end)?.confidence)
    }

    @Test
    fun rejectsExtremelyLongGaps() {
        val start = LocalDateTime.of(2026, 9, 6, 20, 0)
        val end = start.plusHours(16)
        assertNull(SleepInferenceEngine.infer(start, end))
    }
}
