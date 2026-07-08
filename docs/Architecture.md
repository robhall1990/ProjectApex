# Architecture

This document explains the architectural decisions behind Project Apex's
foundation and the conventions future feature work is expected to follow.
It describes what exists in the codebase today, not aspirational design.

## Overview

Project Apex is a single-activity Compose application built on MVVM:

```
Composable Screen  --observes-->  StateFlow<UiState>  <--exposed by--  ViewModel
       |                                                                   |
       '-------------------------- user events -------------------------'
```

Each feature screen is a pair of files: `XScreen.kt` (stateless-ish
Composable, owns no business logic) and `XViewModel.kt` (`@HiltViewModel`,
owns an immutable `UiState` and exposes it as `StateFlow`). The screen
collects state with `collectAsStateWithLifecycle()` and forwards user
actions either as ViewModel calls or navigation callbacks passed in from
`ApexNavHost`.

This pattern is applied uniformly to every screen — including Analysis and
Settings, which have no real state yet — so that the next engineer adding a
feature has exactly one pattern to copy, not a judgment call about when
MVVM "is worth it."

## Package structure

```
com.projectapex
├── core/
│   ├── theme/        Cross-feature Compose theme (colors, typography, MaterialTheme wrapper)
│   ├── model/         Shared state models with no feature owner (e.g. SessionState)
│   ├── ui/            Reusable Compose components (e.g. ApexCard)
│   ├── navigation/    ApexDestination/ApexBottomDestination (routes), ApexNavHost
│   │                   (top-level graph), ApexMainScreen (bottom-nav shell),
│   │                   ApexBottomNavBar (reusable nav bar)
│   └── di/            Hilt modules for domain types that can't be constructor-
│                       injected directly (e.g. a qualified CoroutineDispatcher)
├── domain/
│   ├── DefaultDispatcher.kt  Qualifier for the shared background CoroutineDispatcher
│   ├── model/         Driver, TyreCompound, CarState, RaceState — pure Kotlin,
│   │                   no Android imports
│   ├── race/          RaceEngine — owns the current RaceState
│   ├── simulation/    RaceSimulator — generates believable fake RaceState
│   │                   updates for UI development, sits above RaceEngine
│   ├── timeline/      RaceTimeline — records RaceState history, lets the UI
│   │                   browse it independently of RaceEngine's live state
│   └── intelligence/  RaceIntelligenceEngine — deterministic rules over
│                       RaceState producing RaceInsight (battles, gap trends,
│                       tyre concerns, ...); no AI, no external calls
├── feature/
│   ├── splash/
│   ├── race/          Screen + one RaceViewModel, combining RaceTimeline
│   │   │               (race data + replay position) and RaceEngine ->
│   │   │               RaceIntelligenceEngine (insights) into one RaceUiState
│   │   └── components/  SessionHeader, RaceStatusBar, UnwrappedTrackView,
│   │                     RaceIntelligenceSection, RaceInsightCard, RaceLeaderboard,
│   │                     LeaderboardRow, PanelHeader, SectionCard, StatusChip, InfoRow
│   ├── analysis/
│   └── settings/      Screen + ViewModel + DeveloperModeCard (drives RaceSimulator)
├── ApexApplication.kt
└── MainActivity.kt
```

