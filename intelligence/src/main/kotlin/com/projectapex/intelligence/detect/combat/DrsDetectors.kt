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
import kotlin.math.ceil

/**
 * A car already within DRS range of the car ahead (A3). Confidence is capped
 * at 0.8: with lap-line-only timing we approximate the detection-point gap
 * (docs/RaceIntelligencePlatform.md §10 A3). Ongoing.
 */
class DrsActiveDetector(config: IntelligenceConfig) : Detector {
    private val combat = config.combat
    override val id = "drs-active-detector"

    override fun detect(context: DetectionContext): List<Observation> =
        context.features.runningOrder
            .filter { !it.retired }
            .zipWithNext()
            .mapNotNull { (ahead, behind) ->
                val gap = context.features.interval(ahead.driverId, behind.driverId)?.value
                    ?: return@mapNotNull null
                if (gap > combat.drsGapSeconds) return@mapNotNull null

                val pairKey = listOf(ahead.driverId, behind.driverId).sorted().joinToString(":")
                Observation(
                    id = ObservationId("${CombatTypes.DRS_ACTIVE}:$pairKey"),
                    type = ObservationType(CombatTypes.DRS_ACTIVE),
                    severity = Severity.HIGH,
                    confidence = Confidence.clamp01(0.5 + 0.3 * (combat.drsGapSeconds - gap) / combat.drsGapSeconds),
                    timestamp = context.frame.timestamp,
                    subjectDrivers = listOf(behind.driverId, ahead.driverId),
                    metadata = mapOf(CombatKeys.GAP_S to gap),
                    timeHorizon = TimeHorizon.Ongoing,
                    expiry = Expiry.AtLap(context.frame.lap + 1),
                    sourceDetector = id,
                )
            }
}

/**
 * A car not yet in DRS range but projected to reach it within
 * [com.projectapex.intelligence.api.CombatConfig.drsImminentHorizonLaps] laps
 * (A4) — the marquee predictive insight ("NOR projected to enter DRS in 2
 * laps"). Horizon-based, so the prioritiser treats it as a prediction.
 */
class DrsImminentDetector(config: IntelligenceConfig) : Detector {
    private val combat = config.combat
    override val id = "drs-imminent-detector"

    override fun detect(context: DetectionContext): List<Observation> {
        val view = context.features
        if (view.lap < combat.warmupLaps) return emptyList()

        return view.runningOrder
            .filter { !it.retired }
            .zipWithNext()
            .mapNotNull { (ahead, behind) ->
                val trend = GapAnalysis.trend(ahead.driverId, behind.driverId, view, combat.gapTrendWindowLaps)
                    ?: return@mapNotNull null
                if (trend.currentGapSeconds <= combat.drsGapSeconds) return@mapNotNull null // already active
                if (trend.samples < 2 || trend.ratePerLap > -combat.gapTrendMinRatePerLap) return@mapNotNull null

                val eta = GapAnalysis.lapsUntil(trend.currentGapSeconds, trend.ratePerLap, combat.drsGapSeconds)
                    ?: return@mapNotNull null
                if (eta > combat.drsImminentHorizonLaps) return@mapNotNull null

                val pairKey = listOf(ahead.driverId, behind.driverId).sorted().joinToString(":")
                val confidence = Confidence.clamp01(
                    Confidence.sampleSize(trend.samples) *
                        (0.5 + 0.5 * Confidence.margin(-trend.ratePerLap, combat.gapTrendMinRatePerLap, combat.gapTrendMinRatePerLap * 4))
                )
                Observation(
                    id = ObservationId("${CombatTypes.DRS_IMMINENT}:$pairKey"),
                    type = ObservationType(CombatTypes.DRS_IMMINENT),
                    severity = Severity.HIGH,
                    confidence = confidence,
                    timestamp = context.frame.timestamp,
                    subjectDrivers = listOf(behind.driverId, ahead.driverId),
                    metadata = mapOf(
                        CombatKeys.GAP_S to trend.currentGapSeconds,
                        CombatKeys.ETA_LAPS to ceil(eta),
                        CombatKeys.RATE_S_PER_LAP to trend.ratePerLap,
                    ),
                    timeHorizon = TimeHorizon.InLaps(eta),
                    expiry = Expiry.AtLap(context.frame.lap + ceil(eta).toInt() + 1),
                    sourceDetector = id,
                )
            }
    }
}
