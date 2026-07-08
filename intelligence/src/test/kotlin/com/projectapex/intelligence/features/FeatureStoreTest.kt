package com.projectapex.intelligence.features

import com.projectapex.intelligence.ingest.IngestPipeline
import com.projectapex.intelligence.ingest.PitStatus
import com.projectapex.intelligence.ingest.TrackStatus
import com.projectapex.intelligence.ingest.TyreCompound
import com.projectapex.intelligence.support.Fixtures.car
import com.projectapex.intelligence.support.Fixtures.frame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Drives the whole cheap path (validate → normalise → derive → store) with
 * scripted frame sequences and asserts what the FeatureView reports.
 */
class FeatureStoreTest {

    @Test
    fun `records laps, fuel-corrects them, and estimates pace`() {
        val pipeline = IngestPipeline()
        val totalLaps = 50

        // One lap edge per frame, constant 90.0s raw laps.
        for (lap in 1..7) {
            pipeline.submit(
                frame(
                    sequence = lap.toLong(), lap = lap, totalLaps = totalLaps,
                    cars = listOf(
                        car("VER", 1, lapsCompleted = lap, gap = 0.0, lastLap = 90.0),
                        car("NOR", 2, lapsCompleted = lap, gap = 1.0 + 0.5 * lap, lastLap = 90.5),
                    ),
                )
            )
        }

        val view = pipeline.view
        // 7 frames = 6 lap edges (first frame has no previous to diff against).
        assertEquals(6, view.laps("VER").size)

        val lap2 = view.laps("VER").first()
        assertEquals(90.0, lap2.timeSeconds!!, 1e-9)
        // Fuel correction (§9.1): 90.0 − 0.055 × (50 − 2).
        assertEquals(90.0 - 0.055 * (totalLaps - 2), lap2.fuelCorrectedSeconds!!, 1e-9)

        // Constant raw laps mean corrected times *rise* by exactly the fuel
        // effect per lap — the pace trend slope must recover it.
        val pace = view.pace("VER")!!
        assertEquals(5, pace.n) // window default
        assertEquals(0.055, pace.slopePerLap, 1e-6)
        assertEquals(0.0, pace.sigma, 1e-6)
    }

    @Test
    fun `laps completed under safety car are flagged and excluded from pace`() {
        val pipeline = IngestPipeline()
        var seq = 1L

        fun edge(lap: Int, status: TrackStatus = TrackStatus.GREEN) = pipeline.submit(
            frame(seq++, lap = lap, cars = listOf(car("VER", 1, lap, 0.0, lastLap = 90.0)), status = status)
        )

        edge(1); edge(2); edge(3)
        // SC appears mid-lap (no lap edge on this frame)...
        pipeline.submit(
            frame(seq++, lap = 3, cars = listOf(car("VER", 1, 3, 0.0)), status = TrackStatus.SC)
        )
        // ...and the next completed lap carries the flag even though it ends under green.
        edge(4)
        edge(5)

        val laps = view(pipeline).laps("VER")
        val lap4 = laps.single { it.lap == 4 }
        assertTrue(LapFlag.SC_LAP in lap4.flags)
        val lap5 = laps.single { it.lap == 5 }
        assertTrue(lap5.isClean)
    }

    @Test
    fun `a pit stop closes the stint, opens a new one, and logs the stop`() {
        val pipeline = IngestPipeline()

        pipeline.submit(
            frame(1, lap = 10, cars = listOf(car("VER", 1, 10, 0.0, tyreAge = 10)))
        )
        pipeline.submit(
            frame(2, lap = 10, cars = listOf(car("VER", 1, 10, 0.0, tyreAge = 10, pit = PitStatus.IN_PIT)))
        )
        pipeline.submit(
            frame(
                3, lap = 11,
                cars = listOf(car("VER", 1, 11, 0.0, lastLap = 112.0, compound = TyreCompound.SOFT, tyreAge = 0)),
            )
        )

        val view = pipeline.view
        val stops = view.pitStops("VER")
        assertEquals(1, stops.size)
        assertEquals(10, stops.single().lapIn)
        assertEquals(11, stops.single().lapOut)
        assertEquals(TyreCompound.SOFT, stops.single().compoundAfter)

        val stints = view.stints("VER")
        assertEquals(2, stints.size)
        assertEquals(TyreCompound.MEDIUM, stints[0].compound)
        assertEquals(11, stints[0].endLap)
        assertEquals(TyreCompound.SOFT, stints[1].compound)

        // The in/out lap is flagged — it can never poison a pace or deg fit.
        val pitLap = view.laps("VER").single { it.lap == 11 }
        assertTrue(LapFlag.IN_LAP in pitLap.flags || LapFlag.OUT_LAP in pitLap.flags)
    }

