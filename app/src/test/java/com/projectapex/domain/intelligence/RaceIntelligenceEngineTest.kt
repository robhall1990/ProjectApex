package com.projectapex.domain.intelligence

import com.projectapex.core.model.SessionStatus
import com.projectapex.domain.model.CarState
import com.projectapex.domain.model.Driver
import com.projectapex.domain.model.RaceState
import com.projectapex.domain.model.TyreCompound
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RaceIntelligenceEngineTest {

    private fun car(
        id: String,
        position: Int,
        gap: Double,
        tyreAgeLaps: Int = 5
    ) = CarState(
        driver = Driver(id = id, name = id, team = "Team", number = position),
        position = position,
        lap = 10,
        gapToLeaderSeconds = gap,
        tyreCompound = TyreCompound.MEDIUM,
        tyreAgeLaps = tyreAgeLaps,
        isInPitLane = false
    )

    private fun raceState(cars: List<CarState>, timestamp: Long = 1_000L) = RaceState(
        sessionStatus = SessionStatus.LIVE,
        currentLap = 10,
        totalLaps = 58,
        cars = cars,
        timestamp = timestamp
    )

    @Test
    fun `battle is detected when adjacent cars are within 1_5 seconds`() {
        val engine = RaceIntelligenceEngine()
        val state = raceState(
            listOf(
                car("VER", position = 1, gap = 0.0),
                car("NOR", position = 2, gap = 1.0)
            )
        )

        val insights = engine.analyse(state)

        val battle = insights.singleOrNull { it.type == InsightType.BATTLE_DETECTED }
        assertNotNull(battle)
        assertEquals(InsightPriority.HIGH, battle!!.priority)
        assertTrue(battle.title.contains("VER") && battle.title.contains("NOR"))
    }

    @Test
    fun `gap closing is detected when a car reduces its gap to the leader`() {
        val engine = RaceIntelligenceEngine()
        val first = raceState(
            listOf(
                car("VER", position = 1, gap = 0.0),
                car("PIA", position = 2, gap = 5.0)
            ),
            timestamp = 1_000L
        )
        val second = raceState(
            listOf(
                car("VER", position = 1, gap = 0.0),
                car("PIA", position = 2, gap = 4.0)
            ),
            timestamp = 2_000L
        )

        engine.analyse(first)
        val insights = engine.analyse(second)

        val closing = insights.singleOrNull { it.type == InsightType.GAP_CLOSING }
        assertNotNull(closing)
        assertTrue(closing!!.title.contains("PIA") && closing.title.contains("VER"))
    }

    @Test
    fun `tyre concern is detected once tyre age exceeds the threshold`() {
        val engine = RaceIntelligenceEngine()
        val state = raceState(
            listOf(car("VER", position = 1, gap = 0.0, tyreAgeLaps = 25))
        )

        val insights = engine.analyse(state)

        val concern = insights.singleOrNull { it.type == InsightType.TYRE_CONCERN }
        assertNotNull(concern)
        assertTrue(concern!!.title.contains("VER"))
    }

    @Test
    fun `no insights are produced for an empty race state`() {
        val engine = RaceIntelligenceEngine()

        val insights = engine.analyse(RaceState.empty())

        assertTrue(insights.isEmpty())
    }
}
