package com.projectapex.intelligence.adapter

import com.projectapex.domain.DefaultDispatcher
import com.projectapex.domain.model.RaceState
import com.projectapex.domain.race.RaceEngine
import com.projectapex.intelligence.api.IntelligenceConfig
import com.projectapex.intelligence.detect.DetectorEngine
import com.projectapex.intelligence.detect.combat.CombatDetectors
import com.projectapex.intelligence.ingest.IngestPipeline
import com.projectapex.intelligence.rank.ConfidenceSummary
import com.projectapex.intelligence.rank.PrioritisationEngine
import com.projectapex.intelligence.rank.RacePulse
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-side orchestrator that drives the pure `:intelligence` pipeline from the
 * live [RaceEngine] and exposes the result as a [RacePulse] the UI can
 * consume (APX-012):
 *
 * ```
 * RaceEngine -> RaceStateAdapter -> IngestPipeline -> DetectorEngine
 *            -> PrioritisationEngine -> RacePulse -> RaceViewModel -> UI
 * ```
 *
 * Lives in `:app` (not `:intelligence`) because it wires app types
 * ([RaceEngine], [RaceStateAdapter]) to the pure module, which never imports
 * app types.
 *
 * Like [com.projectapex.domain.timeline.RaceTimeline], it reads the **live**
 * engine state in chronological order — never the replay timeline. The
 * pipeline is stateful (feature history, detector continuity, novelty
 * bookkeeping) and assumes ordered input, so replay scrubbing must not reach
 * it; the timeline position is purely a UI concern. All pipeline mutation
 * happens on the single collector coroutine, giving the same single-writer
 * guarantee the platform spec assumes.
 */
@Singleton
class RacePulseEngine @Inject constructor(
    raceEngine: RaceEngine,
    @DefaultDispatcher dispatcher: CoroutineDispatcher,
    private val config: IntelligenceConfig,
    private val clock: Clock,
) {

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val adapter = RaceStateAdapter()
    private val ingest = IngestPipeline(config)
    private val detectorEngine = DetectorEngine(clock).apply {
        CombatDetectors.all(config).forEach(::register)
    }
    private val prioritisation = PrioritisationEngine(config.prioritisation, clock)

    private val _pulse = MutableStateFlow(emptyPulse())
    val pulse: StateFlow<RacePulse> = _pulse.asStateFlow()

    init {
        scope.launch {
            raceEngine.state.collect { process(it) }
        }
    }

    /**
     * Runs one race state through the full pipeline. Public so tests can drive
     * it deterministically without the background subscription (mirrors
     * [com.projectapex.domain.timeline.RaceTimeline.record]). States the
     * adapter rejects (offline / empty grid) leave the current pulse intact.
     */
    fun process(state: RaceState) {
        val frame = adapter.adapt(state) ?: return
        when (val result = ingest.submit(frame)) {
            is IngestPipeline.IngestResult.Rejected -> return // malformed frame — keep last pulse
            is IngestPipeline.IngestResult.Accepted -> {
                val detection = detectorEngine.execute(frame, ingest.view)
                _pulse.value = prioritisation.prioritise(detection.observations, atLap = frame.lap)
            }
        }
    }

    private fun emptyPulse(): RacePulse = RacePulse(
        headline = config.prioritisation.quietHeadline,
        topObservations = emptyList(),
        generatedAt = Instant.now(clock),
        confidenceSummary = ConfidenceSummary.EMPTY,
    )
}
