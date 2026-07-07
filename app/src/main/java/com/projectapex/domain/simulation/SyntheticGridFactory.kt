package com.projectapex.domain.simulation

import com.projectapex.core.model.SessionStatus
import com.projectapex.domain.model.CarState
import com.projectapex.domain.model.Driver
import com.projectapex.domain.model.RaceState
import com.projectapex.domain.model.TyreCompound

/**
 * Builds the starting grid for a synthetic (non-production) race. Used only
 * by [RaceSimulator] to seed believable UI-development data ahead of live
 * timing integration - this is not real race data.
 */
internal object SyntheticGridFactory {

    private const val FIELD_SIZE = 20

    private val namedDrivers = listOf(
        Driver(id = "VER", name = "Max Verstappen", team = "Red Bull Racing", number = 1),
        Driver(id = "NOR", name = "Lando Norris", team = "McLaren", number = 4),
        Driver(id = "PIA", name = "Oscar Piastri", team = "McLaren", number = 81),
        Driver(id = "LEC", name = "Charles Leclerc", team = "Ferrari", number = 16),
        Driver(id = "HAM", name = "Lewis Hamilton", team = "Ferrari", number = 44),
        Driver(id = "RUS", name = "George Russell", team = "Mercedes", number = 63)
    )

    private val placeholderDrivers = (namedDrivers.size + 1..FIELD_SIZE).map { number ->
        Driver(
            id = "D%02d".format(number),
            name = "Driver $number",
            team = "Team $number",
            number = number
        )
    }

    private val grid: List<Driver> = namedDrivers + placeholderDrivers

    fun initialState(totalLaps: Int = 58): RaceState {
        val cars = grid.mapIndexed { index, driver ->
            CarState(
                driver = driver,
                position = index + 1,
                lap = 1,
                gapToLeaderSeconds = index * 1.2,
                tyreCompound = TyreCompound.MEDIUM,
                tyreAgeLaps = 0,
                isInPitLane = false
            )
        }

        return RaceState(
            sessionStatus = SessionStatus.LIVE,
            currentLap = 1,
            totalLaps = totalLaps,
            cars = cars,
            timestamp = 0L
        )
    }
}
