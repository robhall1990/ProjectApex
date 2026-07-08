package com.projectapex.intelligence.detect.combat

import com.projectapex.intelligence.api.IntelligenceConfig
import com.projectapex.intelligence.detect.DetectionContext
import com.projectapex.intelligence.detect.Observation
import com.projectapex.intelligence.features.FeatureView
import com.projectapex.intelligence.ingest.CarTiming
import com.projectapex.intelligence.ingest.IngestPipeline
import com.projectapex.intelligence.ingest.TimingFrame
import com.projectapex.intelligence.support.Fixtures.frame

/**
 * Test support for the combat detectors: builds a real [FeatureView] by
 * driving scripted frames through the actual [IngestPipeline], so detectors
 * are exercised against genuinely-accumulated history rather than a mock.
 */
object CombatSupport {

    /** Feeds each frame through the pipeline and returns the resulting view. */
    fun viewFrom(frames: List<TimingFrame>, config: IntelligenceConfig = IntelligenceConfig()): FeatureView {
        val pipeline = IngestPipeline(config)
        frames.forEach { pipeline.submit(it) }
        return pipeline.view
    }

    /** A single-frame context (no history) for detectors that read only the instant. */
    fun context(frame: TimingFrame, config: IntelligenceConfig = IntelligenceConfig()): DetectionContext {
        val pipeline = IngestPipeline(config)
        pipeline.submit(frame)
        return DetectionContext(frame, pipeline.view, emptyList())
    }

    /** A context whose view carries the history of all [frames]; the last frame is "current". */
    fun contextFrom(frames: List<TimingFrame>, config: IntelligenceConfig = IntelligenceConfig()): DetectionContext {
        val pipeline = IngestPipeline(config)
        frames.forEach { pipeline.submit(it) }
        return DetectionContext(frames.last(), pipeline.view, emptyList())
    }

    fun List<Observation>.ofType(type: String): List<Observation> = filter { it.type.value == type }
}
