package com.projectapex.domain.timeline

/**
 * Immutable snapshot of [RaceTimeline]'s own state: the recorded history and
 * where the user is currently looking within it.
 */
data class RaceTimelineState(
    val snapshots: List<RaceSnapshot> = emptyList(),
    val currentIndex: Int = -1
) {
    val currentSnapshot: RaceSnapshot?
        get() = snapshots.getOrNull(currentIndex)

    /**
     * True when the user is viewing the most recently recorded snapshot -
     * i.e. following the race live rather than browsing history. An empty
     * timeline counts as live (there is no history to be behind on).
     */
    val isLive: Boolean
        get() = snapshots.isEmpty() || currentIndex == snapshots.lastIndex
}
