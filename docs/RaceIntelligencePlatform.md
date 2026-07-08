# Project Apex — Race Intelligence Platform

**Status:** Architecture specification (design sprint deliverable — no implementation yet)
**Scope:** The intelligence system behind the existing Android app. This document does not
redesign the app; the app's `RaceEngine`/`RaceState`/timeline/simulator remain the host
environment. Everything specified here is pure Kotlin with zero Android dependencies,
so the same engine can run on-device, on the JVM in tests, or server-side.

**Mission it serves:** *"Tell fans what is happening, why it is happening, and what is
likely to happen next."* The emphasis throughout is **prediction, not observation** —
an insight about the future outranks an equally confident insight about the present.

---

## Table of contents

1. [Design principles](#1-design-principles)
2. [Overall architecture](#2-overall-architecture)
3. [Domain model](#3-domain-model)
4. [Processing pipeline](#4-processing-pipeline)
5. [State ownership & concurrency model](#5-state-ownership--concurrency-model)
6. [Event system](#6-event-system)
7. [Package structure & module responsibilities](#7-package-structure--module-responsibilities)
8. [Core Kotlin interfaces](#8-core-kotlin-interfaces)
9. [The feature layer — shared models & math](#9-the-feature-layer--shared-models--math)
10. [Insight catalogue](#10-insight-catalogue)
11. [Insight ranking — the scoring system](#11-insight-ranking--the-scoring-system)
12. [Prediction engine](#12-prediction-engine)
13. [Where ML adds value (and where it does not)](#13-where-ml-adds-value-and-where-it-does-not)
14. [LLM narration layer](#14-llm-narration-layer)
15. [Testing strategy](#15-testing-strategy)
16. [Performance & scalability](#16-performance--scalability)
17. [Known weaknesses](#17-known-weaknesses)
18. [Future enhancements](#18-future-enhancements)

---

## 1. Design principles

1. **Deterministic core, generative shell.** Every number the user ever sees is computed
   by deterministic code. The LLM (§14) explains and summarises structured facts; it never
   calculates. Given the same input frame sequence, the engine produces bit-identical
   output — this is what makes replay testing, backtesting, and calibration possible.

2. **Snapshot in, facts out.** Modules receive immutable views and return immutable
   results. No detector or predictor mutates shared state. The only mutable state in the
   system lives in the `FeatureStore` (history) and the `InsightLifecycleManager`
   (emission bookkeeping), each with a single writer.

3. **Single-writer pipeline.** The whole engine is one logical actor on one dispatcher.
   F1 has 20 cars and ~1 Hz timing updates; parallelism would buy nothing and cost
   determinism. Concurrency exists only at the boundaries (input conflation, output
   `StateFlow`, LLM calls).

4. **Prediction over observation.** The scoring model (§11) explicitly weights urgency of
   *future* events. "VER will be in DRS range in 3 laps" beats "VER set a fast lap".

5. **Every insight is explainable.** An `Insight` carries the structured numbers that
   justify it (`numbers: Map<Metric, Quantity>`). This is simultaneously the audit trail,
   the LLM's raw material, and the test oracle.

6. **Degrade honestly.** Timing-only data cannot prove some things (fuel saving, driver
   error). Such detectors exist but carry hard confidence caps and say so. Confidence is
   a first-class output, never cosmetic.

7. **Live-state only.** As already established in `docs/Architecture.md`: intelligence
   consumes the live feed, never the replay timeline. Trend detection assumes
   chronological input; the replay position is a UI concern.

---

## 2. Overall architecture

Six layers. Data flows strictly downward; no layer reaches back up.

```
                        ┌──────────────────────────────────────────┐
   EXISTING APP         │  RaceEngine (StateFlow<RaceState>)        │
   (unchanged)          │  Future: LiveTimingClient (F1 feed)       │
                        └──────────────────┬───────────────────────┘
                                           │ adapt
 ┌─────────────────────────────────────────▼───────────────────────────────────────┐
 │ 1. INGESTION        TimingFrameAdapter · FrameValidator · FrameNormaliser        │
 │    "make the feed trustworthy"      out: TimingFrame (canonical, validated)      │
 ├─────────────────────────────────────────────────────────────────────────────────┤
 │ 2. EVENT DERIVATION EventDeriver (diffs frame N vs N-1)                          │
 │    "turn snapshots into edges"      out: EngineEvent (LapCompleted, PitEntry…)   │
 ├─────────────────────────────────────────────────────────────────────────────────┤
 │ 3. FEATURES         FeatureStore: LapBook · StintBook · GapMatrix · PitLog       │
 │    "remember and model"             PaceModel · DegModel · PitLossModel ·        │
 │                                     TrafficProjector      out: FeatureView       │
 ├─────────────────────────────────────────────────────────────────────────────────┤
 │ 4. INTELLIGENCE     26 InsightDetectors (stateless, pure)                        │
 │                     Predictors: GapForecaster · PitWindowPlanner ·               │
 │                     OvertakeModel · RaceForecaster · ScenarioEngine              │
 │                                     out: List<InsightCandidate>, Predictions     │
 ├─────────────────────────────────────────────────────────────────────────────────┤
 │ 5. RANKING          ScoringModel → DiversitySelector → LifecycleManager          │
 │    "what are the 3 most important things?"   out: IntelligenceFrame              │
 ├─────────────────────────────────────────────────────────────────────────────────┤
 │ 6. FACTS & NARRATION FactEncoder → FactStore → RaceNarrator (LLM) + NumericAudit │
 │    "explain, summarise, answer"     out: NarrationResult (validated prose)       │
 └─────────────────────────────────────────────────────────────────────────────────┘
                                           │
                        ┌──────────────────▼───────────────────────┐
   EXISTING APP         │  RaceViewModel consumes                   │
   (unchanged)          │  StateFlow<IntelligenceFrame>             │
                        └──────────────────────────────────────────┘
```

Component diagram with dependency directions:

```
 ┌────────────┐      ┌────────────┐      ┌─────────────┐      ┌──────────┐
 │  ingest    │─────▶│  events    │─────▶│  features   │◀─────│  config  │
 └────────────┘      └────────────┘      └──────┬──────┘      │ (track   │
                                                │             │ constants│
                              ┌─────────────────┼─────────┐   │ & priors)│
                              ▼                 ▼         │   └──────────┘
                        ┌──────────┐      ┌──────────┐    │
                        │  detect  │      │ predict  │────┘
                        └────┬─────┘      └────┬─────┘
                             │  candidates     │ predictions
                             ▼                 ▼
                        ┌─────────────────────────┐
                        │          rank           │
                        └────────────┬────────────┘
                                     │ IntelligenceFrame
                        ┌────────────▼────────────┐
                        │      facts / narrate    │──▶ LLM (external)
                        └─────────────────────────┘
```

The existing `domain/intelligence` package (APX-007's `RaceIntelligenceEngine` and its
five detectors) is the seed of layer 4 and migrates into this structure: each existing
detector becomes an `InsightDetector` implementation; the engine class becomes the
pipeline facade.

---

## 3. Domain model

### 3.1 Canonical input: `TimingFrame`

The platform defines its **own** input type rather than consuming `RaceState` directly,
for two reasons: (a) the live F1 timing feed carries far more than today's simulator
(sector times, intervals, track status, weather) and the domain model must have slots for
it now, even if adapters leave them null; (b) it decouples the intelligence module from
the app's domain package, keeping it a standalone pure-Kotlin module.

```kotlin
data class TimingFrame(
    val sequence: Long,                 // monotonic, gap-detectable
    val timestamp: Instant,
    val lap: Int,
    val totalLaps: Int,
    val trackStatus: TrackStatus,       // GREEN, YELLOW, SC, VSC, RED
    val weather: WeatherSample?,        // null until a real feed exists
    val cars: List<CarTiming>,
)

data class CarTiming(
    val driverId: String,               // "VER"
    val position: Int,
    val lapsCompleted: Int,
    val gapToLeader: Seconds?,          // null when lapped w/o data
    val interval: Seconds?,             // gap to car ahead; derivable if absent
    val lastLapTime: Seconds?,
    val sectorTimes: List<Seconds?>,    // empty until real feed
    val speedTrap: Kph?,
    val pitStatus: PitStatus,           // NONE, ENTRY, IN_PIT, OUT_LAP
    val tyre: TyreFit,                  // compound + ageLaps
    val retired: Boolean,
)

@JvmInline value class Seconds(val value: Double)
enum class TrackStatus { GREEN, YELLOW, SC, VSC, RED }
```

`RaceStateAdapter` maps today's `RaceState`/`CarState` into `TimingFrame` (interval
derived from consecutive `gapToLeaderSeconds`; `lastLapTime` synthesised by the adapter
from lap-edge timestamps until the simulator produces it; sector times empty).

### 3.2 Derived history (owned by `FeatureStore`)

```
FeatureStore
 ├── LapBook      per driver: List<LapRecord(lap, time, sectors, tyre, flags)>
 │                flags: IN_LAP, OUT_LAP, SC_LAP, VSC_LAP, YELLOW, OUTLIER
 ├── StintBook    per driver: List<Stint(compound, startLap, endLap?, laps, DegFit?)>
 ├── GapMatrix    per adjacent pair (by position): ring buffer of (lap, interval)
 │                plus per driver: ring buffer of (lap, gapToLeader)
 ├── PositionLog  per driver: List<(lap, position)> — only on change
 ├── PitLog       List<PitStop(driver, lapIn, lapOut, stationaryEst?, tyreBefore/After)>
 └── EventLog     bounded log of all EngineEvents (feeds Q&A retrieval, §14)
```

All buffers are bounded by race length (≤ ~80 laps × 20 cars) — total memory is a few MB
worst-case; no eviction policy needed within a session.

### 3.3 Output: `IntelligenceFrame`

```kotlin
data class IntelligenceFrame(
    val atLap: Int,
    val at: Instant,
    val spotlight: List<Insight>,        // the ranked top K (K=3)
    val all: List<Insight>,              // every ACTIVE insight, ranked
    val forecasts: Forecasts,            // structured predictions (§12)
    val health: EngineHealth,            // data quality, frame lag, degraded flags
)

data class Insight(
    val key: InsightKey,                 // stable identity (type + subjects + context)
    val type: InsightType,               // one of the 26 catalogue entries
    val subjects: List<String>,          // driver ids, ordered by relevance
    val headline: String,                // deterministic template, e.g. "Undercut window: NOR on HAM"
    val detail: String,                  // deterministic template with numbers
    val numbers: Map<Metric, Quantity>,  // e.g. GAP_S → 1.4, ETA_LAPS → 3, PROBABILITY → 0.62
    val confidence: Double,              // [0,1] — §11.2
    val score: Double,                   // ranking score at emission — §11
    val priorityBand: Priority,          // HIGH/MEDIUM/LOW, derived from score for UI
    val emittedAtLap: Int,
    val expiry: ExpiryPolicy,
    val state: InsightState,             // lifecycle — §6.3
)

data class Quantity(val value: Double, val unit: Unit) // SECONDS, LAPS, PROBABILITY, POSITIONS, POINTS
```

`headline`/`detail` are rendered by deterministic templates at emission time — the engine
is fully usable with **no LLM at all**; narration is an enhancement layer, not a
dependency.

---

## 4. Processing pipeline

### 4.1 Anatomy of a tick

Every accepted `TimingFrame` runs the **cheap path**; the **detector pass** runs on a
schedule (see 4.2). Sequence diagram:

```
 Feed          Pipeline(actor)      FeatureStore      Detectors/Predictors    Rank
  │ frame N        │                     │                    │                │
  ├───────────────▶│ validate+normalise  │                    │                │
  │                ├── diff vs N-1 ─────▶│                    │                │
  │                │   (EngineEvents)    │ update LapBook,    │                │
  │                │                     │ StintBook, gaps,   │                │
  │                │                     │ refit touched      │                │
  │                │                     │ models (lazy)      │                │
  │                │◀─ FeatureView ──────┤                    │                │
  │                │  [if detector pass due]                  │                │
  │                ├── detect(view) ─────────────────────────▶│                │
  │                │◀─ candidates ────────────────────────────┤                │
  │                ├── enrich w/ predictions ────────────────▶│                │
  │                ├── score, select, lifecycle ─────────────────────────────▶ │
  │                │◀─ IntelligenceFrame ─────────────────────────────────────┤
  │                ├─ publish StateFlow · encode FactFrame → FactStore         │
```

### 4.2 Refresh scheduling

Detectors declare a `RefreshPolicy`; the scheduler maps triggers to detector subsets so
per-frame cost stays flat:

| Policy | Fires | Typical users |
|---|---|---|
| `EVERY_FRAME` | each accepted frame | battle gap tracking, DRS eligibility |
| `ON_LAP_COMPLETE(driver)` | when that driver's lap counter increments | pace, deg, pit window (lap-granular math) |
| `ON_EVENT(types)` | when EventDeriver emits a matching event | SC opportunity (on `TrackStatusChanged`), pit delta (on `PitExit`) |
| `PERIODIC(nLaps)` | every n leader laps | race forecast, championship swing |

**Cheap path** (every frame, always): validation, event derivation, feature update —
O(n) with n = 20 cars.
**Detector pass**: bounded O(n²) worst case (pairwise projections) = 400 pair
evaluations; trivial at this scale (§16).

### 4.3 Backpressure & degradation

Input arrives on a `Channel(capacity = 64, onBufferOverflow = DROP_OLDEST)` **after**
event derivation has stamped each frame — the EventDeriver must see every frame (edges
are lost if frames are dropped before diffing), so derivation happens at intake on the
pipeline dispatcher, and only the *detector pass* is skippable under backlog. If the
channel lags more than `maxLagFrames` (default 5), the pipeline sets
`health.degraded = true`, runs the cheap path for the backlog, and executes a single
detector pass on the latest state. Correctness of history is preserved; only insight
latency suffers, and the frame says so.

---

## 5. State ownership & concurrency model

### 5.1 Ownership table

| State | Owner (single writer) | Readers | Mutability |
|---|---|---|---|
| Raw frame history (last 2 frames) | `EventDeriver` | — | internal |
| LapBook / StintBook / GapMatrix / PitLog / PositionLog | `FeatureStore` | detectors, predictors (via read-only `FeatureView`) | mutable inside store only |
| Fitted models (pace fits, deg fits) | `FeatureStore` (lazy memo, invalidated on new lap) | same | memoised |
| Insight lifecycle (active set, repeat counts, incumbent top-K, min-display timers) | `InsightLifecycleManager` | ranker | mutable inside manager only |
| Fact history | `FactStore` (append-only ring) | narration layer | append-only |
| Published output | `MutableStateFlow<IntelligenceFrame>` in the facade | app, tests | replaced atomically |
| Track constants & priors | `IntelligenceConfig` (immutable, injected) | everything | immutable |

### 5.2 Concurrency model

```kotlin
class IntelligencePipeline(
    config: IntelligenceConfig,
    clock: Clock,                                    // injectable — tests control time
    dispatcher: CoroutineDispatcher =
        Dispatchers.Default.limitedParallelism(1),   // THE single thread of truth
)
```

- **One logical thread** executes ingestion → ranking. No locks, no atomics, no
  data races by construction, and — critically — **deterministic replay**: the same
  frame sequence yields the same `IntelligenceFrame` sequence.
- Detectors/predictors are **pure functions** of `FeatureView` + `IntelligenceConfig`.
  They are trivially unit-testable and could be parallelised later without redesign
  (they share no mutable state) — but at n=20 this is deliberately not done.
- **LLM calls run outside** the pipeline (separate scope, `Dispatchers.IO`); narration
  consumes immutable `FactFrame`s and can never block or perturb the engine.
- The app-facing boundary is a `StateFlow` — the ViewModel's existing
  `WhileSubscribed(5_000)` pattern applies unchanged. When the app backgrounds, the
  pipeline keeps ingesting (history must not have holes) but the scheduler may downshift
  detector passes to `ON_LAP_COMPLETE` only, to save battery.

---

## 6. Event system

### 6.1 Derived events

Timing feeds deliver snapshots; most intelligence needs **edges**. `EventDeriver` diffs
frame N against N−1 and emits a sealed hierarchy:

```kotlin
sealed interface EngineEvent {
    val atLap: Int
    val at: Instant

    data class LapCompleted(val driverId: String, val lapTime: Seconds, ...) : EngineEvent
    data class PositionChange(val driverId: String, val from: Int, val to: Int,
                              val onTrack: Boolean /* false if via others pitting */) : EngineEvent
    data class PitEntry(val driverId: String, ...) : EngineEvent
    data class PitExit(val driverId: String, val tyre: TyreFit,
                       val rejoinPosition: Int, val rejoinInterval: Seconds?) : EngineEvent
    data class TrackStatusChanged(val from: TrackStatus, val to: TrackStatus, ...) : EngineEvent
    data class DriverRetired(val driverId: String, ...) : EngineEvent
    data class CarLapped(val driverId: String, val byDriverId: String, ...) : EngineEvent
    data class DataGap(val missedFrames: Int, ...) : EngineEvent   // feed hiccup — degrade confidence
}
```

Rules for robust derivation:

- **Lap edge**: `lapsCompleted` increment. If it jumps by >1 (feed gap), synthesise
  `DataGap` and mark the affected `LapRecord`s `OUTLIER` rather than guessing.
- **On-track vs cycle position change**: a position gain is `onTrack = true` only if the
  displaced car is neither `IN_PIT`/`OUT_LAP` nor retired. This single bit is what
  separates "overtake!" from "gained a place in the pit cycle" everywhere downstream.
- **Pit stationary time** is not in timing-only data; `PitStop.stationaryEst` is derived
  as `(out-lap arrival delta) − pitLaneLoss` and flagged as an estimate.

Events feed three consumers: the `FeatureStore` (accumulation), `ON_EVENT` detector
triggers, and the append-only `EventLog` (Q&A retrieval).

### 6.2 Why an internal event bus and not a public one

Events are an *implementation detail* of the pipeline (they exist to convert snapshots
into history). The public API surface is `StateFlow<IntelligenceFrame>` plus the
`FactStore` — not the event stream. Exposing events publicly would invite the UI to
re-derive intelligence and bypass ranking; the "three most important things" contract
only works if the engine is the sole author.

### 6.3 Insight lifecycle (state machine)

Owned by `InsightLifecycleManager`, keyed by `InsightKey`:

```
                 emit (candidate passes threshold)
     ┌─────────┐        ┌────────┐   material change    ┌─────────┐
     │ (none)  ├───────▶│ ACTIVE ├────────────────────▶│ UPDATED │──┐
     └─────────┘        └───┬────┘   (re-scored,        └────┬────┘  │ (collapses back
                            │         novelty reset)         │       │  to ACTIVE)
          predicate false   │                                ◀───────┘
          for M frames      ▼
          (hysteresis)  ┌─────────┐   TTL / superseded   ┌─────────┐
                        │EXPIRING ├─────────────────────▶│ EXPIRED │
                        └─────────┘                      └─────────┘
                            │ predicted event occurred
                            ▼
                        ┌──────────┐
                        │ CONSUMED │  e.g. predicted overtake happened →
                        └──────────┘  resolved with outcome, feeds calibration log
```

- **Material change** = subject set changes, or any headline `Quantity` moves by more
  than its per-metric epsilon (e.g. gap ±0.3 s, probability ±0.15, ETA ±1 lap). Material
  changes reset novelty decay (§11.3); immaterial ones update numbers in place silently.
- **CONSUMED with outcome** is the calibration hook: every expired *prediction* records
  (predicted, occurred?, error) into the calibration log consumed by §15.4.

---

## 7. Package structure & module responsibilities

New Gradle module `:intelligence` (pure Kotlin/JVM — enforced by the build: no Android
plugin, so an accidental `android.*` import cannot compile). The app depends on it;
it depends on nothing but stdlib + coroutines.

```
com.projectapex.intelligence
├── api/          IntelligenceEngine (facade), IntelligenceFrame, Insight, InsightType,
│                 IntelligenceConfig, EngineHealth
├── ingest/       TimingFrame + adapters (RaceStateAdapter now, LiveTimingAdapter later),
│                 FrameValidator, FrameNormaliser
├── events/       EngineEvent, EventDeriver, EventLog
├── features/     FeatureStore, FeatureView, LapBook, StintBook, GapMatrix, PitLog,
│                 PaceModel, DegModel, PitLossModel, TrafficProjector, FuelModel
├── detect/       InsightDetector SPI + one implementation file per family:
│                 CombatDetectors, StrategyDetectors, TyrePaceDetectors, MetaDetectors
├── predict/      Predictor SPI, GapForecaster, PitWindowPlanner, OvertakeModel,
│                 RaceForecaster, ScenarioEngine
├── rank/         ScoringModel, DiversitySelector, InsightLifecycleManager
├── facts/        FactFrame, FactEncoder, FactStore, FactQuery
├── narrate/      RaceNarrator SPI, PromptBuilder, NumericAuditor,
│                 TemplateNarrator (deterministic fallback), LlmNarrator
└── testing/      ScenarioDsl, ReplayHarness, CalibrationReport (published test fixtures)
```

| Module | Responsibility | Explicitly NOT responsible for |
|---|---|---|
| `ingest` | one canonical, validated input type; unit sanity (gaps ≥ 0, positions form a permutation); dedupe by `sequence` | interpretation of any kind |
| `events` | snapshot→edge conversion; data-gap flagging | business meaning of events |
| `features` | history, curve fitting, shared math; memoised model fits | deciding what's *interesting* |
| `detect` | "is X happening / about to happen?" — candidates with confidence | ranking, wording for users, prediction math (delegates to `predict`) |
| `predict` | forward models (§12); scenario evaluation | detection thresholds |
| `rank` | scoring, top-K selection, stability, lifecycle | generating candidates |
| `facts` | lossless structured export; retrieval queries | prose |
| `narrate` | prose from facts; numeric audit; graceful fallback | any computation |

---

## 8. Core Kotlin interfaces

```kotlin
// ── Facade ──────────────────────────────────────────────────────────────────
interface IntelligenceEngine {
    val frames: StateFlow<IntelligenceFrame>
    val health: StateFlow<EngineHealth>
    fun submit(frame: TimingFrame)                       // non-suspending; buffered
    suspend fun evaluate(scenario: Scenario): ScenarioResult   // "what if VER pits now?"
    fun close()
}

// ── Detection SPI ───────────────────────────────────────────────────────────
interface InsightDetector {
    val type: InsightType
    val refresh: RefreshPolicy
    /** Pure. Returns zero or more candidates; ranking decides what surfaces. */
    fun detect(view: FeatureView, config: IntelligenceConfig): List<InsightCandidate>
}

data class InsightCandidate(
    val key: InsightKey,
    val subjects: List<String>,
    val numbers: Map<Metric, Quantity>,
    val impact: Double,          // [0,1] — magnitude within category (§11.1)
    val urgency: Urgency,        // Ongoing | EventAt(lapEta: Double)
    val confidence: Double,      // [0,1] — §11.2 composite
    val expiry: ExpiryPolicy,
)

sealed interface RefreshPolicy {
    object EveryFrame : RefreshPolicy
    object OnLapComplete : RefreshPolicy
    data class OnEvent(val types: Set<KClass<out EngineEvent>>) : RefreshPolicy
    data class Periodic(val leaderLaps: Int) : RefreshPolicy
}

// ── Feature access (read-only view over the store) ─────────────────────────
interface FeatureView {
    val lap: Int; val totalLaps: Int; val trackStatus: TrackStatus
    val runningOrder: List<CarTiming>                     // by position
    fun laps(driverId: String): List<LapRecord>
    fun currentStint(driverId: String): Stint?
    fun stints(driverId: String): List<Stint>
    fun interval(aheadId: String, behindId: String): Seconds?
    fun intervalHistory(aheadId: String, behindId: String, lastNLaps: Int): List<GapSample>
    fun pace(driverId: String, window: Int = 5): PaceEstimate?     // fuel-corrected, clean laps
    fun degFit(driverId: String): DegFit?                          // current stint
    fun pitLoss(status: TrackStatus = GREEN): Seconds
    fun cumulativeTime(driverId: String): Seconds?                 // leader-relative race time
    fun events(sinceLap: Int): List<EngineEvent>
}

// ── Prediction SPI ──────────────────────────────────────────────────────────
interface GapForecaster {
    /** Closed-form gap projection with uncertainty (§12.1). */
    fun forecast(aheadId: String, behindId: String, horizonLaps: Int,
                 view: FeatureView): GapForecast?
}
data class GapForecast(
    val points: List<GapPoint>,           // (lapOffset, mean, sigma)
    val battleEtaLaps: Double?,           // first k with mean ≤ battleThreshold
    val battleProbabilityByHorizon: Double,
)

interface PitWindowPlanner {
    fun window(driverId: String, view: FeatureView): PitWindow?
    fun rejoinProjection(driverId: String, pitAtEndOfLap: Int, view: FeatureView): RejoinProjection
}

interface OvertakeModel {
    /** Per-lap pass probability and cumulative P(overtake ≤ k laps). §12.4 */
    fun probability(attackerId: String, defenderId: String, horizonLaps: Int,
                    view: FeatureView): OvertakeForecast?
}

interface ScenarioEngine {
    fun evaluate(scenario: Scenario, view: FeatureView): ScenarioResult
}
sealed interface Scenario {
    data class PitNow(val driverId: String) : Scenario
    data class PitAtLap(val driverId: String, val lap: Int) : Scenario
    data class StayOut(val driverId: String, val extraLaps: Int) : Scenario
}

// ── Ranking SPI ─────────────────────────────────────────────────────────────
interface InsightRanker {
    fun rank(candidates: List<InsightCandidate>,
             incumbents: List<Insight>,           // previous frame's spotlight (stability)
             view: FeatureView,
             config: IntelligenceConfig): RankedInsights
}

// ── Narration SPI (§14) ─────────────────────────────────────────────────────
interface RaceNarrator {
    suspend fun summarise(frame: FactFrame, style: NarrationStyle): NarrationResult
    suspend fun answer(question: String, retrieval: FactRetrieval): NarrationResult
}
sealed interface NarrationResult {
    data class Text(val prose: String, val citedFacts: List<FactId>) : NarrationResult
    data class Refused(val reason: String) : NarrationResult          // audit failed / no facts
    data class Fallback(val templated: String) : NarrationResult      // LLM unavailable
}
```

`IntelligenceConfig` carries every constant named in this document (thresholds, priors,
track constants, scoring weights) as data — nothing is hardcoded in detectors, so tuning
and per-track calibration never require code changes, and tests can pin exact configs.

---

## 9. The feature layer — shared models & math

All detectors and predictors build on five shared models. Centralising them means every
insight agrees on what "pace" means — and there is exactly one place to improve it.

### 9.1 Clean-lap filter & pace model

A `LapRecord` is **clean** iff none of: `IN_LAP`, `OUT_LAP`, `SC_LAP`, `VSC_LAP`,
`YELLOW`, `OUTLIER`. Outliers: `|t − median| > 3 × MAD` over the trailing window
(robust to single mistakes without discarding genuine deg trends).

**Fuel correction.** Cars get faster as fuel burns. Corrected time normalises every lap
to end-of-race fuel load:

```
t_corr(l) = t_raw(l) − f · (totalLaps − l)        f = config.fuelEffectPerLap  (default 0.055 s/lap,
                                                       per-track override)
```

**PaceEstimate** over window W (default 5 clean laps): ordinary least squares on
`(lapIndex, t_corr)` →

```
pace(driver) = (μ, β, σ, n)
  μ : intercept-adjusted mean corrected pace (s)
  β : trend slope (s/lap)  — deg + fuel-model error + driver phase
  σ : residual std dev (s) — noise level, feeds every confidence formula
  n : clean laps in window — feeds the sample-size factor q_n = n / (n + 3)
```

### 9.2 Tyre degradation model (`DegFit`)

Per stint, on fuel-corrected clean laps, fit the linear phase:

```
t_corr(a) = b + d·a          a = tyre age (laps),  b = base pace,  d = deg rate (s/lap)
```

- Fit by OLS once ≥ 4 clean laps; before that, fall back to compound prior
  `config.degPrior[compound]` with confidence floor.
- **Cliff detection (live):** residual `r(a) = t_corr(a) − (b + d·a)`. Declare
  `CLIFF` when 3 consecutive laps satisfy `r > max(2σ, 0.4 s)` *and* r is increasing.
  Two thresholds because early-stint σ can be tiny.
- **Cliff prediction:** compound prior life `μ_life[compound]` (per-track), scaled
  inversely by observed vs prior deg rate:

  ```
  a_cliff ≈ clamp( μ_life · (d_prior / max(d, d_min))^η ,  a_now ,  μ_life · 1.5 )
  η = 1.0 default; confidence from stint length and fit quality (§11.2).
  ```

### 9.3 Gap kinematics

For each adjacent pair, over the trailing `w` laps (default 4) of `GapMatrix` samples:
OLS slope `c = d(gap)/d(lap)` (negative = closing) with residual `σ_gap`. This is the
universal primitive: battles, DRS approach, leader pressure, compression all read it.
Closed-form *forecasting* (which also accounts for divergent deg) is §12.1.

### 9.4 Pit loss model & cumulative time

```
pitLoss(GREEN) = config.track.pitLaneLoss          (default 22.0 s)
pitLoss(VSC)   = pitLoss(GREEN) × 0.65             (field slowed ~35%)
pitLoss(SC)    = pitLoss(GREEN) × 0.45             (field bunched + slow laps)
```

**Cumulative race time**, the backbone of all rejoin math: with timing-only data,
`cum(i) = cum(leader) + gapToLeader(i)`. Absolute leader time is unknown but irrelevant —
every projection below only ever uses *differences* of `cum`, where it cancels.

### 9.5 Traffic projector

Projects the whole field forward `k` laps assuming each car laps at `pace(driver).μ`
(+ deg slope). For a car pitting at end of lap L:

```
cum'(D, L+1) = cum(D, L) + lap(D) + pitLoss(status)
rejoin position = 1 + |{ i ≠ D : cum'(i, L+1) < cum'(D, L+1) }|
rejoin interval ahead/behind = adjacent cum' differences
dirtyAir(D) = rejoin interval ahead < config.dirtyAirGap (default 2.0 s)
```

O(n) per query, O(n²) to evaluate everyone — trivial at n=20.

---

## 10. Insight catalogue

Every catalogue entry follows one template. Shared vocabulary:

- **Conf** uses the composite `C = q_data · q_fit · q_margin` from §11.2 unless stated;
  entries list only the *distinctive* factors and any hard cap.
- **Base impact** `I₀` is the category weight fed to §11.1; effective impact scales it
  by the position-at-stake weight `posW(p) = 1/(1 + 0.18·(p−1))` (lead ≈ 1.0, P10 ≈ 0.38).
- **Expiry** hysteresis default: predicate false for M = 3 consecutive detector passes.
- Complexities are per detector pass, n = cars (20), w = window laps (≤ 5).

### Family A — Combat (proximity & overtaking)

**A1. BATTLE** — a fight is on now
- **Algorithm:** adjacent pair with `interval ≤ 1.2 s` sustained ≥ 2 laps, and gap slope
  `c ≤ +0.1 s/lap` (closing or holding — a car falling away is not a battle).
- **Inputs:** GapMatrix, gap kinematics (§9.3).
- **Conf:** `q_margin = clamp((1.2 − gap)/0.8, 0, 1)`; sustain count as `q_data`.
- **Base impact:** `I₀ = 0.75`, × posW of the position at stake.
- **Expiry:** gap > 1.8 s for 3 passes (hysteresis band), or position change (→ CONSUMED,
  outcome = overtake), or either car pits.
- **Refresh:** EveryFrame. **Complexity:** O(n).

**A2. BATTLE_FORECAST** — likely future battle
- **Algorithm:** for each pair (not only adjacent — cars separated by a pitting rival
  count), `GapForecaster` (§12.1): emit when `battleEtaLaps ≤ 5` and
  `P(battle ≤ eta) ≥ 0.5`.
- **Inputs:** pace models, deg fits, gap history.
- **Conf:** the forecast probability itself × fit quality.
- **Base impact:** `I₀ = 0.7` × posW. (Prediction bonus comes via urgency, §11.)
- **Expiry:** CONSUMED when A1 fires for the pair; expire if ETA recedes beyond 8 laps.
- **Refresh:** OnLapComplete. **Complexity:** O(n) adjacent + O(n²) worst full-pairs.

**A3. DRS_ACTIVE** — car in DRS range now
- **Algorithm:** `interval ≤ 1.0 s` at lap line, lap ≥ drsEnabledLap (config, default 3),
  trackStatus GREEN. *Approximation note:* true eligibility is measured at detection
  points; with lap-line-only data confidence is capped 0.8 until sector data exists.
- **Inputs:** current intervals, track status. **Conf:** margin on the 1.0 s line, cap 0.8.
- **Base impact:** `I₀ = 0.6` × posW (it usually co-occurs with A1; diversity selection §11.4 dedupes).
- **Expiry:** interval > 1.0 s for 2 passes; suppressed entirely under SC/VSC/YELLOW.
- **Refresh:** EveryFrame. **Complexity:** O(n).

**A4. DRS_IMMINENT** — car entering DRS range soon
- **Algorithm:** not in range now; `GapForecaster` crossing of the 1.0 s line within 3
  laps with P ≥ 0.55.
- **Inputs/Conf:** as A2. **Base impact:** `I₀ = 0.55` × posW.
- **Expiry:** CONSUMED by A3; expire if slope flips sign for 2 passes.
- **Refresh:** OnLapComplete. **Complexity:** O(n).

**A5. OVERTAKE_PROB** — probability of overtake (enriches A1; standalone when P high)
- **Algorithm:** §12.4 logistic per-lap model, cumulative over horizon 3 laps. Emitted
  as its own insight when `P(≤3 laps) ≥ 0.6`.
- **Inputs:** pace delta, DRS availability, tyre age delta, track overtaking difficulty
  constant. **Conf:** calibration-table provenance (starts 0.6 cap until backtested).
- **Base impact:** `I₀ = 0.8` × posW. **Expiry:** CONSUMED on pass; recompute each lap.
- **Refresh:** OnLapComplete. **Complexity:** O(active battles).

**A6. POSITION_CHANGE_PROB** — probability of position change by any means
- **Algorithm:** union of on-track pass probability (A5) and strategy-driven swap
  probability from pit-cycle projection (§12.2): `P = 1 − (1−P_track)(1−P_strategy)`.
- **Inputs:** A5 + PitWindowPlanner rejoin projections. **Conf:** min of components.
- **Base impact:** `I₀ = 0.7` × posW. **Expiry:** window closes or swap occurs.
- **Refresh:** OnLapComplete. **Complexity:** O(n).

**A7. NEXT_BATTLE_SPOTLIGHT** — "watch this next" (broadcast-director feature)
- **Algorithm:** `argmin` over all A2/A4 candidates of `battleEtaLaps / audienceW(pair)`
  (§11.3 audience weight) — the soonest *interesting* convergence. Exactly one instance
  alive at a time (singleton key).
- **Base impact:** `I₀ = 0.65`. **Expiry:** superseded by a nearer candidate (with §11.5
  stability margin), or CONSUMED when the battle starts.
- **Refresh:** OnLapComplete. **Complexity:** O(candidates).

**A8. GAP_COMPRESSION** — the field (or a group) is bunching
- **Algorithm:** spread `S(t) = interval sum over top-10`; compression ratio
  `ρ = S(now)/S(now − 4 laps)`; emit when `ρ ≤ 0.7` under GREEN (SC bunching is
  suppressed as trivially expected — the SC insights own that story).
  Attribute cause when possible: leader pace drop (leader β > +0.15 s/lap) → "leader
  backing the pack up".
- **Conf:** margin below 0.7 and sample quality. **Base impact:** `I₀ = 0.6`.
- **Expiry:** ρ recovers > 0.85. **Refresh:** OnLapComplete. **Complexity:** O(n).

**A9. LEADER_PRESSURE** — race leader under threat
- **Algorithm:** composite predicate on P1/P2: `interval ≤ 3.0 s` AND any of
  (closing slope `c ≤ −0.10 s/lap`; P2 tyres ≥ 5 laps fresher; P2 has a live undercut
  window B1 on P1). Score magnitude `mag = (3.0 − gap)/3.0` blended with closing rate.
- **Base impact:** `I₀ = 0.9` (it *is* the lead, posW = 1). **Conf:** per contributing
  branch, take max. **Expiry:** gap > 4.5 s for 3 passes or leadership changes (CONSUMED).
- **Refresh:** EveryFrame. **Complexity:** O(1).

**A10. BLUE_FLAG** — lapping traffic situation
- **Algorithm:** for each lapped car B and each lapping car F approaching:
  `ttc = interval(F→B) / max(paceDelta, ε)` laps; emit when `ttc ≤ 2`.
  **Wildcard boost:** if B will interpose within an active battle pair (traffic
  projector places B between them inside the horizon), raise impact — backmarkers
  decide races.
- **Base impact:** `I₀ = 0.25`, or `0.6` with wildcard boost. **Conf:** ttc margin.
- **Expiry:** CONSUMED when `CarLapped` event fires; TTL 4 laps.
- **Refresh:** OnLapComplete. **Complexity:** O(lapped × lapping) ≤ O(n²).

### Family B — Strategy

**B1. UNDERCUT_WINDOW** — pitting now steals the position
- **Algorithm (§12.3):** attacker A behind target T, gap `g`. Undercut gain over the k
  laps until T responds (k default 2):

  `U(k) = Σ_{l=1..k} [ pace_T,old(l) − pace_A,new(l) ]` — where `pace_A,new` uses fresh-tyre
  offset for the plausible next compound minus out-lap penalty on l = 1.
  Emit when `U(k) > g + margin` (margin 0.5 s) AND rejoin projection (§9.5) is clean air
  AND A has tyres old enough that stopping is rational (`age ≥ minStint`).
- **Inputs:** deg fits both cars, compound offsets, pit loss, traffic projector.
- **Conf:** deg-fit quality both cars × clean-air certainty. **Base impact:** `I₀ = 0.85` × posW.
- **Expiry:** CONSUMED when A pits (hand off to B4 to grade it); expire when `U(k) ≤ g`
  or T pits first.
- **Refresh:** OnLapComplete. **Complexity:** O(n).

**B2. OVERCUT_WINDOW** — staying out longer wins the place
- **Algorithm:** mirror of B1: T has pitted (or is committed/boxed this lap); A stays out.
  Gain `O(k) = Σ [ pace_A,current(l) − pace_T,new(l) ]` for k = laps A can stay out before
  own cliff (§9.2). Emit when `O(k) > (g′ − pitLoss) + margin` where g′ is the projected
  deficit after A's eventual stop. Typically viable when T rejoined in traffic or new-tyre
  warm-up is slow (config per compound).
- **Conf:** capped 0.7 (depends on A's unobserved future in-lap). **Base impact:** `I₀ = 0.8` × posW.
- **Expiry:** CONSUMED at A's stop; TTL = A's cliff lap. **Refresh:** OnLapComplete. **Complexity:** O(n).

**B3. PIT_WINDOW** — a driver's stop window is open/closing
- **Algorithm:** window opens at `max(minStint, lap where U(k) vs nearest rival > 0)`;
  closes at predicted cliff (§9.2) or when rejoin projection turns dirty-air/behind-rival.
  Emit on open, and again (higher urgency) when ≤ 2 laps of window remain.
- **Inputs:** deg fit, traffic projector, rival windows. **Conf:** cliff-fit quality.
- **Base impact:** `I₀ = 0.7` × posW; `0.95` × posW for the "closing!" variant.
- **Expiry:** CONSUMED on pit; expire on window close. **Refresh:** OnLapComplete. **Complexity:** O(n²) (rival cross-products).

**B4. PIT_DELTA** — grade a completed stop (gain/loss)
- **Algorithm:** on `PitEntry`, snapshot reference deltas: gaps to the 3 nearest
  strategic rivals. On `PitExit` + 2 laps (tyres warm), compare:
  `Δ_r = (gap to r before) − (gap to r now)` corrected for r's own stops. Headline the
  largest |Δ|: "Undercut worked: NOR +1.8 s vs HAM".
- **Conf:** high (it's accounting, not prediction) — `q_data` only. **Base impact:** `I₀ = 0.75` × posW.
- **Expiry:** TTL 3 laps (news goes stale). **Refresh:** OnEvent(PitExit). **Complexity:** O(1) per stop.

**B5. SC_OPPORTUNITY** — cheap stop under Safety Car
- **Algorithm:** on `TrackStatusChanged(→SC)`: for every car, compare
  `pitLoss(SC)` against the positions-lost calculation via traffic projector with
  SC-frozen order semantics. Cars whose gap cushion behind > `pitLoss(SC)` get a
  **free stop** flag. Rank field-wide: emit one aggregate insight ("SC: free stop for
  VER, LEC; HAM would lose P2 to RUS") plus per-car facts.
- **Conf:** high on the arithmetic; 0.85 cap (SC bunching evolves while it's out).
- **Base impact:** `I₀ = 0.95` (strategy inflection point for the whole race).
- **Expiry:** hard — on `TrackStatusChanged(SC→)` or after the pit response wave.
- **Refresh:** OnEvent(TrackStatusChanged) + EveryFrame while SC. **Complexity:** O(n²).

**B6. VSC_IMPACT** — same, VSC economics
- **Algorithm:** as B5 with `pitLoss(VSC)` and no field bunching (gaps roughly frozen in
  time, not distance). Additional output: who *already benefited* (pitted just before/under it).
- **Base impact:** `I₀ = 0.9`. Everything else as B5.

**B7. TRAFFIC_AHEAD** — a driver is about to hit traffic
- **Algorithm:** traffic projector: cars C will catch within 4 laps a cluster of ≥ 2
  slower/lapped cars within 5 s of each other. Estimated cost: `Σ dirtyAirPenalty`
  (config 0.4 s/lap per blocked lap).
- **Conf:** projection horizon decay `0.9^lapsAhead`. **Base impact:** `I₀ = 0.5` × posW,
  boosted to 0.7 if C is in an active battle or open pit window (traffic changes those).
- **Expiry:** cluster passed or dispersed. **Refresh:** OnLapComplete. **Complexity:** O(n²).

**B8. PIT_EXIT_TRAFFIC** — the stop lands in traffic
- **Algorithm:** attached to every B1/B3 evaluation: if `rejoinProjection.dirtyAir` or
  rejoin behind a slower car, emit warning variant with the cost estimate
  (`blockedLaps × dirtyAirPenalty`); it is the *reason* a window closes early.
- **Conf/impact:** inherits from the parent window insight, impact +0.05.
- **Expiry:** with parent. **Refresh:** with parent. **Complexity:** O(1) on top of B1/B3.

**B9. STRATEGY_SPLIT** — teammates diverging
- **Algorithm:** teammates on different compounds now, OR projected optimal stop laps
  (B3) differ ≥ 8 laps, OR inferred stop counts differ (stint age + deg vs remaining
  distance makes n-stop infeasible for one). Emit on divergence detection with the
  inferred plans as numbers.
- **Conf:** plan inference is soft — cap 0.7. **Base impact:** `I₀ = 0.55` × posW(best teammate).
- **Expiry:** plans reconverge or race ends. **Refresh:** OnLapComplete. **Complexity:** O(teams).

### Family C — Tyre & pace

**C1. TYRE_DEG_HIGH** — abnormal degradation
- **Algorithm:** deg fit `d` exceeds compound prior: `d ≥ d_prior × 1.5` with fit
  n ≥ 4. Compare against **cohort** (same compound, similar age): if the whole cohort is
  high, reframe as track-wide ("deg is brutal today") via a singleton variant.
- **Conf:** fit R² and n. **Base impact:** `I₀ = 0.6` × posW.
- **Expiry:** stint ends. **Refresh:** OnLapComplete. **Complexity:** O(n).

**C2. TYRE_CLIFF_FORECAST** — cliff predicted / arriving
- **Algorithm:** §9.2 predicted `a_cliff` within 4 laps → forecast variant; live CLIFF
  state → "cliff now" variant (urgency 1, impact +0.1).
- **Conf:** stint length, residual quality; forecast variant × horizon decay.
- **Base impact:** `I₀ = 0.8` × posW. **Expiry:** CONSUMED on pit; falsified if 3 laps
  past predicted cliff with flat residuals (log for calibration!).
- **Refresh:** OnLapComplete. **Complexity:** O(n).

**C3. PACE_LEADER** — fastest race pace (not fastest lap)
- **Algorithm:** rank fuel-corrected clean pace `μ` over trailing 5 laps; emit when the
  leader-of-pace changes, or when pace leader ≠ race leader by ≥ 0.3 s/lap (the
  interesting case: "HAM is the fastest car on track right now, P4").
- **Conf:** σ-separation between P1 and P2 of pace: `q = Φ((μ₂−μ₁)/√(σ₁²+σ₂²))`.
- **Base impact:** `I₀ = 0.5`; 0.7 for the pace≠position variant. **Expiry:** superseded.
- **Refresh:** OnLapComplete. **Complexity:** O(n).

**C4. PACE_ANOMALY** — unexpected pace change
- **Algorithm:** CUSUM change-point detection on fuel-corrected residuals vs the
  driver's own stint baseline: `S⁺ = max(0, S⁺ + (x − μ − 0.5σ))`, alarm at `S⁺ > 4σ`
  (slow-down) and symmetric `S⁻` (speed-up). Exclude laps already explained by traffic
  (B7 active on them) or deg (within fit band) — *unexplained* residual is the trigger.
- **Conf:** CUSUM excess margin; cap 0.75 (cause unknown by construction).
- **Base impact:** `I₀ = 0.65` × posW. **Expiry:** new stint or baseline re-established.
- **Refresh:** OnLapComplete. **Complexity:** O(n).

**C5. FUEL_SAVING** — inferred lift-and-coast *(weakest detector — honest low confidence)*
- **Algorithm:** sustained mild slow-down (0.3–0.8 s) with **low variance** (deliberate,
  controlled), cohort not slowing (rules out track/deg), gaps ahead & behind stable
  (rules out defending/holding), mid-stint. All four must hold for ≥ 3 laps.
- **Conf:** **hard cap 0.5** — timing-only data cannot verify fuel state; the insight
  says "consistent with fuel saving". Sector data would raise the cap (slow in straights
  ≠ slow in corners); telemetry would remove it.
- **Base impact:** `I₀ = 0.4` × posW. **Expiry:** pace recovers 2 laps.
- **Refresh:** OnLapComplete. **Complexity:** O(n).

**C6. RECOVERY_DRIVE** — driver recovering from a mistake
- **Algorithm:** trigger event: on-track position loss ≥ 2 in one lap without pit, or a
  single-lap outlier ≥ 4 s (spin/off proxy). Then track recovery: pace ≥ personal
  baseline − 0.1 s AND net positions regained ≥ 1. Insight narrates progress
  ("NOR: P9→P6 since the lap-12 off, 3 to go to recover P4").
- **Conf:** trigger inference cap 0.7 (we infer the mistake). **Base impact:** `I₀ = 0.6` × posW(original position).
- **Expiry:** original position regained (CONSUMED, satisfying) or pace fades 3 laps.
- **Refresh:** OnLapComplete. **Complexity:** O(n).

### Family D — Meta

**D1. CHAMPIONSHIP_SWING** — title fight implications
- **Algorithm:** input: season standings (config/feed). Provisional points from the live
  classification (+ fastest-lap point if applicable). Emit when the provisional
  championship **order flips**, or a contender's margin changes ≥ 7 points vs pre-race,
  or a live battle (A1)/window (B1) *would* cause either — that last coupling is the
  gold insight: "this battle is for the championship lead".
- **Conf:** classification certainty (drops while pit cycles are unresolved — gate on
  no open windows among contenders, else ×0.7). **Base impact:** `I₀ = 0.95`.
- **Expiry:** re-evaluated per lap; superseded in place.
- **Refresh:** Periodic(1) + OnEvent(PositionChange among contenders). **Complexity:** O(n).

### Catalogue summary table

| ID | Insight | Refresh | Complexity | I₀ | Conf cap |
|----|---------|---------|-----------|-----|----------|
| A1 | Battle | frame | O(n) | 0.75 | — |
| A2 | Battle forecast | lap | O(n²) | 0.70 | — |
| A3 | DRS active | frame | O(n) | 0.60 | 0.80 |
| A4 | DRS imminent | lap | O(n) | 0.55 | — |
| A5 | Overtake probability | lap | O(battles) | 0.80 | 0.60* |
| A6 | Position-change probability | lap | O(n) | 0.70 | — |
| A7 | Next battle spotlight | lap | O(cand.) | 0.65 | — |
| A8 | Gap compression | lap | O(n) | 0.60 | — |
| A9 | Leader pressure | frame | O(1) | 0.90 | — |
| A10 | Blue flag | lap | O(n²) | 0.25/0.60 | — |
| B1 | Undercut window | lap | O(n) | 0.85 | — |
| B2 | Overcut window | lap | O(n) | 0.80 | 0.70 |
| B3 | Pit window | lap | O(n²) | 0.70/0.95 | — |
| B4 | Pit delta | event | O(1) | 0.75 | — |
| B5 | SC opportunity | event+frame | O(n²) | 0.95 | 0.85 |
| B6 | VSC impact | event+frame | O(n²) | 0.90 | 0.85 |
| B7 | Traffic ahead | lap | O(n²) | 0.50/0.70 | — |
| B8 | Pit exit traffic | with parent | O(1) | +0.05 | — |
| B9 | Strategy split | lap | O(teams) | 0.55 | 0.70 |
| C1 | Tyre deg high | lap | O(n) | 0.60 | — |
| C2 | Tyre cliff forecast | lap | O(n) | 0.80 | — |
| C3 | Pace leader | lap | O(n) | 0.50/0.70 | — |
| C4 | Pace anomaly | lap | O(n) | 0.65 | 0.75 |
| C5 | Fuel saving | lap | O(n) | 0.40 | **0.50** |
| C6 | Recovery drive | lap | O(n) | 0.60 | 0.70 |
| D1 | Championship swing | lap/event | O(n) | 0.95 | — |

\* until backtest calibration raises it (§15.4).

---

## 11. Insight ranking — the scoring system

The contract: at any moment, answer *"what are the three most important things happening
in the race?"* — mathematically, stably, and with prediction favoured.

### 11.1 The score

For candidate *i* at time *t*:

```
S(i) = I(i)^α · U(i)^β · C(i)^γ · N(i) · A(i)
```

Multiplicative on purpose: a zero in any factor (no confidence, fully stale, irrelevant)
must kill the insight outright, which additive models fail to guarantee. Exponents
`α, β, γ` (defaults 1.0, 1.2, 0.8) tune the shape without touching the factors —
`β > 1` is the explicit **prediction bias**: urgency differences are amplified.

**Impact** `I ∈ [0,1]`: `I = I₀(type) · posW(positionAtStake) · mag`, where `I₀` comes
from the catalogue table and `mag ∈ [0.5, 1]` scales within-type magnitude (e.g. a 0.2 s
battle gap vs a 1.1 s one; a 20-point championship swing vs a 7-point one). Each detector
defines its own `mag` normalisation in the catalogue's terms.

**Urgency** `U ∈ (0,1]`:

```
U(Ongoing)        = 1
U(EventAt(eta))   = exp(−eta / τ)        τ = 4 laps (config)
```

A prediction 2 laps out scores `e^(−0.5) ≈ 0.61`; combined with `β = 1.2` and the fact
that observations of *past* events get TTL-decayed novelty instead of `U = 1`, near-term
predictions systematically outrank recent news — the design's stated bias.

**Confidence** `C ∈ [0,1]` — §11.2.
**Novelty** `N ∈ (0,1]` — §11.3.
**Audience** `A ∈ (0,1]` — §11.3.

### 11.2 Confidence, standardised

Every detector composes the same three terms (catalogue entries state the distinctive
ones):

```
C = cap(type) · q_data · q_fit · q_margin

q_data   = Π over each required series of  n/(n + n₀)        n₀ = 3 (half-confidence at 3 samples)
q_fit    = model fit quality: R² for regressions, Φ-separation for rankings, 1 for pure arithmetic
q_margin = clamp((x − θ)/(θ_sat − θ), 0, 1)                  how decisively past the threshold
```

`cap(type)` encodes epistemic honesty (C5 fuel saving = 0.5, A5 = 0.6 until calibrated).
Confidence is shown to the ranking *and* carried on the insight for the UI/LLM — it is
never folded invisibly into the score alone.

### 11.3 Novelty and audience

**Novelty** fights repetition:

```
N = ρ^r          ρ = 0.7,  r = re-emission count for this InsightKey
```

`r` resets to 0 on **material change** (§6.3) — a battle that closes from 1.1 s to 0.4 s
is news again. Immaterial re-detections update numbers silently without re-emission.

**Audience relevance** encodes "who the story is about":

```
A(insight) = max over subjects s of  w(s)
w(s) = normalise( posW(position(s)) + champW(s) + focus(s) )

champW(s) = 0.3 if s is within 25 pts of the championship lead, else 0
focus(s)  = user-selected favourite-driver boost (0.2), default 0
```

`focus` is the personalisation hook — it is a *ranking input*, deliberately not a filter,
so a user's favourite midfielder rises without hiding the lead battle.

### 11.4 Top-K selection with diversity

Greedy selection with overlap penalties (K = 3):

```
1. pick  j = argmax S
2. for every remaining i:
     S(i) ← S(i) · λ_drv^|subjects(i) ∩ picked_subjects| · λ_cat^[family(i) = family(j)]
     λ_drv = 0.55, λ_cat = 0.65
3. repeat until K picked
```

This is why A1/A3/A5 (battle + DRS + overtake-prob about the same pair) don't occupy all
three slots: the second and third stories about the same cars are progressively taxed.
Hard rule on top: never two insights with identical `(type, subjects)`.

### 11.5 Stability (anti-flapping)

UI slots must not oscillate. Two mechanisms in `InsightLifecycleManager`:

- **Challenger margin:** a non-incumbent displaces an incumbent spotlight slot only if
  `S_challenger > h · S_incumbent`, h = 1.25.
- **Minimum display:** an incumbent holds its slot ≥ 10 s (wall clock) unless it expires
  or is CONSUMED — event resolution always interrupts.

Both constants are config; both are disabled in backtest mode where raw ranking is under
measurement.

---

## 12. Prediction engine

All deterministic. Uncertainty is carried explicitly (σ, probabilities) rather than
hidden. Shared notation: for driver X, pace model gives base `b_X`, deg rate `d_X`,
tyre age `a_X`, residual `σ_X` (§9.1–9.2).

### 12.1 Future gaps (closed form)

Lap time k laps ahead: `t_X(k) = b_X + d_X · (a_X + k)`. Gap between A (ahead) and B
(behind) after k laps:

```
ĝ(k) = g₀ + Σ_{l=1..k} [ t_B(l) − t_A(l) ]
     = g₀ + k(b_B − b_A) + (d_B − d_A)·k(k+1)/2 + k(d_B·a_B − d_A·a_A)
```

Linear term = raw pace delta; quadratic term = **divergent degradation** — this is what
makes "NOR catches HAM in 6 laps" bend realistically as tyres age at different rates.

Uncertainty (independent lap noise): `σ_g(k) = √k · √(σ_A² + σ_B²)`.

**Battle ETA:** smallest k with `ĝ(k) ≤ θ_battle` (1.0 s).
**Battle probability by horizon k:** `P = Φ( (θ_battle − ĝ(k)) / σ_g(k) )`.

Refits on every lap; O(1) per pair.

### 12.2 Pit stop windows & rejoin

Given `cum(i)` (§9.4) and the traffic projector (§9.5):

```
window_open(D)  = max( minStint,  first lap where U(k) > 0 vs nearest rival )     (§12.3)
window_close(D) = min( a_cliff(D) mapped to race lap,  last lap with clean-air rejoin )
optimal_lap(D)  = argmax over L in window of  netPosition(L) then −trafficCost(L)
```

`netPosition(L)` and `trafficCost(L)` come from a single projector sweep per candidate
lap: O(n) each, O(n·|window|) per driver, ≤ 20·10·20 = 4 000 ops field-wide. Emitted as
`PitWindow(open, close, optimal, rejoinProjection)` — consumed by B1/B3/B8 and the
scenario engine.

### 12.3 Undercut / overcut deltas

Undercut gain of A over target T, response delay k (default 2):

```
U(k) = Σ_{l=1..k} [ (b_T + d_T(a_T + l))  −  (b_A,new + d_new·l + outLap·[l=1]) ]
b_A,new = b_A − freshOffset(currentCompound → newCompound) + warmup(newCompound)·[l=1]
```

Config per compound pair: `freshOffset` (e.g. used-M → new-S ≈ 0.6 s), `outLap` ≈ 2.5 s,
`warmup` (hards warm slowly → overcut-friendly). Overcut is the same sum with roles
reversed and A's staying-out laps capped by `a_cliff(A)`. Both are pure arithmetic over
already-fitted models: O(1) per pair.

### 12.4 Overtake probability

Per-lap pass probability while in battle (gap ≤ 1.2 s):

```
p = σ( θ₀ + θ₁·Δpace_eff + θ₂·DRS + θ₃·Δtyre + θ₄·(1/gap) − θ₅·trackDifficulty )
P(pass within k) = 1 − Π_{l=1..k} (1 − p_l)        (p_l re-evaluated as ĝ evolves)
```

- `Δpace_eff` = fuel-corrected pace delta net of dirty-air penalty (config 0.3 s inside 1 s).
- `trackDifficulty` ∈ [0,1] per circuit (Monaco ≈ 0.95, Spa ≈ 0.25) — the single most
  important constant in the model.
- θ coefficients start as a hand-calibrated table (senior-engineer priors), then get
  fitted from the calibration log (§15.4). The functional form stays fixed and
  explainable either way.

### 12.5 Tyre life

§9.2's `a_cliff` prediction, restated as the general principle: **prior × observed-rate
scaling**. Compound/track priors are the calibration surface; live fits bend the prior.
Confidence grows with stint length; the falsification path (cliff didn't happen) is
logged, making priors improvable from data without any architectural change.

### 12.6 Traffic after a pit stop

§9.5 projector evaluated at the candidate pit lap: rejoin position, interval to the car
ahead, `blockedLaps` (laps until the blocking car is passed per §12.4 or pits per B3),
`trafficCost = blockedLaps × dirtyAirPenalty`. This cost term feeds back into window
optimisation (§12.2) — traffic is priced, not just flagged.

### 12.7 Race evolution (deterministic forward simulation)

One sweep, no Monte Carlo, O(n · lapsRemaining):

```
for each remaining lap l:
    advance every car by its modelled lap time (pace + deg + dirty-air if within 1 s)
    if l = optimal_lap(D) for some D not yet stopped and a stop is required: apply stop
    resolve pass-throughs with §12.4 expected-laps-to-pass (deterministic threshold P>0.5)
→ provisional classification + per-position confidence from accumulated σ
```

Output: `RaceForecast(classification, perDriverBands)` refreshed `Periodic(2)` laps.
This is intentionally a *median-path* simulation: cheap, explainable, good enough for
"likely to happen next". Distribution-quality forecasts are the Monte Carlo upgrade (§18).

### 12.8 Scenario engine ("what if")

`ScenarioEngine.evaluate(PitNow("VER"))` = run §12.7 with the overridden decision, diff
against the baseline forecast, return structured deltas (positions, gaps, traffic).
This is also the deterministic backend for LLM Q&A about hypotheticals (§14.4) — the
LLM never speculates about outcomes; it narrates a scenario evaluation.

---

## 13. Where ML adds value (and where it does not)

| Problem | Verdict | Why |
|---|---|---|
| Gap arithmetic, pit loss, rejoin math, SC/VSC economics | **No ML, ever** | Exact arithmetic; ML adds error and destroys explainability where determinism is available |
| Rule-defined detections (DRS eligibility, blue flags, windows) | **No ML** | Regulations are rules, not distributions |
| Tyre cliff timing | **ML later, high value** | Survival analysis / gradient boosting over (compound, track, temp, stint history) beats a scaled prior; deterministic fallback stays |
| Overtake probability coefficients | **ML later, high value** | Same logistic form, coefficients fitted on historical battles — pure calibration, form stays explainable |
| Pace anomaly detection | **No ML needed** | CUSUM is optimal-ish, transparent, and tunable; a learned detector would be unexplainable for no accuracy win at n=20 |
| Compound/track priors | **Light ML** | Hierarchical shrinkage across seasons (empirical Bayes) — fits the config-as-data design perfectly |
| Driver behaviour priors (defence quality, tyre management) | **ML later, medium value** | Per-driver offsets in the overtake/deg models; needs multi-season data |
| Natural-language output | **LLM only** (§14) | Language is the one place generative models are the right tool |

Structural rule: ML slots in only as **(a)** better constants for existing deterministic
formulas, or **(b)** a better prior that the live fit still bends. The pipeline shape,
interfaces, and testability never change — that is what "no major architectural
decisions left" means for the ML roadmap.

---

## 14. LLM narration layer

### 14.1 The contract

```
Deterministic engine  →  structured facts  →  LLM  →  prose
                                              │
The LLM may:    explain, summarise, answer questions, adjust tone/length
The LLM never:  calculates, predicts, ranks, invents numbers, sees raw timing
```

### 14.2 FactFrame — the interface between the two worlds

```kotlin
data class FactFrame(
    val schemaVersion: Int,                 // engine and narrator evolve independently
    val raceContext: RaceContextFact,       // event, lap x/y, track status, weather
    val standings: List<StandingFact>,      // pos, driver, interval, tyre(compound, age)
    val insights: List<InsightFact>,        // the ranked insights, verbatim numbers
    val forecasts: List<ForecastFact>,      // gap forecasts, race forecast, windows
    val recentEvents: List<EventFact>,      // last N EngineEvents, humanised keys
)

data class InsightFact(
    val id: FactId,
    val type: String,                       // "UNDERCUT_WINDOW"
    val subjects: List<String>,
    val numbers: Map<String, NumberFact>,   // "gap" → NumberFact(1.4, "s", precision=1)
    val confidence: Double,
    val templatedText: String,              // the deterministic rendering — the floor
)
```

Serialised as JSON into the prompt. `NumberFact` carries value, unit, and display
precision — the narrator is told exactly how each number may be written.

### 14.3 Prompt architecture & guardrails

**System prompt (fixed):** *"You are the race narrator. Use only the facts provided.
Every number you write must appear in the facts, in the stated unit and precision. If a
question cannot be answered from the facts, say so plainly. Never estimate, extrapolate,
or compute."*

**NumericAuditor (post-generation, deterministic):** extract every numeric token from
the LLM output; each must match a `NumberFact` (value within display-precision tolerance,
unit consistent). Ordinals/positions and driver names are matched against the fact set
too. On violation → one retry with the violation named → then
`NarrationResult.Refused`, and the UI shows `templatedText`. **The deterministic
template is always the floor** — the product works with the LLM off, slow, or wrong.

### 14.4 Question answering

```
user question → QuestionRouter (intent + entities: drivers, insight types, time range)
      ├─ factual   ("why did HAM pit?")      → FactQuery over FactStore/EventLog
      ├─ hypothetical ("what if VER pits?")  → ScenarioEngine (§12.8) → facts
      └─ out of scope ("who'll win in 2027") → Refused with reason
→ context assembly (only matched facts, capped ~2k tokens) → LLM → NumericAuditor
```

The FactStore keeps the whole race's fact history (append-only, lap-indexed), so "why"
questions retrieve the facts *from when it happened*, not a reconstruction.

### 14.5 Placement

Narration runs outside the pipeline (async, `Dispatchers.IO`), on-device via API or
server-side — the `FactFrame` schema is the wire format either way. Latency budget:
summaries are pull-based (user opens the narration panel) or throttled push (once per
lap max); nothing in the UI's critical path ever waits on an LLM.

---

## 15. Testing strategy

### 15.1 Layer-by-layer

| Layer | Technique |
|---|---|
| Feature models (pace, deg, gaps) | pure unit tests on synthetic series with known ground truth (inject `d = 0.08 s/lap`, assert fit recovers it within tolerance) |
| Detectors | table-driven: hand-built `FeatureView` fixtures per catalogue entry — fire case, near-miss case, hysteresis case, confidence-cap case |
| Predictors | closed-form checks (§12.1 has an algebraic answer — assert it exactly), plus property tests |
| Ranker | scenario fixtures asserting the *top-3 set* and stability behaviour under a scripted sequence |
| EventDeriver | frame-pair diff tables incl. malformed feeds (gaps, jumps, duplicates) |
| Narration | NumericAuditor unit tests; golden-prompt regression tests; contract test that Refused → template fallback |

### 15.2 Determinism & replay equivalence

The keystone test: feed a recorded frame sequence twice → assert **bit-identical**
`IntelligenceFrame` sequences. Runs in CI on every golden race. Any nondeterminism
(iteration order, wall-clock leakage, float instability) is a build-breaking bug. The
injectable `Clock` and single-threaded pipeline make this achievable.

### 15.3 Golden races & scenario DSL

Extend the existing simulator with a **scenario DSL** so tests script causally-consistent
races rather than hand-editing frames:

```kotlin
scenario("undercut at lap 20") {
    grid(20); lap(18) { car("NOR").gapTo("HAM", 2.1); car("HAM").tyreAge(16) }
    expectInsight(UNDERCUT_WINDOW, subjects = "NOR", "HAM", byLap = 19)
    lap(20) { car("NOR").pits() }
    expectInsight(PIT_DELTA, byLap = 23) { numbers["gain_s"]!! > 0 }
}
```

Golden corpus: one scripted race per insight family + composite races (SC mid pit-window,
battle through traffic) + real historical timing data once an adapter for archived F1
feeds exists.

### 15.4 Calibration backtesting

The lifecycle's CONSUMED-with-outcome log (§6.3) is the measurement instrument:

- **Probabilities** (A5, A6, A2): reliability diagrams + **Brier score** per type; a
  stated P = 0.6 must come true ≈ 60% of the time or θ/priors get retuned.
- **ETAs** (battle ETA, cliff lap, window close): mean absolute error in laps.
- **Precision/recall of detections** vs hand-labelled golden races.

Backtest mode disables §11.5 stability so raw ranking quality is measurable. This closes
the loop that lets ML (§13) improve constants later with zero architecture change.

### 15.5 Robustness fuzzing

Feed mutators over golden races: drop 1–10% of frames, duplicate frames, jitter gaps by
±0.2 s, freeze a car's data. Assertions: no crash, no NaN, confidence *degrades*
(monotonically w.r.t. injected noise), `health.degraded` reflects reality.

---

## 16. Performance & scalability

### 16.1 The honest numbers

n = 20 cars, ~1 Hz frames, ≤ 80 laps. Worst-case detector pass ≈ n² pair evaluations ×
~26 detectors ≈ 10⁴ arithmetic-heavy evaluations — **microseconds to low milliseconds on
a phone**. This engine is not compute-bound and the design spends complexity budget on
correctness and explainability instead. Budgets enforced by benchmark tests anyway:
cheap path p99 < 1 ms, detector pass p99 < 10 ms, zero allocations growth per frame
beyond the emitted immutable frame (ring buffers preallocated).

### 16.2 Battery & memory

Lap-granular scheduling (§4.2) means the expensive passes run ~once per ~90 s per driver
naturally. Backgrounded: ingestion continues (history integrity), detector cadence drops
to OnLapComplete only. Memory: bounded ring buffers (§3.2), a few MB ceiling.

### 16.3 Scalability paths

- **Server-side:** the module is pure Kotlin/JVM — the identical artifact runs
  multi-session on a server (one pipeline instance per session; they share nothing).
  `FactFrame`'s versioned schema is the client/server wire contract.
- **Replay/backtest farm:** determinism (§15.2) makes historical races embarrassingly
  parallel batch jobs for calibration.
- **Faster feeds:** real F1 timing bursts faster than 1 Hz; the conflation/degradation
  design (§4.3) absorbs it, and `EveryFrame` detectors are the O(n) cheap ones by
  deliberate assignment.

---

## 17. Known weaknesses

1. **Timing-only blindness.** No throttle/brake/ERS/fuel telemetry → C5 (fuel saving) and
   C6's trigger are inference with capped confidence; sector-less data (today's adapter)
   further caps A3. The design mitigates by *saying so* (confidence caps), not by
   pretending.
2. **Constants rule everything.** Pit loss, fresh-tyre offsets, track difficulty,
   compound priors — wrong constants mean wrong strategy insights. Mitigation:
   config-as-data, per-track overrides, calibration loop (§15.4). Cold reality: the first
   race at a new track runs on priors.
3. **Regime changes break linear models.** Rain, red flags, damage: pace fits are
   invalidated. Mitigation: track-status gating, stint-boundary resets, CUSUM
   flags-then-refits; but the first 2–3 laps after any regime change are low-confidence
   by construction (cold-start is also why lap 1–4 insights are sparse).
4. **First-lap chaos.** Gaps and positions are noisy/unstable; the engine deliberately
   suppresses most detectors until lap `config.warmupLaps` (default 3).
5. **O(n²) pairs assumption.** Fine at 20 cars; a 40-car endurance grid would want the
   pair set pruned (adjacent ± pit-cycle neighbours) — noted, not built.
6. **LLM risk is fenced but not zero.** The auditor catches numeric fabrication; it
   cannot catch a *misleading but number-free* sentence. Mitigation: templated floor,
   cited FactIds, and narration styles that keep prose close to facts.
7. **Calibration data hunger.** A5/A2 probabilities are only as good as the outcome log;
   early-season output will be visibly conservative (caps) until the log fills.

---

## 18. Future enhancements

1. **Monte Carlo race simulation** (§12.7 upgrade): sample lap noise, SC hazard (Poisson
   per-track), pass success — full finishing-position distributions and "win probability"
   graphs. The deterministic sim becomes the median path of the ensemble.
2. **Hierarchical Bayesian priors:** compound/track/driver constants shrunk across
   seasons; per-race posterior updating formalises what §9.2 approximates.
3. **Learned calibration** for §12.4 θ and cliff survival curves (the two flagged ML
   slots), trained from the backtest farm.
4. **Sector-level intelligence** once the live feed lands: mini-sector battle
   anticipation, true DRS-detection-point eligibility (lifts A3's cap), corner-vs-straight
   pace signatures (lifts C5's cap).
5. **Weather strategy module:** crossover-lap estimation (slick↔inter), rain-arrival
   integration — a new detector family (E) slotting into the existing SPI untouched.
6. **Radio/flag feed enrichment:** team radio transcripts as *facts* (never as numbers)
   for the narration layer; investigation/penalty tracking as a Meta-family detector.
7. **Personalised audience weights:** per-user `focus` vectors (§11.3) synced from app
   preferences; same ranker, different `A(i)`.
8. **Broadcast-director mode:** A7 generalised into a full attention planner producing a
   timeline of "where to look next", usable for auto-generated highlight reels.
9. **Post-race intelligence report:** replay the FactStore through the narrator for a
   structured race review — zero new engine work, pure narration reuse.

---

*End of specification. Implementation sequencing suggestion: features layer + ingestion
(the foundations everything reads) → combat family (reuses APX-007 detectors) → ranking →
strategy family → prediction engine → facts/narration. Each stage is shippable behind the
existing `RaceIntelligenceEngine` facade.*
