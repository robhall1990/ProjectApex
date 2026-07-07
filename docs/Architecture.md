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
│   └── navigation/    ApexDestination/ApexBottomDestination (routes), ApexNavHost
│                       (top-level graph), ApexMainScreen (bottom-nav shell),
│                       ApexBottomNavBar (reusable nav bar)
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

`data/` and `domain/` are absent for the same reason: there is no repository,
API client, or use case yet, because no feature reads or writes real data.
When the first feature needs one (most likely live timing), add:

```
data/
├── remote/        Retrofit service interfaces + DTOs
├── local/         Room entities/DAOs
└── repository/    Repository implementations, exposed via a domain interface
domain/
├── model/         Plain Kotlin domain models (not DTOs, not entities)
└── repository/    Repository interfaces consumed by ViewModels
```

Repositories should be bound via Hilt `@Binds` in a module under
`core/di/` (also not yet created), not instantiated directly in ViewModels.

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

Hilt is wired at three points:

- `ApexApplication` — `@HiltAndroidApp`.
- `MainActivity` — `@AndroidEntryPoint`.
- Every ViewModel — `@HiltViewModel`, obtained in Compose via
  `hiltViewModel()`.

There are no Hilt modules yet (no `core/di/` package). Retrofit, OkHttp,
Room, and Coil are present as Gradle dependencies for future networking and
persistence work, but are deliberately not wired into DI or used anywhere —
adding a `NetworkModule` or `DatabaseModule` before there is a real API
client or entity would be dead code.

## Testing strategy

- **ViewModel unit tests** (`app/src/test`) — plain JUnit, no
  Android/Hilt/Compose dependency, run on the JVM. `RaceViewModelTest`
  asserts the default `RaceUiState` (offline session, event name, capability
  list) without touching Compose or Android at all.
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
