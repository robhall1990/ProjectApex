package com.projectapex.domain.intelligence

import com.projectapex.domain.model.CarState
import com.projectapex.domain.model.RaceState
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

private const val BATTLE_THRESHOLD_SECONDS = 1.5
private const val GAP_TREND_THRESHOLD_SECONDS = 0.3
private const val FASTEST_CAR_MIN_GAIN_SECONDS = 0.1
private const val TYRE_AGE_CONCERN_LAPS = 20

/**
 * Deterministic race analysis: no AI models, no external calls, just rules
 * over [RaceState]. The foundation a future AI explanation layer would sit
 * on top of, reading [RaceInsight]s rather than raw race state.
 *
 * Gap-closing/increasing and "fastest car" detection compare the current
 * state against the previous [analyse] call, so - unlike the rest of the
 * domain layer - this class is intentionally stateful: it remembers exactly
 * one previous [RaceState]. It assumes [analyse] is called with states in
 * chronological order; fed an out-of-order state (e.g. from scrubbing a
 * replay timeline backwards), gap-trend detection would compare against the
 * wrong "previous" moment. Not solved in v1 - see docs/Architecture.md.
 */
@Singleton
class RaceIntelligenceEngine @Inject constructor() {

    private var previousState: RaceState? = null

    fun analyse(state: RaceState): List<RaceInsight> {
        val previous = previousState
        previousState = state

        val insights = detectBattles(state) +
            detectGapTrends(state, previous) +
            detectFastestCar(state, previous) +
            detectTyreConcerns(state)

        return insights.sortedByDescending { it.priority.ordinal }
    }

    /** Cars within [BATTLE_THRESHOLD_SECONDS] of the car immediately ahead of them. */
    private fun detectBattles(state: RaceState): List<RaceInsight> =
        state.cars.sortedBy { it.position }
            .zipWithNext()
            .mapNotNull { (ahead, behind) ->
                val gap = abs(behind.gapToLeaderSeconds - ahead.gapToLeaderSeconds)
                if (gap > BATTLE_THRESHOLD_SECONDS) return@mapNotNull null

                RaceInsight(
                    id = "battle-${ahead.driver.id}-${behind.driver.id}-${state.timestamp}",
                    type = InsightType.BATTLE_DETECTED,
                    priority = InsightPriority.HIGH,
                    title = "${ahead.driver.id} and ${behind.driver.id} are battling",
                    description = "${behind.driver.id} is ${"%.1f".format(gap)}s behind ${ahead.driver.id}",
                    timestamp = state.timestamp
                )
            }

    /**
     * v1 tracks each car's gap to the *leader* (position 1), not the car
     * immediately ahead of it - "closing on VER" means closing on whoever
     * currently holds P1.
     */
    private fun detectGapTrends(state: RaceState, previous: RaceState?): List<RaceInsight> {
        if (previous == null) return emptyList()
        val leader = state.cars.firstOrNull { it.position == 1 } ?: return emptyList()
        val previousByDriver = previous.cars.associateBy { it.driver.id }

        return state.cars
            .filter { it.position != 1 }
            .mapNotNull { car ->
                val previousCar = previousByDriver[car.driver.id] ?: return@mapNotNull null
                val delta = previousCar.gapToLeaderSeconds - car.gapToLeaderSeconds

                when {
                    delta > GAP_TREND_THRESHOLD_SECONDS -> RaceInsight(
                        id = "gap-closing-${car.driver.id}-${state.timestamp}",
                        type = InsightType.GAP_CLOSING,
                        priority = InsightPriority.MEDIUM,
                        title = "${car.driver.id} is closing on ${leader.driver.id}",
                        description = "${car.driver.id} has taken ${"%.1f".format(delta)}s out of ${leader.driver.id}",
                        timestamp = state.timestamp
                    )
                    delta < -GAP_TREND_THRESHOLD_SECONDS -> RaceInsight(
                        id = "gap-increasing-${car.driver.id}-${state.timestamp}",
                        type = InsightType.GAP_INCREASING,
                        priority = InsightPriority.MEDIUM,
                        title = "${car.driver.id} is losing time to ${leader.driver.id}",
                        description = "${car.driver.id} has lost ${"%.1f".format(-delta)}s to ${leader.driver.id}",
                        timestamp = state.timestamp
                    )
                    else -> null
                }
            }
    }

    /** The non-leader car that gained the most time on the leader since the last state. */
    private fun detectFastestCar(state: RaceState, previous: RaceState?): List<RaceInsight> {
        if (previous == null) return emptyList()
        val previousByDriver = previous.cars.associateBy { it.driver.id }

        val (car, gain) = state.cars
            .filter { it.position != 1 }
            .mapNotNull { car ->
                val previousCar = previousByDriver[car.driver.id] ?: return@mapNotNull null
                car to (previousCar.gapToLeaderSeconds - car.gapToLeaderSeconds)
            }
            .maxByOrNull { (_, gain) -> gain }
            ?: return emptyList()

        if (gain <= FASTEST_CAR_MIN_GAIN_SECONDS) return emptyList()

        return listOf(
            RaceInsight(
                id = "fastest-car-${state.timestamp}",
                type = InsightType.FASTEST_CAR,
                priority = InsightPriority.LOW,
                title = "${car.driver.id} has the strongest pace",
                description = "${car.driver.id} gained ${"%.1f".format(gain)}s since the last update",
                timestamp = state.timestamp
            )
        )
    }

    private fun detectTyreConcerns(state: RaceState): List<RaceInsight> =
        state.cars
            .filter { it.tyreAgeLaps > TYRE_AGE_CONCERN_LAPS }
            .map { car ->
                RaceInsight(
                    id = "tyre-concern-${car.driver.id}-${state.timestamp}",
                    type = InsightType.TYRE_CONCERN,
                    priority = InsightPriority.MEDIUM,
                    title = "${car.driver.id}'s tyres are ageing",
                    description = "${car.driver.id} is on ${car.tyreAgeLaps}-lap-old ${car.tyreCompound} tyres",
                    timestamp = state.timestamp
                )
            }
}
