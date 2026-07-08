package com.projectapex.intelligence.rank

import com.projectapex.intelligence.detect.Observation
import java.time.Instant

/**
 * The prioritised answer to "what matters in the race right now?" — the
 * object the UI (and later the LLM narration layer) consumes (APX-011 §5).
 * In docs/RaceIntelligencePlatform.md terms this is the first cut of §3.3's
 * `IntelligenceFrame`.
 */
data class RacePulse(
    /**
     * One-line summary of the top observation. Deterministic placeholder
     * rendering for now ("type: A vs B") — the presentation layer
     * (RaceInsight templates, then LLM narration) replaces it later.
     */
    val headline: String,
    /** The [com.projectapex.intelligence.api.PrioritisationConfig.topK] highest-scoring observations, selection order. */
    val topObservations: List<ScoredObservation>,
    val generatedAt: Instant,
    val confidenceSummary: ConfidenceSummary,
)

/**
 * An observation with the score that ranked it. The score is the *base*
 * score (severity·confidence·urgency·recency·novelty); top-K selection order
 * additionally reflects diversity penalties, which are ordering-only and not
 * folded into the reported number.
 */
data class ScoredObservation(
    val observation: Observation,
    val score: Double,
)

data class ConfidenceSummary(
    /** Over the top observations; all zero when the pulse is quiet. */
    val mean: Double,
    val min: Double,
    val max: Double,
    /** Live (unexpired, above-floor) observations considered this pass. */
    val activeObservationCount: Int,
) {
    companion object {
        val EMPTY = ConfidenceSummary(mean = 0.0, min = 0.0, max = 0.0, activeObservationCount = 0)
    }
}