    @Test
    fun `tyre change without pit-status data still splits the stint`() {
        // Today's simulator has no pit lane model — the age reset is the only signal.
        val pipeline = IngestPipeline()

        pipeline.submit(frame(1, lap = 10, cars = listOf(car("VER", 1, 10, 0.0, tyreAge = 10))))
        pipeline.submit(frame(2, lap = 11, cars = listOf(car("VER", 1, 11, 0.0, lastLap = 90.0, tyreAge = 0))))

        assertEquals(2, pipeline.view.stints("VER").size)
    }

    @Test
    fun `deg fit through the full chain recovers the injected rate`() {
        val pipeline = IngestPipeline()
        val totalLaps = 50
        val fuel = 0.055

        // Raw laps engineered so fuel-corrected time = 90 + 0.08 × age (age = lap here).
        for (lap in 1..8) {
            val raw = 90.0 + 0.08 * lap + fuel * (totalLaps - lap)
            pipeline.submit(
                frame(
                    sequence = lap.toLong(), lap = lap, totalLaps = totalLaps,
                    cars = listOf(car("VER", 1, lapsCompleted = lap, gap = 0.0, lastLap = raw)),
                )
            )
        }

        val fit = pipeline.view.degFit("VER")!!
        assertEquals(0.08, fit.ratePerLap, 1e-6)
        assertEquals(90.0, fit.baseSeconds, 1e-6)
    }

    @Test
    fun `interval history aligns two drivers' gap samples by lap`() {
        val pipeline = IngestPipeline()

        for (lap in 1..5) {
            pipeline.submit(
                frame(
                    sequence = lap.toLong(), lap = lap,
                    cars = listOf(
                        car("VER", 1, lap, 0.0, lastLap = 90.0),
                        car("NOR", 2, lap, 2.0 + 0.5 * lap, lastLap = 90.0),
                    ),
                )
            )
        }

        val history = pipeline.view.intervalHistory("VER", "NOR", lastNLaps = 10)
        assertEquals(4, history.size) // laps 2..5 (first frame has no edge)
        assertEquals(3.0, history.first().interval.value, 1e-9)  // lap 2
        assertEquals(4.5, history.last().interval.value, 1e-9)   // lap 5

        // Instantaneous interval from the latest frame agrees.
        assertEquals(4.5, pipeline.view.interval("VER", "NOR")!!.value, 1e-9)
    }

    @Test
    fun `rejected frames leave the store untouched`() {
        val pipeline = IngestPipeline()
        pipeline.submit(frame(1, lap = 1, cars = listOf(car("VER", 1, 1, 0.0))))

        val rejected = pipeline.submit(
            frame(2, lap = 2, cars = listOf(car("VER", 1, 2, 0.0), car("VER", 2, 2, 1.0)))
        )
        assertTrue(rejected is IngestPipeline.IngestResult.Rejected)
        assertEquals(1, pipeline.view.lap) // still the accepted frame's lap
        assertNull(pipeline.view.cumulativeTime("NOR"))
    }

    @Test
    fun `an empty store answers safely before any frame`() {
        val view = IngestPipeline().view
        assertEquals(0, view.lap)
        assertTrue(view.runningOrder.isEmpty())
        assertNull(view.pace("VER"))
        assertNull(view.degFit("VER"))
        assertNull(view.cumulativeTime("VER"))
    }

    private fun view(pipeline: IngestPipeline): FeatureView = pipeline.view
}
