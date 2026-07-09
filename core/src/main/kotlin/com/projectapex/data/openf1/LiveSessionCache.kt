package com.projectapex.data.openf1

import java.time.Instant
import java.time.OffsetDateTime

/**
 * Accumulating per-driver store for OpenF1's monotonically-growing
 * time-series endpoints (`/position`, `/intervals`, `/laps`). Owned by
 * [com.projectapex.domain.livedata.OpenF1LiveDataSource] as single-writer
 * state driven from its poll loop — no synchronisation needed.
 *
 * Each poll fetches only records newer than [positionsAfter]/[intervalsAfter]/
 * [lapsAfter] (the `date>` cursor OpenF1 accepts) and merges them in,
 * keeping the latest record per driver — highest lap number for laps,
 * since [LapDto] has no `date` field — rather than replaying full session
 * history every time. Records older than what's already cached (possible at
 * cursor boundaries) never regress the store.
 */
class LiveSessionCache {

    private val positions = mutableMapOf<Int, PositionDto>()
    private val intervals = mutableMapOf<Int, IntervalDto>()
    private val laps = mutableMapOf<Int, LapDto>()

    private var positionsCursor: String? = null
    private var intervalsCursor: String? = null
    private var lapsCursor: String? = null

    val latestPositions: Map<Int, PositionDto> get() = positions
    val latestIntervals: Map<Int, IntervalDto> get() = intervals
    val latestLaps: Map<Int, LapDto> get() = laps

    fun positionsAfter(): String? = positionsCursor
    fun intervalsAfter(): String? = intervalsCursor
    fun lapsAfter(): String? = lapsCursor

    fun mergePositions(records: List<PositionDto>) {
        positionsCursor = mergeDated(positions, records, positionsCursor) { it.driverNumber }
    }

    fun mergeIntervals(records: List<IntervalDto>) {
        intervalsCursor = mergeDated(intervals, records, intervalsCursor) { it.driverNumber }
    }

    fun mergeLaps(records: List<LapDto>) {
        for (record in records) {
            val existing = laps[record.driverNumber]
            if (existing == null || record.lapNumber >= existing.lapNumber) {
                laps[record.driverNumber] = record
            }
            lapsCursor = maxCursor(lapsCursor, record.dateStart)
        }
    }

    fun reset() {
        positions.clear()
        intervals.clear()
        laps.clear()
        positionsCursor = null
        intervalsCursor = null
        lapsCursor = null
    }

    private fun <T : DatedRecord> mergeDated(
        store: MutableMap<Int, T>,
        records: List<T>,
        cursor: String?,
        driverNumber: (T) -> Int,
    ): String? {
        var newCursor = cursor
        for (record in records) {
            val number = driverNumber(record)
            val existing = store[number]
            if (existing == null || compareDates(record.date, existing.date) >= 0) {
                store[number] = record
            }
            newCursor = maxCursor(newCursor, record.date)
        }
        return newCursor
    }

    private fun maxCursor(current: String?, candidate: String?): String? {
        if (candidate == null) return current
        if (current == null) return candidate
        return if (compareDates(candidate, current) > 0) candidate else current
    }

    private fun compareDates(a: String, b: String): Int {
        val instantA = parseInstant(a)
        val instantB = parseInstant(b)
        return if (instantA != null && instantB != null) instantA.compareTo(instantB) else a.compareTo(b)
    }

    private fun parseInstant(raw: String): Instant? =
        runCatching { OffsetDateTime.parse(raw).toInstant() }.getOrNull()
            ?: runCatching { Instant.parse(raw) }.getOrNull()
}
