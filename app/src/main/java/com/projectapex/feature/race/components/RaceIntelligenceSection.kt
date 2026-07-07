package com.projectapex.feature.race.components

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
        Text(
            text = stringResource(R.string.race_intelligence_title),
            style = MaterialTheme.typography.titleMedium
        )

        if (insights.isEmpty()) {
            Text(
                text = stringResource(R.string.race_intelligence_no_insights),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp)
            )
        } else {
            Column(modifier = Modifier.padding(top = 12.dp)) {
                insights.take(MAX_DISPLAYED_INSIGHTS).forEach { insight ->
                    key(insight.id) {
                        InsightRow(insight)
                    }
                }
            }
        }
    }
}

@Composable
private fun InsightRow(insight: RaceInsight) {
    Column(modifier = Modifier.padding(bottom = 12.dp)) {
        Text(text = insight.title, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = insight.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
