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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.projectapex.R
import com.projectapex.domain.model.CarState
import com.projectapex.domain.model.TyreCompound

/**
 * Position / Driver / Tyre compound / Gap for a single car. Plain data in -
 * no reference to any domain service.
 */
@Composable
fun LeaderboardRow(
    car: CarState,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = car.position.toString(),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(28.dp)
        )
        Text(
            text = car.driver.name,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        TyreCompoundBadge(compound = car.tyreCompound)
        Text(
            text = if (car.position == 1) {
                stringResource(R.string.race_leaderboard_leader_gap)
            } else {
                stringResource(R.string.race_leaderboard_gap_format, car.gapToLeaderSeconds)
            },
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}

/** Standard F1 tyre compound colour coding - not team colours, a universal motorsport convention. */
@Composable
private fun TyreCompoundBadge(compound: TyreCompound) {
    val (color, label) = when (compound) {
        TyreCompound.SOFT -> Color(0xFFDA291C) to "S"
        TyreCompound.MEDIUM -> Color(0xFFFFD700) to "M"
        TyreCompound.HARD -> Color(0xFFF0F0F0) to "H"
        TyreCompound.INTERMEDIATE -> Color(0xFF43B02A) to "I"
        TyreCompound.WET -> Color(0xFF0067AD) to "W"
    }
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.Black
        )
    }
}
