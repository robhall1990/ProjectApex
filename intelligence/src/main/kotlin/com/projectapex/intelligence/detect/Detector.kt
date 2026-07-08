package com.projectapex.intelligence.detect

import com.projectapex.intelligence.features.FeatureView
import com.projectapex.intelligence.ingest.TimingFrame

/**
 * One pluggable detection unit (APX-011). Implementations must be:
 *
 * - **independent** — no detector references another detector,
 * - **pure** — a function of the [DetectionContext] and their own config;
 *   no mutable state, no clocks, no I/O (the engine supplies timestamps via
 *   the frame),
 * - **total** — throwing is tolerated (the engine isolates failures) but
 *   never part of the contract; "nothing to report" is an empty list.
 *
 * Registering an instance with [DetectorEngine] is the *only* integration
 * step — the engine has no knowledge of any concrete detector.
 */
interface Detector {
    /** Unique, stable id (e.g. "battle-detector") — metrics key off it. */
    val id: String

    fun detect(context: DetectionContext): List<Observation>
}

/**
 * Everything a detector may read. [features] is the read-only [FeatureView]
 * over the FeatureStore (APX-010) — full race history: lap books, stints,
 * gap history, pace/deg fits, events. [previousObservations] is the engine's
 * deduplicated output from the previous pass, for detectors that need
 * emission continuity (hysteresis, trend confirmation).
 */
data class DetectionContext(
    val frame: TimingFrame,
    val features: FeatureView,
    val previousObservations: List<Observation>,
)
