package com.projectapex.intelligence.detect.analysis

import com.projectapex.intelligence.ingest.IngestPipeline
import com.projectapex.intelligence.support.Fixtures.car
import com.projectapex.intelligence.support.Fixtures.frame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisTest {

    @Test
    fun `lapsUntil is closed-form and only defined when converging`() {
        assertEquals(1.0, GapAnalysis.lapsUntil(currentGap = 1.3, ratePerLap = -0.3, targetGap = 1.0)!!, 1e-9)
        assertEquals(0.0, GapAnalysis.lapsUntil(currentGap = 0.9, ratePerLap = -0.3, targetGap = 1.0)!!, 1e-9)
        assertNull(GapAnalysis.lapsUntil(currentGap = 2.0, ratePerLap = 0.3, targetGap = 1.0))  // widening
        assertNull(GapAnalysis.lapsUntil(currentGap = 2.0, ratePerLap = 0.0, targetGap = 1.0))  // flat
    }

    @Test
    fun `trend recovers the sign of a closing interval`() {
        val pipeline = IngestPipeline()
        listOf(2.4, 2.1, 1.7, 1.4).forEachIndexed { i, gap ->
            val lap = i + 1
            pipeline.submit(
                frame(lap.toLong(), lap = lap, cars = listOf(car("VER", 1, lap, 0.0, lastLap = 90.0), car("NOR", 2, lap, gap, lastLap = 90.0)))
            )
        }
        val trend = GapAnalysis.trend("VER", "NOR", pipeline.view, windowLaps = 4)!!
        assertEquals(1.4, trend.currentGapSeconds, 1e-9)
        assertTrue("closing → negative slope", trend.ratePerLap < 0.0)
    }

    @Test
    fun `pace ranking orders fuel-corrected clean pace fastest-first`() {
        val pipeline = IngestPipeline()
        for (lap in 1..5) {
            pipeline.submit(
                frame(
                    lap.toLong(), lap = lap,
                    cars = listOf(
                        car("VER", 1, lap, 0.0, lastLap = 91.0),
                        car("NOR", 2, lap, 5.0, lastLap = 90.0),
                    ),
                )
            )
        }
        val ranking = RelativePace.ranking(pipeline.view)
        assertEquals("NOR", ranking.first().driverId) // quicker corrected pace
        assertEquals(1.0, RelativePace.advantagePerLap("NOR", "VER", pipeline.view)!!, 0.05)
    }

    @Test
    fun `confidence primitives stay within range`() {
        assertEquals(0.5, Confidence.sampleSize(3), 1e-9)
        assertEquals(1.0, Confidence.margin(10.0, 0.0, 5.0), 1e-9)   // saturates
        assertEquals(0.0, Confidence.margin(-1.0, 0.0, 5.0), 1e-9)   // below threshold
        assertEquals(1.0, Confidence.clamp01(2.0), 1e-9)
    }
}
