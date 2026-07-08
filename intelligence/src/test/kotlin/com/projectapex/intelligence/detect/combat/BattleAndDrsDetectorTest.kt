package com.projectapex.intelligence.detect.combat

import com.projectapex.intelligence.api.IntelligenceConfig
import com.projectapex.intelligence.detect.Severity
import com.projectapex.intelligence.detect.TimeHorizon
import com.projectapex.intelligence.detect.combat.CombatSupport.context
import com.projectapex.intelligence.detect.combat.CombatSupport.contextFrom
import com.projectapex.intelligence.ingest.TimingFrame
import com.projectapex.intelligence.support.Fixtures.car
import com.projectapex.intelligence.support.Fixtures.frame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BattleAndDrsDetectorTest {

    private val config = IntelligenceConfig()

    /** Two cars where the second closes on the leader over successive laps. */
    private fun closingFrames(gaps: List<Double>): List<TimingFrame> =
        gaps.mapIndexed { i, gap ->
            val lap = i + 1
            frame(
                sequence = lap.toLong(), lap = lap,
                cars = listOf(
                    car("VER", 1, lap, 0.0, lastLap = 90.0),
                    car("NOR", 2, lap, gap, lastLap = 90.0),
                ),
            )
        }

    @Test
    fun `battle fires for a pair inside the battle gap and not beyond it`() {
        val inRange = BattleDetector(config).detect(
            context(frame(1, lap = 5, cars = listOf(car("VER", 1, 5, 0.0), car("NOR", 2, 5, 0.8))))
        )
        assertEquals(1, inRange.size)
        val battle = inRange.single()
        assertEquals(CombatTypes.BATTLE, battle.type.value)
        assertEquals(Severity.HIGH, battle.severity)
        assertEquals(listOf("NOR", "VER"), battle.subjectDrivers)
        assertTrue(battle.confidence > 0.6)

        val outOfRange = BattleDetector(config).detect(
            context(frame(1, lap = 5, cars = listOf(car("VER", 1, 5, 0.0), car("NOR", 2, 5, 2.0))))
        )
        assertTrue(outOfRange.isEmpty())
    }

    @Test
    fun `DRS active fires within one second and is capped in confidence`() {
        val active = DrsActiveDetector(config).detect(
            context(frame(1, lap = 5, cars = listOf(car("VER", 1, 5, 0.0), car("NOR", 2, 5, 0.6))))
        )
        assertEquals(CombatTypes.DRS_ACTIVE, active.single().type.value)
        assertTrue(active.single().confidence <= 0.8)

        val notYet = DrsActiveDetector(config).detect(
            context(frame(1, lap = 5, cars = listOf(car("VER", 1, 5, 0.0), car("NOR", 2, 5, 1.4))))
        )
        assertTrue(notYet.isEmpty())
    }

    @Test
    fun `DRS imminent projects a closing car into DRS range as a prediction`() {
        // NOR closes 2.4 → 1.3 over five laps; still outside DRS but converging.
        val ctx = contextFrom(closingFrames(listOf(2.4, 2.1, 1.7, 1.5, 1.3)))
        val imminent = DrsImminentDetector(config).detect(ctx)

        val obs = imminent.single()
        assertEquals(CombatTypes.DRS_IMMINENT, obs.type.value)
        assertTrue("should carry a lap ETA", obs.timeHorizon is TimeHorizon.InLaps)
        assertTrue(obs.metadata.getValue(CombatKeys.ETA_LAPS) >= 1.0)
        assertTrue(obs.metadata.getValue(CombatKeys.RATE_S_PER_LAP) < 0.0) // closing
    }

    @Test
    fun `DRS imminent does not fire for a car already in DRS range`() {
        val ctx = contextFrom(closingFrames(listOf(1.4, 1.2, 1.0, 0.9, 0.8)))
        assertTrue(DrsImminentDetector(config).detect(ctx).isEmpty())
    }

    @Test
    fun `DRS imminent does not fire for a stable gap`() {
        val ctx = contextFrom(closingFrames(listOf(2.0, 2.0, 2.0, 2.0, 2.0)))
        assertTrue(DrsImminentDetector(config).detect(ctx).isEmpty())
    }
}
