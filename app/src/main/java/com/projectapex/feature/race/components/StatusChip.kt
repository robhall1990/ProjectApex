package com.projectapex.feature.race.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * A small status pill used in [RaceStatusBar]: a muted label plus a value.
 * [enabled] = false renders a visibly dimmed state for features that don't
 * exist yet (Track Status/Weather/DRS placeholders), rather than hiding
 * them - the chip is still there, just clearly inactive.
 */
@Composable
fun StatusChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val alpha = if (enabled) 1f else 0.5f
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (enabled) 1f else 0.4f))
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .semantics { contentDescription = "$label: $value" },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
        )
    }
}
