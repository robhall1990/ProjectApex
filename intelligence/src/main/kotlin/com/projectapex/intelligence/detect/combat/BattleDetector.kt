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

/**
 * Two cars running within [com.projectapex.intelligence.api.CombatConfig.battleGapSeconds]
 * of each other (A1). Ongoing, high severity — a fight happening now.
 */
class BattleDetector(config: IntelligenceConfig) : Detector {
    private val combat = config.combat
    override val id = "battle-detector"

    override fun detect(context: DetectionContext): List<Observation> =
        context.features.runningOrder
            .filter { !it.retired }
            .zipWithNext()
            .mapNotNull { (ahead, behind) ->
                val gap = context.features.interval(ahead.driverId, behind.driverId)?.value
                    ?: return@mapNotNull null
                if (gap > combat.battleGapSeconds) return@mapNotNull null

                val pairKey = listOf(ahead.driverId, behind.driverId).sorted().joinToString(":")
                Observation(
                    id = ObservationId("${CombatTypes.BATTLE}:$pairKey"),
                    type = ObservationType(CombatTypes.BATTLE),
                    severity = Severity.HIGH,
                    // A detected battle is a near-fact; confidence rises as the gap shrinks.
                    confidence = Confidence.clamp01(0.6 + 0.4 * (combat.battleGapSeconds - gap) / combat.battleGapSeconds),
                    timestamp = context.frame.timestamp,
                    subjectDrivers = listOf(behind.driverId, ahead.driverId),
                    metadata = mapOf(CombatKeys.GAP_S to gap),
                    timeHorizon = TimeHorizon.Ongoing,
                    expiry = Expiry.AtLap(context.frame.lap + 2),
                    sourceDetector = id,
                )
            }
}
