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

class UnwrappedTrackViewTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun sampleRaceState(): RaceState {
        val driver = Driver(id = "VER", name = "Max Verstappen", team = "Red Bull Racing", number = 1)
        val car = CarState(
            driver = driver,
            position = 1,
            lap = 5,
            gapToLeaderSeconds = 0.0,
            tyreCompound = TyreCompound.MEDIUM,
            tyreAgeLaps = 5,
            isInPitLane = false
        )
        return RaceState(
            sessionStatus = SessionStatus.LIVE,
            currentLap = 5,
            totalLaps = 58,
            cars = listOf(car),
            timestamp = 5_000L
        )
    }

    @Test
    fun rendersStartAndFinishMarkersForAnActiveRace() {
        composeRule.setContent {
            ProjectApexTheme {
                UnwrappedTrackView(raceState = sampleRaceState())
            }
        }

        composeRule.onNodeWithText("Unwrapped Track").assertExists()
        composeRule.onNodeWithText("START").assertExists()
        composeRule.onNodeWithText("FINISH").assertExists()
    }

    @Test
    fun displaysDriverAbbreviationAndPositionForEachCar() {
        composeRule.setContent {
            ProjectApexTheme {
                UnwrappedTrackView(raceState = sampleRaceState())
            }
        }

        composeRule.onNodeWithText("VER").assertExists()
        composeRule.onNodeWithText("1").assertExists()
    }

    @Test
    fun rendersEmptyStateWhenThereAreNoCars() {
        composeRule.setContent {
            ProjectApexTheme {
                UnwrappedTrackView(raceState = RaceState.empty())
            }
        }

        composeRule.onNodeWithText("START").assertExists()
        composeRule.onNodeWithText("FINISH").assertExists()
    }
}
