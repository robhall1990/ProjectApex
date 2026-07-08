package com.projectapex.intelligence.api

/**
 * Every threshold the combat detector family reads, as data (APX-012) — same
 * config-as-data rule as the rest of the platform: tuning and per-track
 * calibration never require code changes, and tests pin exact configs.
 */
data class CombatConfig(
    /** Interval at/under which two cars are "battling" (seconds). */
    val battleGapSeconds: Double = 1.2,
    /** Interval at/under which a car is within DRS range (seconds). */
    val drsGapSeconds: Double = 1.0,
    /** Emit "DRS imminent" only if projected to reach DRS range within this many laps. */
    val drsImminentHorizonLaps: Double = 3.0,
    /** Trailing laps used to fit a pairwise interval trend. */
    val gapTrendWindowLaps: Int = 4,
    /** Minimum |rate| (s/lap) for a gap trend to count as closing/increasing. */
    val gapTrendMinRatePerLap: Double = 0.15,
    /** Gap-closing/increasing only fire in this range — below is "battle", above is uninteresting. */
    val gapTrendMaxGapSeconds: Double = 5.0,
    /** Leader is "under pressure" when P2's interval is at/under this (seconds). */
    val leaderPressureGapSeconds: Double = 3.0,
    /** Fastest-pace insight fires only when the pace leader is this much faster than the race leader (s/lap). */
    val fastestPaceMinAdvantagePerLap: Double = 0.3,
    /** Tyre age (laps) beyond which a plain age concern is raised (when no deg fit yet). */
    val tyreAgeConcernLaps: Int = 20,
    /** Predicted cliff within this many laps raises a cliff-forecast insight. */
    val tyreCliffHorizonLaps: Double = 5.0,
    /** Suppress most detectors during the noisy opening laps. */
    val warmupLaps: Int = 3,
)
