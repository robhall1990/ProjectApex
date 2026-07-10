package com.projectapex.intelligence.adapter

import com.projectapex.core.model.SessionStatus
import com.projectapex.domain.model.CarState
import com.projectapex.domain.model.Driver
import com.projectapex.domain.model.RaceState
import com.projectapex.domain.model.TyreCompound
import com.projectapex.domain.race.RaceEngine
import com.projectapex.feature.race.ObservationPresenter
import com.projectapex.intelligence.api.IntelligenceConfig
import com.projectapex.intelligence.detect.Severity
import com.projectapex.intelligence.detect.combat.CombatTypes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * Full app-side pipeline integration (APX-012):
 * RaceState → RaceStateAdapter → IngestPipeline → FeatureStore →
 * DetectorEngine → PrioritisationEngine → RacePulse, then presented for the UI.
 *
 * Drives [RacePulseEngine.process] directly with a StandardTestDispatcher (so
 * the background subscription stays inert) — deterministic given the fixed
 * clock.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RacePulseEngineTest {

    private val lapMillis = 90_000L
    private val finalLap = 6
    private val clock: Clock =
        Clock.fixed(Instant.ofEpochMilli(finalLap * lapMillis), ZoneOffset.UTC)

    private fun engine(): RacePulseEngine =
        RacePulseEngine(RaceEngine(), StandardTestDispatcher(), IntelligenceConfig(), clock)

    /** VER leads; NOR (P2) closes from [norGap] over the lap. */
    private fun state(lap: Int, norGap: Double): RaceState = RaceState(
        sessionStatus = SessionStatus.LIVE,
        currentLap = lap,
        totalLaps = 58,
        cars = listOf(
            CarState(Driver("VER", "Max Verstappen", "Red Bull Racing", 1), 1, lap, 0.0, TyreCompound.MEDIUM, lap, false),
            CarState(Driver("NOR", "Lando Norris", "McLaren", 4), 2, lap, norGap, TyreCompound.MEDIUM, lap, false),
        ),
        timestamp = lap * lapMillis,
    )

    @Test
    fun `a closing chase drives the pulse to a meaningful, driver-specific insight`() {
        val pulseEngine = engine()
        val presenter = ObservationPresenter()

        // NOR reels VER in from 2.4s to 0.8s over six laps.
        val gaps = listOf(2.4, 2.0, 1.6, 1.4, 1.1, 0.8)
        gaps.forEachIndexed { i, gap -> pulseEngine.process(state(lap = i + 1, norGap = gap)) }

        val pulse = pulseEngine.pulse.value
        assertTrue("pulse should have observations", pulse.topObservations.isNotEmpty())

        val top = pulse.topObservations.first().observation
        assertTrue("top insight is about the two cars in the fight",
            top.subjectDrivers.containsAll(listOf("VER", "NOR")))
        assertTrue("top insight is high-value",
            top.severity == Severity.CRITICAL || top.severity == Severity.HIGH)

        // Presented for the UI: a specific sentence, never the generic quiet line.
        val insights = presenter.present(pulse)
        val headline = insights.first().headline
        assertTrue(headline.contains("VER") || headline.contains("NOR"))
        assertFalse(headline.equals("No significant race activity", ignoreCase = true))
    }

    @Test
    fun `a predictive DRS insight appears while the car is still approaching`() {
        val pulseEngine = engine()

        // Feed up to the lap where NOR is at 1.1s — closing, not yet in DRS range.
        listOf(2.4, 2.0, 1.6, 1.4, 1.1).forEachIndexed { i, gap ->
            pulseEngine.process(state(lap = i + 1, norGap = gap))
        }

        val types = pulseEngine.pulse.value.topObservations.map { it.observation.type.value }
        assertTrue("a predictive DRS-imminent or leader-pressure insight should be present",
            types.any { it == CombatTypes.DRS_IMMINENT || it == CombatTypes.LEADER_PRESSURE })
        // The generic gap-closing line never leads the pulse.
        assertFalse(types.firstOrNull() == CombatTypes.GAP_CLOSING)
    }

    @Test
    fun `offline states leave the pulse quiet`() {
        val pulseEngine = engine()
        pulseEngine.process(RaceState.empty())
        assertTrue(pulseEngine.pulse.value.topObservations.isEmpty())
    }
}
