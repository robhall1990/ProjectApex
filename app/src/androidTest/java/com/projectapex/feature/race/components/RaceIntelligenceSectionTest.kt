package com.projectapex.feature.race.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.projectapex.core.theme.ProjectApexTheme
import com.projectapex.feature.race.RaceInsightUi
import com.projectapex.intelligence.detect.Severity
import org.junit.Rule
import org.junit.Test

class RaceIntelligenceSectionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun sampleInsight() = RaceInsightUi(
        id = "drs_imminent:NOR:VER",
        icon = "🎯",
        headline = "NOR projected to enter DRS in 2 laps",
        detail = "closing on VER at 0.4s/lap",
        severity = Severity.HIGH,
    )

    @Test
    fun rendersTheSectionTitle() {
        composeRule.setContent {
            ProjectApexTheme {
                RaceIntelligenceSection(insights = listOf(sampleInsight()))
            }
        }

        composeRule.onNodeWithText("Race Intelligence").assertExists()
    }

    @Test
    fun displaysInsightHeadlineAndDetail() {
        composeRule.setContent {
            ProjectApexTheme {
                RaceIntelligenceSection(insights = listOf(sampleInsight()))
            }
        }

        composeRule.onNodeWithText("NOR projected to enter DRS in 2 laps").assertExists()
        composeRule.onNodeWithText("closing on VER at 0.4s/lap").assertExists()
    }

    @Test
    fun showsAPlaceholderWhenThereAreNoInsights() {
        composeRule.setContent {
            ProjectApexTheme {
                RaceIntelligenceSection(insights = emptyList())
            }
        }

        composeRule.onNodeWithText("No insights yet").assertExists()
    }
}
