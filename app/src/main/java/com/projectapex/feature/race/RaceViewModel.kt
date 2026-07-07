package com.projectapex.feature.race

import androidx.lifecycle.ViewModel
import com.projectapex.core.model.SessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class RaceUiState(
    val session: SessionState = SessionState(),
    val sessionType: String = "Race",
    val sessionTime: String = "Sunday 14:00",
    val upcomingCapabilities: List<String> = listOf(
        "Live gaps",
        "Strategy AI",
        "Track visualisation",
        "Race replay"
    )
)

@HiltViewModel
class RaceViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(RaceUiState())
    val uiState: StateFlow<RaceUiState> = _uiState.asStateFlow()
}
