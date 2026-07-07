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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.projectapex.R
import com.projectapex.domain.intelligence.InsightPriority
import com.projectapex.domain.intelligence.InsightType
import com.projectapex.domain.intelligence.RaceInsight

/**
 * A single race insight: an emoji keyed off [InsightType], title,
 * description, and a small priority dot. Plain data in - no reference to
 * [com.projectapex.domain.intelligence.RaceIntelligenceEngine].
 */
@Composable
fun RaceInsightCard(
    insight: RaceInsight,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = insight.type.toEmoji(),
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
                    text = insight.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                )
                PriorityDot(priority = insight.priority)
            }
            Text(
                text = insight.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

private fun InsightType.toEmoji(): String = when (this) {
    InsightType.BATTLE_DETECTED -> "🔥"
    InsightType.GAP_CLOSING -> "📉"
    InsightType.GAP_INCREASING -> "📈"
    InsightType.FASTEST_CAR -> "⚡"
    InsightType.DRS_RANGE -> "🎯"
    InsightType.TYRE_CONCERN -> "🛞"
}

@Composable
private fun PriorityDot(priority: InsightPriority) {
    val color = when (priority) {
        InsightPriority.HIGH -> MaterialTheme.colorScheme.error
        InsightPriority.MEDIUM -> MaterialTheme.colorScheme.primary
        InsightPriority.LOW -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val description = stringResource(
        when (priority) {
            InsightPriority.HIGH -> R.string.insight_priority_high
            InsightPriority.MEDIUM -> R.string.insight_priority_medium
            InsightPriority.LOW -> R.string.insight_priority_low
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
