package com.projectapex.intelligence.events

import com.projectapex.intelligence.ingest.PitStatus
import com.projectapex.intelligence.ingest.TrackStatus
import com.projectapex.intelligence.support.Fixtures.car
import com.projectapex.intelligence.support.Fixtures.frame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EventDeriverTest {

    private val deriver = EventDeriver()

    @Test
    fun `first frame produces no events`() {
        val events = deriver.derive(frame(1, lap = 1, cars = listOf(car("VER", 1, 1, 0.0))))
        assertTrue(events.isEmpty())
    }

    @Test
    fun `lap increment emits LapCompleted with the frame's lap time`() {
        deriver.derive(frame(1, lap = 5, cars = listOf(car("VER", 1, 5, 0.0))))
        val events = deriver.derive(frame(2, lap = 6, cars = listOf(car("VER", 1, 6, 0.0, lastLap = 92.3))))

        val lap = events.filterIsInstance<EngineEvent.LapCompleted>().single()
        assertEquals("VER", lap.driverId)
        assertEquals(6, lap.lapNumber)
        assertEquals(92.3, lap.lapTime!!.value, 1e-9)
    }

    @Test
    fun `multi-lap jump emits DataGap instead of guessing lap records`() {
        deriver.derive(frame(1, lap = 5, cars = listOf(car("VER", 1, 5, 0.0))))
        val events = deriver.derive(frame(2, lap = 8, cars = listOf(car("VER", 1, 8, 0.0))))

        assertTrue(events.filterIsInstance<EngineEvent.LapCompleted>().isEmpty())
        assertTrue(events.filterIsInstance<EngineEvent.DataGap>().isNotEmpty())
    }

    @Test
    fun `sequence jump emits DataGap with the missing count`() {
        deriver.derive(frame(1, lap = 5, cars = listOf(car("VER", 1, 5, 0.0))))
        val events = deriver.derive(frame(5, lap = 5, cars = listOf(car("VER", 1, 5, 0.0))))

        assertEquals(3, events.filterIsInstance<EngineEvent.DataGap>().single().missedFrames)
    }

    @Test
    fun `on-track swap is flagged onTrack for both cars`() {
        deriver.derive(
            frame(1, lap = 10, cars = listOf(car("VER", 1, 10, 0.0), car("NOR", 2, 10, 0.5)))
        )
        val events = deriver.derive(
            frame(2, lap = 10, cars = listOf(car("NOR", 1, 10, 0.0), car("VER", 2, 10, 0.4)))
        )

        val changes = events.filterIsInstance<EngineEvent.PositionChange>()
        assertEquals(2, changes.size)
        assertTrue(changes.all { it.onTrack })
        val norris = changes.single { it.driverId == "NOR" }
        assertEquals(2, norris.from)
        assertEquals(1, norris.to)
    }

    @Test
    fun `gaining a place because the other car pits is not onTrack`() {
        deriver.derive(
            frame(1, lap = 10, cars = listOf(car("VER", 1, 10, 0.0), car("NOR", 2, 10, 3.0)))
        )
        val events = deriver.derive(
            frame(
                2, lap = 10,
                cars = listOf(car("NOR", 1, 10, 0.0), car("VER", 2, 10, 20.0, pit = PitStatus.IN_PIT)),
            )
        )

        val norris = events.filterIsInstance<EngineEvent.PositionChange>().single { it.driverId == "NOR" }
        assertFalse(norris.onTrack)
    }

    @Test
    fun `pit status transitions emit PitEntry then PitExit with rejoin info`() {
        deriver.derive(frame(1, lap = 20, cars = listOf(car("VER", 1, 20, 0.0))))

        val entry = deriver.derive(
            frame(2, lap = 20, cars = listOf(car("VER", 1, 20, 0.0, pit = PitStatus.IN_PIT)))
        )
        assertEquals("VER", entry.filterIsInstance<EngineEvent.PitEntry>().single().driverId)

        val exit = deriver.derive(
            frame(3, lap = 21, cars = listOf(car("VER", 1, 21, 0.0, tyreAge = 0)))
        )
        val pitExit = exit.filterIsInstance<EngineEvent.PitExit>().single()
        assertEquals("VER", pitExit.driverId)
        assertEquals(1, pitExit.rejoinPosition)
        assertEquals(0, pitExit.tyre.ageLaps)
    }

    @Test
    fun `track status change emits an edge event`() {
        deriver.derive(frame(1, lap = 12, cars = listOf(car("VER", 1, 12, 0.0))))
        val events = deriver.derive(
            frame(2, lap = 12, cars = listOf(car("VER", 1, 12, 0.0)), status = TrackStatus.SC)
        )

        val change = events.filterIsInstance<EngineEvent.TrackStatusChanged>().single()
        assertEquals(TrackStatus.GREEN, change.from)
        assertEquals(TrackStatus.SC, change.to)
    }

    @Test
    fun `retirement flag flip and disappearance both emit DriverRetired`() {
        deriver.derive(
            frame(1, lap = 30, cars = listOf(car("VER", 1, 30, 0.0), car("NOR", 2, 30, 5.0)))
        )
        val flagged = deriver.derive(
            frame(2, lap = 30, cars = listOf(car("VER", 1, 30, 0.0), car("NOR", 2, 30, 5.0, retired = true)))
        )
        assertEquals("NOR", flagged.filterIsInstance<EngineEvent.DriverRetired>().single().driverId)

        val vanished = deriver.derive(frame(3, lap = 31, cars = listOf(car("VER", 1, 31, 0.0))))
        // NOR was already retired in the previous frame, so no duplicate event.
        assertTrue(vanished.filterIsInstance<EngineEvent.DriverRetired>().isEmpty())
    }

    @Test
    fun `growing lap deficit to the leader emits CarLapped`() {
        deriver.derive(
            frame(1, lap = 30, cars = listOf(car("VER", 1, 30, 0.0), car("STR", 2, 29, 80.0)))
        )
        val events = deriver.derive(
            frame(2, lap = 31, cars = listOf(car("VER", 1, 31, 0.0, lastLap = 90.0), car("STR", 2, 29, 85.0)))
        )

        val lapped = events.filterIsInstance<EngineEvent.CarLapped>().single()
        assertEquals("STR", lapped.driverId)
        assertEquals("VER", lapped.byDriverId)
    }
}
