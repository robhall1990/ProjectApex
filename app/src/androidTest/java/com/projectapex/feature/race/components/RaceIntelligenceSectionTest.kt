package com.projectapex.feature.race.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.projectapex.core.theme.ProjectApexTheme
import com.projectapex.domain.intelligence.InsightPriority
import com.projectapex.domain.intelligence.InsightType
import com.projectapex.domain.intelligence.RaceInsight
import org.junit.Rule
import org.junit.Test

class RaceIntelligenceSectionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun sampleInsight() = RaceInsight(
        id = "battle-NOR-VER-1000",
        type = InsightType.BATTLE_DETECTED,
        priority = InsightPriority.HIGH,
        title = "NOR and VER are battling",
        description = "NOR is 0.8s behind VER",
        timestamp = 1_000L
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
    fun displaysInsightTitleAndDescription() {
        composeRule.setContent {
            ProjectApexTheme {
                RaceIntelligenceSection(insights = listOf(sampleInsight()))
            }
        }

        composeRule.onNodeWithText("NOR and VER are battling").assertExists()
        composeRule.onNodeWithText("NOR is 0.8s behind VER").assertExists()
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
