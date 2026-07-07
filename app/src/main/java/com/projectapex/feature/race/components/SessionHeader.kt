package com.projectapex.feature.race.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.projectapex.R
import com.projectapex.core.ui.ApexCard

/**
 * The top-of-screen session banner: event name, lap counter, and the big
 * LIVE/REPLAY status word, plus the Previous/Play-Pause/Next controls for
 * scrubbing RaceTimeline. Plain state and callbacks only - no
 * [com.projectapex.domain.timeline.RaceTimeline] reference;
 * [com.projectapex.feature.race.RaceViewModel] is the only place that
 * imports it.
 */
@Composable
fun SessionHeader(
    eventName: String,
    currentLap: Int,
    totalLaps: Int,
    isReplayMode: Boolean,
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    onPreviousClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ApexCard(modifier = modifier.fillMaxWidth()) {
        PanelHeader(
            title = eventName,
            style = MaterialTheme.typography.headlineSmall
        )
        InfoRow(
            label = stringResource(R.string.race_session_lap_label),
            value = "$currentLap / $totalLaps",
            modifier = Modifier.padding(top = 6.dp)
        )
        PanelHeader(
            title = if (isReplayMode) {
                stringResource(R.string.race_mode_replay)
            } else {
                stringResource(R.string.race_mode_live)
            },
            style = MaterialTheme.typography.labelLarge,
            color = if (isReplayMode) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.primary
            },
            modifier = Modifier.padding(top = 10.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            OutlinedButton(onClick = onPreviousClick, enabled = canGoPrevious) {
                Text(stringResource(R.string.race_control_previous))
            }
            OutlinedButton(onClick = onPlayPauseClick) {
                Text(
                    text = if (isReplayMode) {
                        stringResource(R.string.race_control_play)
                    } else {
                        stringResource(R.string.race_control_pause)
                    }
                )
            }
            OutlinedButton(onClick = onNextClick, enabled = canGoNext) {
                Text(stringResource(R.string.race_control_next))
            }
        }
    }
}
