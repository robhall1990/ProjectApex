package com.projectapex.domain.timeline

import com.projectapex.domain.race.RaceEngine
import com.projectapex.domain.race.RaceStateFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [StandardTestDispatcher] never runs anything unless its scheduler is
 * explicitly advanced, so [RaceTimeline]'s internal auto-subscription to
 * [RaceEngine] simply never fires here - every test drives the timeline
 * directly via [RaceTimeline.record], independent of that wiring.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RaceTimelineTest {

    private fun newTimeline(): RaceTimeline =
        RaceTimeline(RaceEngine(), StandardTestDispatcher())

    @Test
    fun `recording a snapshot stores it and stays live`() {
        val timeline = newTimeline()
        val raceState = RaceStateFactory.fiveCarField()

        timeline.record(raceState)

        val state = timeline.state.value
        assertEquals(1, state.snapshots.size)
        assertEquals(raceState, state.snapshots[0].raceState)
        assertEquals(0, state.currentIndex)
        assertTrue(state.isLive)
    }

    @Test
    fun `previous moves back and leaves live mode`() {
        val timeline = newTimeline()
        val first = RaceStateFactory.fiveCarField()
        val second = first.copy(currentLap = first.currentLap + 1)
        timeline.record(first)
        timeline.record(second)

        timeline.previous()

        val state = timeline.state.value
        assertEquals(0, state.currentIndex)
        assertEquals(first, state.currentSnapshot?.raceState)
        assertFalse(state.isLive)
    }

    @Test
    fun `next moves forward and returns to live at the newest snapshot`() {
        val timeline = newTimeline()
        val first = RaceStateFactory.fiveCarField()
        val second = first.copy(currentLap = first.currentLap + 1)
        timeline.record(first)
        timeline.record(second)
        timeline.previous()

        timeline.next()

        val state = timeline.state.value
        assertEquals(1, state.currentIndex)
        assertEquals(second, state.currentSnapshot?.raceState)
        assertTrue(state.isLive)
    }

    @Test
    fun `seek jumps directly to the requested index`() {
        val timeline = newTimeline()
        repeat(5) { index ->
            timeline.record(RaceStateFactory.fiveCarField().copy(currentLap = index))
        }

        timeline.seek(2)

        val state = timeline.state.value
        assertEquals(2, state.currentIndex)
        assertEquals(2, state.currentSnapshot?.raceState?.currentLap)
        assertFalse(state.isLive)
    }

    @Test
    fun `recording beyond the maximum drops the oldest snapshots`() {
        val timeline = newTimeline()

        repeat(1_005) { index ->
            timeline.record(RaceStateFactory.fiveCarField().copy(currentLap = index))
        }

        val state = timeline.state.value
        assertEquals(1_000, state.snapshots.size)
        assertEquals(5, state.snapshots.first().raceState.currentLap)
        assertEquals(1_004, state.snapshots.last().raceState.currentLap)
        assertTrue(state.isLive)
    }

    @Test
    fun `clear resets the timeline to empty`() {
        val timeline = newTimeline()
        timeline.record(RaceStateFactory.fiveCarField())

        timeline.clear()

        val state = timeline.state.value
        assertTrue(state.snapshots.isEmpty())
        assertEquals(-1, state.currentIndex)
        assertTrue(state.isLive)
    }
}
