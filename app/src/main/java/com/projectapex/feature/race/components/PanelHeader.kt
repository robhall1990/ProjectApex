package com.projectapex.feature.race.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle

/**
 * Consistent title styling reused across Race screen sections - the top
 * session banner (via a larger [style] override) as well as the "Race
 * Intelligence"/"Leaderboard"/"Unwrapped Track" section titles.
 */
@Composable
fun PanelHeader(
    title: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleMedium,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    Text(
        text = title,
        style = style,
        color = color,
        modifier = modifier
    )
}
