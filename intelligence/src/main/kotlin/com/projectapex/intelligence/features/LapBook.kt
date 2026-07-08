package com.projectapex.intelligence.features

import com.projectapex.intelligence.ingest.TyreFit

/**
 * One completed lap for one driver (docs/RaceIntelligencePlatform.md §3.2).
 * A lap is *clean* — usable by pace/deg fits — iff it has a time and no flag.
 */
data class LapRecord(
    /** The driver's own lap number. */
    val lap: Int,
    /** Raw lap time in seconds; null when the feed lost it. */
    val timeSeconds: Double?,
    /** Fuel-corrected time (§9.1): raw − fuelEffect × lapsRemaining. Null iff raw is. */
    val fuelCorrectedSeconds: Double?,
    val tyre: TyreFit,
    val flags: Set<LapFlag>,
) {
    val isClean: Boolean get() = timeSeconds != null && flags.isEmpty()
}

enum class LapFlag { IN_LAP, OUT_LAP, SC_LAP, VSC_LAP, YELLOW, OUTLIER }

/** Estimate of a driver's underlying pace over a clean-lap window (§9.1). */
data class PaceEstimate(
    /** Mean fuel-corrected clean pace over the window (s). */
    val meanSeconds: Double,
    /** OLS trend slope over the window (s/lap). */
    val slopePerLap: Double,
    /** Residual noise (s) — feeds every confidence formula downstream. */
    val sigma: Double,
    /** Clean laps in the window — feeds the sample-size factor n/(n+3). */
    val n: Int,
)
