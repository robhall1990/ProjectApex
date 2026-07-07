package com.projectapex.feature.race.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.projectapex.R
import com.projectapex.domain.model.RaceState

/**
 * A wrapping row of [StatusChip]s: real signals derived from [raceState]/
 * [isReplayMode], plus disabled placeholders for features that don't exist
 * yet (Track Status/Weather/DRS). "Session" reflects the raw domain
 * [com.projectapex.core.model.SessionStatus]; "Simulation" reflects whether
 * there's actually car data flowing - a distinct (if currently correlated)
 * signal that could diverge from a future, richer data source.
 */
@Composable
fun RaceStatusBar(
    raceState: RaceState,
    isReplayMode: Boolean,
    modifier: Modifier = Modifier
) {
    SectionCard(title = stringResource(R.string.race_status_title), modifier = modifier) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatusChip(
                label = stringResource(R.string.race_status_session_label),
                value = raceState.sessionStatus.name
            )
            StatusChip(
                label = stringResource(R.string.race_status_simulation_label),
                value = if (raceState.cars.isNotEmpty()) {
                    stringResource(R.string.race_status_simulation_running)
                } else {
                    stringResource(R.string.race_status_simulation_stopped)
                }
            )
            if (isReplayMode) {
                StatusChip(
                    label = stringResource(R.string.race_status_replay_label),
                    value = stringResource(R.string.race_status_replay_active)
                )
            }
            StatusChip(
                label = stringResource(R.string.race_status_track_label),
                value = stringResource(R.string.race_status_placeholder_value),
                enabled = false
            )
            StatusChip(
                label = stringResource(R.string.race_status_weather_label),
                value = stringResource(R.string.race_status_placeholder_value),
                enabled = false
            )
            StatusChip(
                label = stringResource(R.string.race_status_drs_label),
                value = stringResource(R.string.race_status_placeholder_value),
                enabled = false
            )
        }
    }
}
