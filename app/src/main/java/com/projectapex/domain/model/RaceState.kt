package com.projectapex.domain.model

import com.projectapex.core.model.SessionStatus

/**
 * A complete snapshot of a race at a point in time. [RaceEngine] owns the
 * current instance; everything else (UI, future AI insight engine) only
 * ever reads it.
 */
data class RaceState(
    val sessionStatus: SessionStatus,
    val currentLap: Int,
    val totalLaps: Int,
    val cars: List<CarState>,
    val timestamp: Long
) {
    companion object {
        fun empty(): RaceState = RaceState(
            sessionStatus = SessionStatus.OFFLINE,
            currentLap = 0,
            totalLaps = 0,
            cars = emptyList(),
            timestamp = 0L
        )
    }
}
