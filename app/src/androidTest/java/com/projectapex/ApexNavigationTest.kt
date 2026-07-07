package com.projectapex

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class ApexNavigationTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun waitForNodesWithText(text: String, minCount: Int = 1) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().size >= minCount
        }
    }

    @Test
    fun raceDashboardRendersAfterSplash() {
        // "LIVE" can legitimately appear more than once (the session header's
        // status word and the status bar's session chip both use it), so the
        // wait itself - not a follow-up assertExists() - is the assertion.
        waitForNodesWithText("LIVE")
    }

    @Test
    fun bottomNavigationSwitchesToAnalysisTab() {
        waitForNodesWithText("LIVE")

        // Before navigating, "Analysis" only appears once, as the nav bar label.
        composeRule.onAllNodesWithText("Analysis").onFirst().performClick()

        // After navigating, it appears a second time as the screen's own content.
        waitForNodesWithText("Analysis", minCount = 2)
    }

    @Test
    fun bottomNavigationSwitchesToSettingsTab() {
        waitForNodesWithText("LIVE")

        composeRule.onAllNodesWithText("Settings").onFirst().performClick()

        waitForNodesWithText("Settings", minCount = 2)
    }
}
