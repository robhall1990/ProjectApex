package com.projectapex.feature.race

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.projectapex.MainActivity
import com.projectapex.core.model.SessionStatus
import com.projectapex.domain.model.CarState
import com.projectapex.domain.model.Driver
import com.projectapex.domain.model.RaceState
import com.projectapex.domain.model.TyreCompound
import com.projectapex.domain.race.RaceEngine
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

/**
 * Verifies the Race screen actually renders data sourced from [RaceEngine] -
 * not a simulator, and not hardcoded UI state.
 */
@HiltAndroidTest
class RaceScreenTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var raceEngine: RaceEngine

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun displaysRaceDataFromRaceEngine() {
        val driver = Driver(id = "NOR", name = "Lando Norris", team = "McLaren", number = 4)
        val car = CarState(
            driver = driver,
            position = 1,
            lap = 10,
            gapToLeaderSeconds = 0.0,
            tyreCompound = TyreCompound.MEDIUM,
            tyreAgeLaps = 10,
            isInPitLane = false
        )
        raceEngine.updateState(
            RaceState(
                sessionStatus = SessionStatus.LIVE,
                currentLap = 10,
                totalLaps = 58,
                cars = listOf(car),
                timestamp = 10_000L
            )
        )

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Lando Norris").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("Live Race").assertExists()
        composeRule.onNodeWithText("Lando Norris").assertExists()
        composeRule.onNodeWithText("NOR").assertExists()
    }
}
