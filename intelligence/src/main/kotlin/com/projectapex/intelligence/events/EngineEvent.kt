package com.projectapex.intelligence.events

import com.projectapex.intelligence.ingest.Seconds
import com.projectapex.intelligence.ingest.TrackStatus
import com.projectapex.intelligence.ingest.TyreFit
import java.time.Instant

/**
 * Discrete edges derived by diffing consecutive frames
 * (docs/RaceIntelligencePlatform.md §6.1). Timing feeds deliver snapshots;
 * most intelligence needs edges. These are internal to the pipeline — the
 * public API is IntelligenceFrame, not this stream.
 */
sealed interface EngineEvent {
    /** Race lap (leader's) when the event was derived. */
    val atLap: Int
    val at: Instant

    data class LapCompleted(
        override val atLap: Int,
        override val at: Instant,
        val driverId: String,
        /** The driver's newly completed lap number (their own counter). */
        val lapNumber: Int,
        /** Null when the feed had no time for this lap (e.g. after a data gap). */
        val lapTime: Seconds?,
    ) : EngineEvent

    data class PositionChange(
        override val atLap: Int,
        override val at: Instant,
        val driverId: String,
        val from: Int,
        val to: Int,
        /**
         * True only when both this car and its counterpart (the car displaced
         * for a gain; the car that took the old spot for a loss) are racing —
         * neither in the pit-entry/pit/out-lap phase nor retired. This single
         * bit separates "overtake!" from "gained a place in the pit cycle".
         */
        val onTrack: Boolean,
    ) : EngineEvent

    data class PitEntry(
        override val atLap: Int,
        override val at: Instant,
        val driverId: String,
    ) : EngineEvent

    data class PitExit(
        override val atLap: Int,
        override val at: Instant,
        val driverId: String,
        val tyre: TyreFit,
        val rejoinPosition: Int,
        val rejoinInterval: Seconds?,
    ) : EngineEvent

    data class TrackStatusChanged(
        override val atLap: Int,
        override val at: Instant,
        val from: TrackStatus,
        val to: TrackStatus,
    ) : EngineEvent

    data class DriverRetired(
        override val atLap: Int,
        override val at: Instant,
        val driverId: String,
    ) : EngineEvent

    data class CarLapped(
        override val atLap: Int,
        override val at: Instant,
        val driverId: String,
        val byDriverId: String,
    ) : EngineEvent

    /** Feed hiccup — frames or laps went missing. Downstream degrades confidence. */
    data class DataGap(
        override val atLap: Int,
        override val at: Instant,
        val missedFrames: Int,
    ) : EngineEvent
}
