package com.projectapex.intelligence.detect.analysis

import com.projectapex.intelligence.features.FeatureView
import com.projectapex.intelligence.features.olsFit

/**
 * Shared pairwise-interval analysis (APX-012) — the one place battle, DRS, and
 * gap-trend detectors agree on what "closing" means, so they can't drift apart.
 * A light form of the closed-form gap kinematics in
 * docs/RaceIntelligencePlatform.md §9.3/§12.1 (the full forecaster with
 * divergent-degradation and uncertainty is a later predict/ ticket).
 */
object GapAnalysis {

    data class GapTrend(
        /** Current interval (behind − ahead), seconds. */
        val currentGapSeconds: Double,
        /** OLS slope of the interval over recent laps, s/lap. Negative = closing. */
        val ratePerLap: Double,
        /** Lap-aligned samples the trend was fit from. */
        val samples: Int,
    )

    /**
     * Interval trend between the car [aheadId] and the car [behindId]. Null
     * only when there's no current interval at all; with too little history
     * the rate is 0 (flat) rather than absent, so callers can still act on the
     * instantaneous gap.
     */
    fun trend(aheadId: String, behindId: String, view: FeatureView, windowLaps: Int): GapTrend? {
        val current = view.interval(aheadId, behindId)?.value ?: return null
        val history = view.intervalHistory(aheadId, behindId, windowLaps)
        if (history.size < 2) return GapTrend(current, 0.0, history.size)
        val fit = olsFit(history.map { it.lap.toDouble() to it.interval.value })
            ?: return GapTrend(current, 0.0, history.size)
        return GapTrend(current, fit.slope, history.size)
    }

    /**
     * Laps until the interval reaches [targetGap] at the current [ratePerLap].
     * Null if it isn't converging (already there → 0.0; widening or flat → null).
     */
    fun lapsUntil(currentGap: Double, ratePerLap: Double, targetGap: Double): Double? = when {
        currentGap <= targetGap -> 0.0
        ratePerLap >= 0.0 -> null
        else -> (currentGap - targetGap) / -ratePerLap
    }
}
