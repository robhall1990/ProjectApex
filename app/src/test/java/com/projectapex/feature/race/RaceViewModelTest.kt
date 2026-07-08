package com.projectapex.feature.race

import com.projectapex.core.model.SessionStatus
import com.projectapex.domain.model.CarState
import com.projectapex.domain.model.Driver
import com.projectapex.domain.model.RaceState
import com.projectapex.domain.model.TyreCompound
import com.projectapex.domain.race.RaceEngine
import com.projectapex.domain.race.RaceStateFactory
import com.projectapex.domain.timeline.RaceTimeline
import com.projectapex.intelligence.adapter.RacePulseEngine
import com.projectapex.intelligence.api.IntelligenceConfig
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
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class RaceViewModelTest {

    private val mainDispatcher = UnconfinedTestDispatcher()
    private val clock = Clock.fixed(Instant.parse("2026-07-08T14:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * A StandardTestDispatcher for the timeline and pulse engine means their
     * own auto-subscriptions to RaceEngine never run (the scheduler is never
     * advanced) — only the direct record()/process() calls below drive them.
     */
    private fun pulseEngine(engine: RaceEngine) =
        RacePulseEngine(engine, StandardTestDispatcher(), IntelligenceConfig(), clock)

    @Test
    fun `uiState reflects the timeline's race data and replay mode`() = runTest(mainDispatcher) {
        val engine = RaceEngine()
        val timeline = RaceTimeline(engine, StandardTestDispatcher())
        val viewModel = RaceViewModel(timeline, pulseEngine(engine), ObservationPresenter())
        val emissions = mutableListOf<RaceUiState>()

        val job = launch { viewModel.uiState.collect { emissions.add(it) } }

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
    fun `insights reflect the live pulse, independent of the replay position`() = runTest(mainDispatcher) {
        val engine = RaceEngine()
        val timeline = RaceTimeline(engine, StandardTestDispatcher())
        val pulse = pulseEngine(engine)
        val viewModel = RaceViewModel(timeline, pulse, ObservationPresenter())
        val emissions = mutableListOf<RaceUiState>()

        val job = launch { viewModel.uiState.collect { emissions.add(it) } }

        // The timeline is scrubbed to a replayed snapshot of the 5-car field
        // (gaps 1.5s apart — no battle).
        val replayed = RaceStateFactory.fiveCarField()
        timeline.record(replayed)
        timeline.record(replayed.copy(currentLap = replayed.currentLap + 1))
        timeline.previous()

        // Meanwhile the *live* pipeline is fed a two-car battle, independent of
        // whatever the timeline is pointed at.
        pulse.process(twoCarBattle())

        val state = viewModel.uiState.value
        assertTrue(state.isReplayMode)
        assertEquals(replayed, state.raceState)        // race data from the timeline
        // Intelligence from the live pulse, not the replayed snapshot.
        assertTrue(state.insights.any { it.headline.contains("VER") && it.headline.contains("NOR") })

        job.cancel()
    }

    private fun twoCarBattle(): RaceState = RaceState(
        sessionStatus = SessionStatus.LIVE,
        currentLap = 5,
        totalLaps = 58,
        cars = listOf(
            CarState(Driver("VER", "Max Verstappen", "Red Bull Racing", 1), 1, 5, 0.0, TyreCompound.MEDIUM, 5, false),
            CarState(Driver("NOR", "Lando Norris", "McLaren", 4), 2, 5, 0.8, TyreCompound.MEDIUM, 5, false),
        ),
        timestamp = 5_000L,
    )
}
