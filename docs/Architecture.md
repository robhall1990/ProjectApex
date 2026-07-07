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

This pattern is applied uniformly to all four screens — including Race and
Settings, which have no real state yet — so that the next engineer adding a
feature has exactly one pattern to copy, not a judgment call about when
MVVM "is worth it."

## Package structure

```
com.projectapex
├── core/
│   ├── theme/        Cross-feature Compose theme (colors, typography, MaterialTheme wrapper)
│   └── navigation/    ApexDestination (routes) and ApexNavHost (graph)
├── feature/
│   ├── splash/
│   ├── home/
│   ├── race/
│   └── settings/
├── ApexApplication.kt
└── MainActivity.kt
```

`core/ui` and `core/util` are not present. They will be added only when a
composable or utility function is genuinely shared across two or more
features — introducing them speculatively would be premature abstraction.

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

`ApexNavHost` is the single `NavHost` for the app, declared once in
`MainActivity`. Routes are defined as a sealed class (`ApexDestination`)
rather than raw strings, so route typos fail at compile time. Screens do
not call `NavController` methods directly — `ApexNavHost` passes navigation
as plain lambdas (`onEnterGarage`, `onBack`, etc.) into each screen. This
keeps screens navigation-agnostic and easy to preview/test in isolation.

Splash removes itself from the back stack on navigating to Home
(`popUpTo(Splash) { inclusive = true }`), so back-press from Home exits the
app rather than re-showing Splash.

## Theming

Material 3, dark-mode-first, no Formula One branding (no red/checkered
motifs). The accent is a custom purple (`#7C5CFF` / `#A98BFF`) intended to
read as premium/technical rather than sporty.

`dynamicColor` in `ProjectApexTheme` defaults to `false`. Android 12+
wallpaper-based dynamic color is supported but opt-in, because the brand
palette is a deliberate design choice that should not be silently overridden
by a user's wallpaper.

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
  Android/Hilt/Compose dependency, run on the JVM. `HomeViewModelTest` is
  the existing example.
- **Compose UI / navigation tests** (`app/src/androidTest`) — use
  `createAndroidComposeRule<MainActivity>()` plus `HiltAndroidRule` for
  screens wired through Hilt, and a custom `HiltTestRunner`
  (`testInstrumentationRunner`) that swaps in `HiltTestApplication`.
  `ApexNavigationTest` exercises the real Splash → Home → Race flow rather
  than testing a screen in isolation, since navigation wiring is exactly
  what a foundation-level test should catch.

New features should follow the same split: pure logic in a ViewModel test,
end-to-end flow in a Compose navigation test — rather than trying to unit
test Composables directly.

## Adding a new feature screen

1. Create `feature/<name>/<Name>Screen.kt` and `<Name>ViewModel.kt` following
   the existing four as a template (immutable `UiState`, `StateFlow`,
   `@HiltViewModel`, `hiltViewModel()`).
2. Add a route to `ApexDestination`.
3. Register the composable in `ApexNavHost`, passing navigation callbacks as
   lambdas — do not inject `NavController` into the screen or ViewModel.
4. If the feature needs real data, introduce `data/` and `domain/` packages
   at that point (see [Package structure](#package-structure)), not before.
