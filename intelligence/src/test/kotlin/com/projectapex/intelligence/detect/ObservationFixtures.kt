package com.projectapex.intelligence.detect

import java.time.Instant

/** Test-only builders for [Observation]s and stub [Detector]s. */
object ObservationFixtures {

    fun observation(
        id: String,
        type: String = "battle",
        severity: Severity = Severity.HIGH,
        confidence: Double = 0.8,
        timestamp: Instant = Instant.EPOCH,
        subjects: List<String> = listOf("VER", "NOR"),
        metadata: Map<String, Double> = emptyMap(),
        horizon: TimeHorizon = TimeHorizon.Ongoing,
        expiry: Expiry = Expiry.Never,
        source: String = "test-detector",
    ): Observation = Observation(
        id = ObservationId(id),
        type = ObservationType(type),
        severity = severity,
        confidence = confidence,
        timestamp = timestamp,
        subjectDrivers = subjects,
        metadata = metadata,
        timeHorizon = horizon,
        expiry = expiry,
        sourceDetector = source,
    )

    /** Emits a fixed list every pass. */
    class FixedDetector(
        override val id: String,
        private val emits: List<Observation>,
    ) : Detector {
        var executions = 0
        var lastContext: DetectionContext? = null
        override fun detect(context: DetectionContext): List<Observation> {
            executions++
            lastContext = context
            return emits
        }
    }

    /** Always throws. */
    class ThrowingDetector(override val id: String) : Detector {
        override fun detect(context: DetectionContext): List<Observation> =
            throw IllegalStateException("boom from $id")
    }
}
