package com.projectapex.intelligence.rank

import com.projectapex.intelligence.api.PrioritisationConfig
import com.projectapex.intelligence.detect.Expiry
import com.projectapex.intelligence.detect.ObservationFixtures.observation
import com.projectapex.intelligence.detect.Severity
import com.projectapex.intelligence.detect.TimeHorizon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.exp

class PrioritisationEngineTest {

    private val now = Instant.parse("2026-07-08T14:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    private fun engine(config: PrioritisationConfig = PrioritisationConfig()) =
        PrioritisationEngine(config, clock)

    @Test
    fun `higher severity outranks lower severity at equal confidence`() {
        val pulse = engine().prioritise(
            listOf(
                observation("low", severity = Severity.LOW, timestamp = now, subjects = listOf("A")),
                observation("crit", severity = Severity.CRITICAL, timestamp = now, subjects = listOf("B")),
            ),
            atLap = 10,
        )
        assertEquals("crit", pulse.topObservations.first().observation.id.value)
    }

    @Test
    fun `score multiplies severity weight by confidence`() {
        val pulse = engine().prioritise(
            listOf(observation("x", severity = Severity.HIGH, confidence = 0.5, timestamp = now)),
            atLap = 10,
        )
        assertEquals(0.8 * 0.5, pulse.topObservations.single().score, 1e-9)
    }

    @Test
    fun `a near prediction outranks a distant one`() {
        val pulse = engine().prioritise(
            listOf(
                observation("far", timestamp = now, subjects = listOf("A"),
                    horizon = TimeHorizon.InLaps(8.0)),
                observation("near", timestamp = now, subjects = listOf("B"),
                    horizon = TimeHorizon.InLaps(1.0)),
            ),
            atLap = 10,
        )
        assertEquals(listOf("near", "far"), pulse.topObservations.map { it.observation.id.value })
        // Urgency is exp(−laps/τ) with τ = 4.
        assertEquals(0.8 * 0.8 * exp(-1.0 / 4.0), pulse.topObservations[0].score, 1e-9)
    }

    @Test
    fun `stale observations decay by recency`() {
        val pulse = engine().prioritise(
            listOf(
                observation("fresh", timestamp = now, subjects = listOf("A")),
                observation("stale", timestamp = now.minusSeconds(240), subjects = listOf("B")),
            ),
            atLap = 10,
        )
        assertEquals("fresh", pulse.topObservations.first().observation.id.value)
        // 240 s old with τ = 120 s → factor e⁻².
        assertEquals(0.8 * 0.8 * exp(-2.0), pulse.topObservations[1].score, 1e-9)
    }

    @Test
    fun `repeated emissions decay by novelty and a material change resets it`() {
        val e = engine()
        val first = e.prioritise(listOf(observation("x", timestamp = now)), atLap = 10)
        val second = e.prioritise(listOf(observation("x", timestamp = now)), atLap = 11)
        val third = e.prioritise(listOf(observation("x", timestamp = now)), atLap = 12)

        val base = first.topObservations.single().score
        assertEquals(base * 0.7, second.topObservations.single().score, 1e-9)
        assertEquals(base * 0.49, third.topObservations.single().score, 1e-9)

        // Confidence jumps by ≥ 0.15 → material change → novelty resets.
        val changed = e.prioritise(
            listOf(observation("x", confidence = 0.99, timestamp = now)), atLap = 13,
        )
        assertEquals(0.8 * 0.99, changed.topObservations.single().score, 1e-9)
    }

    @Test
    fun `expired observations are dropped`() {
        val pulse = engine().prioritise(
            listOf(
                observation("past-lap", timestamp = now, expiry = Expiry.AtLap(9)),
                observation("timed-out", timestamp = now.minusSeconds(60),
                    expiry = Expiry.AfterSeconds(30.0), subjects = listOf("B")),
                observation("alive", timestamp = now, expiry = Expiry.AtLap(10), subjects = listOf("C")),
            ),
            atLap = 10,
        )
        assertEquals(listOf("alive"), pulse.topObservations.map { it.observation.id.value })
        assertEquals(1, pulse.confidenceSummary.activeObservationCount)
    }

    @Test
    fun `observations below the confidence floor never surface`() {
        val pulse = engine().prioritise(
            listOf(observation("hopeless", confidence = 0.01, timestamp = now)),
            atLap = 10,
        )
        assertTrue(pulse.topObservations.isEmpty())
        assertEquals("No significant race activity", pulse.headline)
    }

    @Test
    fun `duplicate ids collapse to the most confident before scoring`() {
        val pulse = engine().prioritise(
            listOf(
                observation("x", confidence = 0.5, timestamp = now),
                observation("x", confidence = 0.9, timestamp = now),
            ),
            atLap = 10,
        )
        assertEquals(1, pulse.topObservations.size)
        assertEquals(0.9, pulse.topObservations.single().observation.confidence, 1e-9)
    }

    @Test
    fun `top-K selection taxes stories about the same drivers`() {
        // Battle and DRS are both about VER/NOR; tyre is about HAM and scores
        // lower raw — but after the subject-overlap tax on the DRS story the
        // tyre one takes second place.
        val pulse = engine().prioritise(
            listOf(
                observation("battle", type = "battle", severity = Severity.HIGH,
                    confidence = 0.9, timestamp = now, subjects = listOf("VER", "NOR")),
                observation("drs", type = "drs", severity = Severity.HIGH,
                    confidence = 0.85, timestamp = now, subjects = listOf("VER", "NOR")),
                observation("tyre", type = "tyre", severity = Severity.MEDIUM,
                    confidence = 0.6, timestamp = now, subjects = listOf("HAM")),
            ),
            atLap = 10,
        )
        assertEquals(
            listOf("battle", "tyre", "drs"),
            pulse.topObservations.map { it.observation.id.value },
        )
    }

    @Test
    fun `pulse carries headline, generatedAt and confidence summary`() {
        val pulse = engine().prioritise(
            listOf(
                observation("battle", type = "battle", confidence = 0.9, timestamp = now,
                    subjects = listOf("VER", "NOR")),
                observation("tyre", type = "tyre", confidence = 0.5, timestamp = now,
                    subjects = listOf("HAM"), severity = Severity.MEDIUM),
            ),
            atLap = 10,
        )

        assertEquals("battle: VER vs NOR", pulse.headline)
        assertEquals(now, pulse.generatedAt)
        assertEquals(0.7, pulse.confidenceSummary.mean, 1e-9)
        assertEquals(0.5, pulse.confidenceSummary.min, 1e-9)
        assertEquals(0.9, pulse.confidenceSummary.max, 1e-9)
        assertEquals(2, pulse.confidenceSummary.activeObservationCount)
    }

    @Test
    fun `an empty input produces a quiet pulse`() {
        val pulse = engine().prioritise(emptyList(), atLap = 1)
        assertEquals("No significant race activity", pulse.headline)
        assertTrue(pulse.topObservations.isEmpty())
        assertEquals(ConfidenceSummary.EMPTY, pulse.confidenceSummary)
    }

    @Test
    fun `scoring constants are configurable`() {
        val flatSeverity = PrioritisationConfig(
            severityWeights = Severity.entries.associateWith { 1.0 },
            topK = 1,
        )
        val pulse = engine(flatSeverity).prioritise(
            listOf(
                observation("info", severity = Severity.INFO, confidence = 0.9,
                    timestamp = now, subjects = listOf("A")),
                observation("crit", severity = Severity.CRITICAL, confidence = 0.8,
                    timestamp = now, subjects = listOf("B")),
            ),
            atLap = 10,
        )
        // With flat severity weights, confidence alone decides.
        assertEquals("info", pulse.topObservations.single().observation.id.value)
        assertEquals(1, pulse.topObservations.size)
    }
}
