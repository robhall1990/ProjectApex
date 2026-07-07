package com.projectapex.feature.race

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.projectapex.R
import com.projectapex.core.ui.ApexCard

@Composable
fun RaceIntelligenceCard(
    capabilities: List<String>,
    modifier: Modifier = Modifier
) {
    ApexCard(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.race_intelligence_title),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = stringResource(R.string.race_intelligence_no_active_session),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
        )
        capabilities.forEach { capability ->
            Text(
                text = "✓ $capability",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
    }
}
