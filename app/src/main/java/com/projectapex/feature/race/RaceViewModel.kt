package com.projectapex.feature.race

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class RaceUiState(
    val screenName: String = "Race"
)

@HiltViewModel
class RaceViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(RaceUiState())
    val uiState: StateFlow<RaceUiState> = _uiState.asStateFlow()
}
