package com.projectapex.intelligence.ingest

import java.time.Instant

/**
 * The platform's canonical, validated input snapshot
 * (docs/RaceIntelligencePlatform.md §3.1).
 *
 * Deliberately richer than the app's current `RaceState`: it has slots for
 * everything a real live-timing feed carries (sector times, intervals, track
 * status, weather) even though today's [com.projectapex] adapter leaves most
 * of them null/empty. Defining our own input type also keeps this module
 * decoupled from the app's domain package.
 */
data class TimingFrame(
    /** Monotonic feed counter — gaps are detectable ([EventDeriver] emits DataGap). */
    val sequence: Long,
    val timestamp: Instant,
    /** Race lap (leader's lap). */
    val lap: Int,
    val totalLaps: Int,
    val trackStatus: TrackStatus,
    val weather: WeatherSample?,
    val cars: List<CarTiming>,
)

data class CarTiming(
    val driverId: String,
    val position: Int,
    /**
     * Monotonic per-driver lap counter. Only *increments* carry meaning
     * (lap-edge detection); the absolute offset vs. other counters cancels
     * everywhere it is used (lapped-car deficits are differences).
     */
    val lapsCompleted: Int,
    val gapToLeader: Seconds?,          // null when unknown (e.g. lapped without data)
    val interval: Seconds?,             // gap to car ahead; derived by FrameNormaliser if absent
    val lastLapTime: Seconds?,
    val sectorTimes: List<Seconds?> = emptyList(),
    val speedTrap: Kph? = null,
    val pitStatus: PitStatus = PitStatus.NONE,
    val tyre: TyreFit,
    val retired: Boolean = false,
)

data class TyreFit(val compound: TyreCompound, val ageLaps: Int)

data class WeatherSample(val airTempC: Double?, val trackTempC: Double?, val rainfall: Boolean)

enum class TrackStatus { GREEN, YELLOW, SC, VSC, RED }

enum class PitStatus { NONE, ENTRY, IN_PIT, OUT_LAP }

/**
 * The intelligence module's own compound enum (it cannot depend on the app's
 * `domain.model.TyreCompound`; adapters map by name).
 */
enum class TyreCompound { SOFT, MEDIUM, HARD, INTERMEDIATE, WET }

@JvmInline
value class Seconds(val value: Double) : Comparable<Seconds> {
    operator fun plus(other: Seconds): Seconds = Seconds(value + other.value)
    operator fun minus(other: Seconds): Seconds = Seconds(value - other.value)
    override fun compareTo(other: Seconds): Int = value.compareTo(other.value)
}

@JvmInline
value class Kph(val value: Double)
