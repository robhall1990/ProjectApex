package com.projectapex.intelligence.features

import com.projectapex.intelligence.events.EngineEvent
import com.projectapex.intelligence.ingest.CarTiming
import com.projectapex.intelligence.ingest.Seconds
import com.projectapex.intelligence.ingest.TrackStatus

/**
 * Read-only window onto the FeatureStore (docs/RaceIntelligencePlatform.md §8)
 * — the only thing detectors and predictors ever see. They are pure functions
 * of this view plus config; nothing downstream can mutate history.
 */
interface FeatureView {
    val lap: Int
    val totalLaps: Int
    val trackStatus: TrackStatus

    /** Current field sorted by position; empty before the first frame. */
    val runningOrder: List<CarTiming>

    fun laps(driverId: String): List<LapRecord>
    fun currentStint(driverId: String): Stint?
    fun stints(driverId: String): List<Stint>

    /** Instantaneous gap between any two cars (behind − ahead), from the latest frame. */
    fun interval(aheadId: String, behindId: String): Seconds?

    /** Lap-aligned interval history over the last [lastNLaps] laps (§9.3). */
    fun intervalHistory(aheadId: String, behindId: String, lastNLaps: Int): List<GapPoint>

    /** Fuel-corrected clean pace over the trailing window (§9.1); null until 2 clean laps. */
    fun pace(driverId: String, window: Int = 0): PaceEstimate?

    /** Degradation fit for the current stint (§9.2); null until enough clean laps. */
    fun degFit(driverId: String): DegFit?

    fun pitLoss(status: TrackStatus = TrackStatus.GREEN): Seconds

    /**
     * Leader-relative cumulative race time (§9.4). Absolute time is unknown
     * and irrelevant — all projection math uses differences, where it cancels.
     */
    fun cumulativeTime(driverId: String): Seconds?

    fun pitStops(driverId: String): List<PitStop>
    fun positionHistory(driverId: String): List<PositionSample>
    fun events(sinceLap: Int): List<EngineEvent>
}
