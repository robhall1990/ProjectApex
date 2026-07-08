package com.projectapex.intelligence.detect.combat

import com.projectapex.intelligence.api.IntelligenceConfig
import com.projectapex.intelligence.detect.Severity
import com.projectapex.intelligence.detect.combat.CombatSupport.contextFrom
import com.projectapex.intelligence.ingest.TimingFrame
import com.projectapex.intelligence.support.Fixtures.car
import com.projectapex.intelligence.support.Fixtures.frame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GapTrendDetectorTest {

    private val config = IntelligenceConfig()

    private fun frames(gaps: List<Double>): List<TimingFrame> =
        gaps.mapIndexed { i, gap ->
            val lap = i + 1
            frame(
                sequence = lap.toLong(), lap = lap,
                cars = listOf(
                    car("VER", 1, lap, 0.0, lastLap = 90.0),
                    car("NOR", 2, lap, gap, lastLap = 90.0),
                ),
            )
        }

    @Test
    fun `gap closing fires in the mid-range and is low severity`() {
        // 4.5 → 3.0 over five laps: closing, well outside DRS/battle range, and
        // not converging on DRS within the horizon.
        val ctx = contextFrom(frames(listOf(4.5, 4.1, 3.7, 3.3, 3.0)))
        val closing = GapClosingDetector(config).detect(ctx)

        val obs = closing.single()
        assertEquals(CombatTypes.GAP_CLOSING, obs.type.value)
        assertEquals(Severity.LOW, obs.severity)
        assertEquals(listOf("NOR", "VER"), obs.subjectDrivers)
        assertTrue(obs.metadata.getValue(CombatKeys.RATE_S_PER_LAP) < 0.0)
    }

    @Test
    fun `gap closing yields to DRS imminent when converging on DRS range`() {
        // 2.4 → 1.3: closing and would reach DRS within the horizon, so
        // DrsImminentDetector owns it and GapClosingDetector stays silent.
        val ctx = contextFrom(frames(listOf(2.4, 2.1, 1.7, 1.5, 1.3)))
        assertTrue(GapClosingDetector(config).detect(ctx).isEmpty())
    }

    @Test
    fun `gap increasing fires when a car drops away, at the lowest severity`() {
        val ctx = contextFrom(frames(listOf(2.0, 2.4, 2.8, 3.2, 3.6)))
        val increasing = GapIncreasingDetector(config).detect(ctx)

        val obs = increasing.single()
        assertEquals(CombatTypes.GAP_INCREASING, obs.type.value)
        assertEquals(Severity.INFO, obs.severity)
        assertTrue(obs.metadata.getValue(CombatKeys.RATE_S_PER_LAP) > 0.0)
    }

    @Test
    fun `a stable gap produces neither`() {
        val ctx = contextFrom(frames(listOf(3.0, 3.0, 3.0, 3.0, 3.0)))
        assertTrue(GapClosingDetector(config).detect(ctx).isEmpty())
        assertTrue(GapIncreasingDetector(config).detect(ctx).isEmpty())
    }
}
