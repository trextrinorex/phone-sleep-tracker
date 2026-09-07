package com.phonesleeptracker

import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Lightweight synthetic evaluation suite.
 *
 * Goal: quantify how the heuristic engine behaves on controlled scenarios
 * *before* any ML work. Metrics are intentionally simple and transparent.
 *
 * Scenarios cover:
 * - normal nights
 * - late nights
 * - early wake
 * - daytime inactivity (should not be sleep)
 * - short interruptions (should merge)
 * - unrealistically long gaps (should reject)
 */
class InferenceEvaluationTest {

    data class Scenario(
        val name: String,
        val signals: List<PhoneSignal>,
        val expectSleep: Boolean,
        val expectedMinConfidence: Int = 0,
        val expectedMaxConfidence: Int = 100,
        val expectedMinDurationMinutes: Long? = null,
        val personalStats: SmartSleepInference.PersonalStats = SmartSleepInference.PersonalStats()
    )

    private fun act(t: LocalDateTime) = PhoneSignal(t, PhoneSignal.Type.APP_RESUMED)
    private fun charge(t: LocalDateTime) = PhoneSignal(t, PhoneSignal.Type.CHARGING_START)

    private fun buildScenarios(): List<Scenario> {
        val nightStart = LocalDateTime.of(2026, 9, 6, 23, 0)
        return listOf(
            // Normal 7.5h night with charging
            Scenario(
                name = "normal_night_with_charging",
                signals = listOf(
                    act(nightStart),
                    charge(nightStart.plusMinutes(8)),
                    act(nightStart.plusHours(7).plusMinutes(30))
                ),
                expectSleep = true,
                expectedMinConfidence = 80,
                expectedMinDurationMinutes = 450
            ),
            // Late night (01:30 → 09:00)
            Scenario(
                name = "late_night",
                signals = listOf(
                    act(LocalDateTime.of(2026, 9, 7, 1, 30)),
                    act(LocalDateTime.of(2026, 9, 7, 9, 0))
                ),
                expectSleep = true,
                expectedMinConfidence = 65
            ),
            // Early wake (22:30 → 05:15)
            Scenario(
                name = "early_wake",
                signals = listOf(
                    act(LocalDateTime.of(2026, 9, 6, 22, 30)),
                    act(LocalDateTime.of(2026, 9, 7, 5, 15))
                ),
                expectSleep = true,
                expectedMinConfidence = 60
            ),
            // Daytime 5.5h inactivity – should be low confidence or rejected by threshold
            Scenario(
                name = "daytime_inactivity",
                signals = listOf(
                    act(LocalDateTime.of(2026, 9, 6, 12, 0)),
                    act(LocalDateTime.of(2026, 9, 6, 17, 30))
                ),
                expectSleep = false,
                expectedMaxConfidence = 55
            ),
            // Brief interruption that should be merged
            Scenario(
                name = "brief_interruption_merge",
                signals = listOf(
                    act(nightStart),
                    act(nightStart.plusHours(3).plusMinutes(20)),
                    act(nightStart.plusHours(3).plusMinutes(22)),
                    act(nightStart.plusHours(8))
                ),
                expectSleep = true,
                expectedMinConfidence = 70,
                expectedMinDurationMinutes = 470
            ),
            // Unrealistically long gap
            Scenario(
                name = "too_long_gap",
                signals = listOf(
                    act(LocalDateTime.of(2026, 9, 6, 20, 0)),
                    act(LocalDateTime.of(2026, 9, 7, 12, 0))
                ),
                expectSleep = false
            ),
            // Phone-left-behind style: long daytime + evening gap
            Scenario(
                name = "phone_left_behind_day",
                signals = listOf(
                    act(LocalDateTime.of(2026, 9, 6, 9, 0)),
                    act(LocalDateTime.of(2026, 9, 6, 18, 0))
                ),
                expectSleep = false,
                expectedMaxConfidence = 50
            ),
            // Personalization helps a slightly late bedtime
            Scenario(
                name = "personalized_slightly_late",
                signals = listOf(
                    act(LocalDateTime.of(2026, 9, 6, 23, 40)),
                    act(LocalDateTime.of(2026, 9, 7, 7, 20))
                ),
                expectSleep = true,
                expectedMinConfidence = 75,
                personalStats = SmartSleepInference.PersonalStats(
                    medianBedtime = LocalTime.of(23, 15),
                    bedtimeMadMinutes = 25,
                    medianWakeTime = LocalTime.of(7, 10),
                    wakeMadMinutes = 20,
                    medianDurationMinutes = 460,
                    durationMadMinutes = 30,
                    sampleSize = 12
                )
            )
        )
    }

    @Test
    fun evaluateHeuristicOnSyntheticScenarios() {
        val scenarios = buildScenarios()
        var correct = 0
        var total = 0
        val failures = mutableListOf<String>()

        for (s in scenarios) {
            total++
            val result = SmartSleepInference.infer(s.signals, personalStats = s.personalStats)
            val detected = result != null && result.session.confidence >= 60
            val confidence = result?.session?.confidence ?: 0
            val duration = result?.session?.durationMinutes

            var ok = true
            if (s.expectSleep) {
                if (!detected) {
                    ok = false
                    failures += "${s.name}: expected sleep but none detected (conf=$confidence)"
                } else if (confidence < s.expectedMinConfidence) {
                    ok = false
                    failures += "${s.name}: confidence $confidence < min ${s.expectedMinConfidence}"
                }
                if (s.expectedMinDurationMinutes != null && (duration ?: 0) < s.expectedMinDurationMinutes) {
                    ok = false
                    failures += "${s.name}: duration $duration < min ${s.expectedMinDurationMinutes}"
                }
            } else {
                // We do not want a high-confidence sleep detection
                if (detected && confidence > s.expectedMaxConfidence) {
                    ok = false
                    failures += "${s.name}: false high-confidence sleep (conf=$confidence)"
                }
            }

            if (ok) correct++
        }

        val accuracy = correct.toDouble() / total
        println("Synthetic evaluation: $correct/$total correct (accuracy=${"%.2f".format(accuracy * 100)}%)")
        if (failures.isNotEmpty()) {
            println("Failures:")
            failures.forEach { println("  - $it") }
        }

        // Baseline expectation: at least 75% of these controlled scenarios behave correctly.
        // Raise this bar as the engine improves.
        assertTrue(
            "Heuristic accuracy too low (${"%.1f".format(accuracy * 100)}%). Failures: $failures",
            accuracy >= 0.75
        )
    }
}
