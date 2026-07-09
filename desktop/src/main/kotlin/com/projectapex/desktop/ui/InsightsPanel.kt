package com.projectapex.desktop.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.projectapex.feature.race.RaceInsightUi

@Composable
fun InsightsPanel(insights: List<RaceInsightUi>, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().border(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        if (insights.isEmpty()) {
            Text(
                text = "No insights yet",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(insights, key = { it.id }) { insight ->
                    InsightRow(insight)
                }
            }
        }
    }
}

@Composable
private fun InsightRow(insight: RaceInsightUi) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "${insight.icon} ${insight.headline}",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = insight.detail,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
