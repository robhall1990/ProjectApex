package com.projectapex.feature.race

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.projectapex.domain.model.RaceState
import com.projectapex.domain.timeline.RaceTimeline
import com.projectapex.intelligence.adapter.RacePulseEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class RaceUiState(
    val raceState: RaceState = RaceState.empty(),
    val insights: List<RaceInsightUi> = emptyList(),
    val isReplayMode: Boolean = false,
    val timelinePosition: Int = 0,
    val timelineSize: Int = 0
)

/**
 * Coordinates two independent pipelines into one [RaceUiState]:
 * [RaceTimeline] (race data + replay position) and [RacePulseEngine] (the new
 * APX-010/011/012 intelligence platform, always live — the pipeline is
 * stateful and reads the live engine, never the replay position, see
 * docs/Architecture.md). Observations are mapped to presentation models by
 * [ObservationPresenter]; composables never touch the pulse, the timeline, or
 * the presenter's inputs directly — only this ViewModel does.
 */
@HiltViewModel
class RaceViewModel @Inject constructor(
    private val raceTimeline: RaceTimeline,
    racePulseEngine: RacePulseEngine,
    private val presenter: ObservationPresenter
) : ViewModel() {

    val uiState: StateFlow<RaceUiState> = combine(
        raceTimeline.state,
        racePulseEngine.pulse
    ) { timeline, pulse ->
        RaceUiState(
            raceState = timeline.currentSnapshot?.raceState ?: RaceState.empty(),
            insights = presenter.present(pulse),
            isReplayMode = !timeline.isLive,
            timelinePosition = timeline.currentIndex,
            timelineSize = timeline.snapshots.size
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
