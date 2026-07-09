package com.projectapex.intelligence.adapter

import com.projectapex.core.model.SessionStatus
import com.projectapex.domain.model.CarState
import com.projectapex.domain.model.Driver
import com.projectapex.domain.model.RaceState
import com.projectapex.domain.model.TyreCompound
import com.projectapex.intelligence.ingest.PitStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RaceStateAdapterTest {

    private val adapter = RaceStateAdapter()

    private fun state(
        lap: Int,
        timestamp: Long,
        status: SessionStatus = SessionStatus.LIVE,
        inPit: Boolean = false,
    ): RaceState = RaceState(
        sessionStatus = status,
        currentLap = lap,
        totalLaps = 58,
        cars = listOf(
            CarState(
                driver = Driver(id = "VER", name = "Max Verstappen", team = "Red Bull Racing", number = 1),
                position = 1,
                lap = lap,
                gapToLeaderSeconds = 0.0,
                tyreCompound = TyreCompound.MEDIUM,
                tyreAgeLaps = lap,
                isInPitLane = inPit,
            )
        ),
        timestamp = timestamp,
    )

    @Test
    fun `maps fields into the canonical frame`() {
        val frame = adapter.adapt(state(lap = 12, timestamp = 5_000L))!!

        assertEquals(12, frame.lap)
        assertEquals(58, frame.totalLaps)
        val car = frame.cars.single()
        assertEquals("VER", car.driverId)
        assertEquals(1, car.position)
        assertEquals(12, car.lapsCompleted)
        assertEquals(0.0, car.gapToLeader!!.value, 1e-9)
        assertEquals(
            com.projectapex.intelligence.ingest.TyreCompound.MEDIUM,
            car.tyre.compound,
        )
        assertEquals(PitStatus.NONE, car.pitStatus)
    }

    @Test
    fun `assigns monotonically increasing sequences`() {
        val first = adapter.adapt(state(lap = 1, timestamp = 1_000L))!!
        val second = adapter.adapt(state(lap = 1, timestamp = 2_000L))!!
        assertEquals(first.sequence + 1, second.sequence)
    }

    @Test
    fun `synthesises lap time from the wall clock between lap edges`() {
        adapter.adapt(state(lap = 5, timestamp = 100_000L))
        adapter.adapt(state(lap = 5, timestamp = 145_000L)) // mid-lap frame
        val edge = adapter.adapt(state(lap = 6, timestamp = 190_000L))!!

        // Lap 6 edge: 190s − 100s since the lap-5 edge.
        assertEquals(90.0, edge.cars.single().lastLapTime!!.value, 1e-9)

        // Mid-lap frames after the edge carry no lap time.
        val midLap = adapter.adapt(state(lap = 6, timestamp = 191_000L))!!
        assertNull(midLap.cars.single().lastLapTime)
    }

    @Test
    fun `first lap edge has no synthesised time and jumps never guess`() {
        val first = adapter.adapt(state(lap = 3, timestamp = 100_000L))!!
        assertNull(first.cars.single().lastLapTime)

        // A two-lap jump: no previous edge for lap 5's start — stay null.
        val jump = adapter.adapt(state(lap = 5, timestamp = 280_000L))!!
        assertNull(jump.cars.single().lastLapTime)
    }

    @Test
    fun `offline and empty states produce no frame`() {
        assertNull(adapter.adapt(state(lap = 1, timestamp = 0L, status = SessionStatus.OFFLINE)))
        assertNull(adapter.adapt(RaceState.empty()))
    }

    @Test
    fun `pit lane flag maps to IN_PIT`() {
        val frame = adapter.adapt(state(lap = 9, timestamp = 1_000L, inPit = true))!!
        assertEquals(PitStatus.IN_PIT, frame.cars.single().pitStatus)
    }
}
