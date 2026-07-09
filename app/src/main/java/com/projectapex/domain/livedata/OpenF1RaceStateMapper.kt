package com.projectapex.domain.livedata

import com.projectapex.core.model.SessionStatus
import com.projectapex.data.openf1.DatedRecord
import com.projectapex.data.openf1.DriverDto
import com.projectapex.data.openf1.IntervalDto
import com.projectapex.data.openf1.LapDto
import com.projectapex.data.openf1.PitDto
import com.projectapex.data.openf1.PositionDto
import com.projectapex.data.openf1.RaceControlDto
import com.projectapex.data.openf1.StintDto
import com.projectapex.data.openf1.asGapSeconds
import com.projectapex.domain.model.CarState
import com.projectapex.domain.model.Driver
import com.projectapex.domain.model.RaceState
import com.projectapex.domain.model.TrackStatus
import com.projectapex.domain.model.TyreCompound
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime

/**
 * Pure mapping of raw OpenF1 API responses into a [RaceState]. No network or
 * coroutine dependencies — safe to unit test directly with hand-built DTOs.
 *
 * `FrameValidator` downstream (`:intelligence`) requires [CarState.position]
 * to be a strict 1..N permutation, so this mapper never trusts OpenF1's raw
 * `position` field directly: it's used only as a sort key (falling back to
 * gap-to-leader, then driver number, which is always unique), and the final
 * position is always `sortedIndex + 1` — a valid permutation regardless of
 * gaps, duplicates, or missing records in the source data.
 *
 * Gap-to-leader falls back to [previousState]'s last-known value when
 * OpenF1 reports a non-numeric gap (e.g. `"+1 LAP"` for lapped cars) or no
 * record at all, rather than guessing.
 *
 * [positions], [intervals] and [laps] are the already latest-per-driver
 * views maintained by [com.projectapex.data.openf1.LiveSessionCache] (APX-015)
 * rather than raw endpoint payloads — a driver absent from this poll's
 * response simply keeps whatever the cache last saw, which is exactly the
 * "no new records this poll" case this mapper needs to tolerate anyway.
 */
object OpenF1RaceStateMapper {

    private val PIT_WINDOW: Duration = Duration.ofSeconds(45)

    fun map(
        drivers: List<DriverDto>,
        positions: Map<Int, PositionDto>,
        intervals: Map<Int, IntervalDto>,
        laps: Map<Int, LapDto>,
        stints: List<StintDto>,
        pitStops: List<PitDto>,
        raceControl: List<RaceControlDto>,
        totalLapsOverride: Int,
        previousState: RaceState?,
        now: Instant,
    ): RaceState {
        val trackStatus = deriveTrackStatus(raceControl)

        val driverNumbers = drivers.map { it.driverNumber }.toSet().ifEmpty {
            (positions.keys + intervals.keys + laps.keys)
        }
        if (driverNumbers.isEmpty()) {
            return RaceState(
                sessionStatus = SessionStatus.LIVE,
                currentLap = 0,
                totalLaps = totalLapsOverride,
                cars = emptyList(),
                timestamp = now.toEpochMilli(),
                trackStatus = trackStatus,
            )
        }

        val driverByNumber = drivers.associateBy { it.driverNumber }
        val currentStint = stints.groupBy { it.driverNumber }
            .mapValues { (_, records) -> records.firstOrNull { it.lapEnd == null } ?: records.maxByOrNull { it.stintNumber } }
        val latestPit = latestByDriver(pitStops) { it.driverNumber }
        val previousById = previousState?.cars?.associateBy { it.driver.id }.orEmpty()

        val raws = driverNumbers.map { number ->
            buildRawCar(
                number = number,
                driverDto = driverByNumber[number],
                lap = laps[number]?.lapNumber ?: 0,
                parsedGap = intervals[number]?.gapToLeader.asGapSeconds(),
                sortPosition = positions[number]?.position ?: Int.MAX_VALUE,
                stint = currentStint[number],
                pitInstant = latestPit[number]?.date?.let(::parseInstant),
                previousGap = previousById[number.toString()]?.gapToLeaderSeconds,
                now = now,
            )
        }

        val ordered = raws.sortedWith(
            compareBy(
                { it.sortPosition },
                { it.parsedGap ?: Double.MAX_VALUE },
                { it.driverNumber },
            )
        )

        val cars = ordered.mapIndexed { index, raw ->
            CarState(
                driver = Driver(id = raw.id, name = raw.name, team = raw.team, number = raw.driverNumber),
                position = index + 1,
                lap = raw.lap,
                gapToLeaderSeconds = raw.resolvedGap,
                tyreCompound = raw.tyreCompound,
                tyreAgeLaps = raw.tyreAgeLaps,
                isInPitLane = raw.isInPitLane,
            )
        }

        return RaceState(
            sessionStatus = SessionStatus.LIVE,
            currentLap = cars.maxOfOrNull { it.lap } ?: 0,
            totalLaps = totalLapsOverride,
            cars = cars,
            timestamp = now.toEpochMilli(),
            trackStatus = trackStatus,
        )
    }

