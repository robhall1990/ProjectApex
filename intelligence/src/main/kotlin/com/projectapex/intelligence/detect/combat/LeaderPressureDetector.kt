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
 * The race leader threatened by P2 (A9): within
 * [com.projectapex.intelligence.api.CombatConfig.leaderPressureGapSeconds] and
 * either closing or on fresher tyres. Highest-value combat story — it's the
 * lead of the race ("VER under sustained pressure"). CRITICAL when the pass
 * is imminent (inside battle range and closing), HIGH otherwise.
 */
class LeaderPressureDetector(config: IntelligenceConfig) : Detector {
    private val combat = config.combat
    override val id = "leader-pressure-detector"

    override fun detect(context: DetectionContext): List<Observation> {
        val order = context.features.runningOrder.filter { !it.retired }
        val leader = order.getOrNull(0) ?: return emptyList()
        val chaser = order.getOrNull(1) ?: return emptyList()

        val gap = context.features.interval(leader.driverId, chaser.driverId)?.value ?: return emptyList()
        if (gap > combat.leaderPressureGapSeconds) return emptyList()

        val trend = GapAnalysis.trend(leader.driverId, chaser.driverId, context.features, combat.gapTrendWindowLaps)
        val ratePerLap = trend?.ratePerLap ?: 0.0
        val closing = ratePerLap < 0.0
        val fresherTyres = chaser.tyre.ageLaps < leader.tyre.ageLaps

        // Pressure = close and (closing OR a tyre-offset threat). A stable,
        // equal-tyre gap isn't pressure.
        if (!closing && !fresherTyres) return emptyList()

        // "Sustained" when the trend was fit over the whole window and is closing.
        val sustained = closing && (trend?.samples ?: 0) >= combat.gapTrendWindowLaps
        val severity = if (gap <= combat.battleGapSeconds && closing) Severity.CRITICAL else Severity.HIGH

        return listOf(
            Observation(
                id = ObservationId("${CombatTypes.LEADER_PRESSURE}:${leader.driverId}"),
                type = ObservationType(CombatTypes.LEADER_PRESSURE),
                severity = severity,
                confidence = Confidence.clamp01(
                    0.6 + 0.4 * Confidence.margin(combat.leaderPressureGapSeconds - gap, 0.0, combat.leaderPressureGapSeconds)
                ),
                timestamp = context.frame.timestamp,
                subjectDrivers = listOf(leader.driverId, chaser.driverId),
                metadata = mapOf(
                    CombatKeys.GAP_S to gap,
                    CombatKeys.RATE_S_PER_LAP to ratePerLap,
                    CombatKeys.SUSTAINED to if (sustained) 1.0 else 0.0,
                ),
                timeHorizon = TimeHorizon.Ongoing,
                expiry = Expiry.AtLap(context.frame.lap + 2),
                sourceDetector = id,
            )
        )
    }
}
