package com.projectapex.intelligence.ingest

/**
 * Canonicalises an already-validated frame so downstream code can rely on
 * invariants instead of re-deriving them:
 *
 * - cars sorted by position (running order == list order everywhere else),
 * - `interval` filled in from consecutive `gapToLeader`s where the feed
 *   didn't provide it (clamped at zero — timing noise can make consecutive
 *   gaps momentarily cross).
 */
class FrameNormaliser {

    fun normalise(frame: TimingFrame): TimingFrame {
        val sorted = frame.cars.sortedBy { it.position }
        val withIntervals = sorted.mapIndexed { index, car ->
            if (car.interval != null || index == 0) {
                car
            } else {
                val ahead = sorted[index - 1]
                val derived = if (car.gapToLeader != null && ahead.gapToLeader != null) {
                    Seconds((car.gapToLeader.value - ahead.gapToLeader.value).coerceAtLeast(0.0))
                } else {
                    null
                }
                car.copy(interval = derived)
            }
        }
        return frame.copy(cars = withIntervals)
    }
}
