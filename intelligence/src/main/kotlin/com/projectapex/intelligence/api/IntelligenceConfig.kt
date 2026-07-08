package com.projectapex.intelligence.api

import com.projectapex.intelligence.ingest.TyreCompound

/**
 * Every constant the platform uses, as data
 * (docs/RaceIntelligencePlatform.md §8): nothing is hardcoded in detectors or
 * models, so per-track tuning and calibration never require code changes, and
 * tests can pin exact configs. This ticket (APX-010) carries the constants the
 * features layer needs; detector/ranking constants land with their tickets.
 */
data class IntelligenceConfig(
    /** Seconds per lap a car gains as one lap of fuel burns off (§9.1). */
    val fuelEffectPerLap: Double = 0.055,
    /** Clean laps in the rolling pace window (§9.1). */
    val paceWindow: Int = 5,
    /** Outlier if |t − median| > max(this × MAD, [outlierFloorSeconds]) (§9.1). */
    val outlierMadMultiplier: Double = 3.0,
    /** MAD is ~0 on synthetic/clean data; never flag inside this band. */
    val outlierFloorSeconds: Double = 1.0,
    /** Minimum clean laps in a stint before a deg fit is attempted (§9.2). */
    val minLapsForDegFit: Int = 4,
    /** Cliff when residual > max(this × σ, [cliffResidualFloor]) for [cliffConsecutiveLaps] rising laps. */
    val cliffResidualSigmas: Double = 2.0,
    val cliffResidualFloor: Double = 0.4,
    val cliffConsecutiveLaps: Int = 3,
    /** a_cliff ≈ life · (priorRate / observedRate)^exponent (§9.2). */
    val cliffPredictionExponent: Double = 1.0,
    /** Rejoin interval below this is dirty air (§9.5). */
    val dirtyAirGapSeconds: Double = 2.0,
    /** Fallback lap-time estimate when a driver has no fitted pace yet. */
    val fallbackLapSeconds: Double = 90.0,
    val track: TrackConstants = TrackConstants(),
    val degPriors: Map<TyreCompound, DegPrior> = DEFAULT_DEG_PRIORS,
) {
    companion object {
        val DEFAULT_DEG_PRIORS: Map<TyreCompound, DegPrior> = mapOf(
            TyreCompound.SOFT to DegPrior(ratePerLap = 0.08, lifeLaps = 18.0),
            TyreCompound.MEDIUM to DegPrior(ratePerLap = 0.05, lifeLaps = 28.0),
            TyreCompound.HARD to DegPrior(ratePerLap = 0.03, lifeLaps = 40.0),
            TyreCompound.INTERMEDIATE to DegPrior(ratePerLap = 0.06, lifeLaps = 30.0),
            TyreCompound.WET to DegPrior(ratePerLap = 0.05, lifeLaps = 35.0),
        )
    }
}

data class TrackConstants(
    /** Total time lost by pitting under green vs. staying out (§9.4). */
    val pitLaneLossSeconds: Double = 22.0,
    val scPitLossFactor: Double = 0.45,
    val vscPitLossFactor: Double = 0.65,
)

data class DegPrior(val ratePerLap: Double, val lifeLaps: Double)
