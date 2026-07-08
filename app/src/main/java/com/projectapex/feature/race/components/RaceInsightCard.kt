package com.projectapex.feature.race.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.projectapex.R
import com.projectapex.feature.race.RaceInsightUi
import com.projectapex.intelligence.detect.Severity

/**
 * A single race insight: an emoji icon, headline, supporting detail, and a
 * small severity dot. Plain presentation data ([RaceInsightUi]) in — no
 * reference to the intelligence platform's detectors or observations.
 */
@Composable
fun RaceInsightCard(
    insight: RaceInsightUi,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = insight.icon,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(end = 12.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = insight.headline,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                )
                SeverityDot(severity = insight.severity)
            }
            Text(
                text = insight.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun SeverityDot(severity: Severity) {
    val color: Color = when (severity) {
        Severity.CRITICAL, Severity.HIGH -> MaterialTheme.colorScheme.error
        Severity.MEDIUM -> MaterialTheme.colorScheme.primary
        Severity.LOW, Severity.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val description = stringResource(
        when (severity) {
            Severity.CRITICAL, Severity.HIGH -> R.string.insight_priority_high
            Severity.MEDIUM -> R.string.insight_priority_medium
            Severity.LOW, Severity.INFO -> R.string.insight_priority_low
        }
    )
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color)
            .semantics { contentDescription = description }
    )
}
