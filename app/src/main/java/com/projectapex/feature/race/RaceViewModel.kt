package com.projectapex.feature.race

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.projectapex.domain.model.RaceState
import com.projectapex.domain.timeline.RaceTimeline
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class RaceUiState(
    val raceState: RaceState = RaceState.empty(),
    val isLiveMode: Boolean = true,
    val timelinePosition: Int = 0,
    val timelineSize: Int = 0
)

@HiltViewModel
class RaceViewModel @Inject constructor(
    private val raceTimeline: RaceTimeline
) : ViewModel() {

    val uiState: StateFlow<RaceUiState> = raceTimeline.state
        .map { timeline ->
            RaceUiState(
                raceState = timeline.currentSnapshot?.raceState ?: RaceState.empty(),
                isLiveMode = timeline.isLive,
                timelinePosition = timeline.currentIndex,
                timelineSize = timeline.snapshots.size
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = RaceUiState()
        )

    fun onPreviousClicked() {
        raceTimeline.previous()
    }

    fun onNextClicked() {
        raceTimeline.next()
    }

    /**
     * A single toggle standing in for "pause"/"play": while live, there is
     * no separate frozen state to enter beyond stepping back one snapshot
     * (RaceTimeline has no dedicated pause concept); while replaying,
     * pressing it jumps back to the live edge.
     */
    fun onPlayPauseClicked() {
        val timeline = raceTimeline.state.value
        if (timeline.isLive) {
            raceTimeline.previous()
        } else {
            raceTimeline.seek(timeline.snapshots.lastIndex)
        }
    }
}
