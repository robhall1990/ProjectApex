package com.projectapex.domain.simulation

import com.projectapex.domain.race.RaceEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RaceSimulatorTest {

    @Test
    fun `starts successfully and seeds a 20 car field`() = runTest {
        val engine = RaceEngine()
        val simulator = RaceSimulator(engine, StandardTestDispatcher(testScheduler))

        simulator.start()

        assertTrue(simulator.isRunning.value)
        assertEquals(20, engine.state.value.cars.size)

        simulator.stop()
    }

    @Test
    fun `emits state updates while running`() = runTest {
        val engine = RaceEngine()
        val simulator = RaceSimulator(engine, StandardTestDispatcher(testScheduler))

        simulator.start()
        val initial = engine.state.value

        advanceTimeBy(1_100)
        runCurrent()

        val afterOneTick = engine.state.value
        assertTrue(afterOneTick.timestamp > initial.timestamp)

        simulator.stop()
    }

    @Test
    fun `stopping the simulator stops further state updates`() = runTest {
        val engine = RaceEngine()
        val simulator = RaceSimulator(engine, StandardTestDispatcher(testScheduler))

        simulator.start()
        advanceTimeBy(1_100)
        runCurrent()

        simulator.stop()
        val stateAtStop = engine.state.value

        advanceTimeBy(5_000)
        runCurrent()

        assertFalse(simulator.isRunning.value)
        assertEquals(stateAtStop, engine.state.value)
    }
}
