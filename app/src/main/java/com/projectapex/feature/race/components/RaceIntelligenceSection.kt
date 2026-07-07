package com.projectapex.feature.race.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.projectapex.R
import com.projectapex.core.ui.ApexCard
import com.projectapex.domain.intelligence.RaceInsight

private const val MAX_DISPLAYED_INSIGHTS = 3

/**
 * The top [MAX_DISPLAYED_INSIGHTS] insights from [com.projectapex.domain.intelligence.RaceIntelligenceEngine].
 * Takes plain data only - no reference to the engine or RaceEngine, same as
 * every other Race component.
 */
@Composable
fun RaceIntelligenceSection(
    insights: List<RaceInsight>,
    modifier: Modifier = Modifier
) {
    ApexCard(modifier = modifier.fillMaxWidth()) {
        SectionHeader(title = stringResource(R.string.race_intelligence_title))

        if (insights.isEmpty()) {
            Text(
                text = stringResource(R.string.race_intelligence_no_insights),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp)
            )
        } else {
            Column(
                modifier = Modifier.padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                insights.take(MAX_DISPLAYED_INSIGHTS).forEach { insight ->
                    key(insight.id) {
                        RaceInsightCard(insight = insight)
                    }
                }
            }
        }
    }
}
