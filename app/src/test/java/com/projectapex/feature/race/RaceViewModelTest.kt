package com.projectapex.feature.race

import com.projectapex.domain.model.RaceState
import com.projectapex.domain.race.RaceEngine
import com.projectapex.domain.race.RaceStateFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RaceViewModelTest {

    private val mainDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `uiState reflects RaceEngine's current and updated state`() = runTest(mainDispatcher) {
        val engine = RaceEngine()
        val viewModel = RaceViewModel(engine)
        val emissions = mutableListOf<RaceState>()

        val job = launch {
            viewModel.uiState.collect { emissions.add(it.raceState) }
        }

        val newState = RaceStateFactory.fiveCarField()
        engine.updateState(newState)

        assertEquals(2, emissions.size)
        assertEquals(RaceState.empty(), emissions[0])
        assertEquals(newState, emissions[1])

        job.cancel()
    }
}
