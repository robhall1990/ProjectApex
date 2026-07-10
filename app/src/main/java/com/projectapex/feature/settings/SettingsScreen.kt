package com.projectapex.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.projectapex.R
import com.projectapex.domain.livedata.ConnectionStatus

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineMedium
        )

        DeveloperModeCard(
            isSimulationRunning = uiState.isSimulationRunning,
            onStartSimulation = viewModel::startSimulation,
            onStopSimulation = viewModel::stopSimulation
        )

        val connectionStatus = uiState.liveConnectionStatus
        LiveSessionCard(
            isLiveSessionRunning = uiState.isLiveSessionRunning,
            statusText = when (connectionStatus) {
                is ConnectionStatus.Idle -> stringResource(R.string.settings_live_session_status_idle)
                is ConnectionStatus.Connecting -> stringResource(R.string.settings_live_session_status_connecting)
                is ConnectionStatus.Live -> stringResource(R.string.settings_live_session_status_live)
                is ConnectionStatus.Error ->
                    stringResource(R.string.settings_live_session_status_error, connectionStatus.message)
            },
            isError = connectionStatus is ConnectionStatus.Error,
            onStartLiveSession = viewModel::startLiveSession,
            onStopLiveSession = viewModel::stopLiveSession
        )
    }
}
