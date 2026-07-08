package com.projectapex.intelligence.events

import com.projectapex.intelligence.ingest.CarTiming
import com.projectapex.intelligence.ingest.PitStatus
import com.projectapex.intelligence.ingest.TimingFrame

/**
 * Diffs frame N against N−1 and emits [EngineEvent]s
 * (docs/RaceIntelligencePlatform.md §6.1). Stateful (holds the previous
 * frame); single-writer like the rest of the pipeline.
 *
 * Event ordering within one frame is deliberate: DataGap and status changes
 * first (context), then pit events, then lap completions (so the
 * FeatureStore's pending in/out-lap flags are set before the lap record is
 * built), then position changes and lappings.
 */
class EventDeriver {

    private var previous: TimingFrame? = null

    fun derive(frame: TimingFrame): List<EngineEvent> {
        val prev = previous
        previous = frame
        if (prev == null) return emptyList() // first frame: history starts here, no edges yet

        val prevById = prev.cars.associateBy { it.driverId }
        val nowByPosition = frame.cars.associateBy { it.position }
        val events = mutableListOf<EngineEvent>()
        val atLap = frame.lap
        val at = frame.timestamp

        // Feed gap (sequence jump).
        val missed = (frame.sequence - prev.sequence - 1).toInt()
        if (missed > 0) events += EngineEvent.DataGap(atLap, at, missed)

        // Track status edge.
        if (frame.trackStatus != prev.trackStatus) {
            events += EngineEvent.TrackStatusChanged(atLap, at, prev.trackStatus, frame.trackStatus)
        }

        // Retirements: flag flip, or car vanished from the frame.
        frame.cars.forEach { car ->
            val before = prevById[car.driverId] ?: return@forEach
            if (car.retired && !before.retired) {
                events += EngineEvent.DriverRetired(atLap, at, car.driverId)
            }
        }
        prev.cars.forEach { before ->
            if (frame.cars.none { it.driverId == before.driverId } && !before.retired) {
                events += EngineEvent.DriverRetired(atLap, at, before.driverId)
            }
        }

        // Pit entry/exit edges from pitStatus transitions.
        frame.cars.forEach { car ->
            val before = prevById[car.driverId] ?: return@forEach
            val wasIn = before.pitStatus == PitStatus.ENTRY || before.pitStatus == PitStatus.IN_PIT
            val isIn = car.pitStatus == PitStatus.ENTRY || car.pitStatus == PitStatus.IN_PIT
            if (isIn && !wasIn) {
                events += EngineEvent.PitEntry(atLap, at, car.driverId)
            }
            if (wasIn && !isIn) {
                events += EngineEvent.PitExit(
                    atLap = atLap, at = at, driverId = car.driverId, tyre = car.tyre,
                    rejoinPosition = car.position, rejoinInterval = car.interval,
                )
            }
        }

        // Lap completions. A jump of >1 laps means the feed lost edges: emit a
        // DataGap instead of inventing lap records (spec §6.1 — never guess).
        frame.cars.forEach { car ->
            val before = prevById[car.driverId] ?: return@forEach
            val delta = car.lapsCompleted - before.lapsCompleted
            when {
                delta == 1 -> events += EngineEvent.LapCompleted(
                    atLap = atLap, at = at, driverId = car.driverId,
                    lapNumber = car.lapsCompleted, lapTime = car.lastLapTime,
                )
                delta > 1 -> events += EngineEvent.DataGap(atLap, at, delta - 1)
            }
        }

        // Position changes with the on-track bit.
        frame.cars.forEach { car ->
            val before = prevById[car.driverId] ?: return@forEach
            if (car.position == before.position) return@forEach
            val gained = car.position < before.position
            // Gain: the displaced car is now directly behind us.
            // Loss: the car that took our old spot is now standing in it.
            val counterpart = if (gained) nowByPosition[car.position + 1] else nowByPosition[before.position]
            events += EngineEvent.PositionChange(
                atLap = atLap, at = at, driverId = car.driverId,
                from = before.position, to = car.position,
                onTrack = car.isRacing() && counterpart?.isRacing() == true,
            )
        }

        // Lappings, detected against the leader only (v1): a car's lap deficit
        // to the leader grew and is at least one full lap.
        val leaderNow = frame.cars.minByOrNull { it.position }
        val leaderBefore = prev.cars.minByOrNull { it.position }
        if (leaderNow != null && leaderBefore != null) {
            frame.cars.forEach { car ->
                if (car.driverId == leaderNow.driverId) return@forEach
                val before = prevById[car.driverId] ?: return@forEach
                val deficitNow = leaderNow.lapsCompleted - car.lapsCompleted
                val deficitBefore = leaderBefore.lapsCompleted - before.lapsCompleted
                if (deficitNow >= 1 && deficitNow > deficitBefore) {
                    events += EngineEvent.CarLapped(atLap, at, car.driverId, leaderNow.driverId)
                }
            }
        }

        return events
    }

    private fun CarTiming.isRacing(): Boolean = !retired && pitStatus == PitStatus.NONE
}
