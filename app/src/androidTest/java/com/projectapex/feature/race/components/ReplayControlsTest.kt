package com.projectapex.feature.race.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.projectapex.core.theme.ProjectApexTheme
import org.junit.Rule
import org.junit.Test

class ReplayControlsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun displaysLiveIndicatorWhenLive() {
        composeRule.setContent {
            ProjectApexTheme {
                ReplayControls(
                    isReplayMode = false,
                    onPreviousClick = {},
                    onPlayPauseClick = {},
                    onNextClick = {}
                )
            }
        }

        composeRule.onNodeWithText("LIVE RACE").assertExists()
    }

    @Test
    fun displaysReplayModeIndicatorWhenReplaying() {
        composeRule.setContent {
            ProjectApexTheme {
                ReplayControls(
                    isReplayMode = true,
                    onPreviousClick = {},
                    onPlayPauseClick = {},
                    onNextClick = {}
                )
            }
        }

        composeRule.onNodeWithText("REPLAY MODE").assertExists()
    }

    @Test
    fun rendersPreviousPlayPauseAndNextControls() {
        composeRule.setContent {
            ProjectApexTheme {
                ReplayControls(
                    isReplayMode = false,
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
