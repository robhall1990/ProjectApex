package com.projectapex.feature.race.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.projectapex.R
import com.projectapex.feature.race.RaceInsightUi

private const val MAX_DISPLAYED_INSIGHTS = 3

/**
 * The top [MAX_DISPLAYED_INSIGHTS] insights from the intelligence platform's
 * [com.projectapex.intelligence.rank.RacePulse] (already ranked, most
 * important first), mapped to [RaceInsightUi] by the ViewModel and rendered as
 * a clean, dividing-line-separated feed. Plain presentation data only — no
 * reference to detectors, observations, or the pulse engine.
 */
@Composable
fun RaceIntelligenceSection(
    insights: List<RaceInsightUi>,
    modifier: Modifier = Modifier
) {
    SectionCard(title = stringResource(R.string.race_intelligence_title), modifier = modifier) {
        if (insights.isEmpty()) {
            Text(
                text = stringResource(R.string.race_intelligence_no_insights),
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            val shown = insights.take(MAX_DISPLAYED_INSIGHTS)
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                shown.forEachIndexed { index, insight ->
                    key(insight.id) {
                        RaceInsightCard(insight = insight)
                    }
                    if (index != shown.lastIndex) {
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
