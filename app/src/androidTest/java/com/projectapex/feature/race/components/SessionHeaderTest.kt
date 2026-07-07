package com.projectapex.feature.race.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.projectapex.core.theme.ProjectApexTheme
import org.junit.Rule
import org.junit.Test

class SessionHeaderTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun displaysEventNameAndLapCounter() {
        composeRule.setContent {
            ProjectApexTheme {
                SessionHeader(
                    eventName = "British Grand Prix",
                    currentLap = 24,
                    totalLaps = 52,
                    isReplayMode = false,
                    canGoPrevious = true,
                    canGoNext = true,
                    onPreviousClick = {},
                    onPlayPauseClick = {},
                    onNextClick = {}
                )
            }
        }

        composeRule.onNodeWithText("British Grand Prix").assertExists()
        composeRule.onNodeWithText("24 / 52").assertExists()
        composeRule.onNodeWithText("LIVE").assertExists()
    }

    @Test
    fun displaysReplayIndicatorWhenReplaying() {
        composeRule.setContent {
            ProjectApexTheme {
                SessionHeader(
                    eventName = "British Grand Prix",
                    currentLap = 10,
                    totalLaps = 52,
                    isReplayMode = true,
                    canGoPrevious = true,
                    canGoNext = true,
                    onPreviousClick = {},
                    onPlayPauseClick = {},
                    onNextClick = {}
                )
            }
        }

        composeRule.onNodeWithText("REPLAY").assertExists()
    }

    @Test
    fun rendersPreviousPlayPauseAndNextControls() {
        composeRule.setContent {
            ProjectApexTheme {
                SessionHeader(
                    eventName = "British Grand Prix",
                    currentLap = 24,
                    totalLaps = 52,
                    isReplayMode = false,
                    canGoPrevious = true,
                    canGoNext = true,
                    onPreviousClick = {},
                    onPlayPauseClick = {},
                    onNextClick = {}
                )
            }
        }

        composeRule.onNodeWithText("< Previous").assertExists()
        composeRule.onNodeWithText("Pause").assertExists()
        composeRule.onNodeWithText("Next >").assertExists()
    }
}
