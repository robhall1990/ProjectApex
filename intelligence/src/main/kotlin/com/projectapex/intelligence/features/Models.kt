package com.projectapex.intelligence.features

import com.projectapex.intelligence.api.IntelligenceConfig
import com.projectapex.intelligence.ingest.Seconds
import com.projectapex.intelligence.ingest.TrackStatus

/**
 * Fuel correction (§9.1): normalise every lap to end-of-race fuel load so
 * pace/deg comparisons aren't dominated by the car simply getting lighter.
 */
object FuelModel {
    fun corrected(rawSeconds: Double, lap: Int, totalLaps: Int, config: IntelligenceConfig): Double =
        rawSeconds - config.fuelEffectPerLap * (totalLaps - lap).coerceAtLeast(0)
}

/** Pit-lane time loss by track status (§9.4). */
object PitLossModel {
    fun pitLoss(status: TrackStatus, config: IntelligenceConfig): Seconds {
        val green = config.track.pitLaneLossSeconds
        return Seconds(
            when (status) {
                TrackStatus.SC -> green * config.track.scPitLossFactor
                TrackStatus.VSC -> green * config.track.vscPitLossFactor
                TrackStatus.GREEN, TrackStatus.YELLOW, TrackStatus.RED -> green
            }
        )
    }
}

/** One leader-gap sample at a driver's lap completion. */
data class GapSample(val lap: Int, val gapToLeader: Seconds)

/** One pairwise interval sample, derived from aligned [GapSample]s. */
data class GapPoint(val lap: Int, val interval: Seconds)

/** A completed (or in-progress) pit stop, from pit entry/exit events. */
data class PitStop(
    val driverId: String,
    val lapIn: Int,
    val lapOut: Int?,
    val tyreAfterAgeLaps: Int?,
    val compoundAfter: com.projectapex.intelligence.ingest.TyreCompound?,
)

/** One position-change sample (recorded only on change, plus the initial position). */
data class PositionSample(val lap: Int, val position: Int)
