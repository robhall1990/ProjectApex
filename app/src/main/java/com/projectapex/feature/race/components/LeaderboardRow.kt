package com.projectapex.feature.race.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.projectapex.domain.model.CarState
import com.projectapex.domain.model.TyreCompound

/**
 * Position / Driver / Gap / Tyre compound for a single car, in the column
 * order the design calls for. Plain data in - no reference to any domain
 * service.
 */
@Composable
fun LeaderboardRow(
    car: CarState,
    modifier: Modifier = Modifier
) {
    val isLeader = car.position == 1

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isLeader) {
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                } else {
                    Modifier
                }
            )
            .padding(horizontal = if (isLeader) 8.dp else 0.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = car.position.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isLeader) FontWeight.Bold else FontWeight.Normal,
            color = if (isLeader) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(28.dp)
        )
        Text(
            text = car.driver.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isLeader) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = if (isLeader) {
                stringResource(R.string.race_leaderboard_leader_gap)
            } else {
                stringResource(R.string.race_leaderboard_gap_format, car.gapToLeaderSeconds)
            },
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        TyreCompoundBadge(compound = car.tyreCompound)
    }
}

/** Column labels matching [LeaderboardRow]'s layout, for the header row above it. */
@Composable
fun LeaderboardColumnHeader(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = stringResource(R.string.race_leaderboard_column_position),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(28.dp)
        )
        Text(
            text = stringResource(R.string.race_leaderboard_column_driver),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = stringResource(R.string.race_leaderboard_column_gap),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        Text(
            text = stringResource(R.string.race_leaderboard_column_tyre),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Standard F1 tyre compound colour coding - not team colours, a universal
 * motorsport convention. Text colour is chosen per-compound for contrast,
 * not a single fixed colour, since some compound colours are light (HARD,
 * MEDIUM) and some are dark/saturated (SOFT, WET, INTERMEDIATE).
 */
@Composable
private fun TyreCompoundBadge(compound: TyreCompound) {
    val (background, label, foreground) = when (compound) {
        TyreCompound.SOFT -> Triple(Color(0xFFDA291C), "S", Color.White)
        TyreCompound.MEDIUM -> Triple(Color(0xFFFFD700), "M", Color.Black)
        TyreCompound.HARD -> Triple(Color(0xFFF0F0F0), "H", Color.Black)
        TyreCompound.INTERMEDIATE -> Triple(Color(0xFF43B02A), "I", Color.White)
        TyreCompound.WET -> Triple(Color(0xFF0067AD), "W", Color.White)
    }
    val description = stringResource(
        when (compound) {
            TyreCompound.SOFT -> R.string.tyre_compound_soft
            TyreCompound.MEDIUM -> R.string.tyre_compound_medium
            TyreCompound.HARD -> R.string.tyre_compound_hard
            TyreCompound.INTERMEDIATE -> R.string.tyre_compound_intermediate
            TyreCompound.WET -> R.string.tyre_compound_wet
        }
    )
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(background)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = foreground
        )
    }
}
