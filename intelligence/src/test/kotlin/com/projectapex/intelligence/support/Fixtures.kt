package com.projectapex.intelligence.support

import com.projectapex.intelligence.ingest.CarTiming
import com.projectapex.intelligence.ingest.PitStatus
import com.projectapex.intelligence.ingest.Seconds
import com.projectapex.intelligence.ingest.TimingFrame
import com.projectapex.intelligence.ingest.TrackStatus
import com.projectapex.intelligence.ingest.TyreCompound
import com.projectapex.intelligence.ingest.TyreFit
import java.time.Instant

/** Test-only builders for [TimingFrame]s. */
object Fixtures {

    fun frame(
        sequence: Long,
        lap: Int,
        cars: List<CarTiming>,
        totalLaps: Int = 50,
        status: TrackStatus = TrackStatus.GREEN,
        timestampMs: Long = sequence * 1_000L,
    ): TimingFrame = TimingFrame(
        sequence = sequence,
        timestamp = Instant.ofEpochMilli(timestampMs),
        lap = lap,
        totalLaps = totalLaps,
        trackStatus = status,
        weather = null,
        cars = cars,
    )

    fun car(
        id: String,
        position: Int,
        lapsCompleted: Int,
        gap: Double?,
        lastLap: Double? = null,
        pit: PitStatus = PitStatus.NONE,
        compound: TyreCompound = TyreCompound.MEDIUM,
        tyreAge: Int = lapsCompleted,
        retired: Boolean = false,
        interval: Double? = null,
    ): CarTiming = CarTiming(
        driverId = id,
        position = position,
        lapsCompleted = lapsCompleted,
        gapToLeader = gap?.let { Seconds(it) },
        interval = interval?.let { Seconds(it) },
        lastLapTime = lastLap?.let { Seconds(it) },
        pitStatus = pit,
        tyre = TyreFit(compound, tyreAge),
        retired = retired,
    )
}
