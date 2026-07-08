package com.projectapex.intelligence.rank

import com.projectapex.intelligence.api.PrioritisationConfig
import com.projectapex.intelligence.detect.Observation
import com.projectapex.intelligence.detect.ObservationId
import com.projectapex.intelligence.detect.Severity
import com.projectapex.intelligence.detect.TimeHorizon
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow

/**
 * Turns raw observations into a [RacePulse] (APX-011 §4): filters expired and
 * hopeless ones, scores the rest, and selects a diverse top K.
 *
 *   score = severityWeight · confidence^γ · urgency · recency · novelty
 *
 * Multiplicative so any zero factor kills an observation outright; every
 * constant lives in [PrioritisationConfig]. Stateful only for novelty
 * bookkeeping (how often each id has already been shown) — the same
 * single-writer discipline as the rest of the pipeline.
 */
class PrioritisationEngine(
    private val config: PrioritisationConfig = PrioritisationConfig(),
    private val clock: Clock = Clock.systemUTC(),
) {

    private val noveltyState = mutableMapOf<ObservationId, NoveltyEntry>()

    private class NoveltyEntry(
        var repeats: Int,
        var lastSeverity: Severity,
        var lastConfidence: Double,
        var lastSeenAt: Instant,
    )

    fun prioritise(observations: List<Observation>, atLap: Int): RacePulse {
        val now = clock.instant()
        forgetStaleNovelty(now)

        val live = observations
            .asSequence()
            .filter { !it.isExpired(atLap, now) }
            .filter { it.confidence >= config.confidenceFloor }
            .groupBy { it.id }
            .map { (_, duplicates) -> duplicates.maxByOrNull { it.confidence }!! }

        val scored = live.map { obs -> ScoredObservation(obs, score(obs, now)) }
        live.forEach { recordSeen(it, now) }

        val top = selectDiverseTop(scored)
        return RacePulse(
            headline = headline(top),
            topObservations = top,
            generatedAt = now,
            confidenceSummary = summarise(top, live.size),
        )
    }

    // ── Scoring ─────────────────────────────────────────────────────────────

    private fun score(obs: Observation, now: Instant): Double {
        val severity = config.severityWeights.getValue(obs.severity)
        val confidence = obs.confidence.pow(config.confidenceExponent)
        val urgency = when (val h = obs.timeHorizon) {
            TimeHorizon.Ongoing -> 1.0
            is TimeHorizon.InLaps -> exp(-h.laps / config.horizonTauLaps)
        }
        val ageSeconds = Duration.between(obs.timestamp, now).toMillis() / 1000.0
        val recency = exp(-ageSeconds.coerceAtLeast(0.0) / config.recencyTauSeconds)
        val novelty = config.noveltyDecay.pow(effectiveRepeats(obs))

        return severity * confidence * urgency * recency * novelty
    }

    /**
     * How many times this id has already been scored — unless the observation
     * changed materially (severity moved, or confidence moved by at least
     * [PrioritisationConfig.materialConfidenceDelta]), in which case it is
     * news again and decays from scratch.
     */
    private fun effectiveRepeats(obs: Observation): Int {
        val entry = noveltyState[obs.id] ?: return 0
        val material = entry.lastSeverity != obs.severity ||
            abs(entry.lastConfidence - obs.confidence) >= config.materialConfidenceDelta
        return if (material) 0 else entry.repeats
    }

    private fun recordSeen(obs: Observation, now: Instant) {
        val repeats = effectiveRepeats(obs) + 1
        val entry = noveltyState.getOrPut(obs.id) {
            NoveltyEntry(0, obs.severity, obs.confidence, now)
        }
        entry.repeats = repeats
        entry.lastSeverity = obs.severity
        entry.lastConfidence = obs.confidence
        entry.lastSeenAt = now
    }

    private fun forgetStaleNovelty(now: Instant) {
        noveltyState.entries.removeIf { (_, entry) ->
            Duration.between(entry.lastSeenAt, now).toMillis() / 1000.0 > config.noveltyForgetSeconds
        }
    }

    // ── Top-K selection ─────────────────────────────────────────────────────

    /**
     * Greedy selection with overlap penalties (duplicate suppression beyond
     * exact-id dedupe): after each pick, remaining candidates sharing subject
     * drivers or the type are taxed, so three stories about the same fight
     * can't fill the whole pulse. Ties break by id — fully deterministic.
     */
    private fun selectDiverseTop(scored: List<ScoredObservation>): List<ScoredObservation> {
        val adjusted = scored
            .map { it to it.score }
            .sortedWith(compareByDescending<Pair<ScoredObservation, Double>> { it.second }
                .thenBy { it.first.observation.id.value })
            .toMutableList()

        val picked = mutableListOf<ScoredObservation>()
        while (picked.size < config.topK && adjusted.isNotEmpty()) {
            adjusted.sortWith(
                compareByDescending<Pair<ScoredObservation, Double>> { it.second }
                    .thenBy { it.first.observation.id.value }
            )
            val (winner, _) = adjusted.removeAt(0)
            picked += winner

            val pickedSubjects = winner.observation.subjectDrivers.toSet()
            for (i in adjusted.indices) {
                val (candidate, current) = adjusted[i]
                val sharedSubjects = candidate.observation.subjectDrivers.count { it in pickedSubjects }
                val sameType = candidate.observation.type == winner.observation.type
                var penalised = current * config.subjectOverlapPenalty.pow(sharedSubjects)
                if (sameType) penalised *= config.typeOverlapPenalty
                adjusted[i] = candidate to penalised
            }
        }
        return picked
    }

    // ── Pulse assembly ──────────────────────────────────────────────────────

    private fun headline(top: List<ScoredObservation>): String {
        val lead = top.firstOrNull()?.observation ?: return config.quietHeadline
        return if (lead.subjectDrivers.isEmpty()) {
            lead.type.value
        } else {
            "${lead.type.value}: ${lead.subjectDrivers.joinToString(" vs ")}"
        }
    }

    private fun summarise(top: List<ScoredObservation>, activeCount: Int): ConfidenceSummary {
        if (top.isEmpty()) return ConfidenceSummary.EMPTY.copy(activeObservationCount = activeCount)
        val confidences = top.map { it.observation.confidence }
        return ConfidenceSummary(
            mean = confidences.average(),
            min = confidences.min(),
            max = confidences.max(),
            activeObservationCount = activeCount,
        )
    }
}
