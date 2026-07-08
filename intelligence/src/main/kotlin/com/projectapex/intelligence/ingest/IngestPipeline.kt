package com.projectapex.intelligence.ingest

import com.projectapex.intelligence.api.IntelligenceConfig
import com.projectapex.intelligence.events.EngineEvent
import com.projectapex.intelligence.events.EventDeriver
import com.projectapex.intelligence.features.FeatureStore
import com.projectapex.intelligence.features.FeatureView

/**
 * The synchronous "cheap path" of docs/RaceIntelligencePlatform.md §4.1:
 * validate → normalise → derive events → update the feature store. Runs on
 * whatever thread calls it — the coroutine actor that gives the full pipeline
 * its single-writer guarantee wraps this in a later ticket; tests and the
 * adapter integration drive it directly today.
 */
class IngestPipeline(config: IntelligenceConfig = IntelligenceConfig()) {

    private val validator = FrameValidator()
    private val normaliser = FrameNormaliser()
    private val deriver = EventDeriver()
    private val store = FeatureStore(config)

    /** Read-only view over everything the pipeline has learned so far. */
    val view: FeatureView get() = store

    sealed interface IngestResult {
        data class Accepted(val events: List<EngineEvent>) : IngestResult
        data class Rejected(val violations: List<FrameValidator.Violation>) : IngestResult
    }

    fun submit(frame: TimingFrame): IngestResult {
        val violations = validator.validate(frame)
        if (violations.isNotEmpty()) return IngestResult.Rejected(violations)

        val normalised = normaliser.normalise(frame)
        val events = deriver.derive(normalised)
        store.update(normalised, events)
        return IngestResult.Accepted(events)
    }
}
