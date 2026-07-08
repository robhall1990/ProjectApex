package com.projectapex.intelligence.detect.analysis

import com.projectapex.intelligence.features.FeatureView
import com.projectapex.intelligence.features.PaceEstimate

/**
 * Shared fuel-corrected pace comparison (APX-012), built on
 * [FeatureView.pace] (which already fuel-corrects and filters to clean laps —
 * docs/RaceIntelligencePlatform.md §9.1). Used by the fastest-pace detector
 * and available to any future pace-aware detector.
 */
object RelativePace {

    data class Ranked(val driverId: String, val position: Int, val pace: PaceEstimate)

    /** Drivers that have a pace fit, fastest (lowest mean lap) first. */
    fun ranking(view: FeatureView): List<Ranked> =
        view.runningOrder
            .filter { !it.retired }
            .mapNotNull { car -> view.pace(car.driverId)?.let { Ranked(car.driverId, car.position, it) } }
            .sortedBy { it.pace.meanSeconds }

    /** How many s/lap faster [fasterId] is than [slowerId]; null if either lacks a fit. */
    fun advantagePerLap(fasterId: String, slowerId: String, view: FeatureView): Double? {
        val faster = view.pace(fasterId) ?: return null
        val slower = view.pace(slowerId) ?: return null
        return slower.meanSeconds - faster.meanSeconds
    }
}
