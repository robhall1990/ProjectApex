# Detection Framework & Prioritisation Engine (APX-011)

The modular detection platform inside the `:intelligence` module that future
race intelligence detectors plug into. It implements the *detect* and *rank*
layers of [RaceIntelligencePlatform.md](RaceIntelligencePlatform.md) on top of
APX-010's ingestion/features foundations. Pure Kotlin/JVM — no Android, no
Compose, no coroutines.

**Naming note.** The platform specification predates this ticket and uses
different names for the same roles: `Observation` implements the spec's
`InsightCandidate`, `PrioritisationEngine` the spec's `InsightRanker`, and
`RacePulse` is the first cut of the spec's `IntelligenceFrame`. The ticket
names are the code names; the spec describes the maths and intent.

---

## Architecture

```
FeatureStore (APX-010)
     ↓  read-only FeatureView
DetectorEngine ──────── register(Detector)  ← the ONLY integration step
     │  runs every detector, isolates failures, collects metrics
     ↓
List<Observation>       merged + deduplicated by ObservationId
     ↓
PrioritisationEngine    score → filter → diverse top-K   (stateful: novelty)
     ↓
RacePulse               headline · topObservations · generatedAt · confidenceSummary
     ↓
(future) presentation / LLM narration layer
```

Separation of concerns, deliberately hard-edged:

| Component | Owns | Never does |
|---|---|---|
| `Detector` | "is X happening / about to happen?" + its own confidence | ranking, wording, reading other detectors |
| `DetectorEngine` | registration, execution, merge, exact-id dedupe, failure isolation, metrics | scoring, any knowledge of concrete detectors |
| `PrioritisationEngine` | expiry/floor filtering, scoring, novelty, diversity, top-K | producing or mutating observations |
| `RacePulse` | the consumable answer to "what matters right now?" | any behaviour (pure data) |

## The Observation model

`Observation` is the internal intelligence object. `RaceInsight` (the app's
current UI type) becomes a presentation-layer rendering of observations in a
later ticket — nothing in this module references it.

| Field | Type | Notes |
|---|---|---|
| `id` | `ObservationId` | **Stable identity**: the same real situation ⇒ same id across passes (e.g. `battle:NOR:VER`, subjects sorted). Dedupe + novelty key off it. |
| `type` | `ObservationType` | Open string tag, *not* an enum — new detectors add types without touching framework files. |
| `severity` | `Severity` | `CRITICAL/HIGH/MEDIUM/LOW/INFO` — the detector's judgment of importance-class. |
| `confidence` | `Double` | [0, 1], validated at construction. Never cosmetic: scoring multiplies by it. |
| `timestamp` | `Instant` | When produced — recency scoring reads it. |
| `subjectDrivers` | `List<String>` | Who it's about, most-relevant first. Diversity selection reads it. |
| `metadata` | `Map<String, Double>` | Structured *numbers only* (gap seconds, ETA laps, probabilities). Becomes the LLM-auditable fact payload later; text has no place in scoring. |
| `timeHorizon` | `TimeHorizon` | `Ongoing` or `InLaps(k)` — happening now vs predicted. |
| `expiry` | `Expiry` | `Never`, `AtLap(n)`, or `AfterSeconds(s)` — when it stops being relevant if not re-emitted. |
| `sourceDetector` | `String` | Provenance — metrics and debugging. |

## Processing pipeline (one pass)

1. A `TimingFrame` has been ingested (APX-010 cheap path) — the FeatureStore
   is current.
2. `DetectorEngine.execute(frame, featureView)`:
   - builds one `DetectionContext(frame, features, previousObservations)`,
   - runs every registered detector **in registration order**, each inside
     its own try/catch,
   - a throwing detector contributes nothing, its error count increments, and
     the failure is reported in the `DetectionResult` — the others always run,
   - merges all output and collapses duplicate `ObservationId`s to the single
     most confident instance,
   - records per-detector metrics.
3. `PrioritisationEngine.prioritise(observations, atLap)`:
   - drops expired observations and those below the confidence floor,
   - scores the rest (below), updates novelty bookkeeping,
   - greedily selects a diverse top-K,
   - assembles the `RacePulse`.

## Detector lifecycle

```
        register(detector)          every pipeline pass                 (never)
(none) ───────────────────▶ REGISTERED ──────────────▶ detect(context) ──▶ unregister
                                 │                          │
                                 │ throws                   │ returns List<Observation>
                                 ▼                          ▼
                        error recorded, others          observations merged,
                        unaffected, detector            metrics updated
                        retried next pass
```

- Registration is **the only integration step** — `DetectorEngine` has no
  knowledge of any concrete detector (verified by tests: two anonymous stubs
  and a throwing stub exercise every engine behaviour).
- Duplicate detector ids fail loudly at registration (wiring bug, not data).
- A failing detector is retried every pass — transient data problems recover
  by themselves; persistent failures show up in `errorCount`.

