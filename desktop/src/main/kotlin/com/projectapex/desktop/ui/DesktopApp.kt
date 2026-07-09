package com.projectapex.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.projectapex.desktop.AppContainer

/**
 * Top-level desktop screen: a control bar (simulator / live session), a
 * leaderboard, and an insights panel side by side. Deliberately not a port
 * of feature/race's mobile screens - this is a fresh, minimal "engine bench"
 * for exercising [com.projectapex.domain.race.RaceEngine] and the
 * intelligence pipeline without an Android device.
 */
@Composable
fun DesktopApp(container: AppContainer) {
    val raceState by container.raceEngine.state.collectAsState()
    val pulse by container.racePulseEngine.pulse.collectAsState()
    val isSimRunning by container.raceSimulator.isRunning.collectAsState()
    val isLiveRunning by container.liveDataSource.isRunning.collectAsState()
    val connectionStatus by container.liveDataSource.connectionStatus.collectAsState()

    val insights by remember(pulse) {
        derivedStateOf { container.observationPresenter.present(pulse) }
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ControlsBar(
                    raceState = raceState,
                    isSimRunning = isSimRunning,
                    isLiveRunning = isLiveRunning,
                    connectionStatus = connectionStatus,
                    onStartSimulator = container::startSimulator,
                    onStartLiveSession = container::startLiveSession,
                    onStop = container::stopAll,
                )

                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    LeaderboardTable(raceState = raceState, modifier = Modifier.weight(1.4f))
                    InsightsPanel(insights = insights, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
