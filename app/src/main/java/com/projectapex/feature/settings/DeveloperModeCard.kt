package com.projectapex.feature.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
 * Development-only controls for driving [com.projectapex.domain.simulation.RaceSimulator]
 * before live timing exists. Takes plain state and callbacks only - it has
 * no idea the simulator exists, that's [SettingsViewModel]'s job.
 */
@Composable
fun DeveloperModeCard(
    isSimulationRunning: Boolean,
    onStartSimulation: () -> Unit,
    onStopSimulation: () -> Unit,
    modifier: Modifier = Modifier
) {
    ApexCard(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.settings_developer_mode_title),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = if (isSimulationRunning) {
                stringResource(R.string.settings_simulation_status_running)
            } else {
                stringResource(R.string.settings_simulation_status_stopped)
            },
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
        )
        Button(
            onClick = onStartSimulation,
            enabled = !isSimulationRunning,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.settings_start_simulated_race))
        }
        OutlinedButton(
            onClick = onStopSimulation,
            enabled = isSimulationRunning,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            Text(stringResource(R.string.settings_stop_simulation))
        }
    }
}
