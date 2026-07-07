package com.projectapex.feature.race.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.projectapex.R
import com.projectapex.core.ui.ApexCard
import com.projectapex.domain.model.CarState
import com.projectapex.domain.model.RaceState
import kotlin.math.roundToInt

private val ROW_HEIGHT = 48.dp
private val MARKER_WIDTH = 48.dp
private val MARKER_HEIGHT = 40.dp
private const val ANIMATION_DURATION_MS = 600

/**
 * A horizontal "unwrapped" race-distance ribbon: not a geographical track
 * map, just cars laid out left-to-right by how far through the race they
 * are, with the leader visually distinct. Renders purely from [raceState] -
 * it has no idea a [com.projectapex.domain.simulation.RaceSimulator] exists.
 */
@Composable
fun UnwrappedTrackView(
    raceState: RaceState,
    modifier: Modifier = Modifier
) {
    val cars = raceState.cars.sortedBy { it.position }
    val density = LocalDensity.current

    ApexCard(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.race_track_start),
            style = MaterialTheme.typography.labelMedium
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        if (cars.isEmpty()) {
            Text(
                text = stringResource(R.string.race_track_no_session),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 24.dp)
            )
        } else {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ROW_HEIGHT * cars.size)
            ) {
                val trackWidthPx = with(density) { (maxWidth - MARKER_WIDTH).toPx() }
                val rowHeightPx = with(density) { ROW_HEIGHT.toPx() }

                cars.forEachIndexed { index, car ->
                    key(car.driver.id) {
                        CarMarker(
                            car = car,
                            progress = car.trackProgress(raceState),
                            trackWidthPx = trackWidthPx,
                            rowOffsetPx = index * rowHeightPx
                        )
                    }
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        Text(
            text = stringResource(R.string.race_track_finish),
            style = MaterialTheme.typography.labelMedium
        )
    }
}

/**
 * Race-distance progress for a single car, in the range `0f..1f` along the
 * ribbon.
 *
 * v1 uses the race's overall lap progress, shared by every car, because
 * [CarState] doesn't yet track an individual distance-around-lap value. The
 * signature deliberately takes the [CarState] (not just the [RaceState]) so
 * that swapping this for a real per-car `distanceAroundLap` later only
 * requires changing this one function - not the view's layout code.
 */
private fun CarState.trackProgress(raceState: RaceState): Float {
    if (raceState.totalLaps <= 0) return 0f
    return (raceState.currentLap.toFloat() / raceState.totalLaps.toFloat()).coerceIn(0f, 1f)
}

@Composable
private fun CarMarker(
    car: CarState,
    progress: Float,
    trackWidthPx: Float,
    rowOffsetPx: Float
) {
    val targetX = progress * trackWidthPx
    val animatedX by animateFloatAsState(
        targetValue = targetX,
        animationSpec = tween(durationMillis = ANIMATION_DURATION_MS),
        label = "carMarkerX"
    )
    val animatedY by animateFloatAsState(
        targetValue = rowOffsetPx,
        animationSpec = tween(durationMillis = ANIMATION_DURATION_MS),
        label = "carMarkerY"
    )

    val isLeader = car.position == 1
    val backgroundColor = if (isLeader) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (isLeader) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .offset { IntOffset(animatedX.roundToInt(), animatedY.roundToInt()) }
            .size(width = MARKER_WIDTH, height = MARKER_HEIGHT)
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = car.position.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
            Text(
                text = car.driver.id,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor
            )
        }
    }
}
