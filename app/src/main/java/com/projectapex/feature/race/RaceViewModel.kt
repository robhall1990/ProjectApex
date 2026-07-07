package com.projectapex.feature.race

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.projectapex.domain.model.RaceState
import com.projectapex.domain.race.RaceEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class RaceUiState(
    val raceState: RaceState = RaceState.empty()
)

@HiltViewModel
class RaceViewModel @Inject constructor(
    raceEngine: RaceEngine
) : ViewModel() {

    val uiState: StateFlow<RaceUiState> = raceEngine.state
        .map { raceState -> RaceUiState(raceState = raceState) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = RaceUiState()
        )
}
