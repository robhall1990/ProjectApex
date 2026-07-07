package com.projectapex.feature.race.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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

/**
 * The top-of-screen LIVE RACE/REPLAY MODE banner plus Previous/Play-Pause/Next
 * controls. Plain state and callbacks only - no [com.projectapex.domain.timeline.RaceTimeline]
 * reference; [com.projectapex.feature.race.RaceViewModel] is the only place
 * that imports it. Buttons are always enabled: RaceTimeline's previous/next
 * already clamp safely at the ends of recorded history, so a boundary press
 * is a harmless no-op rather than something that needs disabling.
 */
@Composable
fun ReplayControls(
    isReplayMode: Boolean,
    onPreviousClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(
            title = if (isReplayMode) {
                stringResource(R.string.race_mode_replay)
            } else {
                stringResource(R.string.race_mode_live)
            },
            style = MaterialTheme.typography.headlineSmall,
            color = if (isReplayMode) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.primary
            }
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            OutlinedButton(onClick = onPreviousClick) {
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
            OutlinedButton(onClick = onNextClick) {
                Text(stringResource(R.string.race_control_next))
            }
        }
    }
}
