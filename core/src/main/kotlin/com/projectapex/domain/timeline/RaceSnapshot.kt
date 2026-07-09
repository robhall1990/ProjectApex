package com.projectapex.domain.timeline

import com.projectapex.domain.model.RaceState

/**
 * A single recorded point in time: a [RaceState] plus the wall-clock instant
 * it was captured (distinct from [RaceState.timestamp], which is the
 * simulated race clock).
 */
data class RaceSnapshot(
    val raceState: RaceState,
    val timestamp: Long
)
