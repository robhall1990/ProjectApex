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
import com.projectapex.intelligence.detect.analysis.RelativePace

/**
 * The car with the fastest race pace when that car is *not* leading the race
 * (C3) — the interesting case ("HAM is the quickest car on track, in P4").
 * Fastest pace that belongs to the leader is unremarkable and suppressed.
 * Uses fuel-corrected clean-lap pace, so it is genuinely "race pace", not a
 * single fast lap.
 */
class FastestPaceDetector(config: IntelligenceConfig) : Detector {
    private val combat = config.combat
    override val id = "fastest-pace-detector"

    override fun detect(context: DetectionContext): List<Observation> {
        val view = context.features
        if (view.lap < combat.warmupLaps) return emptyList()

        val leader = view.runningOrder.filter { !it.retired }.minByOrNull { it.position } ?: return emptyList()
        val ranking = RelativePace.ranking(view)
        val paceLeader = ranking.firstOrNull() ?: return emptyList()
        if (paceLeader.driverId == leader.driverId) return emptyList() // leader is fastest — unremarkable

        val advantage = RelativePace.advantagePerLap(paceLeader.driverId, leader.driverId, view) ?: return emptyList()
        if (advantage < combat.fastestPaceMinAdvantagePerLap) return emptyList()

        return listOf(
            Observation(
                id = ObservationId("${CombatTypes.FASTEST_PACE}:${paceLeader.driverId}"),
                type = ObservationType(CombatTypes.FASTEST_PACE),
                severity = Severity.MEDIUM,
                confidence = Confidence.clamp01(
                    Confidence.sampleSize(paceLeader.pace.n) *
                        (0.5 + 0.5 * Confidence.margin(advantage, combat.fastestPaceMinAdvantagePerLap, combat.fastestPaceMinAdvantagePerLap * 3))
                ),
                timestamp = context.frame.timestamp,
                subjectDrivers = listOf(paceLeader.driverId),
                metadata = mapOf(
                    CombatKeys.PACE_MARGIN_S to advantage,
                    CombatKeys.POSITION to paceLeader.position.toDouble(),
                ),
                timeHorizon = TimeHorizon.Ongoing,
                expiry = Expiry.AtLap(context.frame.lap + 2),
                sourceDetector = id,
            )
        )
    }
}