    private class RawCar(
        val driverNumber: Int,
        val id: String,
        val name: String,
        val team: String,
        val lap: Int,
        val sortPosition: Int,
        val parsedGap: Double?,
        val resolvedGap: Double,
        val tyreCompound: TyreCompound,
        val tyreAgeLaps: Int,
        val isInPitLane: Boolean,
    )

    private fun buildRawCar(
        number: Int,
        driverDto: DriverDto?,
        lap: Int,
        parsedGap: Double?,
        sortPosition: Int,
        stint: StintDto?,
        pitInstant: Instant?,
        previousGap: Double?,
        now: Instant,
    ): RawCar {
        val tyreCompound = stint?.compound?.let { compound ->
            runCatching { TyreCompound.valueOf(compound.uppercase()) }.getOrNull()
        } ?: TyreCompound.MEDIUM
        val tyreAgeLaps = if (stint != null) {
            stint.tyreAgeAtStart + (lap - stint.lapStart).coerceAtLeast(0)
        } else {
            0
        }
        val isInPitLane = pitInstant != null && isWithinPitWindow(pitInstant, now)

        return RawCar(
            driverNumber = number,
            id = number.toString(),
            name = driverDto?.fullName ?: driverDto?.broadcastName ?: "#$number",
            team = driverDto?.teamName ?: "Unknown",
            lap = lap,
            sortPosition = sortPosition,
            parsedGap = parsedGap,
            resolvedGap = parsedGap ?: previousGap ?: 0.0,
            tyreCompound = tyreCompound,
            tyreAgeLaps = tyreAgeLaps,
            isInPitLane = isInPitLane,
        )
    }

    private fun isWithinPitWindow(pitInstant: Instant, now: Instant): Boolean {
        val elapsed = Duration.between(pitInstant, now)
        return !elapsed.isNegative && elapsed <= PIT_WINDOW
    }

    private fun <T : DatedRecord> latestByDriver(records: List<T>, driverNumber: (T) -> Int): Map<Int, T> =
        records.groupBy(driverNumber)
            .mapValues { (_, entries) ->
                entries.maxByOrNull { parseInstant(it.date) ?: Instant.EPOCH } ?: entries.last()
            }

    /**
     * Best-effort track status from `/race_control` messages: replays every
     * record in chronological order, updating status only on messages that
     * clearly indicate a flag/safety-car change and leaving it unchanged
     * otherwise (most race-control messages — penalties, DRS enabled, etc.
     * — aren't status changes). No single OpenF1 field cleanly encodes
     * "current track status", so this is a heuristic — verify against a
     * real session before relying on it race day.
     */
    private fun deriveTrackStatus(raceControl: List<RaceControlDto>): TrackStatus {
        var status = TrackStatus.GREEN
        raceControl
            .sortedBy { parseInstant(it.date) ?: Instant.EPOCH }
            .forEach { record ->
                val message = record.message?.uppercase().orEmpty()
                val flag = record.flag?.uppercase().orEmpty()
                status = when {
                    message.contains("VIRTUAL SAFETY CAR DEPLOYED") -> TrackStatus.VIRTUAL_SAFETY_CAR
                    message.contains("SAFETY CAR DEPLOYED") -> TrackStatus.SAFETY_CAR
                    flag.contains("RED") -> TrackStatus.RED
                    message.contains("SAFETY CAR IN THIS LAP") -> TrackStatus.GREEN
                    message.contains("VIRTUAL SAFETY CAR ENDING") -> TrackStatus.GREEN
                    flag == "CLEAR" || flag == "GREEN" -> TrackStatus.GREEN
                    flag.contains("YELLOW") -> TrackStatus.YELLOW
                    else -> status
                }
            }
        return status
    }

    private fun parseInstant(raw: String): Instant? =
        runCatching { OffsetDateTime.parse(raw).toInstant() }.getOrNull()
            ?: runCatching { Instant.parse(raw) }.getOrNull()
}
