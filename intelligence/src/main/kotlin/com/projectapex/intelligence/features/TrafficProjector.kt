package com.projectapex.intelligence.features

import com.projectapex.intelligence.api.IntelligenceConfig
import com.projectapex.intelligence.ingest.Seconds

/**
 * Forward projection of the whole field (docs/RaceIntelligencePlatform.md
 * §9.5): where does a car rejoin if it pits, and is that clear air? All math
 * is leader-relative cumulative time (§9.4) — absolute lap times only matter
 * as *differences*, so a consistent estimate per car is what counts.
 */
class TrafficProjector(private val config: IntelligenceConfig) {

    data class RejoinProjection(
        val position: Int,
        val intervalAhead: Seconds?,
        val intervalBehind: Seconds?,
        /** Rejoin interval to the car ahead below the dirty-air threshold. */
        val dirtyAir: Boolean,
    )

    /**
     * Project [driverId] pitting at the end of lap now+[lapsUntilPit]
     * (0 = pit at the end of the current lap). Cars without a leader gap
     * (no data) are excluded from the projection.
     */
    fun rejoin(driverId: String, lapsUntilPit: Int, view: FeatureView): RejoinProjection? {
        val horizon = lapsUntilPit + 1 // laps everyone completes incl. the pit lap
        val projected = view.runningOrder
            .filter { !it.retired }
            .mapNotNull { car ->
                val cum = view.cumulativeTime(car.driverId) ?: return@mapNotNull null
                val perLap = estimatedLapSeconds(car.driverId, view)
                val pitPenalty = if (car.driverId == driverId) view.pitLoss(view.trackStatus).value else 0.0
                car.driverId to (cum.value + perLap * horizon + pitPenalty)
            }
            .sortedBy { it.second }

        val index = projected.indexOfFirst { it.first == driverId }
        if (index < 0) return null

        val intervalAhead = if (index > 0) Seconds(projected[index].second - projected[index - 1].second) else null
        val intervalBehind =
            if (index < projected.lastIndex) Seconds(projected[index + 1].second - projected[index].second) else null

        return RejoinProjection(
            position = index + 1,
            intervalAhead = intervalAhead,
            intervalBehind = intervalBehind,
            dirtyAir = intervalAhead != null && intervalAhead.value < config.dirtyAirGapSeconds,
        )
    }

    /**
     * Per-lap advance estimate for one car: deg model if fitted (bends with
     * tyre age), else pace mean, else the config fallback. Fuel-corrected
     * values are fine here — the correction is identical across cars at the
     * same lap, so it cancels in the differences this feeds.
     */
    fun estimatedLapSeconds(driverId: String, view: FeatureView): Double {
        val stint = view.currentStint(driverId)
        val deg = view.degFit(driverId)
        if (deg != null && stint != null) {
            val nextAge = (stint.laps.lastOrNull()?.tyre?.ageLaps ?: stint.startAgeLaps) + 1
            return deg.baseSeconds + deg.ratePerLap * nextAge
        }
        return view.pace(driverId)?.meanSeconds ?: config.fallbackLapSeconds
    }
}
