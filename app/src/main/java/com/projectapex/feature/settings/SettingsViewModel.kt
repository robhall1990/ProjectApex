package com.projectapex.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.projectapex.domain.simulation.RaceSimulator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class SettingsUiState(
    val isSimulationRunning: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val raceSimulator: RaceSimulator
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = raceSimulator.isRunning
        .map { isRunning -> SettingsUiState(isSimulationRunning = isRunning) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SettingsUiState()
        )

    fun startSimulation() {
        raceSimulator.start()
    }

    fun stopSimulation() {
        raceSimulator.stop()
    }
}
