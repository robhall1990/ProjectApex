package com.projectapex.domain.timeline

import com.projectapex.domain.DefaultDispatcher
import com.projectapex.domain.model.RaceState
import com.projectapex.domain.race.RaceEngine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val MAX_SNAPSHOTS = 1000

/**
 * Sits between [RaceEngine] and the UI, recording every [RaceState] the
 * engine ever holds and letting the UI browse that history independently of
 * whatever the engine is currently doing:
 *
 * ```
 * RaceSimulator -> RaceEngine -> RaceTimeline -> UI
 * ```
 *
 * In-memory only, capped at [MAX_SNAPSHOTS] - oldest snapshots are dropped
 * once the cap is exceeded. Not a replay video: [previous]/[next]/[seek]
 * jump between discrete recorded snapshots, nothing auto-advances on a timer.
 *
 * "Live" vs "replay" isn't a separate flag - it falls out of comparing
 * [RaceTimelineState.currentIndex] to the newest index. While the caller is
 * at the live edge, each new [record] keeps them there (like a DVR); once
 * they step backwards, new recordings keep arriving in the background
 * without dragging their view forward.
 */
@Singleton
class RaceTimeline @Inject constructor(
    raceEngine: RaceEngine,
    @DefaultDispatcher dispatcher: CoroutineDispatcher
) {

    private val timelineScope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _state = MutableStateFlow(RaceTimelineState())
    val state: StateFlow<RaceTimelineState> = _state.asStateFlow()

    init {
        timelineScope.launch {
            raceEngine.state.collect { raceState -> record(raceState) }
        }
    }

    fun record(state: RaceState) {
        _state.update { current ->
            val wasLive = current.isLive
            val appended = current.snapshots + RaceSnapshot(state, System.currentTimeMillis())
            val overflow = (appended.size - MAX_SNAPSHOTS).coerceAtLeast(0)
            val trimmed = if (overflow > 0) appended.drop(overflow) else appended

            val newIndex = if (wasLive) {
                trimmed.lastIndex
            } else {
                (current.currentIndex - overflow).coerceIn(0, trimmed.lastIndex)
            }

            current.copy(snapshots = trimmed, currentIndex = newIndex)
        }
    }

    fun previous() {
        _state.update { current ->
            if (current.snapshots.isEmpty()) return@update current
            current.copy(currentIndex = (current.currentIndex - 1).coerceIn(0, current.snapshots.lastIndex))
        }
    }

    fun next() {
        _state.update { current ->
            if (current.snapshots.isEmpty()) return@update current
            current.copy(currentIndex = (current.currentIndex + 1).coerceIn(0, current.snapshots.lastIndex))
        }
    }

    fun seek(index: Int) {
        _state.update { current ->
            if (current.snapshots.isEmpty()) return@update current
            current.copy(currentIndex = index.coerceIn(0, current.snapshots.lastIndex))
        }
    }

    fun clear() {
        _state.value = RaceTimelineState()
    }
}
