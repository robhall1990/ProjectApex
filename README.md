# Project Apex

The smartest way to watch Formula 1.

Project Apex is a native Android client for live F1 timing, circuit visualisation,
and race analysis. This repository currently contains the production foundation
and the first real dashboard shell — architecture, navigation, theming, and the
Apex Command Centre (Race, Analysis, Settings). No business logic (live timing,
replay, strategy insights, etc.) has been implemented yet; all session data on
screen is static placeholder state.

See [docs/Architecture.md](docs/Architecture.md) for the architectural
decisions behind this foundation and the conventions future features should
follow.

## Tech stack

| Concern          | Choice                              |
|-------------------|--------------------------------------|
| Language           | Kotlin                              |
| UI                 | Jetpack Compose (Material 3)        |
| Architecture       | MVVM                                |
| DI                 | Hilt                                |
| Navigation         | Navigation Compose                  |
| Async              | Kotlin Coroutines + Flow            |
| Serialization      | kotlinx.serialization               |
| Networking         | Retrofit + OkHttp *(dependency only, not yet wired up)* |
| Image loading      | Coil                                |
| Persistence        | Room *(dependency only, not yet wired up)* |
| Icons              | Compose Material Icons (core + extended) |
| Testing            | JUnit, Compose UI Testing, Hilt Testing |

- **Min SDK:** 28
- **Target / Compile SDK:** 35

## Getting started

### Prerequisites

- Android Studio (Ladybug or newer) with a JDK it can use for Gradle (the
  bundled JBR works).
- Android SDK platform 35 and build-tools 34+ installed via the SDK Manager.

### Build and run

```bash
./gradlew :app:assembleDebug
```

Or open the project root in Android Studio and let it sync — no manual
configuration is required beyond a valid `local.properties` `sdk.dir`
(Android Studio generates this automatically on first sync).

### Run tests

```bash
# JVM unit tests (ViewModels)
./gradlew :app:testDebugUnitTest

# Instrumented Compose UI tests (requires a connected device or emulator)
./gradlew :app:connectedDebugAndroidTest
```

## Project structure

```
com.projectapex
├── core/
│   ├── theme/        Material 3 theme, color scheme, typography
│   ├── model/         Shared, feature-agnostic domain-ish models (e.g. SessionState)
│   ├── ui/            Reusable Compose components (e.g. ApexCard)
│   └── navigation/    Top-level NavHost, bottom-nav shell, route definitions
├── feature/
│   ├── splash/        Splash screen (Screen + ViewModel)
│   ├── race/          Apex Command Centre dashboard (Screen + ViewModel + cards)
│   ├── analysis/      Analysis tab (Screen + ViewModel)
│   └── settings/      Settings tab (Screen + ViewModel)
├── ApexApplication.kt Hilt application entry point
└── MainActivity.kt    Single-activity host for the Compose navigation graph
```

`data/` and `domain/` packages are intentionally not present yet — they will
be introduced when the first real feature (e.g. live timing) needs a
repository or use case, rather than scaffolded empty.

## Current screens

- **Splash** — brief branded loading screen, auto-navigates into the main shell.
- **Main shell** — a `Scaffold` with a bottom navigation bar (Race / Analysis /
  Settings) wrapping a nested `NavHost`. Race is the default tab.
- **Race** ("Apex Command Centre") — the header ("PROJECT APEX" + tagline), a
  Next Session card (event, session type, time, status — static placeholder
  data), an "ENTER LIVE SESSION" button (currently a no-op), and a Race
  Intelligence card listing upcoming capabilities.
- **Analysis** — placeholder tab for future session analysis tooling.
- **Settings** — placeholder tab for user preferences.
