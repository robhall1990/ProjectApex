package com.projectapex.data.openf1

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveSessionCacheTest {

    @Test
    fun `merging advances the date cursor to the latest record seen`() {
        val cache = LiveSessionCache()

        cache.mergePositions(
            listOf(
                PositionDto(date = "2026-07-11T14:00:00Z", driverNumber = 1, position = 2),
                PositionDto(date = "2026-07-11T14:00:05Z", driverNumber = 4, position = 1),
            )
        )

        assertEquals("2026-07-11T14:00:05Z", cache.positionsAfter())
    }

    @Test
    fun `merge keeps the latest-by-date record per driver across polls`() {
        val cache = LiveSessionCache()

        cache.mergeIntervals(
            listOf(IntervalDto(date = "2026-07-11T14:00:00Z", driverNumber = 1)),
        )
        cache.mergeIntervals(
            listOf(IntervalDto(date = "2026-07-11T14:00:05Z", driverNumber = 1)),
        )

        assertEquals("2026-07-11T14:00:05Z", cache.latestIntervals.getValue(1).date)
        assertEquals("2026-07-11T14:00:05Z", cache.intervalsAfter())
    }

    @Test
    fun `out-of-order position records do not regress the cached state or cursor`() {
        val cache = LiveSessionCache()

        cache.mergePositions(listOf(PositionDto(date = "2026-07-11T14:00:10Z", driverNumber = 1, position = 3)))
        cache.mergePositions(listOf(PositionDto(date = "2026-07-11T14:00:00Z", driverNumber = 1, position = 1)))

        assertEquals(3, cache.latestPositions.getValue(1).position)
        assertEquals("2026-07-11T14:00:10Z", cache.positionsAfter())
    }

    @Test
    fun `laps keep the highest lap number per driver and don't regress on an out-of-order poll`() {
        val cache = LiveSessionCache()

        cache.mergeLaps(listOf(LapDto(driverNumber = 1, lapNumber = 5, dateStart = "2026-07-11T14:10:00Z")))
        cache.mergeLaps(listOf(LapDto(driverNumber = 1, lapNumber = 3, dateStart = "2026-07-11T14:05:00Z")))

        assertEquals(5, cache.latestLaps.getValue(1).lapNumber)
        assertEquals("2026-07-11T14:10:00Z", cache.lapsAfter())
    }

    @Test
    fun `laps cursor tolerates a null dateStart without regressing`() {
        val cache = LiveSessionCache()

        cache.mergeLaps(listOf(LapDto(driverNumber = 1, lapNumber = 1, dateStart = "2026-07-11T14:00:00Z")))
        cache.mergeLaps(listOf(LapDto(driverNumber = 1, lapNumber = 2, dateStart = null)))

        assertEquals(2, cache.latestLaps.getValue(1).lapNumber)
        assertEquals("2026-07-11T14:00:00Z", cache.lapsAfter())
    }

    @Test
    fun `a driver missing from a poll keeps their previously cached record`() {
        val cache = LiveSessionCache()

        cache.mergePositions(
            listOf(
                PositionDto(date = "2026-07-11T14:00:00Z", driverNumber = 1, position = 1),
                PositionDto(date = "2026-07-11T14:00:00Z", driverNumber = 4, position = 2),
            )
        )
        cache.mergePositions(listOf(PositionDto(date = "2026-07-11T14:00:05Z", driverNumber = 1, position = 1)))

        assertEquals(2, cache.latestPositions.size)
        assertEquals(2, cache.latestPositions.getValue(4).position)
    }

    @Test
    fun `reset clears every store and cursor`() {
        val cache = LiveSessionCache()

        cache.mergePositions(listOf(PositionDto(date = "2026-07-11T14:00:00Z", driverNumber = 1, position = 1)))
        cache.mergeIntervals(listOf(IntervalDto(date = "2026-07-11T14:00:00Z", driverNumber = 1)))
        cache.mergeLaps(listOf(LapDto(driverNumber = 1, lapNumber = 2, dateStart = "2026-07-11T14:00:00Z")))

        cache.reset()

        assertTrue(cache.latestPositions.isEmpty())
        assertTrue(cache.latestIntervals.isEmpty())
        assertTrue(cache.latestLaps.isEmpty())
        assertNull(cache.positionsAfter())
        assertNull(cache.intervalsAfter())
        assertNull(cache.lapsAfter())
    }
}
