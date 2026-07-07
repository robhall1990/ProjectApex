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
    fun displaysLiveModeIndicatorWhenLive() {
        composeRule.setContent {
            ProjectApexTheme {
                ReplayControls(
                    isLiveMode = true,
                    timelinePosition = 4,
                    timelineSize = 5,
                    onPreviousClick = {},
                    onPlayPauseClick = {},
                    onNextClick = {}
                )
            }
        }

        composeRule.onNodeWithText("LIVE MODE").assertExists()
    }

    @Test
    fun displaysReplayModeIndicatorWhenNotLive() {
        composeRule.setContent {
            ProjectApexTheme {
                ReplayControls(
                    isLiveMode = false,
                    timelinePosition = 2,
                    timelineSize = 5,
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
                    isLiveMode = true,
                    timelinePosition = 4,
                    timelineSize = 5,
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
