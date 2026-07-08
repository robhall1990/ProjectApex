package com.projectapex.intelligence.api

import com.projectapex.intelligence.detect.Severity

/**
 * Every scoring constant of the PrioritisationEngine, as data (APX-011 §4
 * "keep scoring configurable") — same config-as-data rule as everything else
 * on the platform: tuning never requires code changes, tests pin exact
 * configs.
 *
 * The score of an observation (docs/DetectionFramework.md):
 *
 *   score = severityWeight · confidence^confidenceExponent
 *         · urgency(timeHorizon) · recency(age) · novelty(repeats)
 *
 * Multiplicative on purpose: any zero factor kills the observation outright.
 */
data class PrioritisationConfig(
    /** How many observations make the pulse's top list. */
    val topK: Int = 3,
    val severityWeights: Map<Severity, Double> = mapOf(
        Severity.CRITICAL to 1.0,
        Severity.HIGH to 0.8,
        Severity.MEDIUM to 0.55,
        Severity.LOW to 0.35,
        Severity.INFO to 0.2,
    ),
    val confidenceExponent: Double = 1.0,
    /** Urgency of a prediction in k laps = exp(−k/τ); ongoing = 1. */
    val horizonTauLaps: Double = 4.0,
    /** Recency of an observation aged t seconds = exp(−t/τ). */
    val recencyTauSeconds: Double = 120.0,
    /** Novelty of the r-th repeat emission = decay^r; material change resets r. */
    val noveltyDecay: Double = 0.7,
    /** A confidence move at least this big counts as a material change. */
    val materialConfidenceDelta: Double = 0.15,
    /** Observations below this confidence never surface at all. */
    val confidenceFloor: Double = 0.05,
    /** Top-K diversity: candidate score × this per subject shared with an already-picked observation. */
    val subjectOverlapPenalty: Double = 0.55,
    /** Top-K diversity: candidate score × this if its type is already picked. */
    val typeOverlapPenalty: Double = 0.65,
    /** Novelty bookkeeping for ids not seen this long is forgotten. */
    val noveltyForgetSeconds: Double = 600.0,
    /** Headline when there is nothing worth saying. */
    val quietHeadline: String = "No significant race activity",
)
