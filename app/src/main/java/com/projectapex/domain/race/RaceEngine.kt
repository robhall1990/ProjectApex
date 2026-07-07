package com.projectapex.domain.race

import com.projectapex.domain.model.RaceState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owns the single source of truth for the current [RaceState]. Pure Kotlin,
 * no Android or networking dependencies: an external data source will call
 * [updateState] once one exists, and every consumer - Android UI, and later
 * an AI insight engine - only ever reads [state].
 *
 * Thread safety and observability both come from [MutableStateFlow]: value
 * assignment is atomic, and it always replays its latest value to new and
 * existing collectors.
 */
class RaceEngine {

    private val _state = MutableStateFlow(RaceState.empty())
    val state: StateFlow<RaceState> = _state.asStateFlow()

    fun updateState(newState: RaceState) {
        _state.value = newState
    }
}
