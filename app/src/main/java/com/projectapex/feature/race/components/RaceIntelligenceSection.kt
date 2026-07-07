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
import com.projectapex.domain.intelligence.RaceInsight

private const val MAX_DISPLAYED_INSIGHTS = 3

/**
 * The top [MAX_DISPLAYED_INSIGHTS] insights from [com.projectapex.domain.intelligence.RaceIntelligenceEngine]
 * as a clean, dividing-line-separated feed - highest priority first, since
 * that's the order the engine already returns them in. Plain data only - no
 * reference to the engine or RaceEngine, same as every other Race component.
 */
@Composable
fun RaceIntelligenceSection(
    insights: List<RaceInsight>,
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
