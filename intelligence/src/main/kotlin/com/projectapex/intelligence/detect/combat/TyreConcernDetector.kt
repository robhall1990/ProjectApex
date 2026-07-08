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
import com.projectapex.intelligence.features.DegModel
import kotlin.math.ceil

/**
 * Tyre state per car (C1/C2). Prefers the predictive cliff story where a
 * degradation fit exists ("HAM approaching the tyre cliff") and falls back to
 * a plain age concern otherwise. At most one observation per car — the cliff
 * subsumes the age concern.
 */
class TyreConcernDetector(private val config: IntelligenceConfig) : Detector {
    private val combat = config.combat
    override val id = "tyre-concern-detector"

    override fun detect(context: DetectionContext): List<Observation> {
        val view = context.features
        return view.runningOrder.filter { !it.retired }.mapNotNull { car ->
            val driverId = car.driverId
            val stint = view.currentStint(driverId)
            val degFit = view.degFit(driverId)
            val age = car.tyre.ageLaps

            when {
                // Live cliff, or predicted cliff within the horizon.
                degFit != null && stint != null -> {
                    val predictedCliffAge = DegModel.predictCliffAge(degFit, stint.compound, age, config)
                    val etaLaps = predictedCliffAge - age
                    val imminent = degFit.cliffDetected || (etaLaps in 0.0..combat.tyreCliffHorizonLaps)
                    if (!imminent) return@mapNotNull null

                    Observation(
                        id = ObservationId("${CombatTypes.TYRE_CLIFF}:$driverId"),
                        type = ObservationType(CombatTypes.TYRE_CLIFF),
                        severity = Severity.HIGH,
                        confidence = Confidence.clamp01(0.4 + 0.4 * degFit.r2 * Confidence.sampleSize(degFit.n)),
                        timestamp = context.frame.timestamp,
                        subjectDrivers = listOf(driverId),
                        metadata = mapOf(
                            CombatKeys.TYRE_AGE_LAPS to age.toDouble(),
                            CombatKeys.ETA_LAPS to if (degFit.cliffDetected) 0.0 else ceil(etaLaps),
                            CombatKeys.DEG_RATE_S_PER_LAP to degFit.ratePerLap,
                        ),
                        timeHorizon = if (degFit.cliffDetected) TimeHorizon.Ongoing else TimeHorizon.InLaps(etaLaps),
                        expiry = Expiry.AtLap(context.frame.lap + 3),
                        sourceDetector = id,
                    )
                }
                // No fit yet: fall back to a plain age concern.
                age > combat.tyreAgeConcernLaps -> Observation(
                    id = ObservationId("${CombatTypes.TYRE_CONCERN}:$driverId"),
                    type = ObservationType(CombatTypes.TYRE_CONCERN),
                    severity = Severity.MEDIUM,
                    confidence = Confidence.clamp01(0.4 + 0.4 * Confidence.margin(age.toDouble(), combat.tyreAgeConcernLaps.toDouble(), combat.tyreAgeConcernLaps * 2.0)),
                    timestamp = context.frame.timestamp,
                    subjectDrivers = listOf(driverId),
                    metadata = mapOf(CombatKeys.TYRE_AGE_LAPS to age.toDouble()),
                    timeHorizon = TimeHorizon.Ongoing,
                    expiry = Expiry.AtLap(context.frame.lap + 3),
                    sourceDetector = id,
                )
                else -> null
            }
        }
    }
}
