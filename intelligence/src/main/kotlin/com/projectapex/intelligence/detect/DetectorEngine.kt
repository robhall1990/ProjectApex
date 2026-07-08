package com.projectapex.intelligence.detect

import com.projectapex.intelligence.features.FeatureView
import com.projectapex.intelligence.ingest.TimingFrame
import java.time.Clock
import java.time.Instant

/**
 * Runs every registered [Detector] against a frame and merges their output
 * (APX-011). Responsibilities: registration, execution, merge, dedupe,
 * failure isolation, metrics. Deliberately *not* responsible for scoring —
 * that is the PrioritisationEngine's job, so detectors and ranking evolve
 * independently.
 *
 * Single-writer like the rest of the pipeline: `register`/`execute` are
 * called from one thread (the future pipeline actor). Clock and nanoTime are
 * injectable so tests pin every timestamp.
 */
class DetectorEngine(
    private val clock: Clock = Clock.systemUTC(),
    private val nanoTime: () -> Long = System::nanoTime,
) {

    private val detectors = LinkedHashMap<String, Detector>()
    private val metrics = mutableMapOf<String, MutableMetrics>()
    private var previousObservations: List<Observation> = emptyList()

    /**
     * Registration is the only extension point: no engine change is ever
     * needed for a new detector. Duplicate ids are a wiring bug — fail loudly.
     */
    fun register(detector: Detector) {
        require(detector.id !in detectors) { "Detector '${detector.id}' is already registered" }
        detectors[detector.id] = detector
        metrics[detector.id] = MutableMetrics()
    }

    val registeredDetectorIds: List<String> get() = detectors.keys.toList()

    /**
     * Executes all detectors in registration order. A failure in one detector
     * never prevents the others from running: the exception is captured into
     * the result and the detector's error count, and execution continues.
     */
    fun execute(frame: TimingFrame, features: FeatureView): DetectionResult {
        val context = DetectionContext(frame, features, previousObservations)
        val collected = mutableListOf<Observation>()
        val failures = mutableListOf<DetectorFailure>()

        detectors.values.forEach { detector ->
            val m = metrics.getValue(detector.id)
            val start = nanoTime()
            try {
                val observations = detector.detect(context)
                collected += observations
                m.observationCount += observations.size
            } catch (e: Exception) {
                m.errorCount++
                failures += DetectorFailure(detector.id, e)
            } finally {
                val elapsed = nanoTime() - start
                m.executionCount++
                m.lastDurationNanos = elapsed
                m.totalDurationNanos += elapsed
                m.lastExecutionAt = clock.instant()
            }
        }

        val deduped = dedupe(collected)
        previousObservations = deduped
        return DetectionResult(deduped, failures)
    }

    /**
     * Same [ObservationId] from any source collapses to the single most
     * confident instance (first wins ties — deterministic). Encounter order
     * is preserved.
     */
    private fun dedupe(observations: List<Observation>): List<Observation> =
        observations.groupBy { it.id }.map { (_, duplicates) ->
            duplicates.maxByOrNull { it.confidence }!!
        }

    fun metrics(): Map<String, DetectorMetrics> =
        metrics.mapValues { (id, m) ->
            DetectorMetrics(
                detectorId = id,
                executionCount = m.executionCount,
                errorCount = m.errorCount,
                observationCount = m.observationCount,
                lastExecutionAt = m.lastExecutionAt,
                lastDurationNanos = m.lastDurationNanos,
                totalDurationNanos = m.totalDurationNanos,
            )
        }

    private class MutableMetrics {
        var executionCount: Long = 0
        var errorCount: Long = 0
        var observationCount: Long = 0
        var lastExecutionAt: Instant? = null
        var lastDurationNanos: Long = 0
        var totalDurationNanos: Long = 0
    }
}

data class DetectionResult(
    /** Merged, deduplicated observations from all successful detectors. */
    val observations: List<Observation>,
    /** Detectors that threw on this pass — captured, never propagated. */
    val failures: List<DetectorFailure>,
)

data class DetectorFailure(val detectorId: String, val exception: Exception)

/** Immutable per-detector metrics snapshot (APX-011 §7). */
data class DetectorMetrics(
    val detectorId: String,
    val executionCount: Long,
    val errorCount: Long,
    /** Cumulative observations emitted (pre-dedupe). */
    val observationCount: Long,
    val lastExecutionAt: Instant?,
    val lastDurationNanos: Long,
    val totalDurationNanos: Long,
)
