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

class RaceStatusBarTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun liveRaceState(): RaceState = RaceState(
        sessionStatus = SessionStatus.LIVE,
        currentLap = 10,
        totalLaps = 58,
        cars = listOf(
            CarState(
                driver = Driver(id = "VER", name = "Max Verstappen", team = "Red Bull Racing", number = 1),
                position = 1,
                lap = 10,
                gapToLeaderSeconds = 0.0,
                tyreCompound = TyreCompound.MEDIUM,
                tyreAgeLaps = 10,
                isInPitLane = false
            )
        ),
        timestamp = 10_000L
    )

    @Test
    fun showsSessionAndSimulationChipsWhenLive() {
        composeRule.setContent {
            ProjectApexTheme {
                RaceStatusBar(raceState = liveRaceState(), isReplayMode = false)
            }
        }

        composeRule.onNodeWithText("Session").assertExists()
        composeRule.onNodeWithText("Simulation").assertExists()
        composeRule.onNodeWithText("Running").assertExists()
    }

    @Test
    fun showsReplayChipOnlyWhenReplaying() {
        composeRule.setContent {
            ProjectApexTheme {
                RaceStatusBar(raceState = liveRaceState(), isReplayMode = true)
            }
        }

        composeRule.onNodeWithText("Replay").assertExists()
        composeRule.onNodeWithText("Active").assertExists()
    }

    @Test
    fun showsDisabledPlaceholdersForFutureFeatures() {
        composeRule.setContent {
            ProjectApexTheme {
                RaceStatusBar(raceState = RaceState.empty(), isReplayMode = false)
            }
        }

        composeRule.onNodeWithText("Track").assertExists()
        composeRule.onNodeWithText("Weather").assertExists()
        composeRule.onNodeWithText("DRS").assertExists()
    }
}
