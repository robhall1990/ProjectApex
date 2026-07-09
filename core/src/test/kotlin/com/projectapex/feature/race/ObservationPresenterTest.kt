package com.projectapex.feature.race

import com.projectapex.intelligence.detect.Expiry
import com.projectapex.intelligence.detect.Observation
import com.projectapex.intelligence.detect.ObservationId
import com.projectapex.intelligence.detect.ObservationType
import com.projectapex.intelligence.detect.Severity
import com.projectapex.intelligence.detect.TimeHorizon
import com.projectapex.intelligence.detect.combat.CombatKeys
import com.projectapex.intelligence.detect.combat.CombatTypes
import com.projectapex.intelligence.rank.ConfidenceSummary
import com.projectapex.intelligence.rank.RacePulse
import com.projectapex.intelligence.rank.ScoredObservation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ObservationPresenterTest {

    private val presenter = ObservationPresenter()

    private fun pulseOf(vararg obs: Observation): RacePulse = RacePulse(
        headline = "",
        topObservations = obs.map { ScoredObservation(it, score = 1.0) },
        generatedAt = Instant.EPOCH,
        confidenceSummary = ConfidenceSummary.EMPTY,
    )

    private fun obs(
        type: String,
        subjects: List<String>,
        metadata: Map<String, Double>,
        severity: Severity = Severity.HIGH,
        horizon: TimeHorizon = TimeHorizon.Ongoing,
    ) = Observation(
        id = ObservationId("$type:${subjects.joinToString(":")}"),
        type = ObservationType(type),
        severity = severity,
        confidence = 0.8,
        timestamp = Instant.EPOCH,
        subjectDrivers = subjects,
        metadata = metadata,
        timeHorizon = horizon,
        expiry = Expiry.Never,
        sourceDetector = "test",
    )

    @Test
    fun `renders the DRS-imminent example sentence`() {
        val ui = presenter.present(
            pulseOf(
                obs(
                    CombatTypes.DRS_IMMINENT, listOf("NOR", "VER"),
                    mapOf(CombatKeys.ETA_LAPS to 2.0, CombatKeys.RATE_S_PER_LAP to -0.4, CombatKeys.GAP_S to 1.6),
                    horizon = TimeHorizon.InLaps(1.8),
                )
            )
        ).single()

        assertEquals("🎯", ui.icon)
        assertEquals("NOR projected to enter DRS in 2 laps", ui.headline)
        assertEquals("closing on VER at 0.4s/lap", ui.detail)
        assertEquals(Severity.HIGH, ui.severity)
    }

    @Test
    fun `renders the leader-pressure example, honouring the sustained flag`() {
        val sustained = presenter.present(
            pulseOf(
                obs(
                    CombatTypes.LEADER_PRESSURE, listOf("VER", "NOR"),
                    mapOf(CombatKeys.GAP_S to 1.6, CombatKeys.RATE_S_PER_LAP to -0.2, CombatKeys.SUSTAINED to 1.0),
                )
            )
        ).single()
        assertEquals("VER under sustained pressure", sustained.headline)

        val brief = presenter.present(
            pulseOf(
                obs(
                    CombatTypes.LEADER_PRESSURE, listOf("VER", "NOR"),
                    mapOf(CombatKeys.GAP_S to 2.4, CombatKeys.RATE_S_PER_LAP to 0.0, CombatKeys.SUSTAINED to 0.0),
                )
            )
        ).single()
        assertEquals("VER under pressure", brief.headline)
    }

    @Test
    fun `renders the tyre-cliff example and distinguishes an arrived cliff`() {
        val approaching = presenter.present(
            pulseOf(
                obs(
                    CombatTypes.TYRE_CLIFF, listOf("HAM"),
                    mapOf(CombatKeys.ETA_LAPS to 3.0, CombatKeys.TYRE_AGE_LAPS to 22.0),
                    horizon = TimeHorizon.InLaps(3.0),
                )
            )
        ).single()
        assertEquals("HAM approaching the tyre cliff", approaching.headline)
        assertEquals("predicted in 3 laps", approaching.detail)

        val hit = presenter.present(
            pulseOf(
                obs(CombatTypes.TYRE_CLIFF, listOf("HAM"), mapOf(CombatKeys.ETA_LAPS to 0.0))
            )
        ).single()
        assertEquals("HAM has hit the tyre cliff", hit.headline)
    }

    @Test
    fun `formatting is locale-independent`() {
        val previous = java.util.Locale.getDefault()
        try {
            // German locale uses a comma decimal separator; the presenter must not.
            java.util.Locale.setDefault(java.util.Locale.GERMANY)
            val ui = presenter.present(
                pulseOf(
                    obs(CombatTypes.BATTLE, listOf("NOR", "VER"), mapOf(CombatKeys.GAP_S to 0.8))
                )
            ).single()
            assertTrue(ui.detail.contains("0.8s"))
        } finally {
            java.util.Locale.setDefault(previous)
        }
    }

    @Test
    fun `an empty pulse presents nothing`() {
        assertTrue(presenter.present(pulseOf()).isEmpty())
    }
}