### Per-detector metrics

`DetectorEngine.metrics()` returns, per detector: `executionCount`,
`errorCount`, `observationCount` (cumulative, pre-dedupe), `lastExecutionAt`,
`lastDurationNanos`, `totalDurationNanos`. Clock and nano-timer are injected,
so tests pin every value exactly.

## How to add a detector

1. Implement `Detector` in `detect/` (or a detector-family file):

```kotlin
class BattleDetector(private val config: IntelligenceConfig) : Detector {
    override val id = "battle-detector"

    override fun detect(context: DetectionContext): List<Observation> {
        val order = context.features.runningOrder
        return order.zipWithNext().mapNotNull { (ahead, behind) ->
            val gap = context.features.interval(ahead.driverId, behind.driverId)
                ?: return@mapNotNull null
            if (gap.value > 1.2) return@mapNotNull null
            val subjects = listOf(ahead.driverId, behind.driverId).sorted()
            Observation(
                id = ObservationId("battle:${subjects.joinToString(":")}"),
                type = ObservationType("battle"),
                severity = Severity.HIGH,
                confidence = ((1.2 - gap.value) / 0.8).coerceIn(0.0, 1.0),
                timestamp = context.frame.timestamp,
                subjectDrivers = subjects,
                metadata = mapOf("gap_s" to gap.value),
                timeHorizon = TimeHorizon.Ongoing,
                expiry = Expiry.AtLap(context.frame.lap + 2),
                sourceDetector = id,
            )
        }
    }
}
```

2. Register it: `detectorEngine.register(BattleDetector(config))`. Done — no
   engine change, no framework change.

Rules of the road:

- **Stable ids.** Sort subject driver ids into the observation id so
  `battle:NOR:VER` never coexists with `battle:VER:NOR`.
- **Pure functions.** Read only the `DetectionContext`; take constants from
  `IntelligenceConfig`; no clocks (use `frame.timestamp`), no I/O, no mutable
  state. Hysteresis/continuity reads `context.previousObservations`.
- **Empty list means "nothing to report".** Throwing is survivable but never
  part of the contract.
- **Honest confidence.** Derive it from data quality/margin (spec §11.2), and
  cap it where the data cannot prove the claim.

## Prioritisation strategy

Base score (all constants in `PrioritisationConfig`, wired into
`IntelligenceConfig.prioritisation`):

```
score = severityWeight(severity)          CRITICAL 1.0 … INFO 0.2
      · confidence ^ confidenceExponent   default exponent 1.0
      · urgency(timeHorizon)              Ongoing → 1;  InLaps(k) → exp(−k/τ), τ = 4 laps
      · recency(age)                      exp(−ageSeconds/τ), τ = 120 s
      · novelty(repeats)                  0.7^r; r resets on material change
```

Multiplicative on purpose: any zero factor kills an observation outright —
an unconfident, stale, or thoroughly repeated story cannot ride a high
severity into the pulse.

- **Material change** = severity moved, or confidence moved by ≥ 0.15. A
  battle that closes from 1.1 s to 0.4 s (confidence jump) is news again;
  the same battle re-detected verbatim decays 0.7× per pass.
- **Duplicate suppression** happens twice: exact-id dedupe (keep the most
  confident), then **diversity selection** — greedy top-K where, after each
  pick, remaining candidates are taxed `0.55^sharedSubjects × 0.65^sameType`.
  Three stories about the same fight cannot fill the whole pulse. Reported
  scores are the base scores; the taxes affect ordering only.
- **Expiry** and a confidence floor (0.05) filter before any scoring.
- Ties break by observation id — the whole pass is deterministic given the
  same inputs and clock (both engines take injectable `java.time.Clock`s).

`RacePulse.headline` is a deterministic placeholder rendering
(`"type: A vs B"`); real presentation text arrives with the
RaceInsight-rendering ticket, and LLM narration after that.

## Extension points

| To add | Touch |
|---|---|
| A new detector | one new class + one `register(...)` call |
| A new observation type | nothing — `ObservationType` is an open value class |
| Different scoring behaviour | `PrioritisationConfig` values (no code) |
| A different scoring *formula* | `PrioritisationEngine` only — detectors unaffected |
| Presentation of observations | a new layer consuming `RacePulse`; nothing here changes |

## Testing

20 JVM tests cover the ticket's required areas: registration (incl. duplicate
rejection), execution order and merging, failure isolation (throwing detector
never stops others; retried next pass), exact-id deduplication, prioritisation
(severity/confidence/horizon/recency/novelty each pinned to its closed-form
expected value, material-change reset, expiry, floor, diversity ordering,
configurability), per-detector metrics (pinned clock + fake nano-timer), and
an end-to-end pipeline test: scripted frames → IngestPipeline → stub detector
reading real FeatureView data → PrioritisationEngine → RacePulse.
