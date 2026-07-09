package com.projectapex.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.projectapex.domain.livedata.ConnectionStatus
import com.projectapex.domain.model.RaceState

@Composable
fun ControlsBar(
    raceState: RaceState,
    isSimRunning: Boolean,
    isLiveRunning: Boolean,
    connectionStatus: ConnectionStatus,
    onStartSimulator: () -> Unit,
    onStartLiveSession: (totalLaps: Int) -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var totalLapsInput by remember { mutableStateOf("") }
    val isRunning = isSimRunning || isLiveRunning

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Lap ${raceState.currentLap}/${raceState.totalLaps} · ${raceState.sessionStatus}",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(end = 8.dp),
        )

        Button(onClick = onStartSimulator, enabled = !isRunning) {
            Text("Start Simulator")
        }

        OutlinedTextField(
            value = totalLapsInput,
            onValueChange = { totalLapsInput = it.filter(Char::isDigit) },
            label = { Text("Total laps") },
            enabled = !isRunning,
            singleLine = true,
            modifier = Modifier.width(140.dp),
        )

        Button(
            onClick = { onStartLiveSession(totalLapsInput.toIntOrNull() ?: 0) },
            enabled = !isRunning,
        ) {
            Text("Start Live (OpenF1)")
        }

        OutlinedButton(onClick = onStop, enabled = isRunning) {
            Text("Stop")
        }

        Text(
            text = connectionStatusText(isSimRunning, isLiveRunning, connectionStatus),
            style = MaterialTheme.typography.bodyMedium,
            color = if (connectionStatus is ConnectionStatus.Error) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

private fun connectionStatusText(
    isSimRunning: Boolean,
    isLiveRunning: Boolean,
    status: ConnectionStatus,
): String = when {
    isSimRunning -> "Simulator running"
    isLiveRunning -> when (status) {
        is ConnectionStatus.Idle -> "Idle"
        is ConnectionStatus.Connecting -> "Connecting…"
        is ConnectionStatus.Live -> "Live — receiving OpenF1 data"
        is ConnectionStatus.Error -> "Connection issue: ${status.message}"
    }
    else -> "Stopped"
}
