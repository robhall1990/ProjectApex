package com.projectapex.feature.race

import com.projectapex.domain.intelligence.RaceIntelligenceEngine
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
    fun `uiState reflects the timeline's race data and replay mode`() = runTest(mainDispatcher) {
        val engine = RaceEngine()
        // A StandardTestDispatcher here means RaceTimeline's own auto-subscription
        // to RaceEngine never runs (its scheduler is never advanced) - only the
        // direct record()/previous() calls below affect the timeline.
        val timeline = RaceTimeline(engine, StandardTestDispatcher())
        val viewModel = RaceViewModel(timeline, engine, RaceIntelligenceEngine())
        val emissions = mutableListOf<RaceUiState>()

        val job = launch {
            viewModel.uiState.collect { emissions.add(it) }
        }

        val first = RaceStateFactory.fiveCarField()
        val second = first.copy(currentLap = first.currentLap + 1)
        timeline.record(first)
        timeline.record(second)

        assertEquals(second, emissions.last().raceState)
        assertFalse(emissions.last().isReplayMode)

        timeline.previous()

        assertTrue(viewModel.uiState.value.isReplayMode)
        assertEquals(first, viewModel.uiState.value.raceState)

        job.cancel()
    }

    @Test
    fun `insights reflect RaceEngine's live state, independent of replay position`() = runTest(mainDispatcher) {
        val engine = RaceEngine()
        val timeline = RaceTimeline(engine, StandardTestDispatcher())
        val viewModel = RaceViewModel(timeline, engine, RaceIntelligenceEngine())
        val emissions = mutableListOf<RaceUiState>()

        val job = launch {
            viewModel.uiState.collect { emissions.add(it) }
        }

        // Populate the timeline directly, independent of RaceEngine's own
        // state (the auto-subscription never fires - see setup above).
        val replayedState = RaceStateFactory.fiveCarField()
        timeline.record(replayedState)
        timeline.record(replayedState.copy(currentLap = replayedState.currentLap + 1))
        timeline.previous()

        assertTrue(viewModel.uiState.value.isReplayMode)
        assertEquals(replayedState, viewModel.uiState.value.raceState)

        // RaceEngine's own live state was never touched (still empty),
        // proving insights come from RaceEngine directly - not from
        // whatever the timeline currently happens to be pointed at.
        assertTrue(viewModel.uiState.value.insights.isEmpty())

        job.cancel()
    }
}
