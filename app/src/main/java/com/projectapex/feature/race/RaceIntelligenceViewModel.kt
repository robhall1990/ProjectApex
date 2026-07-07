package com.projectapex.feature.race

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.projectapex.domain.intelligence.RaceInsight
import com.projectapex.domain.intelligence.RaceIntelligenceEngine
import com.projectapex.domain.race.RaceEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Reads [RaceEngine] directly rather than [com.projectapex.domain.timeline.RaceTimeline]
 * - a deliberate exception to "the Race screen reads RaceTimeline, not
 * RaceEngine" (see [RaceViewModel]). [RaceIntelligenceEngine] is stateful
 * and assumes it's fed states in chronological order; RaceTimeline's replay
 * scrubbing (previous/next/seek) can jump non-monotonically through
 * history, which would feed the engine states out of order. Reading the
 * live engine directly avoids that entirely, at the cost of insights not
 * reflecting whatever moment is currently being replayed.
 */
@HiltViewModel
class RaceIntelligenceViewModel @Inject constructor(
    raceEngine: RaceEngine,
    raceIntelligenceEngine: RaceIntelligenceEngine
) : ViewModel() {

    val insights: StateFlow<List<RaceInsight>> = raceEngine.state
        .map { raceState -> raceIntelligenceEngine.analyse(raceState) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )
}
