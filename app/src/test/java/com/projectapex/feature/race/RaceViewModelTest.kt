package com.projectapex.feature.race

import com.projectapex.domain.race.RaceEngine
import com.projectapex.domain.race.RaceStateFactory
import com.projectapex.domain.timeline.RaceTimeline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
    fun `uiState reflects the timeline's current snapshot and mode`() = runTest(mainDispatcher) {
        // A StandardTestDispatcher here means RaceTimeline's own auto-subscription
        // to RaceEngine never runs (its scheduler is never advanced) - only the
        // direct record()/previous() calls below affect the timeline.
        val timeline = RaceTimeline(RaceEngine(), StandardTestDispatcher())
        val viewModel = RaceViewModel(timeline)
        val emissions = mutableListOf<RaceUiState>()

        val job = launch {
            viewModel.uiState.collect { emissions.add(it) }
        }

        val first = RaceStateFactory.fiveCarField()
        val second = first.copy(currentLap = first.currentLap + 1)
        timeline.record(first)
        timeline.record(second)

        assertEquals(second, emissions.last().raceState)
        assertTrue(emissions.last().isLiveMode)
        assertEquals(1, emissions.last().timelinePosition)
        assertEquals(2, emissions.last().timelineSize)

        timeline.previous()

        assertFalse(viewModel.uiState.value.isLiveMode)
        assertEquals(first, viewModel.uiState.value.raceState)

        job.cancel()
    }
}