`core/ui` holds components reused across two or more feature screens
(currently just `ApexCard`, the shared surface treatment behind the Race
dashboard's cards). `core/model` holds state shapes with no single feature
owner (currently `SessionState`/`SessionStatus`, since a session concept will
eventually be read by both Race and Analysis). Neither existed before the
Race dashboard needed them — they were not scaffolded speculatively.

`core/util` still does not exist, for the same reason.

`domain/` now exists (see [Domain layer](#domain-layer) below), introduced
specifically because `RaceEngine` needed somewhere to own race state that has
no Android dependency at all — not scaffolded ahead of need.

`data/` is still absent: there is no repository or API client yet, because
nothing produces real race data. When the first data source exists (most
likely a live timing feed), add:

```
data/
├── remote/        Retrofit service interfaces + DTOs
├── local/         Room entities/DAOs
└── repository/    Repository implementations that push into RaceEngine
```

Repositories should be bound via Hilt `@Binds` in a module under
`core/di/` (not yet created), not instantiated directly in ViewModels.
`domain/repository/` is deliberately not scaffolded yet either — there is
nothing to abstract over until a real data source exists to sit behind an
interface.

## Domain layer

Project Apex's eventual shape is:

```
External Data Source -> RaceEngine -> RaceTimeline -> Android UI
                              |
                              +------> AI Insight Engine (future)
```

`RaceEngine` (`domain/race/RaceEngine.kt`) owns the single current
`RaceState` and exposes it as `StateFlow<RaceState>`. It is the one place
race state is allowed to live — everything downstream (today's UI, a future
AI insight engine) only ever reads from it; nothing downstream is allowed to
hold its own copy of race state or mutate it directly.

`RaceEngine` is intentionally minimal for now:

- **No networking, no persistence.** It has no idea where `RaceState`
  updates come from. A future data source calls `updateState(newState)`;
  `RaceEngine` doesn't care if that's a live timing feed, a replay file, or
  a test.
- **`@Singleton @Inject constructor()`.** Hilt constructs and shares exactly
  one `RaceEngine` app-wide — `RaceSimulator` and `RaceTimeline` both get the
  same instance. Both annotations are plain `javax.inject`, so the file
  still has no Hilt/Android framework import of its own. `RaceViewModel`
  no longer reads `RaceEngine` directly (see [RaceTimeline](#racetimeline)) —
  it reads `RaceTimeline`, which sits between the two.
- **Thread safety and observability are both free.** `MutableStateFlow`
  guarantees atomic value assignment and always replays its latest value to
  every collector, so no manual synchronization was needed to satisfy
  "thread safe" and "observable."
- **No interface.** `RaceEngine` is a concrete class, not an abstraction
  over an interface — there is only one implementation and no test double
  needed (tests exercise the real thing), so an interface would be
  unused indirection.

`domain/model` reuses `core.model.SessionStatus` for `RaceState.sessionStatus`
rather than defining a second, competing status enum. This does mean
`domain` currently depends on a type that lives in `core` — a slightly
inverted dependency direction, since `domain` is meant to be the lower-level
layer other code depends on. It was judged better than duplicating the enum;
a future cleanup ticket that already needs to touch `feature/race`'s imports
would be the right time to relocate `SessionStatus` into `domain/model`
properly.

`RaceStateFactory` (five placeholder drivers — Norris, Verstappen, Piastri,
Russell, Hamilton) lives under `app/src/test`, not `app/src/main` — this is
enforced at compile time, not just by convention: production code physically
cannot import a class that only exists in the test source set.

### RaceSimulator

```
RaceSimulator -> RaceEngine -> UI
```

`RaceSimulator` (`domain/simulation/RaceSimulator.kt`) is a development tool,
not production code: it generates believable (not physically accurate)
`RaceState` updates once a second and pushes them into `RaceEngine`, so the UI
can be built and demoed before any live timing feed exists. Unlike
`RaceStateFactory`, it *does* ship in `app/src/main` — Settings' Developer
Mode controls call it at runtime — but nothing in `feature/` imports it
directly except `SettingsViewModel`; every other screen only ever reads
`RaceEngine`.

Design notes:

- **`@Singleton @Inject constructor(RaceEngine, @DefaultDispatcher CoroutineDispatcher)`.**
  The dispatcher is injected, not hardcoded to `Dispatchers.Default`,
  specifically so `RaceSimulatorTest` can substitute a `StandardTestDispatcher`
  bound to the test's `TestCoroutineScheduler` — that makes the real
  `delay(1_000)` in the tick loop advance under virtual time
  (`advanceTimeBy`/`runCurrent`) instead of costing a real second per test.
  `@DefaultDispatcher` is a plain `javax.inject.Qualifier` annotation living
  at `domain/DefaultDispatcher.kt` (originally `@SimulationDispatcher`,
  scoped to `domain/simulation/` — renamed and moved up once `RaceTimeline`
  became a second consumer needing the exact same binding); the actual
  `@Provides` (`core/di/DomainModule.kt`) lives outside `domain/`, since a
  Hilt `@Module` necessarily imports `dagger.hilt.*`.
- **No interface**, for the same reason as `RaceEngine` — one implementation,
  tests exercise the real thing.
- **Own `CoroutineScope`.** `RaceSimulator` creates
  `CoroutineScope(SupervisorJob() + dispatcher)` itself rather than being
  handed one, since its background tick loop's lifetime is the simulator's
  own lifetime (app-wide, as a Hilt singleton), not tied to any particular
  screen or ViewModel.
- **Synthetic grid, not fake-data-in-the-UI.** `SyntheticGridFactory` builds
  the 20-car starting grid (6 named drivers — VER/NOR/PIA/LEC/HAM/RUS — plus
  14 placeholders) and is `internal`, only usable by `RaceSimulator` itself.
  The UI never sees or constructs fake cars directly; it only ever reads
  whatever `RaceEngine.state` currently holds.

### RaceTimeline

```
RaceSimulator -> RaceEngine -> RaceTimeline -> UI
```

`RaceTimeline` (`domain/timeline/RaceTimeline.kt`) records every `RaceState`
`RaceEngine` ever holds and lets the UI browse that history independently of
what the engine is currently doing — the enabler for replay, historical
analysis, and future AI explanations that read past states, not just the
current one. `RaceViewModel` depends on `RaceTimeline` instead of
`RaceEngine` directly. (`RaceIntelligenceViewModel` is the one exception —
see [RaceIntelligenceEngine](#raceintelligenceengine) below for why.)

- **Auto-recording, not a manually-fed buffer.** `RaceTimeline`'s `init`
  block launches its own subscription to `raceEngine.state` and calls
  `record()` on every emission itself. Nothing external needs to remember to
  forward states — this mirrors how `RaceSimulator` is the *only* thing that
  calls `RaceEngine.updateState()`; here, `RaceTimeline` is the only thing
  that (normally) calls its own `record()`.
- **"Live" vs "replay" is derived, not a stored flag.**
  `RaceTimelineState.isLive` is just `currentIndex == snapshots.lastIndex`.
  `record()` only re-pins `currentIndex` to the new latest snapshot *if the
  timeline was already live* before this recording; if the caller had
  stepped backwards (`previous()`/`seek()`), new recordings keep arriving in
  the background without dragging their view forward. This is the same
  mental model as a live-TV DVR: pause/rewind, and the broadcast keeps
  recording behind you. Falling out of this: calling `next()` enough times
  to reach the newest snapshot naturally re-enters live mode with no special
  casing required.
- **The 1000-snapshot cap shifts a paused viewer's index, it doesn't just
  truncate blindly.** When recording drops old snapshots off the front,
  a `currentIndex` that was pointing into the *middle* of the list (replay
  mode) is shifted back by however many were dropped, then clamped — so the
  viewer keeps looking at the same logical snapshot (or the oldest
  surviving one, if theirs was evicted) rather than silently jumping to a
  different point in history.
- **No new RaceTimeline API for Play/Pause.** The ticket's function list is
  fixed to `record`/`previous`/`next`/`seek`/`clear` — there's no dedicated
  "pause" or "resume live" call. `RaceViewModel.onPlayPauseClicked()` maps
  onto this fixed surface: while live, it calls `previous()` (the only
  available way to leave live mode); while replaying, it calls
  `seek(lastIndex)` (jump back to live). This is a deliberate simplification,
  not a hidden extra timeline feature — see Limitations below.
- **Tests call `record()` directly**, bypassing the `RaceEngine`
  auto-subscription entirely, by constructing `RaceTimeline` with a
  `StandardTestDispatcher()` whose scheduler is never advanced — so the
  `init`-block subscription simply never runs, and only the test's own
  direct calls affect timeline state. No `runTest`/coroutine machinery is
  needed at all for `RaceTimelineTest`, since `record`/`previous`/`next`/
  `seek`/`clear` are all plain, non-suspending functions.

### RaceIntelligenceEngine

```
RaceState -> RaceIntelligenceEngine -> RaceInsight list -> Future AI explanation layer
```

> The full-scale successor to this engine — a prediction-focused intelligence
> platform with 26 detectors, a mathematical ranking model, deterministic
> prediction algorithms, and an LLM narration layer — is specified in
> [RaceIntelligencePlatform.md](RaceIntelligencePlatform.md). What follows
> describes the v1 engine as implemented today.

`RaceIntelligenceEngine` (`domain/intelligence/RaceIntelligenceEngine.kt`) is
deterministic rule-based analysis — no AI model, no external call — meant as
the foundation a future AI explanation layer reads from (`RaceInsight`s)
rather than raw `RaceState`. `analyse(state: RaceState): List<RaceInsight>`
runs five detectors and returns their combined output, highest
`InsightPriority` first.

- **Stateful, unlike the rest of the domain layer.** Gap-closing/increasing
  and "fastest car" are *trends* — they need the previous state to compare
  against, but the ticket's fixed signature only takes one `RaceState`. So
  `RaceIntelligenceEngine` remembers the last state it was given internally
  (`private var previousState`) and diffs against it each call. Battle
  detection and tyre concern are stateless (computed from the current state
  alone) and work even on the very first call.
- **This assumes chronological input.** If fed states out of order — e.g.
  from scrubbing a replay timeline backwards — the "previous state" would no
  longer represent the moment immediately before the current one, and
  gap-trend/fastest-car insights could be misleading. Not solved in v1; see
  Limitations.
- **This is why `RaceViewModel` reads `RaceEngine` directly for insights,
  not `RaceTimeline`** — the one deliberate exception to "the Race screen
  reads `RaceTimeline`, not `RaceEngine`." `RaceEngine`'s state only ever
  advances forward in real time; `RaceTimeline` can jump anywhere via
  `previous`/`next`/`seek`. Feeding the stateful engine from the timeline
  would risk exactly the out-of-order problem above. The trade-off:
  intelligence insights always describe the *live* race, never whatever
  moment is currently being replayed. (APX-007 briefly had this split across
  two ViewModels — `RaceViewModel` and `RaceIntelligenceViewModel` — purely
  because `RaceUiState` hadn't been asked to carry insights yet; APX-008
  merged them into one `RaceViewModel`/`RaceUiState`, combining both flows
  with `combine()`, without changing which domain service feeds which
  field.)
- **Gap trend is measured to the leader, not the car ahead.** `CarState`
  only carries `gapToLeaderSeconds`, so "PIA is closing on VER" means PIA's
  gap to whoever currently holds P1 is shrinking — not necessarily the car
  immediately in front of PIA on track.
- **Battle detection compares adjacent *positions*, not adjacent gaps.**
  Cars are sorted by `position` and checked pairwise
  (`zipWithNext`); the gap compared is `abs(behind.gap - ahead.gap)`,
  covering the "1.5 seconds" threshold regardless of which of the two has
  the larger recorded gap (the synthetic simulator's independent per-car
  random walk doesn't guarantee gaps stay monotonic with position).
- **Priority/threshold values are this ticket's judgment calls**, not
  specified by the ticket beyond "battle = 1.5 seconds, HIGH": gap-trend
  threshold 0.3s, fastest-car minimum gain 0.1s, tyre concern at 20 laps,
  gap-trend/tyre-concern priority MEDIUM, fastest-car priority LOW. All are
  named constants at the top of the file, not buried magic numbers.
- **`DRS_RANGE` has no detector.** It's in `InsightType` because the ticket's
  enum spec includes it, but only five detectors were requested — it's
  reserved for a future ticket.

### The :intelligence module (APX-010)

The first implementation slice of
[RaceIntelligencePlatform.md](RaceIntelligencePlatform.md): the **ingestion**
and **features** layers that every later detector/predictor ticket reads.

- **A separate pure Kotlin/JVM Gradle module** (`:intelligence`) with no
  Android plugin — an accidental `android.*` import cannot compile
  (spec §7 "enforced by the build"). Its only dependency is the stdlib; tests
  are plain JUnit on the JVM, no emulator involved.
- `ingest/` — `TimingFrame` is the platform's canonical input (spec §3.1),
  richer than `RaceState` on purpose: it has slots (sectors, intervals, track
  status, weather) that today's adapter leaves empty but a real live-timing
  feed will fill. `FrameValidator` rejects malformed frames whole (positions
  must form a permutation, gaps non-negative, sequences monotonic) so nothing
  downstream ever defends against bad data; `FrameNormaliser` sorts by
  position and derives missing intervals; `IngestPipeline` composes the
  synchronous "cheap path" (validate → normalise → derive → store) that the
  future coroutine actor will wrap.
- `events/` — `EventDeriver` diffs consecutive frames into `EngineEvent`
  edges (spec §6.1). The critical bit is `PositionChange.onTrack`, which
  separates real overtakes from pit-cycle position changes by checking both
  cars' pit status. Data gaps (sequence or multi-lap jumps) emit `DataGap`
  rather than guessed history.
- `features/` — `FeatureStore` is the single owner of derived history (lap
  book, stint book, gap history, pit log); everything downstream reads the
  `FeatureView` interface. The shared math (spec §9): fuel-corrected lap
  times, MAD-based outlier flagging, OLS pace estimates, per-stint tyre-deg
  fits with cliff detection (the linear-phase fit deliberately *excludes* the
  candidate cliff laps — a fit polluted by them would tilt and hide their own
  residuals), prior-scaled cliff prediction, pit-loss by track status, and
  the `TrafficProjector` (leader-relative cumulative-time rejoin math).
- **`RaceStateAdapter` lives in the app module** (`intelligence/adapter/`)
  because it must see both `RaceState` and `TimingFrame`; the :intelligence
  module never imports app types. It is stateful on purpose: it assigns
  sequence numbers and synthesises `lastLapTime` from wall-clock deltas
  between lap edges, because the simulator doesn't produce lap times yet.
- **Not yet wired into the UI.** Nothing user-facing changes in APX-010; the
  adapter is the entry point the detector-family ticket will connect to
  `RaceEngine`'s flow. `IntelligenceConfig` carries every constant as data —
  per-track tuning and tests pin exact configs without code changes.

### Detection framework & prioritisation (APX-011)

APX-011 added the `detect/` and `rank/` layers on top of APX-010 — see
[DetectionFramework.md](DetectionFramework.md) for the full description.
In brief: an immutable `Observation` model (the internal intelligence object;
`RaceInsight` becomes a presentation-layer rendering of it later), a
`Detector` SPI with **registration-only extensibility** (the `DetectorEngine`
knows no concrete detector; it isolates failures so one throwing detector
never stops the others, and collects per-detector metrics), and a stateful
`PrioritisationEngine` scoring `severity · confidence · urgency · recency ·
novelty` (all constants in `PrioritisationConfig`) with greedy diverse top-K
selection into a `RacePulse`. Deterministic end to end: injectable clocks,
tie-breaks by id. No concrete race detectors exist yet — that is deliberate;
they arrive with the detector-family tickets and plug in via `register(...)`.

## State management

- **Immutable state**: every `UiState` is a `data class` with `val`
  properties and sensible defaults. ViewModels never expose a mutable type.
- **Single source of truth**: `MutableStateFlow` is private; only the
  read-only `StateFlow` is exposed, following the standard
  `_uiState` / `uiState` naming convention.
- **Lifecycle-aware collection**: screens use
  `androidx.lifecycle.compose.collectAsStateWithLifecycle()`, not
  `collectAsState()`, so collection pauses when the app is backgrounded.

Splash is the one screen with real (if simple) logic: `SplashViewModel`
delays in `viewModelScope`, then flips `isReadyToProceed`, which
`SplashScreen` observes via `LaunchedEffect` to trigger navigation. This
logic lives in the ViewModel specifically so it is unit-testable and does
not depend on composition lifecycle.

## Navigation

Navigation is two-level, which is the standard shape for "splash then
bottom-nav shell" apps:

- **Top-level** (`ApexNavHost`, declared once in `MainActivity`): `Splash` →
  `Main`. Routes come from the sealed class `ApexDestination` rather than raw
  strings, so a typo'd route fails at compile time, not at runtime.
- **Nested** (`ApexMainScreen`): owns its own `NavController` and `NavHost`
  inside a `Scaffold` whose `bottomBar` is `ApexBottomNavBar`. Its three
  routes — Race, Analysis, Settings — come from `ApexBottomDestination`, a
  second sealed class carrying a route, label, and icon per tab. Race is the
  `startDestination`.

`ApexBottomNavBar` follows the standard Navigation Compose bottom-nav
pattern: `launchSingleTop = true`, `restoreState = true`, and
`popUpTo(graph.findStartDestination())` with `saveState = true`, so switching
tabs preserves each tab's own back stack/scroll position instead of
rebuilding it, and repeated taps on the same tab don't stack duplicate
destinations.

Screens do not call `NavController` methods directly — navigation is always
passed in as plain lambdas from `ApexMainScreen`/`ApexNavHost`.

Splash removes itself from the back stack on navigating to Main
(`popUpTo(Splash) { inclusive = true }`), so back-press from the Race tab
exits the app rather than re-showing Splash.

## Theming

Material 3, dark-mode-first, no Formula One branding (no red/checkered
motifs). The accent is a custom purple (`#7C5CFF` / `#A98BFF`) intended to
read as premium/technical rather than sporty.

`dynamicColor` in `ProjectApexTheme` defaults to `false`. Android 12+
wallpaper-based dynamic color is supported but opt-in, because the brand
palette is a deliberate design choice that should not be silently overridden
by a user's wallpaper.

## Reusable UI components

`core/ui/ApexCard` is the one genuinely cross-feature component so far: a
`Card` wrapper providing the consistent rounded-corner, tonal-surface,
padded-`Column` treatment used by every dashboard card. `UnwrappedTrackView`
and `RaceLeaderboard` (`feature/race/components/`) both build on top of it,
but stay in `feature/race` since their content is Race-specific — only the
container styling is shared. If a future feature needs a visually similar
but differently-populated card, it should compose `ApexCard` the same way
rather than reusing a Race-specific component.

## Race visualisation

The Race screen (APX-009) is five vertically stacked sections, each its own
component, composed in `RaceScreen.kt` with no layout logic of its own beyond
a scrollable `Column`: `SessionHeader`, `RaceStatusBar`, `UnwrappedTrackView`,
`RaceIntelligenceSection`, `RaceLeaderboard`.

**Design system primitives** (`feature/race/components/`), extracted
specifically to stop every section hand-rolling the same structure:

- `PanelHeader` (renamed from `SectionHeader` in APX-008 — the ticket named
  this component `PanelHeader`, and keeping both names around for the same
  job would itself be the duplication this ticket asked to eliminate).
  Consistent title styling, reused at multiple hierarchy levels via a
  `style` override: the large top banner as well as every section's own
  title.
- `SectionCard` wraps `ApexCard` + `PanelHeader` + a padded content slot —
  the "titled card" pattern every section previously wrote out by hand.
  `UnwrappedTrackView`, `RaceIntelligenceSection`, `RaceLeaderboard`, and
  `RaceStatusBar` all just call `SectionCard(title = ...) { ... }` now.
- `StatusChip` — a labelled pill with an explicit `enabled` flag for the
  three not-yet-real signals (Track/Weather/DRS): dimmed rather than hidden,
  so the UI honestly shows "this exists as a concept, not as data yet"
  rather than pretending the feature isn't planned.
- `InfoRow` — a plain label/value row, used for the session header's "Lap
  24 / 52" line.

`SessionHeader` (`feature/race/components/SessionHeader.kt`) replaces
APX-008's `ReplayControls`, absorbing its role (LIVE/REPLAY banner +
Previous/Play-Pause/Next) plus the event name and lap counter. Bringing back
`timelinePosition`/`timelineSize` to `RaceUiState` (dropped in APX-008) let
the Previous/Next buttons properly disable at the two ends of recorded
history again — this is UI-state plumbing (re-exposing values
`RaceTimelineState` already computed), not new domain logic, so it doesn't
conflict with the ticket's "do not add new race logic." The event name
("British Grand Prix") is a static string resource, not a new `RaceState`
field — adding a real event-name concept to the domain model would be race
logic, out of scope for a UX-only ticket.

`RaceStatusBar` (`feature/race/components/RaceStatusBar.kt`) is a
`FlowRow` (wraps onto multiple lines on narrow screens rather than
requiring horizontal scrolling) of `StatusChip`s: "Session" shows the raw
domain `SessionStatus` (OFFLINE/LIVE); "Simulation" shows whether
`raceState.cars` is non-empty — a distinct, if currently correlated, signal
that could diverge from a richer future data source; "Replay" only appears
while `isReplayMode` is true; "Track"/"Weather"/"DRS" are permanent, always
`enabled = false` placeholders. None of this required touching
`RaceSimulator`, `RaceEngine`, or `RaceIntelligenceEngine` — every chip
derives from data `RaceViewModel` already had.

`UnwrappedTrackView` is explicitly a *race-distance* visualisation, not a
geographical track map: a horizontal ribbon from START to FINISH, with each
car placed left-to-right by race progress and stacked top-to-bottom by
position. It renders purely from the `RaceState` it's given — it has no
reference to `RaceEngine` or `RaceSimulator` at all, satisfying "the UI must
not know about the simulator" by construction rather than by convention.

- **Progress calculation is an isolated seam.** `CarState.trackProgress(raceState)`
  computes each car's horizontal position. Today it returns the *race's*
  overall `currentLap / totalLaps` for every car alike, because `CarState`
  has no per-car distance-around-lap field yet — that's why every car
  currently sits at the same X position, differentiated only by row (Y).
  The function takes the individual `CarState` as its receiver specifically
  so that swapping in a real `distanceAroundLap` later touches only this one
  function, not the view's layout or its callers.
- **Animation via `animateFloatAsState`, not Canvas.** Each car marker
  animates its own X/Y offset (in pixels, via `Modifier.offset { IntOffset(...) }`)
  toward its current target position with a 600ms `tween`. `key(car.driver.id)`
  around each marker gives Compose a stable identity across recompositions,
  so when two cars swap places, `animateFloatAsState` interpolates from each
  marker's *previous* position rather than snapping - no Canvas, no custom
  `Layout`, no manual `Animatable` bookkeeping.
- **No hardcoded coordinates.** The ribbon's pixel width comes from
  `BoxWithConstraints` measuring the actual available width at runtime
  (`maxWidth`), not a fixed dp value, so it adapts to different screen
  sizes.
- **Leader stands out via size, not just colour.** The position-1 marker is
  rendered larger (`LEADER_MARKER_WIDTH`/`HEIGHT` vs `MARKER_WIDTH`/`HEIGHT`)
  in addition to the existing `MaterialTheme.colorScheme.primary` background
  — a colour-only difference doesn't read in greyscale or to anyone who
  can't distinguish the two hues, a size difference always does.
- **Content description per marker** (`"Position 1, VER"`) since the visual
  marker conveys meaning (position + colour + size) that separate,
  unlabelled `Text` nodes don't capture as one coherent announcement for a
  screen reader.

`RaceLeaderboard`/`LeaderboardRow` (`feature/race/components/LeaderboardRow.kt`)
render Pos/Driver/Gap/Tyre in that order (per the ticket), each row keyed by
driver id. The leader's row gets a tinted background
(`primary.copy(alpha = 0.12f)`) and bold text, in addition to
`UnwrappedTrackView`'s own leader emphasis — two independent, low-cost
"leader stands out" treatments rather than one shared mechanism, since the
two components don't share layout code. A `LeaderboardColumnHeader` row
(POS/DRIVER/GAP/TYRE labels) sits above the rows, addressing "improve
typography" by giving the numbers/names something to visually align against.
The tyre-compound badge picks its text colour *per compound*
(white on SOFT/WET/INTERMEDIATE's dark saturated backgrounds, black on
MEDIUM/HARD's light ones) rather than one fixed colour, since a single
choice can't have adequate contrast against both a dark red and a pale
yellow circle — and carries a content description (e.g. "Medium tyres"),
since the colour+letter combination alone conveys nothing to a screen
reader. Both `RaceLeaderboard` and `UnwrappedTrackView` tolerate an empty
`cars` list (`RaceState.empty()`, before any simulation has run) by showing
a short "no active session" message instead of an empty card.

`RaceIntelligenceSection` (`feature/race/components/RaceIntelligenceSection.kt`)
renders the top 3 `RaceInsight`s as `RaceInsightCard`s separated by
dividers — "a clean feed" — in the order `RaceIntelligenceEngine` already
returns them (highest `InsightPriority` first; the ticket's "highest
priority first" requirement was already satisfied by the engine itself, no
detector logic changed). Truncation to 3 (`insights.take(3)`) stays the
UI's job, not the engine's. `RaceInsightCard`
(`feature/race/components/RaceInsightCard.kt`) shows an emoji keyed off
`InsightType` (🔥 battle, 📉/📈 gap closing/increasing, ⚡ fastest car, 🎯
DRS range, 🛞 tyre concern), the insight's title/description, and a small
coloured dot for `InsightPriority` (error/primary/onSurfaceVariant for
HIGH/MEDIUM/LOW — deliberately reusing already-themed colours rather than
the unconfigured Material 3 default `tertiary`), now also carrying a content
description ("High priority", etc.) since colour alone isn't
screen-reader-accessible. Same pattern as every other Race component: plain
data in, no reference to the engine or the ViewModel.

## Icons

`material-icons-core` (the default Compose Material dependency) only bundles
a curated ~50-icon subset — it does not include icons like `Flag` or
`Insights` that the bottom nav needed for a professional-feeling Race/Analysis
tab bar. `material-icons-extended` is added alongside it for access to the
full Material icon set. This is a deliberate size/quality trade-off: R8
strips unused icons from the release build (minification is already enabled
there), so the cost is a slower debug build, not a bloated release APK.

## Dependency injection

Hilt is wired at these points:

- `ApexApplication` — `@HiltAndroidApp`.
- `MainActivity` — `@AndroidEntryPoint`.
- Every ViewModel — `@HiltViewModel`, obtained in Compose via
  `hiltViewModel()`.
- `RaceEngine`, `RaceSimulator`, `RaceTimeline`, and `RaceIntelligenceEngine`
  — all `@Singleton @Inject constructor(...)`, constructor-injected directly
  (no `@Module` needed for any of them — Hilt/Dagger discovers `@Inject`
  constructors automatically). `RaceIntelligenceEngine` needs `@Singleton`
  for more than consistency: it's stateful (remembers the previous
  `RaceState`), so a second instance would mean a second, independent
  "memory" of race history — there must be exactly one.
- `core/di/DomainModule.kt` — the one Hilt `@Module` in the project so far,
  `@InstallIn(SingletonComponent::class)`, providing the one thing that
  *can't* be constructor-injected: a `@DefaultDispatcher`-qualified
  `CoroutineDispatcher` (`Dispatchers.Default`), since `CoroutineDispatcher`
  has no injectable constructor of its own. Both `RaceSimulator` and
  `RaceTimeline` depend on this same binding.

Retrofit, OkHttp, Room, and Coil are present as Gradle dependencies for
future networking and persistence work, but are deliberately not wired into
DI or used anywhere — adding a `NetworkModule` or `DatabaseModule` before
there is a real API client or entity would be dead code.

## Testing strategy

- **ViewModel unit tests** (`app/src/test`) — plain JUnit, no
  Android/Hilt/Compose dependency, run on the JVM. `RaceViewModelTest`
  constructs `RaceViewModel(RaceTimeline(...), RaceEngine(), RaceIntelligenceEngine())`
  directly (bypassing Hilt, same as the domain tests below). One test records
  two timeline snapshots and asserts `uiState.raceState`/`isReplayMode`
  reflect the latest one live, then flip after `previous()` — proving the
  `combine()` of `raceTimeline.state` actually works, not just its initial
  value. A second test proves `insights` tracks `RaceEngine`'s state
  independently of replay position: it populates the timeline directly
  (bypassing `RaceEngine` entirely) and confirms `insights` stays empty even
  while `raceState` shows replayed data — the two fields genuinely come from
  different sources, not both from wherever the timeline points. Needs
  `Dispatchers.setMain(UnconfinedTestDispatcher())` in `@Before`/
  `resetMain()` in `@After` since `viewModelScope` needs a `Main` dispatcher
  that doesn't exist by default on the JVM.
- **Domain unit tests** (`app/src/test`) — `RaceEngineTest`, `RaceSimulatorTest`,
  `RaceTimelineTest`, and `RaceIntelligenceEngineTest` construct
  `RaceEngine()`/`RaceSimulator(...)`/`RaceTimeline(...)`/
  `RaceIntelligenceEngine()` directly, bypassing Hilt entirely (there's no
  Android context needed to build a pure Kotlin class). `RaceSimulatorTest`
  passes a `StandardTestDispatcher(testScheduler)` in place of the
  Hilt-provided `Dispatchers.Default`, so `advanceTimeBy()` fast-forwards the
  simulator's real `delay(1_000)` tick loop under virtual time instead of the
  test actually waiting on a wall clock. `RaceTimelineTest` passes a bare
  `StandardTestDispatcher()` too, but for a different reason: since it's
  never advanced, `RaceTimeline`'s `init`-block auto-subscription to
  `RaceEngine` simply never runs, so every test can call `record()` directly
  and reason about it in isolation, with no coroutine test machinery
  (`runTest`, etc.) needed at all. `RaceIntelligenceEngineTest` needs no
  coroutine setup at all — `analyse()` is a plain synchronous function — but
  its gap-trend/fastest-car tests call `analyse()` twice on the same engine
  instance (seeding, then asserting) to exercise the stateful comparison
  against a previous call.
- **Compose UI / navigation tests** (`app/src/androidTest`) — use
  `createAndroidComposeRule<MainActivity>()` plus `HiltAndroidRule` for
  screens wired through Hilt, and a custom `HiltTestRunner`
  (`testInstrumentationRunner`) that swaps in `HiltTestApplication`.
  `ApexNavigationTest` exercises the real Splash → Race dashboard flow, then
  drives the bottom nav bar to Analysis and Settings, rather than testing a
  screen in isolation, since navigation wiring is exactly what a
  foundation-level test should catch.
  - Bottom nav labels intentionally collide with each destination's
    placeholder screen content (both say "Analysis", both say "Settings").
    The tests account for this by asserting on the *count* of matching nodes
    (`>= 2` after navigating: one for the nav bar label, one for the screen
    body) via `onAllNodesWithText(...).fetchSemanticsNodes().size`, rather
    than `onNodeWithText(...).assertExists()`, which throws if more than one
    node matches.
- **Isolated Compose component tests** (`app/src/androidTest`) —
  `UnwrappedTrackViewTest`, `SessionHeaderTest`, `RaceStatusBarTest`,
  `RaceIntelligenceSectionTest`, and `RaceLeaderboardTest` all use the
  lighter `createComposeRule()` (no `MainActivity`, no Hilt) since every
  Race component takes plain parameters and needs neither. Tests that need
  `RaceState`/`CarState`/`RaceInsight` fixtures build them inline rather
  than reusing `RaceStateFactory`, because that fixture lives in
  `app/src/test`, a different source set `androidTest` cannot see. Since
  "LIVE" can legitimately appear twice at once (the session header's status
  word and the status bar's session chip), tests that touch the full
  `RaceScreen`/`MainActivity` check presence by node count rather than
  `onNodeWithText(...).assertExists()`, same pattern as the "Analysis"/
  "Settings" bottom-nav collision above.
- **Full-stack data test**: `RaceScreenTest` (`app/src/androidTest`) is the
  one test that actually proves "Race screen displays race data" rather than
  just "Race screen renders": it injects the real `RaceEngine` singleton via
  Hilt, calls `updateState()` on it directly (the same call `RaceSimulator`
  makes), and asserts the driver name/abbreviation it just pushed in shows
  up on screen — exercising the full `RaceEngine -> RaceTimeline ->
  RaceViewModel -> RaceScreen` chain with no fakes. Unlike the unit tests,
  this uses the real Hilt-provided `Dispatchers.Default`, so `RaceTimeline`'s
  auto-subscription genuinely runs in the background; the test's
  `waitUntil(...)` polling accounts for that small real (not virtual) delay.

New features should follow the same split: pure logic in a ViewModel test,
Compose component tests for view logic that doesn't need a full Activity,
end-to-end flow in a Compose navigation/data test — rather than trying to
unit test Composables directly.

## Adding a new feature

- **New bottom-nav tab**: add an entry to `ApexBottomDestination`, create
  `feature/<name>/<Name>Screen.kt` + `<Name>ViewModel.kt` following the
  existing screens as a template, and register the composable in
  `ApexMainScreen`'s nested `NavHost`.
- **New screen reached by push navigation** (not a tab): add a route to
  `ApexDestination` and register it in the appropriate `NavHost` — the
  top-level one in `ApexNavHost` if it sits outside the bottom-nav shell, or
  the nested one in `ApexMainScreen` if it's pushed from within a tab.
- In both cases: immutable `UiState`, `StateFlow`, `@HiltViewModel`,
  `hiltViewModel()`; don't inject `NavController` into the screen or
  ViewModel, pass navigation as lambdas instead.
- If the feature needs real data, introduce `data/` and `domain/` packages
  at that point (see [Package structure](#package-structure)), not before.
