package com.projectapex.feature.race

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.projectapex.domain.intelligence.RaceInsight
import com.projectapex.domain.intelligence.RaceIntelligenceEngine
import com.projectapex.domain.model.RaceState
import com.projectapex.domain.race.RaceEngine
import com.projectapex.domain.timeline.RaceTimeline
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class RaceUiState(
    val raceState: RaceState = RaceState.empty(),
    val insights: List<RaceInsight> = emptyList(),
    val isReplayMode: Boolean = false
)

/**
 * Coordinates two independent domain pipelines into one [RaceUiState]:
 * [RaceTimeline] (race data + replay position) and [RaceEngine] piped
 * through [RaceIntelligenceEngine] (insights, always live - see
 * docs/Architecture.md for why intelligence doesn't read RaceTimeline).
 * Composables never call either domain service directly - only this
 * ViewModel does.
 */
@HiltViewModel
class RaceViewModel @Inject constructor(
    private val raceTimeline: RaceTimeline,
    raceEngine: RaceEngine,
    raceIntelligenceEngine: RaceIntelligenceEngine
) : ViewModel() {

    val uiState: StateFlow<RaceUiState> = combine(
        raceTimeline.state,
        raceEngine.state.map { raceState -> raceIntelligenceEngine.analyse(raceState) }
    ) { timeline, insights ->
        RaceUiState(
            raceState = timeline.currentSnapshot?.raceState ?: RaceState.empty(),
            insights = insights,
            isReplayMode = !timeline.isLive
        )
    }.stateIn(
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
