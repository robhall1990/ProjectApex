package com.projectapex.intelligence

import com.projectapex.intelligence.detect.DetectionContext
import com.projectapex.intelligence.detect.Detector
import com.projectapex.intelligence.detect.DetectorEngine
import com.projectapex.intelligence.detect.Expiry
import com.projectapex.intelligence.detect.Observation
import com.projectapex.intelligence.detect.ObservationId
import com.projectapex.intelligence.detect.ObservationType
import com.projectapex.intelligence.detect.Severity
import com.projectapex.intelligence.detect.TimeHorizon
import com.projectapex.intelligence.ingest.IngestPipeline
import com.projectapex.intelligence.rank.PrioritisationEngine
import com.projectapex.intelligence.support.Fixtures.car
import com.projectapex.intelligence.support.Fixtures.frame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * End-to-end wiring of the full APX-010 → APX-011 pipeline:
 * frames → IngestPipeline (FeatureStore) → DetectorEngine → PrioritisationEngine → RacePulse.
 *
 * The detector here is a *test stub*, not a real race detector (APX-011
 * explicitly defers those) — but it reads real FeatureView data, proving the
 * seams line up.
 */
class PulsePipelineTest {

    private val now = Instant.parse("2026-07-08T14:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    /** Flags any pair running closer than 1.2s — a toy proximity detector. */
    private class ProximityStub : Detector {
        override val id = "proximity-stub"
        override fun detect(context: DetectionContext): List<Observation> {
            val order = context.features.runningOrder
            return order.zipWithNext().mapNotNull { (ahead, behind) ->
                val interval = context.features.interval(ahead.driverId, behind.driverId)
                    ?: return@mapNotNull null
                if (interval.value > 1.2) return@mapNotNull null
                val subjects = listOf(ahead.driverId, behind.driverId).sorted()
                Observation(
                    id = ObservationId("proximity:${subjects.joinToString(":")}"),
                    type = ObservationType("proximity"),
                    severity = Severity.HIGH,
                    confidence = 0.9,
                    timestamp = context.frame.timestamp,
                    subjectDrivers = subjects,
                    metadata = mapOf("gap_s" to interval.value),
                    timeHorizon = TimeHorizon.Ongoing,
                    expiry = Expiry.AtLap(context.frame.lap + 2),
                    sourceDetector = id,
                )
            }
        }
    }

    @Test
    fun `frames flow through features, detection, and prioritisation into a pulse`() {
        val ingest = IngestPipeline()
        val detectorEngine = DetectorEngine(clock)
        val prioritisation = PrioritisationEngine(clock = clock)
        detectorEngine.register(ProximityStub())

        // NOR closes on VER over three laps; HAM far behind.
        var pulse = com.projectapex.intelligence.rank.RacePulse("", emptyList(), now, com.projectapex.intelligence.rank.ConfidenceSummary.EMPTY)
        for (lap in 1..3) {
            val gapNor = 3.0 - lap * 0.7 // 2.3, 1.6, 0.9 — inside 1.2 on lap 3
            val timingFrame = frame(
                sequence = lap.toLong(), lap = lap, timestampMs = now.toEpochMilli(),
                cars = listOf(
                    car("VER", 1, lap, 0.0, lastLap = 90.0),
                    car("NOR", 2, lap, gapNor, lastLap = 89.5),
                    car("HAM", 3, lap, 20.0, lastLap = 91.0),
                ),
            )
            val accepted = ingest.submit(timingFrame)
            assertTrue(accepted is IngestPipeline.IngestResult.Accepted)

            val detection = detectorEngine.execute(timingFrame, ingest.view)
            pulse = prioritisation.prioritise(detection.observations, atLap = lap)
        }

        val top = pulse.topObservations.single()
        assertEquals("proximity:NOR:VER", top.observation.id.value)
        assertEquals(0.9, top.observation.metadata.getValue("gap_s"), 1e-9)
        assertEquals("proximity: NOR vs VER", pulse.headline)
        assertEquals(1, pulse.confidenceSummary.activeObservationCount)
        assertTrue(pulse.topObservations.single().score > 0.0)
    }
}
