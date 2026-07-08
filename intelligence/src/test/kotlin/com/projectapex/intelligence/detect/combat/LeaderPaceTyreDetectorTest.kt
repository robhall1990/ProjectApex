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

class LeaderPaceTyreDetectorTest {

    private val config = IntelligenceConfig()
    private val fuel = config.fuelEffectPerLap
    private val totalLaps = 50

    // ── Leader pressure ──────────────────────────────────────────────────────

    private fun leaderChaserFrames(gaps: List<Double>, chaserTyreAge: Int? = null): List<TimingFrame> =
        gaps.mapIndexed { i, gap ->
            val lap = i + 1
            frame(
                sequence = lap.toLong(), lap = lap,
                cars = listOf(
                    car("VER", 1, lap, 0.0, lastLap = 90.0, tyreAge = 20),
                    car("NOR", 2, lap, gap, lastLap = 90.0, tyreAge = chaserTyreAge ?: 20),
                ),
            )
        }

    @Test
    fun `leader pressure fires when P2 is close and closing, and marks it sustained`() {
        val ctx = contextFrom(leaderChaserFrames(listOf(2.9, 2.5, 2.1, 1.8, 1.6)))
        val obs = LeaderPressureDetector(config).detect(ctx).single()

        assertEquals(CombatTypes.LEADER_PRESSURE, obs.type.value)
        assertEquals(Severity.HIGH, obs.severity)
        assertEquals(listOf("VER", "NOR"), obs.subjectDrivers)
        assertEquals(1.0, obs.metadata.getValue(CombatKeys.SUSTAINED), 1e-9)
    }

    @Test
    fun `leader pressure escalates to critical inside battle range while closing`() {
        val ctx = contextFrom(leaderChaserFrames(listOf(2.0, 1.6, 1.2, 1.0, 0.8)))
        val obs = LeaderPressureDetector(config).detect(ctx).single()
        assertEquals(Severity.CRITICAL, obs.severity)
    }

    @Test
    fun `leader pressure fires on a fresher-tyre threat even without closing`() {
        val ctx = contextFrom(leaderChaserFrames(listOf(2.5, 2.5, 2.5, 2.5, 2.5), chaserTyreAge = 4))
        val obs = LeaderPressureDetector(config).detect(ctx).single()
        assertEquals(0.0, obs.metadata.getValue(CombatKeys.SUSTAINED), 1e-9) // not closing
    }

    @Test
    fun `no leader pressure for a stable gap on equal tyres`() {
        val ctx = contextFrom(leaderChaserFrames(listOf(2.5, 2.5, 2.5, 2.5, 2.5)))
        assertTrue(LeaderPressureDetector(config).detect(ctx).isEmpty())
    }

    // ── Fastest pace ─────────────────────────────────────────────────────────

    @Test
    fun `fastest pace fires for a quicker non-leader`() {
        // NOR (P2) laps ~1.5s quicker than the leader VER, every lap.
        val frames = (1..6).map { lap ->
            frame(
                sequence = lap.toLong(), lap = lap, totalLaps = totalLaps,
                cars = listOf(
                    car("VER", 1, lap, 0.0, lastLap = 91.5),
                    car("NOR", 2, lap, 8.0, lastLap = 90.0),
                ),
            )
        }
        val obs = FastestPaceDetector(config).detect(contextFrom(frames)).single()

        assertEquals(CombatTypes.FASTEST_PACE, obs.type.value)
        assertEquals(listOf("NOR"), obs.subjectDrivers)
        assertEquals(2.0, obs.metadata.getValue(CombatKeys.POSITION), 1e-9)
        assertTrue(obs.metadata.getValue(CombatKeys.PACE_MARGIN_S) >= config.combat.fastestPaceMinAdvantagePerLap)
    }

    @Test
    fun `no fastest-pace insight when the leader is the fastest car`() {
        val frames = (1..6).map { lap ->
            frame(
                sequence = lap.toLong(), lap = lap, totalLaps = totalLaps,
                cars = listOf(
                    car("VER", 1, lap, 0.0, lastLap = 90.0),
                    car("NOR", 2, lap, 8.0, lastLap = 91.5),
                ),
            )
        }
        assertTrue(FastestPaceDetector(config).detect(contextFrom(frames)).isEmpty())
    }

    // ── Tyre ─────────────────────────────────────────────────────────────────

    @Test
    fun `plain tyre-age concern fires before a degradation fit exists`() {
        // Only two lap edges → no deg fit (needs four clean laps) → age path.
        val frames = listOf(22, 23, 24).mapIndexed { i, age ->
            val lap = i + 1
            frame(sequence = lap.toLong(), lap = lap, cars = listOf(car("VER", 1, lap, 0.0, lastLap = 90.0, tyreAge = age)))
        }
        val obs = TyreConcernDetector(config).detect(contextFrom(frames)).single()

        assertEquals(CombatTypes.TYRE_CONCERN, obs.type.value)
        assertEquals(Severity.MEDIUM, obs.severity)
        assertEquals(24.0, obs.metadata.getValue(CombatKeys.TYRE_AGE_LAPS), 1e-9)
    }

    @Test
    fun `tyre cliff is forecast as a prediction once degradation is steep enough`() {
        // Fuel-corrected time rises 0.10 s/lap on MEDIUM (prior rate 0.05, life
        // 28) → predicted cliff age ≈ 14; at age 11 that's ~3 laps away.
        val frames = (1..11).map { lap ->
            val raw = 90.0 + 0.10 * lap + fuel * (totalLaps - lap)
            frame(
                sequence = lap.toLong(), lap = lap, totalLaps = totalLaps,
                cars = listOf(car("VER", 1, lap, 0.0, lastLap = raw, tyreAge = lap)),
            )
        }
        val obs = TyreConcernDetector(config).detect(contextFrom(frames)).single()

        assertEquals(CombatTypes.TYRE_CLIFF, obs.type.value)
        assertEquals(Severity.HIGH, obs.severity)
        assertTrue(obs.timeHorizon is TimeHorizon.InLaps)
        assertTrue(obs.metadata.getValue(CombatKeys.ETA_LAPS) >= 1.0)
    }
}
