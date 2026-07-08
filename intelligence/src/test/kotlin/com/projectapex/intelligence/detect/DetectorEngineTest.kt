package com.projectapex.intelligence.detect

import com.projectapex.intelligence.detect.ObservationFixtures.FixedDetector
import com.projectapex.intelligence.detect.ObservationFixtures.ThrowingDetector
import com.projectapex.intelligence.detect.ObservationFixtures.observation
import com.projectapex.intelligence.features.FeatureStore
import com.projectapex.intelligence.api.IntelligenceConfig
import com.projectapex.intelligence.features.FeatureView
import com.projectapex.intelligence.ingest.TimingFrame
import com.projectapex.intelligence.support.Fixtures.car
import com.projectapex.intelligence.support.Fixtures.frame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class DetectorEngineTest {

    private val fixedInstant = Instant.parse("2026-07-08T14:00:00Z")
    private val clock = Clock.fixed(fixedInstant, ZoneOffset.UTC)

    private fun emptyView(): FeatureView = FeatureStore(IntelligenceConfig())

    private fun aFrame(): TimingFrame = frame(1, lap = 10, cars = listOf(car("VER", 1, 10, 0.0)))

    @Test
    fun `runs every registered detector in registration order and merges output`() {
        val engine = DetectorEngine(clock)
        val first = FixedDetector("first", listOf(observation("a")))
        val second = FixedDetector("second", listOf(observation("b"), observation("c")))
        engine.register(first)
        engine.register(second)

        val result = engine.execute(aFrame(), emptyView())

        assertEquals(listOf("first", "second"), engine.registeredDetectorIds)
        assertEquals(listOf("a", "b", "c"), result.observations.map { it.id.value })
        assertEquals(1, first.executions)
        assertEquals(1, second.executions)
        assertTrue(result.failures.isEmpty())
    }

    @Test
    fun `rejects duplicate detector ids loudly`() {
        val engine = DetectorEngine(clock)
        engine.register(FixedDetector("dup", emptyList()))
        try {
            engine.register(FixedDetector("dup", emptyList()))
            throw AssertionError("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("dup"))
        }
    }

    @Test
    fun `a throwing detector never stops the others`() {
        val engine = DetectorEngine(clock)
        val healthy = FixedDetector("healthy", listOf(observation("a")))
        engine.register(ThrowingDetector("broken"))
        engine.register(healthy)

        val result = engine.execute(aFrame(), emptyView())

        assertEquals(1, healthy.executions)
        assertEquals(listOf("a"), result.observations.map { it.id.value })
        assertEquals("broken", result.failures.single().detectorId)

        // The broken detector keeps being attempted on subsequent passes.
        engine.execute(aFrame(), emptyView())
        assertEquals(2L, engine.metrics().getValue("broken").errorCount)
        assertEquals(2, healthy.executions)
    }

    @Test
    fun `duplicate observation ids collapse to the most confident instance`() {
        val engine = DetectorEngine(clock)
        engine.register(FixedDetector("one", listOf(observation("battle:NOR:VER", confidence = 0.6))))
        engine.register(FixedDetector("two", listOf(observation("battle:NOR:VER", confidence = 0.9))))

        val result = engine.execute(aFrame(), emptyView())

        assertEquals(1, result.observations.size)
        assertEquals(0.9, result.observations.single().confidence, 1e-9)
    }

    @Test
    fun `detectors receive the previous pass's deduplicated output as history`() {
        val engine = DetectorEngine(clock)
        val detector = FixedDetector("d", listOf(observation("a")))
        engine.register(detector)

        engine.execute(aFrame(), emptyView())
        assertTrue(detector.lastContext!!.previousObservations.isEmpty())

        engine.execute(aFrame(), emptyView())
        assertEquals(listOf("a"), detector.lastContext!!.previousObservations.map { it.id.value })
    }

    @Test
    fun `collects per-detector metrics`() {
        var fakeNanos = 0L
        val engine = DetectorEngine(clock, nanoTime = { fakeNanos.also { fakeNanos += 500_000 } })
        engine.register(FixedDetector("d", listOf(observation("a"), observation("b"))))

        engine.execute(aFrame(), emptyView())
        engine.execute(aFrame(), emptyView())

        val metrics = engine.metrics().getValue("d")
        assertEquals(2L, metrics.executionCount)
        assertEquals(0L, metrics.errorCount)
        assertEquals(4L, metrics.observationCount) // 2 per pass, cumulative, pre-dedupe
        assertEquals(fixedInstant, metrics.lastExecutionAt)
        assertEquals(500_000L, metrics.lastDurationNanos)
        assertEquals(1_000_000L, metrics.totalDurationNanos)
        assertNotNull(metrics.detectorId)
    }

    @Test
    fun `metrics also track failing detectors' timings and counts`() {
        val engine = DetectorEngine(clock)
        engine.register(ThrowingDetector("broken"))

        engine.execute(aFrame(), emptyView())

        val metrics = engine.metrics().getValue("broken")
        assertEquals(1L, metrics.executionCount)
        assertEquals(1L, metrics.errorCount)
        assertEquals(0L, metrics.observationCount)
        assertEquals(fixedInstant, metrics.lastExecutionAt)
    }
}
