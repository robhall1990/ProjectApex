package com.projectapex.feature.race.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.projectapex.core.model.SessionStatus
import com.projectapex.core.theme.ProjectApexTheme
import com.projectapex.domain.model.CarState
import com.projectapex.domain.model.Driver
import com.projectapex.domain.model.RaceState
import com.projectapex.domain.model.TyreCompound
import org.junit.Rule
import org.junit.Test

class RaceLeaderboardTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun sampleRaceState(): RaceState {
        val cars = listOf(
            CarState(
                driver = Driver(id = "VER", name = "Max Verstappen", team = "Red Bull Racing", number = 1),
                position = 1,
                lap = 10,
                gapToLeaderSeconds = 0.0,
                tyreCompound = TyreCompound.MEDIUM,
                tyreAgeLaps = 10,
                isInPitLane = false
            ),
            CarState(
                driver = Driver(id = "NOR", name = "Lando Norris", team = "McLaren", number = 4),
                position = 2,
                lap = 10,
                gapToLeaderSeconds = 1.2,
                tyreCompound = TyreCompound.SOFT,
                tyreAgeLaps = 5,
                isInPitLane = false
            )
        )
        return RaceState(
            sessionStatus = SessionStatus.LIVE,
            currentLap = 10,
            totalLaps = 58,
            cars = cars,
            timestamp = 10_000L
        )
    }

    @Test
    fun displaysDriverNamesInPositionOrder() {
        composeRule.setContent {
            ProjectApexTheme {
                RaceLeaderboard(raceState = sampleRaceState())
            }
        }

        composeRule.onNodeWithText("Max Verstappen").assertExists()
        composeRule.onNodeWithText("Lando Norris").assertExists()
    }

    @Test
    fun showsAPlaceholderWhenThereAreNoCars() {
        composeRule.setContent {
            ProjectApexTheme {
                RaceLeaderboard(raceState = RaceState.empty())
            }
        }

        composeRule.onNodeWithText("No active session — start a simulation in Settings").assertExists()
    }
}
