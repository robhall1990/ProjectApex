package com.projectapex.feature.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val SPLASH_DISPLAY_DURATION_MS = 1200L

data class SplashUiState(
    val isReadyToProceed: Boolean = false
)

@HiltViewModel
class SplashViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(SplashUiState())
    val uiState: StateFlow<SplashUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            delay(SPLASH_DISPLAY_DURATION_MS)
            _uiState.value = SplashUiState(isReadyToProceed = true)
        }
    }
}
