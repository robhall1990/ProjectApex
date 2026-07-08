package com.projectapex.intelligence.detect.combat

import com.projectapex.intelligence.api.IntelligenceConfig
import com.projectapex.intelligence.detect.DetectorEngine
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
 * Full detect→rank integration for the combat family: scripted frames →
 * IngestPipeline → DetectorEngine (all 8 detectors) → PrioritisationEngine →
 * RacePulse. Proves the pulse leads with a *predictive*, meaningful story
 * rather than a generic gap-closing one.
 */
class CombatPipelineTest {

    private val config = IntelligenceConfig()

    @Test
    fun `pulse leads with a predictive DRS insight for a mid-pack chase`() {
        val ingest = IngestPipeline(config)
        // VER leads clear; HAM P2 stable; LEC P3 steady; NOR P4 closing on LEC.
        val norGaps = listOf(42.4, 42.1, 41.7, 41.5, 41.3) // interval to LEC(40): 2.4 → 1.3
        lateinit var lastFrameLap: Pair<Long, Int>
        norGaps.forEachIndexed { i, norGap ->
            val lap = i + 1
            lastFrameLap = lap.toLong() to lap
            ingest.submit(
                frame(
                    sequence = lap.toLong(), lap = lap,
                    cars = listOf(
                        car("VER", 1, lap, 0.0, lastLap = 90.0),
                        car("HAM", 2, lap, 20.0, lastLap = 90.0),
                        car("LEC", 3, lap, 40.0, lastLap = 90.0),
                        car("NOR", 4, lap, norGap, lastLap = 90.0),
                    ),
                )
            )
        }
        val lastFrame = frame(
            sequence = lastFrameLap.first, lap = lastFrameLap.second,
            cars = listOf(
                car("VER", 1, lastFrameLap.second, 0.0),
                car("HAM", 2, lastFrameLap.second, 20.0),
                car("LEC", 3, lastFrameLap.second, 40.0),
                car("NOR", 4, lastFrameLap.second, 41.3),
            ),
        )

        val detectorEngine = DetectorEngine().apply { CombatDetectors.all(config).forEach(::register) }
        // Clock at the last frame's timestamp so recency ≈ 1 for its observations.
        val clock = Clock.fixed(Instant.ofEpochMilli(lastFrameLap.first * 1000), ZoneOffset.UTC)
        val prioritisation = PrioritisationEngine(config.prioritisation, clock)

        val detection = detectorEngine.execute(lastFrame, ingest.view)
        val pulse = prioritisation.prioritise(detection.observations, atLap = lastFrameLap.second)

        val top = pulse.topObservations.first().observation
        assertEquals(CombatTypes.DRS_IMMINENT, top.type.value)
        assertTrue(top.subjectDrivers.containsAll(listOf("NOR", "LEC")))
        assertTrue(top.timeHorizon is TimeHorizon.InLaps)

        // The generic "gap closing" story never leads (and here is suppressed entirely).
        assertTrue(pulse.topObservations.none { it.observation.type.value == CombatTypes.GAP_CLOSING })
    }
}
