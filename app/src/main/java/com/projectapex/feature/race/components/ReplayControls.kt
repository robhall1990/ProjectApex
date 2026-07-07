package com.projectapex.feature.race.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.projectapex.R
import com.projectapex.core.ui.ApexCard

/**
 * Replay mode indicator plus Previous/Play-Pause/Next controls. Takes plain
 * state and callbacks only - it has no idea [com.projectapex.domain.timeline.RaceTimeline]
 * exists, that's [com.projectapex.feature.race.RaceViewModel]'s job.
 */
@Composable
fun ReplayControls(
    isLiveMode: Boolean,
    timelinePosition: Int,
    timelineSize: Int,
    onPreviousClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ApexCard(modifier = modifier.fillMaxWidth()) {
        Text(
            text = if (isLiveMode) {
                stringResource(R.string.race_mode_live)
            } else {
                stringResource(R.string.race_mode_replay)
            },
            style = MaterialTheme.typography.labelLarge,
            color = if (isLiveMode) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            }
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onPreviousClick,
                enabled = timelineSize > 0 && timelinePosition > 0
            ) {
                Text(stringResource(R.string.race_control_previous))
            }
            OutlinedButton(
                onClick = onPlayPauseClick,
                enabled = timelineSize > 0
            ) {
                Text(
                    text = if (isLiveMode) {
                        stringResource(R.string.race_control_pause)
                    } else {
                        stringResource(R.string.race_control_play)
                    }
                )
            }
            OutlinedButton(
                onClick = onNextClick,
                enabled = timelineSize > 0 && timelinePosition < timelineSize - 1
            ) {
                Text(stringResource(R.string.race_control_next))
            }
        }
    }
}
