package com.projectapex.domain.race

import com.projectapex.core.model.SessionStatus
import com.projectapex.domain.model.CarState
import com.projectapex.domain.model.Driver
import com.projectapex.domain.model.RaceState
import com.projectapex.domain.model.TyreCompound

/**
 * Test-only fixture for building [RaceState] snapshots. Lives in the test
 * source set (not `main`) so it can never be imported from production or UI
 * code - the app itself has no fake race data.
 */
object RaceStateFactory {

    fun fiveCarField(): RaceState {
        val drivers = listOf(
            Driver(id = "norris", name = "Lando Norris", team = "McLaren", number = 4),
            Driver(id = "verstappen", name = "Max Verstappen", team = "Red Bull Racing", number = 1),
            Driver(id = "piastri", name = "Oscar Piastri", team = "McLaren", number = 81),
            Driver(id = "russell", name = "George Russell", team = "Mercedes", number = 63),
            Driver(id = "hamilton", name = "Lewis Hamilton", team = "Ferrari", number = 44)
        )

        val cars = drivers.mapIndexed { index, driver ->
            CarState(
                driver = driver,
                position = index + 1,
                lap = 12,
                gapToLeaderSeconds = index * 1.5,
                tyreCompound = TyreCompound.MEDIUM,
                tyreAgeLaps = 12,
                isInPitLane = false
            )
        }

        return RaceState(
            sessionStatus = SessionStatus.LIVE,
            currentLap = 12,
            totalLaps = 58,
            cars = cars,
            timestamp = 1_000L
        )
    }
}
