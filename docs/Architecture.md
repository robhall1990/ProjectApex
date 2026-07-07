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
│   ├── model/         Driver, TyreCompound, CarState, RaceState — pure Kotlin,
│   │                   no Android imports
│   ├── race/          RaceEngine — owns the current RaceState
│   └── simulation/    RaceSimulator — generates believable fake RaceState
│                       updates for UI development, sits above RaceEngine
├── feature/
│   ├── splash/
│   ├── race/          Screen + ViewModel + NextSessionCard + RaceIntelligenceCard
│   ├── analysis/
│   └── settings/
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
External Data Source -> RaceEngine -> Android UI
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
  one `RaceEngine` app-wide — `RaceSimulator` and any future ViewModel that
  reads race state all get the same instance. Both annotations are plain
  `javax.inject`, so the file still has no Hilt/Android framework import of
  its own. The Race dashboard still reads its own hardcoded `RaceUiState`,
  though — wiring a ViewModel to actually read from `RaceEngine` is a future
  ticket, not this one.
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

- **`@Singleton @Inject constructor(RaceEngine, @SimulationDispatcher CoroutineDispatcher)`.**
  The dispatcher is injected, not hardcoded to `Dispatchers.Default`,
  specifically so `RaceSimulatorTest` can substitute a `StandardTestDispatcher`
  bound to the test's `TestCoroutineScheduler` — that makes the real
  `delay(1_000)` in the tick loop advance under virtual time
  (`advanceTimeBy`/`runCurrent`) instead of costing a real second per test.
  `@SimulationDispatcher` is a plain `javax.inject.Qualifier` annotation
  living in `domain/simulation/`; the actual `@Provides` binding
  (`core/di/DomainModule.kt`) lives outside `domain/`, since a Hilt `@Module`
  necessarily imports `dagger.hilt.*`.
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

Screens do not call `NavController` methods directly. `ENTER LIVE SESSION` on
the Race dashboard is a deliberate no-op (`onClick = {}`) rather than a
lambda parameter, because there is nowhere for it to navigate yet — it is a
placeholder for a future live-session destination, not a wired callback with
nothing behind it.

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
padded-`Column` treatment used by every dashboard card. `NextSessionCard` and
`RaceIntelligenceCard` build on top of it but stay in `feature/race`, because
their content (event name, session status, capability checklist) is
Race-specific — only the container styling is shared. If a future feature
(e.g. Analysis) needs a visually similar but differently-populated card, it
should compose `ApexCard` the same way rather than reusing a Race-specific
card component.

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
- `RaceEngine` and `RaceSimulator` — `@Singleton @Inject constructor(...)`,
  constructor-injected directly (no `@Module` needed for either class
  itself — Hilt/Dagger discovers `@Inject` constructors automatically).
- `core/di/DomainModule.kt` — the one Hilt `@Module` in the project so far,
  `@InstallIn(SingletonComponent::class)`, providing the one thing that
  *can't* be constructor-injected: a `@SimulationDispatcher`-qualified
  `CoroutineDispatcher` (`Dispatchers.Default`), since `CoroutineDispatcher`
  has no injectable constructor of its own.

Retrofit, OkHttp, Room, and Coil are present as Gradle dependencies for
future networking and persistence work, but are deliberately not wired into
DI or used anywhere — adding a `NetworkModule` or `DatabaseModule` before
there is a real API client or entity would be dead code.

## Testing strategy

- **ViewModel unit tests** (`app/src/test`) — plain JUnit, no
  Android/Hilt/Compose dependency, run on the JVM. `RaceViewModelTest`
  asserts the default `RaceUiState` (offline session, event name, capability
  list) without touching Compose or Android at all.
- **Domain unit tests** (`app/src/test`) — `RaceEngineTest` and
  `RaceSimulatorTest` construct `RaceEngine()`/`RaceSimulator(...)` directly,
  bypassing Hilt entirely (there's no Android context needed to build a pure
  Kotlin class). `RaceSimulatorTest` passes a `StandardTestDispatcher(testScheduler)`
  in place of the Hilt-provided `Dispatchers.Default`, so `advanceTimeBy()`
  fast-forwards the simulator's real `delay(1_000)` tick loop under virtual
  time instead of the test actually waiting on a wall clock.
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

New features should follow the same split: pure logic in a ViewModel test,
end-to-end flow in a Compose navigation test — rather than trying to unit
test Composables directly.

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
