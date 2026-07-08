package com.projectapex.intelligence.features

import com.projectapex.intelligence.api.IntelligenceConfig
import com.projectapex.intelligence.ingest.TyreCompound

/**
 * A run on one set of tyres (docs/RaceIntelligencePlatform.md §3.2). Owned and
 * mutated only by the FeatureStore; exposed read-only through FeatureView.
 */
data class Stint(
    val compound: TyreCompound,
    /** Driver lap number on which the stint began. */
    val startLap: Int,
    /** Tyre age at stint start (non-zero for used sets). */
    val startAgeLaps: Int,
    val laps: List<LapRecord>,
    /** Set when the stint has ended (driver pitted / tyres changed). */
    val endLap: Int? = null,
)

/**
 * Linear-phase degradation fit for one stint (§9.2):
 * `t_corr(age) = base + rate·age` over the stint's clean laps.
 */
data class DegFit(
    val baseSeconds: Double,
    val ratePerLap: Double,
    val sigma: Double,
    val r2: Double,
    val n: Int,
    /** Live cliff state (§9.2): consecutive escalating residuals above threshold. */
    val cliffDetected: Boolean,
)

object DegModel {

    /**
     * Null until the stint has [IntelligenceConfig.minLapsForDegFit] clean
     * laps. When a cliff is detected, the returned fit describes the *linear
     * phase only* (the laps before the break) — a fit polluted by cliff laps
     * would misstate the rate the stint actually degraded at.
     */
    fun fit(stint: Stint, config: IntelligenceConfig): DegFit? {
        val clean = stint.laps.filter { it.isClean }
        if (clean.size < config.minLapsForDegFit) return null

        val fullFit = olsFit(clean.map { it.tyre.ageLaps.toDouble() to it.fuelCorrectedSeconds!! })
            ?: return null

        val cliff = detectCliff(clean, config)
        val reported = cliff ?: fullFit

        return DegFit(
            baseSeconds = reported.intercept,
            ratePerLap = reported.slope,
            sigma = reported.sigma,
            r2 = reported.r2,
            n = reported.n,
            cliffDetected = cliff != null,
        )
    }

    /**
     * Cliff = the linear model stops explaining the laps (§9.2). Crucially the
     * base fit *excludes* the trailing candidate laps — fitting over the whole
     * stint would let the cliff laps tilt the line and hide their own
     * residuals. Detected when the last [IntelligenceConfig.cliffConsecutiveLaps]
     * clean laps all sit more than max(k·σ, floor) above the linear-phase fit
     * and are getting worse. Returns the linear-phase fit when detected.
     */
    private fun detectCliff(clean: List<LapRecord>, config: IntelligenceConfig): OlsFit? {
        val tailSize = config.cliffConsecutiveLaps
        if (clean.size < tailSize + 2) return null // need ≥2 linear-phase points to fit

        val linearPhase = clean.dropLast(tailSize)
        val baseFit = olsFit(linearPhase.map { it.tyre.ageLaps.toDouble() to it.fuelCorrectedSeconds!! })
            ?: return null

        val threshold = maxOf(config.cliffResidualSigmas * baseFit.sigma, config.cliffResidualFloor)
        val residuals = clean.takeLast(tailSize).map { record ->
            record.fuelCorrectedSeconds!! - (baseFit.intercept + baseFit.slope * record.tyre.ageLaps)
        }
        val allAbove = residuals.all { it > threshold }
        val rising = residuals.zipWithNext().all { (a, b) -> b > a }
        return if (allAbove && rising) baseFit else null
    }

    /**
     * Predicted tyre age at which the cliff arrives (§9.2): the compound's
     * prior life, scaled inversely by how the observed deg rate compares to
     * the prior rate, clamped to [now, 1.5 × prior life].
     */
    fun predictCliffAge(fit: DegFit, compound: TyreCompound, currentAgeLaps: Int, config: IntelligenceConfig): Double {
        val prior = config.degPriors.getValue(compound)
        val observedRate = maxOf(fit.ratePerLap, 0.005) // deg can fit ~0 or negative early; floor it
        val scaled = prior.lifeLaps *
            Math.pow(prior.ratePerLap / observedRate, config.cliffPredictionExponent)
        return scaled.coerceIn(currentAgeLaps.toDouble(), prior.lifeLaps * 1.5)
    }
}
