package com.projectapex.intelligence.detect.combat

import com.projectapex.intelligence.api.IntelligenceConfig
import com.projectapex.intelligence.detect.DetectionContext
import com.projectapex.intelligence.detect.Detector
import com.projectapex.intelligence.detect.Expiry
import com.projectapex.intelligence.detect.Observation
import com.projectapex.intelligence.detect.ObservationId
import com.projectapex.intelligence.detect.ObservationType
import com.projectapex.intelligence.detect.Severity
import com.projectapex.intelligence.detect.TimeHorizon
import com.projectapex.intelligence.detect.analysis.Confidence
import com.projectapex.intelligence.detect.analysis.GapAnalysis

/**
 * A car meaningfully closing on the car ahead, but not yet in battle range
 * and not so close it's already "DRS imminent" — the precursor signal (a
 * softer cousin of A2). Deliberately LOW severity: on its own it's the
 * generic "gap closing" the pulse should rarely lead with; it earns a spot
 * only when nothing sharper is happening.
 */
class GapClosingDetector(config: IntelligenceConfig) : Detector {
    private val combat = config.combat
    override val id = "gap-closing-detector"

    override fun detect(context: DetectionContext): List<Observation> {
        val view = context.features
        if (view.lap < combat.warmupLaps) return emptyList()

        return view.runningOrder
            .filter { !it.retired }
            .zipWithNext()
            .mapNotNull { (ahead, behind) ->
                val trend = GapAnalysis.trend(ahead.driverId, behind.driverId, view, combat.gapTrendWindowLaps)
                    ?: return@mapNotNull null
                val gap = trend.currentGapSeconds
                // Only the mid-range: battle owns < battleGap, and beyond maxGap is uninteresting.
                if (gap <= combat.battleGapSeconds || gap > combat.gapTrendMaxGapSeconds) return@mapNotNull null
                if (trend.samples < 2 || trend.ratePerLap > -combat.gapTrendMinRatePerLap) return@mapNotNull null

                // If it's converging on DRS within the horizon, DrsImminentDetector owns the story.
                val etaToDrs = GapAnalysis.lapsUntil(gap, trend.ratePerLap, combat.drsGapSeconds)
                if (etaToDrs != null && etaToDrs <= combat.drsImminentHorizonLaps) return@mapNotNull null

                val pairKey = listOf(ahead.driverId, behind.driverId).sorted().joinToString(":")
                Observation(
                    id = ObservationId("${CombatTypes.GAP_CLOSING}:$pairKey"),
                    type = ObservationType(CombatTypes.GAP_CLOSING),
                    severity = Severity.LOW,
                    confidence = Confidence.clamp01(
                        Confidence.sampleSize(trend.samples) *
                            (0.5 + 0.5 * Confidence.margin(-trend.ratePerLap, combat.gapTrendMinRatePerLap, combat.gapTrendMinRatePerLap * 4))
                    ),
                    timestamp = context.frame.timestamp,
                    subjectDrivers = listOf(behind.driverId, ahead.driverId),
                    metadata = mapOf(
                        CombatKeys.GAP_S to gap,
                        CombatKeys.RATE_S_PER_LAP to trend.ratePerLap,
                    ),
                    timeHorizon = TimeHorizon.Ongoing,
                    expiry = Expiry.AtLap(context.frame.lap + 2),
                    sourceDetector = id,
                )
            }
    }
}

/**
 * A car dropping away from the car ahead — the least urgent combat signal
 * (INFO severity), present for completeness and future strategy context.
 */
class GapIncreasingDetector(config: IntelligenceConfig) : Detector {
    private val combat = config.combat
    override val id = "gap-increasing-detector"

    override fun detect(context: DetectionContext): List<Observation> {
        val view = context.features
        if (view.lap < combat.warmupLaps) return emptyList()

        return view.runningOrder
            .filter { !it.retired }
            .zipWithNext()
            .mapNotNull { (ahead, behind) ->
                val trend = GapAnalysis.trend(ahead.driverId, behind.driverId, view, combat.gapTrendWindowLaps)
                    ?: return@mapNotNull null
                val gap = trend.currentGapSeconds
                if (gap > combat.gapTrendMaxGapSeconds) return@mapNotNull null
                if (trend.samples < 2 || trend.ratePerLap < combat.gapTrendMinRatePerLap) return@mapNotNull null

                val pairKey = listOf(ahead.driverId, behind.driverId).sorted().joinToString(":")
                Observation(
                    id = ObservationId("${CombatTypes.GAP_INCREASING}:$pairKey"),
                    type = ObservationType(CombatTypes.GAP_INCREASING),
                    severity = Severity.INFO,
                    confidence = Confidence.clamp01(
                        Confidence.sampleSize(trend.samples) *
                            (0.5 + 0.5 * Confidence.margin(trend.ratePerLap, combat.gapTrendMinRatePerLap, combat.gapTrendMinRatePerLap * 4))
                    ),
                    timestamp = context.frame.timestamp,
                    subjectDrivers = listOf(behind.driverId, ahead.driverId),
                    metadata = mapOf(
                        CombatKeys.GAP_S to gap,
                        CombatKeys.RATE_S_PER_LAP to trend.ratePerLap,
                    ),
                    timeHorizon = TimeHorizon.Ongoing,
                    expiry = Expiry.AtLap(context.frame.lap + 2),
                    sourceDetector = id,
                )
            }
    }
}
