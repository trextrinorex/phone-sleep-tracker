package com.phonesleeptracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime

class SmartSleepInferenceTest {

    private fun activity(time: LocalDateTime) =
        PhoneSignal(time, PhoneSignal.Type.APP_RESUMED)

    private fun charging(time: LocalDateTime) =
        PhoneSignal(time, PhoneSignal.Type.CHARGING_START)

    @Test
    fun ignoresGapsShorterThanFourHours() {
        val base = LocalDateTime.of(2026, 9, 6, 22, 0)
        val signals = listOf(
            activity(base),
            activity(base.plusHours(3).plusMinutes(59))
        )
        assertNull(SmartSleepInference.infer(signals))
    }

    @Test
    fun acceptsExactlyFourHourGapAsCandidate() {
        val base = LocalDateTime.of(2026, 9, 6, 23, 0)
        val signals = listOf(
            activity(base),
            activity(base.plusHours(4))
        )
        val result = SmartSleepInference.infer(signals)
        assertNotNull(result)
        assertEquals(240L, result!!.session.durationMinutes)
    }

    @Test
    fun scoresLongQuietNightHighly() {
        val base = LocalDateTime.of(2026, 9, 6, 22, 30)
        val signals = listOf(
            activity(base),
            charging(base.plusMinutes(5)),
            activity(base.plusHours(8))
        )
        val result = SmartSleepInference.infer(signals)
        assertNotNull(result)
        assertTrue(
            "Expected high confidence for 8h quiet night, got ${result!!.session.confidence}",
            result.session.confidence >= 80
        )
        assertEquals(480L, result.session.durationMinutes)
        assertEquals("High", result.confidenceBand)
    }

    @Test
    fun daytimeLongGapReceivesLowerScore() {
        val base = LocalDateTime.of(2026, 9, 6, 13, 0)
        val signals = listOf(
            activity(base),
            activity(base.plusHours(6))
        )
        val result = SmartSleepInference.infer(signals)
        assertNotNull(result)
        assertTrue(
            "Daytime should score lower than typical night, got ${result!!.session.confidence}",
            result.session.confidence < 60
        )
    }

    @Test
    fun rejectsUnrealisticallyLongInactivity() {
        val base = LocalDateTime.of(2026, 9, 6, 20, 0)
        val signals = listOf(
            activity(base),
            activity(base.plusHours(16))
        )
        assertNull(SmartSleepInference.infer(signals))
    }

    @Test
    fun mergesBriefInterruptionInsideSleepWindow() {
        val start = LocalDateTime.of(2026, 9, 6, 23, 0)
        val signals = listOf(
            activity(start),
            activity(start.plusHours(3).plusMinutes(30)),
            activity(start.plusHours(3).plusMinutes(32)),
            activity(start.plusHours(8))
        )
        val result = SmartSleepInference.infer(signals)
        assertNotNull(result)
        assertTrue(
            "Expected merged duration near 8h, got ${result!!.session.durationMinutes}",
            result.session.durationMinutes >= 470
        )
        assertTrue(result.session.confidence >= 65)
    }

    @Test
    fun historicalMedianBedtimeBoostsScore() {
        val base = LocalDateTime.of(2026, 9, 6, 23, 10)
        val signals = listOf(
            activity(base),
            activity(base.plusHours(7).plusMinutes(30))
        )

        val withoutHistory = SmartSleepInference.infer(signals)
        val withHistory = SmartSleepInference.infer(
            signals = signals,
            personalStats = SmartSleepInference.PersonalStats(
                medianBedtime = LocalTime.of(23, 5),
                bedtimeMadMinutes = 25,
                medianWakeTime = LocalTime.of(6, 40),
                wakeMadMinutes = 20,
                medianDurationMinutes = 450,
                durationMadMinutes = 30,
                sampleSize = 10
            )
        )

        assertNotNull(withoutHistory)
        assertNotNull(withHistory)
        assertTrue(
            "History should not decrease score",
            withHistory!!.session.confidence >= withoutHistory!!.session.confidence
        )
    }

    @Test
    fun screenActivityDuringWindowLowersScore() {
        val base = LocalDateTime.of(2026, 9, 6, 23, 0)
        val quiet = listOf(
            activity(base),
            activity(base.plusHours(7))
        )
        val noisy = listOf(
            activity(base),
            PhoneSignal(base.plusHours(2), PhoneSignal.Type.SCREEN_ON),
            activity(base.plusHours(7))
        )

        val quietScore = SmartSleepInference.infer(quiet)!!.session.confidence
        val noisyScore = SmartSleepInference.infer(noisy)!!.session.confidence
        assertTrue(
            "Screen-on events should reduce confidence ($quietScore vs $noisyScore)",
            noisyScore < quietScore
        )
    }

    @Test
    fun breakdownContainsExpectedKeys() {
        val base = LocalDateTime.of(2026, 9, 6, 23, 0)
        val signals = listOf(
            activity(base),
            activity(base.plusHours(7))
        )
        val result = SmartSleepInference.infer(signals)
        assertNotNull(result)
        val keys = result!!.scoreBreakdown.keys
        assertTrue(keys.contains("duration"))
        assertTrue(keys.contains("nighttime"))
        assertTrue(keys.contains("bedtime_match"))
        assertTrue(keys.contains("wake_match"))
        assertTrue(keys.contains("screen_quiet"))
    }
}
