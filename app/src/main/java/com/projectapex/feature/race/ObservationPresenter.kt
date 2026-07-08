package com.projectapex.feature.race

import com.projectapex.intelligence.detect.combat.CombatKeys
import com.projectapex.intelligence.detect.combat.CombatTypes
import com.projectapex.intelligence.rank.RacePulse
import com.projectapex.intelligence.rank.ScoredObservation
import java.util.Locale
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Turns a [RacePulse]'s structured observations into display-ready
 * [RaceInsightUi]s (APX-012). This is where user-facing wording lives — the
 * `:intelligence` module stays purely numeric (its metadata is the future
 * LLM-auditable fact payload), and this app-side mapper composes the English
 * sentences from those numbers.
 *
 * Deterministic: `Locale.US` formatting means the same observation always
 * renders the same string, on any device.
 */
class ObservationPresenter @Inject constructor() {

    fun present(pulse: RacePulse): List<RaceInsightUi> = pulse.topObservations.map(::present)

    private fun present(scored: ScoredObservation): RaceInsightUi {
        val obs = scored.observation
        val subjects = obs.subjectDrivers
        val meta = obs.metadata
        val primary = subjects.getOrElse(0) { "" }
        val secondary = subjects.getOrElse(1) { "" }

        val (headline, detail) = when (obs.type.value) {
            CombatTypes.BATTLE ->
                "$secondary and $primary are battling" to
                    "$primary is ${s1(meta[CombatKeys.GAP_S])}s behind $secondary"

            CombatTypes.DRS_ACTIVE ->
                "$primary is within DRS range of $secondary" to
                    "gap ${s1(meta[CombatKeys.GAP_S])}s"

            CombatTypes.DRS_IMMINENT ->
                "$primary projected to enter DRS in ${laps(meta[CombatKeys.ETA_LAPS])}" to
                    "closing on $secondary at ${rate(meta[CombatKeys.RATE_S_PER_LAP])}/lap"

            CombatTypes.GAP_CLOSING ->
                "$primary closing on $secondary" to
                    "taking ${rate(meta[CombatKeys.RATE_S_PER_LAP])}/lap, ${s1(meta[CombatKeys.GAP_S])}s behind"

            CombatTypes.GAP_INCREASING ->
                "$primary dropping away from $secondary" to
                    "losing ${rate(meta[CombatKeys.RATE_S_PER_LAP])}/lap"

            CombatTypes.LEADER_PRESSURE -> {
                val sustained = meta[CombatKeys.SUSTAINED] == 1.0
                val word = if (sustained) "sustained pressure" else "pressure"
                "$primary under $word" to
                    "$secondary ${s1(meta[CombatKeys.GAP_S])}s behind"
            }

            CombatTypes.FASTEST_PACE ->
                "$primary has the fastest race pace" to
                    "${rate(meta[CombatKeys.PACE_MARGIN_S])}/lap quicker, running P${whole(meta[CombatKeys.POSITION])}"

            CombatTypes.TYRE_CONCERN ->
                "$primary's tyres are ageing" to
                    "${whole(meta[CombatKeys.TYRE_AGE_LAPS])} laps old"

            CombatTypes.TYRE_CLIFF -> {
                val eta = meta[CombatKeys.ETA_LAPS] ?: 0.0
                if (eta <= 0.0) {
                    "$primary has hit the tyre cliff" to "pace dropping off sharply"
                } else {
                    "$primary approaching the tyre cliff" to "predicted in ${laps(eta)}"
                }
            }

            // Defensive: a future detector type not yet taught to the presenter
            // still renders something sensible rather than crashing.
            else -> obs.type.value to subjects.joinToString(", ")
        }

        return RaceInsightUi(
            id = obs.id.value,
            icon = iconFor(obs.type.value),
            headline = headline,
            detail = detail,
            severity = obs.severity,
        )
    }

    private fun iconFor(type: String): String = when (type) {
        CombatTypes.BATTLE -> "🔥"
        CombatTypes.DRS_ACTIVE, CombatTypes.DRS_IMMINENT -> "🎯"
        CombatTypes.GAP_CLOSING -> "📉"
        CombatTypes.GAP_INCREASING -> "📈"
        CombatTypes.LEADER_PRESSURE -> "⚠️"
        CombatTypes.FASTEST_PACE -> "⚡"
        CombatTypes.TYRE_CONCERN, CombatTypes.TYRE_CLIFF -> "🛞"
        else -> "•"
    }

    private fun s1(value: Double?): String = String.format(Locale.US, "%.1f", value ?: 0.0)
    private fun rate(value: Double?): String = "${s1(abs(value ?: 0.0))}s"
    private fun whole(value: Double?): String = (value ?: 0.0).roundToInt().toString()
    private fun laps(value: Double?): String {
        val n = (value ?: 0.0).roundToInt().coerceAtLeast(1)
        return if (n == 1) "1 lap" else "$n laps"
    }
}
