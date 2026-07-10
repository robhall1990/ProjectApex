package com.projectapex.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.projectapex.domain.livedata.ConnectionStatus
import com.projectapex.domain.livedata.OpenF1LiveDataSource
import com.projectapex.domain.simulation.RaceSimulator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class SettingsUiState(
    val isSimulationRunning: Boolean = false,
    val isLiveSessionRunning: Boolean = false,
    val liveConnectionStatus: ConnectionStatus = ConnectionStatus.Idle,
)

/**
 * [RaceSimulator] and [OpenF1LiveDataSource] both write to the same
 * singleton `RaceEngine`, so only one may run at a time - starting either
 * one stops the other. Enforced here, not inside either data source, so
 * they stay independent of each other.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val raceSimulator: RaceSimulator,
    private val liveDataSource: OpenF1LiveDataSource,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        raceSimulator.isRunning,
        liveDataSource.isRunning,
        liveDataSource.connectionStatus,
    ) { isSimulationRunning, isLiveSessionRunning, liveConnectionStatus ->
        SettingsUiState(
            isSimulationRunning = isSimulationRunning,
            isLiveSessionRunning = isLiveSessionRunning,
            liveConnectionStatus = liveConnectionStatus,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState()
    )

    fun startSimulation() {
        liveDataSource.stop()
        raceSimulator.start()
    }

    fun stopSimulation() {
        raceSimulator.stop()
    }

    fun startLiveSession(totalLaps: Int) {
        raceSimulator.stop()
        liveDataSource.start(totalLaps)
    }

    fun stopLiveSession() {
        liveDataSource.stop()
    }
}
