package com.projectapex.intelligence.detect

import java.time.Duration
import java.time.Instant

/**
 * The internal intelligence object (APX-011): one thing a detector believes
 * is happening or about to happen. Immutable; carries no display text —
 * presentation (today's `RaceInsight`, later the fact/narration layer) is a
 * separate concern layered on top.
 *
 * In docs/RaceIntelligencePlatform.md terms this is the implementation of
 * §8's `InsightCandidate`, with lifecycle-relevant fields (expiry, horizon)
 * carried on the object itself.
 */
data class Observation(
    /**
     * Stable identity: the same real-world situation must produce the same id
     * across detector passes (e.g. `battle:NOR:VER` with subjects sorted).
     * Deduplication, novelty decay, and update-in-place all key off this.
     */
    val id: ObservationId,
    val type: ObservationType,
    val severity: Severity,
    /** [0, 1] — how sure the detector is. Never cosmetic; scoring multiplies by it. */
    val confidence: Double,
    /** When the detector produced it — recency scoring reads this. */
    val timestamp: Instant,
    /** Driver ids this is about, most-relevant first. */
    val subjectDrivers: List<String>,
    /**
     * Structured numeric facts justifying the observation (gap seconds, ETA
     * laps, probabilities…). Numbers only, by design: this becomes the
     * LLM-auditable fact payload later, and text has no place in scoring.
     */
    val metadata: Map<String, Double>,
    val timeHorizon: TimeHorizon,
    val expiry: Expiry,
    /** Id of the detector that produced it — metrics and debugging. */
    val sourceDetector: String,
) {
    init {
        require(confidence in 0.0..1.0) { "confidence must be within [0,1], was $confidence" }
    }

    fun isExpired(atLap: Int, now: Instant): Boolean = when (val e = expiry) {
        Expiry.Never -> false
        is Expiry.AtLap -> atLap > e.lap
        is Expiry.AfterSeconds -> Duration.between(timestamp, now).toMillis() / 1000.0 > e.seconds
    }
}

@JvmInline
value class ObservationId(val value: String)

/**
 * Open type tag rather than an enum: new detectors introduce new types
 * without touching any framework file (registration-only extensibility).
 * The catalogue tickets define their constants next to each detector.
 */
@JvmInline
value class ObservationType(val value: String)

enum class Severity { CRITICAL, HIGH, MEDIUM, LOW, INFO }

/** Is this happening now, or predicted to happen in [InLaps.laps] laps? */
sealed interface TimeHorizon {
    object Ongoing : TimeHorizon
    data class InLaps(val laps: Double) : TimeHorizon
}

/** When the observation stops being true/relevant if not re-emitted. */
sealed interface Expiry {
    object Never : Expiry
    /** Valid through this race lap; expired once the race is past it. */
    data class AtLap(val lap: Int) : Expiry
    /** Valid for this long after [Observation.timestamp]. */
    data class AfterSeconds(val seconds: Double) : Expiry
}
