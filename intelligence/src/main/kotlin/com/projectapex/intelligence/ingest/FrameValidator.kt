package com.projectapex.intelligence.ingest

/**
 * Unit-sanity gate at the mouth of the pipeline
 * (docs/RaceIntelligencePlatform.md §7): positions must form a permutation,
 * gaps must be non-negative, sequences must move forward. A frame that fails
 * any check is rejected whole — downstream code never defends against
 * malformed data because it never sees any.
 *
 * Stateful only for sequence dedupe (single-writer, like everything in the
 * pipeline).
 */
class FrameValidator {

    private var lastAcceptedSequence: Long = Long.MIN_VALUE

    sealed interface Violation {
        data class StaleSequence(val sequence: Long, val lastAccepted: Long) : Violation
        object EmptyGrid : Violation
        data class DuplicateDriver(val driverId: String) : Violation
        data class PositionsNotPermutation(val positions: List<Int>) : Violation
        data class NegativeGap(val driverId: String) : Violation
        data class NegativeLapCount(val driverId: String) : Violation
        data class LapOutOfRange(val lap: Int, val totalLaps: Int) : Violation
    }

    /** Empty result = valid; the frame is recorded as accepted. */
    fun validate(frame: TimingFrame): List<Violation> {
        val violations = buildList {
            if (frame.sequence <= lastAcceptedSequence) {
                add(Violation.StaleSequence(frame.sequence, lastAcceptedSequence))
            }
            if (frame.cars.isEmpty()) add(Violation.EmptyGrid)

            frame.cars.groupBy { it.driverId }
                .filterValues { it.size > 1 }
                .keys.forEach { add(Violation.DuplicateDriver(it)) }

            val positions = frame.cars.map { it.position }.sorted()
            if (frame.cars.isNotEmpty() && positions != (1..frame.cars.size).toList()) {
                add(Violation.PositionsNotPermutation(positions))
            }

            frame.cars.forEach { car ->
                if ((car.gapToLeader?.value ?: 0.0) < 0.0 ||
                    (car.interval?.value ?: 0.0) < 0.0
                ) {
                    add(Violation.NegativeGap(car.driverId))
                }
                if (car.lapsCompleted < 0) add(Violation.NegativeLapCount(car.driverId))
            }

            if (frame.lap < 0 || (frame.totalLaps > 0 && frame.lap > frame.totalLaps)) {
                add(Violation.LapOutOfRange(frame.lap, frame.totalLaps))
            }
        }
        if (violations.isEmpty()) lastAcceptedSequence = frame.sequence
        return violations
    }
}
