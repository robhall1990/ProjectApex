package com.projectapex.domain.livedata

import com.projectapex.data.openf1.DriverDto
import com.projectapex.data.openf1.IntervalDto
import com.projectapex.data.openf1.LapDto
import com.projectapex.data.openf1.PitDto
import com.projectapex.data.openf1.PositionDto
import com.projectapex.data.openf1.RaceControlDto
import com.projectapex.data.openf1.StintDto
import com.projectapex.core.model.SessionStatus
import com.projectapex.domain.model.CarState
import com.projectapex.domain.model.Driver
import com.projectapex.domain.model.RaceState
import com.projectapex.domain.model.TrackStatus
import com.projectapex.domain.model.TyreCompound
import java.time.Instant
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenF1RaceStateMapperTest {

    private val now = Instant.parse("2026-07-11T14:00:30Z")

    private fun driver(number: Int, name: String, team: String) =
        DriverDto(driverNumber = number, fullName = name, broadcastName = name, teamName = team)

    private fun List<PositionDto>.positionsByDriver(): Map<Int, PositionDto> = associateBy { it.driverNumber }
    private fun List<IntervalDto>.intervalsByDriver(): Map<Int, IntervalDto> = associateBy { it.driverNumber }
    private fun List<LapDto>.lapsByDriver(): Map<Int, LapDto> = associateBy { it.driverNumber }

    @Test
    fun `maps a basic field into a valid strict position permutation`() {
        val drivers = listOf(
            driver(1, "Max Verstappen", "Red Bull Racing"),
            driver(4, "Lando Norris", "McLaren"),
            driver(16, "Charles Leclerc", "Ferrari"),
        )
        val positions = listOf(
            PositionDto(date = "2026-07-11T14:00:00Z", driverNumber = 4, position = 1),
            PositionDto(date = "2026-07-11T14:00:00Z", driverNumber = 1, position = 2),
            PositionDto(date = "2026-07-11T14:00:00Z", driverNumber = 16, position = 3),
        )
        val intervals = listOf(
            IntervalDto(date = "2026-07-11T14:00:00Z", driverNumber = 4, gapToLeader = JsonPrimitive(0.0)),
            IntervalDto(date = "2026-07-11T14:00:00Z", driverNumber = 1, gapToLeader = JsonPrimitive(1.234)),
            IntervalDto(date = "2026-07-11T14:00:00Z", driverNumber = 16, gapToLeader = JsonPrimitive(5.6)),
        )
        val laps = listOf(
            LapDto(driverNumber = 4, lapNumber = 12),
            LapDto(driverNumber = 1, lapNumber = 12),
            LapDto(driverNumber = 16, lapNumber = 11),
        )

        val state = OpenF1RaceStateMapper.map(
            drivers = drivers,
            positions = positions.positionsByDriver(),
            intervals = intervals.intervalsByDriver(),
            laps = laps.lapsByDriver(),
            stints = emptyList(),
            pitStops = emptyList(),
            raceControl = emptyList(),
            totalLapsOverride = 58,
            previousState = null,
            now = now,
        )

        assertEquals(setOf(1, 2, 3), state.cars.map { it.position }.toSet())
        assertEquals("4", state.cars[0].driver.id)
        assertEquals(0.0, state.cars[0].gapToLeaderSeconds, 1e-9)
        assertEquals("1", state.cars[1].driver.id)
        assertEquals(1.234, state.cars[1].gapToLeaderSeconds, 1e-9)
        assertEquals("16", state.cars[2].driver.id)
        assertEquals(12, state.currentLap)
    }

    @Test
    fun `a driver with no position record still yields a strict permutation`() {
        val drivers = listOf(
            driver(1, "Max Verstappen", "Red Bull Racing"),
            driver(4, "Lando Norris", "McLaren"),
        )
        val positions = listOf(
            PositionDto(date = "2026-07-11T14:00:00Z", driverNumber = 4, position = 1),
        )
        val intervals = listOf(
            IntervalDto(date = "2026-07-11T14:00:00Z", driverNumber = 4, gapToLeader = JsonPrimitive(0.0)),
            IntervalDto(date = "2026-07-11T14:00:00Z", driverNumber = 1, gapToLeader = JsonPrimitive(3.0)),
        )

        val state = OpenF1RaceStateMapper.map(
            drivers = drivers,
            positions = positions.positionsByDriver(),
            intervals = intervals.intervalsByDriver(),
            laps = emptyMap(),
            stints = emptyList(),
            pitStops = emptyList(),
            raceControl = emptyList(),
            totalLapsOverride = 58,
            previousState = null,
            now = now,
        )

        assertEquals(listOf(1, 2), state.cars.map { it.position })
        // Norris has a real position (1); Verstappen falls back to gap ordering and lands second.
        assertEquals("4", state.cars[0].driver.id)
        assertEquals("1", state.cars[1].driver.id)
    }

    @Test
    fun `non-numeric gap falls back to previous state, then to zero`() {
        val drivers = listOf(driver(1, "Max Verstappen", "Red Bull Racing"))
        val intervals = listOf(
            IntervalDto(date = "2026-07-11T14:00:00Z", driverNumber = 1, gapToLeader = JsonPrimitive("+1 LAP")),
        )

        val withPrevious = OpenF1RaceStateMapper.map(
            drivers = drivers,
            positions = emptyMap(),
            intervals = intervals.intervalsByDriver(),
            laps = emptyMap(),
            stints = emptyList(),
            pitStops = emptyList(),
            raceControl = emptyList(),
            totalLapsOverride = 58,
            previousState = raceStateWithGap(driverId = "1", gapSeconds = 42.0),
            now = now,
        )
        assertEquals(42.0, withPrevious.cars.single().gapToLeaderSeconds, 1e-9)

        val withoutPrevious = OpenF1RaceStateMapper.map(
            drivers = drivers,
            positions = emptyMap(),
            intervals = intervals.intervalsByDriver(),
            laps = emptyMap(),
            stints = emptyList(),
            pitStops = emptyList(),
            raceControl = emptyList(),
            totalLapsOverride = 58,
            previousState = null,
            now = now,
        )
        assertEquals(0.0, withoutPrevious.cars.single().gapToLeaderSeconds, 1e-9)
    }

    @Test
    fun `tyre age combines stint start age with laps completed on the stint`() {
        val drivers = listOf(driver(1, "Max Verstappen", "Red Bull Racing"))
        val laps = listOf(LapDto(driverNumber = 1, lapNumber = 20))
        val stints = listOf(
            StintDto(driverNumber = 1, compound = "HARD", stintNumber = 1, lapStart = 1, lapEnd = 14, tyreAgeAtStart = 0),
            StintDto(driverNumber = 1, compound = "SOFT", stintNumber = 2, lapStart = 15, lapEnd = null, tyreAgeAtStart = 0),
        )

        val state = OpenF1RaceStateMapper.map(
            drivers = drivers,
            positions = emptyMap(),
            intervals = emptyMap(),
            laps = laps.lapsByDriver(),
            stints = stints,
            pitStops = emptyList(),
            raceControl = emptyList(),
            totalLapsOverride = 58,
            previousState = null,
            now = now,
        )

        val car = state.cars.single()
        assertEquals(TyreCompound.SOFT, car.tyreCompound)
        assertEquals(5, car.tyreAgeLaps) // lap 20 - stint start 15
    }

    @Test
    fun `unrecognized compound string falls back to medium instead of throwing`() {
        val drivers = listOf(driver(1, "Max Verstappen", "Red Bull Racing"))
        val stints = listOf(
            StintDto(driverNumber = 1, compound = "UNKNOWN_COMPOUND", stintNumber = 1, lapStart = 1, lapEnd = null, tyreAgeAtStart = 0),
        )

        val state = OpenF1RaceStateMapper.map(
            drivers = drivers,
            positions = emptyMap(),
            intervals = emptyMap(),
            laps = emptyMap(),
            stints = stints,
            pitStops = emptyList(),
            raceControl = emptyList(),
            totalLapsOverride = 58,
            previousState = null,
            now = now,
        )

        assertEquals(TyreCompound.MEDIUM, state.cars.single().tyreCompound)
    }

    @Test
    fun `pit window heuristic marks in-pit only shortly after a pit event`() {
        val drivers = listOf(driver(1, "Max Verstappen", "Red Bull Racing"))
        val recentPit = listOf(
            PitDto(driverNumber = 1, date = "2026-07-11T14:00:10Z"), // 20s before `now`
        )
        val stalePit = listOf(
            PitDto(driverNumber = 1, date = "2026-07-11T13:58:00Z"), // well over 45s before `now`
        )

        val recent = OpenF1RaceStateMapper.map(
            drivers, emptyMap(), emptyMap(), emptyMap(), emptyList(),
            recentPit, emptyList(), 58, null, now,
        )
        assertTrue(recent.cars.single().isInPitLane)

        val stale = OpenF1RaceStateMapper.map(
            drivers, emptyMap(), emptyMap(), emptyMap(), emptyList(),
            stalePit, emptyList(), 58, null, now,
        )
        assertFalse(stale.cars.single().isInPitLane)
    }

    @Test
    fun `race control messages derive safety car and red flag status`() {
        val safetyCar = listOf(
            RaceControlDto(date = "2026-07-11T13:59:00Z", flag = "YELLOW", message = "SAFETY CAR DEPLOYED"),
        )
        assertEquals(TrackStatus.SAFETY_CAR, trackStatusFor(safetyCar))

        val virtualSafetyCar = listOf(
            RaceControlDto(date = "2026-07-11T13:59:00Z", message = "VIRTUAL SAFETY CAR DEPLOYED"),
        )
        assertEquals(TrackStatus.VIRTUAL_SAFETY_CAR, trackStatusFor(virtualSafetyCar))

        val redFlag = listOf(
            RaceControlDto(date = "2026-07-11T13:59:00Z", flag = "RED"),
        )
        assertEquals(TrackStatus.RED, trackStatusFor(redFlag))

        val sequenceBackToGreen = listOf(
            RaceControlDto(date = "2026-07-11T13:58:00Z", message = "SAFETY CAR DEPLOYED"),
            RaceControlDto(date = "2026-07-11T13:59:00Z", message = "SAFETY CAR IN THIS LAP"),
        )
        assertEquals(TrackStatus.GREEN, trackStatusFor(sequenceBackToGreen))

        assertEquals(TrackStatus.GREEN, trackStatusFor(emptyList()))
    }

    @Test
    fun `empty inputs produce no crash and a sensible empty state`() {
        val state = OpenF1RaceStateMapper.map(
            drivers = emptyList(),
            positions = emptyMap(),
            intervals = emptyMap(),
            laps = emptyMap(),
            stints = emptyList(),
            pitStops = emptyList(),
            raceControl = emptyList(),
            totalLapsOverride = 58,
            previousState = null,
            now = now,
        )

        assertTrue(state.cars.isEmpty())
        assertEquals(0, state.currentLap)
        assertEquals(58, state.totalLaps)
        assertEquals(TrackStatus.GREEN, state.trackStatus)
    }

    private fun trackStatusFor(raceControl: List<RaceControlDto>): TrackStatus =
        OpenF1RaceStateMapper.map(
            drivers = listOf(driver(1, "Max Verstappen", "Red Bull Racing")),
            positions = emptyMap(),
            intervals = emptyMap(),
            laps = emptyMap(),
            stints = emptyList(),
            pitStops = emptyList(),
            raceControl = raceControl,
            totalLapsOverride = 58,
            previousState = null,
            now = now,
        ).trackStatus

    private fun raceStateWithGap(driverId: String, gapSeconds: Double): RaceState = RaceState(
        sessionStatus = SessionStatus.LIVE,
        currentLap = 20,
        totalLaps = 58,
        cars = listOf(
            CarState(
                driver = Driver(id = driverId, name = "Max Verstappen", team = "Red Bull Racing", number = driverId.toInt()),
                position = 1,
                lap = 20,
                gapToLeaderSeconds = gapSeconds,
                tyreCompound = TyreCompound.MEDIUM,
                tyreAgeLaps = 5,
                isInPitLane = false,
            )
        ),
        timestamp = now.toEpochMilli(),
    )
}
