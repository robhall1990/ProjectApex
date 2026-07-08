package com.projectapex.intelligence.adapter

import com.projectapex.core.model.SessionStatus
import com.projectapex.domain.model.RaceState
import com.projectapex.domain.model.TrackStatus as AppTrackStatus
import com.projectapex.intelligence.ingest.CarTiming
import com.projectapex.intelligence.ingest.PitStatus
import com.projectapex.intelligence.ingest.Seconds
import com.projectapex.intelligence.ingest.TimingFrame
import com.projectapex.intelligence.ingest.TrackStatus
import com.projectapex.intelligence.ingest.TyreCompound
import com.projectapex.intelligence.ingest.TyreFit
import java.time.Instant

/**
 * Bridges the app's `RaceState` into the intelligence platform's canonical
 * [TimingFrame] (docs/RaceIntelligencePlatform.md §3.1). Lives in the app
 * module because it must see both sides; the :intelligence module itself
 * never imports app types.
 *
 * Stateful on purpose: it assigns monotonic sequence numbers and synthesises
 * `lastLapTime` from lap-edge timestamps, because today's simulator doesn't
 * produce lap times. Once a real feed (or the simulator) carries lap times,
 * the synthesis becomes dead weight and gets deleted — nothing downstream
 * knows the difference.
 */
private val trackStatusMap = mapOf(
    AppTrackStatus.GREEN to TrackStatus.GREEN,
    AppTrackStatus.YELLOW to TrackStatus.YELLOW,
    AppTrackStatus.SAFETY_CAR to TrackStatus.SC,
    AppTrackStatus.VIRTUAL_SAFETY_CAR to TrackStatus.VSC,
    AppTrackStatus.RED to TrackStatus.RED,
)

class RaceStateAdapter {

    private var sequence = 0L
    private val lastLapEdge = mutableMapOf<String, LapEdge>()

    private data class LapEdge(val lap: Int, val timestampMs: Long)

    /** Null for states the platform shouldn't see (offline / empty grid). */
    fun adapt(state: RaceState): TimingFrame? {
        if (state.sessionStatus != SessionStatus.LIVE || state.cars.isEmpty()) return null

        return TimingFrame(
            sequence = sequence++,
            timestamp = Instant.ofEpochMilli(state.timestamp),
            lap = state.currentLap,
            totalLaps = state.totalLaps,
            trackStatus = trackStatusMap.getValue(state.trackStatus),
            weather = null,
            cars = state.cars.map { car ->
                CarTiming(
                    driverId = car.driver.id,
                    position = car.position,
                    lapsCompleted = car.lap,
                    gapToLeader = Seconds(car.gapToLeaderSeconds),
                    interval = null, // FrameNormaliser derives it
                    lastLapTime = synthesiseLapTime(car.driver.id, car.lap, state.timestamp),
                    pitStatus = if (car.isInPitLane) PitStatus.IN_PIT else PitStatus.NONE,
                    tyre = TyreFit(
                        compound = TyreCompound.valueOf(car.tyreCompound.name),
                        ageLaps = car.tyreAgeLaps,
                    ),
                )
            },
        )
    }

    /**
     * Wall-clock delta between consecutive lap edges. Exactly right for a
     * 1 Hz feed to within one frame period; null on the first edge seen and
     * after multi-lap jumps (never guess — the EventDeriver flags those).
     */
    private fun synthesiseLapTime(driverId: String, lap: Int, timestampMs: Long): Seconds? {
        val previous = lastLapEdge[driverId]
        if (previous == null || lap != previous.lap) {
            lastLapEdge[driverId] = LapEdge(lap, timestampMs)
        }
        return when {
            previous == null -> null
            lap == previous.lap + 1 -> Seconds((timestampMs - previous.timestampMs) / 1000.0)
            else -> null
        }
    }
}
