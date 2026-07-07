package com.projectapex.domain.simulation

import com.projectapex.domain.DefaultDispatcher
import com.projectapex.domain.model.CarState
import com.projectapex.domain.model.RaceState
import com.projectapex.domain.race.RaceEngine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Generates believable, non-production race state updates so the UI can be
 * developed ahead of live timing integration. Sits above [RaceEngine] in the
 * dependency graph:
 *
 * ```
 * RaceSimulator -> RaceEngine -> UI
 * ```
 *
 * This is the only class that calls [RaceEngine.updateState] today. The UI
 * never depends on this class directly - a ViewModel (Settings' Developer
 * Mode controls) calls [start]/[stop] and observes [isRunning]; screens only
 * ever read race data via [RaceEngine].
 */
@Singleton
class RaceSimulator @Inject constructor(
    private val raceEngine: RaceEngine,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher
) {

    private val simulatorScope = CoroutineScope(SupervisorJob() + dispatcher)
    private var tickJob: Job? = null

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    fun start() {
        if (_isRunning.value) return

        _isRunning.value = true
        raceEngine.updateState(SyntheticGridFactory.initialState())

        tickJob = simulatorScope.launch {
            while (isActive) {
                delay(TICK_INTERVAL_MS)
                raceEngine.updateState(advance(raceEngine.state.value))
            }
        }
    }

    fun stop() {
        tickJob?.cancel()
        tickJob = null
        _isRunning.value = false
    }

    private fun advance(state: RaceState): RaceState {
        val elapsedTicks = state.timestamp / TICK_INTERVAL_MS
        val lapJustCompleted = elapsedTicks > 0 &&
            elapsedTicks % TICKS_PER_LAP == 0L &&
            state.currentLap < state.totalLaps

        var cars = state.cars.map { car ->
            car.copy(
                lap = if (lapJustCompleted) car.lap + 1 else car.lap,
                tyreAgeLaps = if (lapJustCompleted) car.tyreAgeLaps + 1 else car.tyreAgeLaps,
                gapToLeaderSeconds = nextGap(car)
            )
        }

        if (Random.nextFloat() < POSITION_CHANGE_CHANCE) {
            cars = maybeSwapAdjacentCars(cars)
        }

        return state.copy(
            currentLap = if (lapJustCompleted) state.currentLap + 1 else state.currentLap,
            cars = cars,
            timestamp = state.timestamp + TICK_INTERVAL_MS
        )
    }

    private fun nextGap(car: CarState): Double {
        if (car.position == 1) return 0.0
        val drift = (Random.nextDouble() - 0.5) * GAP_DRIFT_RANGE
        return (car.gapToLeaderSeconds + drift).coerceAtLeast(0.1)
    }

    private fun maybeSwapAdjacentCars(cars: List<CarState>): List<CarState> {
        if (cars.size < 2) return cars
        val sorted = cars.sortedBy { it.position }
        val swapIndex = Random.nextInt(sorted.size - 1)

        return sorted.mapIndexed { index, car ->
            when (index) {
                swapIndex -> car.copy(position = sorted[swapIndex + 1].position)
                swapIndex + 1 -> car.copy(position = sorted[swapIndex].position)
                else -> car
            }
        }
    }

    private companion object {
        const val TICK_INTERVAL_MS = 1_000L
        const val TICKS_PER_LAP = 10L
        const val GAP_DRIFT_RANGE = 0.6
        const val POSITION_CHANGE_CHANCE = 0.15f
    }
}
