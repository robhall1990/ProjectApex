package com.projectapex.domain.race

import com.projectapex.core.model.SessionStatus
import com.projectapex.domain.model.RaceState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RaceEngineTest {

    @Test
    fun `starts with a valid empty state`() {
        val engine = RaceEngine()

        val state = engine.state.value

        assertEquals(RaceState.empty(), state)
        assertEquals(SessionStatus.OFFLINE, state.sessionStatus)
        assertEquals(0, state.currentLap)
        assertEquals(0, state.totalLaps)
        assertTrue(state.cars.isEmpty())
    }

    @Test
    fun `updateState replaces the current race state`() {
        val engine = RaceEngine()
        val newState = RaceStateFactory.fiveCarField()

        engine.updateState(newState)

        assertEquals(newState, engine.state.value)
    }

    @Test
    fun `observers receive the updated state`() = runTest {
        val engine = RaceEngine()
        val emissions = mutableListOf<RaceState>()

        // Unconfined so the collector attaches and captures the initial
        // value synchronously, before updateState() is called below.
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            engine.state.toList(emissions)
        }

        val newState = RaceStateFactory.fiveCarField()
        engine.updateState(newState)

        assertEquals(2, emissions.size)
        assertEquals(RaceState.empty(), emissions[0])
        assertEquals(newState, emissions[1])

        job.cancel()
    }
}
