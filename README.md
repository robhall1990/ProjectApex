# Project Apex

The smartest way to watch Formula 1.

Project Apex is a native Android client for live F1 timing, circuit visualisation,
and race analysis. This repository currently contains the production foundation,
the bottom-nav app shell, a pure-Kotlin race domain model (`RaceEngine`), a
development-only race simulator, the signature "unwrapped track" race-distance
ribbon and leaderboard, a replay timeline (`RaceTimeline`) that records race
history and lets the UI scrub through it independently of the live state, and
a deterministic rule-based analysis engine (`RaceIntelligenceEngine`) that
flags battles, gap trends, and tyre concerns — no AI models, no external
calls. There is still no real data source — everything on screen today comes
from the Developer Mode simulator, not a live timing feed.

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
├── domain/
│   ├── DefaultDispatcher.kt  Shared background-dispatcher qualifier
│   ├── model/         Race domain models (Driver, CarState, RaceState, ...) — pure Kotlin
│   ├── race/          RaceEngine — owns the current RaceState as a StateFlow
│   ├── simulation/    RaceSimulator — generates believable fake race updates
│   │                   for UI development (dev-only, not production data)
│   ├── timeline/      RaceTimeline — records RaceState history; previous/next/seek
│   └── intelligence/  RaceIntelligenceEngine — deterministic RaceInsight
│                       detectors (battles, gap trends, tyre concerns)
├── feature/
│   ├── splash/        Splash screen (Screen + ViewModel)
│   ├── race/          Screen + 2 ViewModels (Race data + Race Intelligence)
│   │   └── components/  UnwrappedTrackView, RaceLeaderboard, ReplayControls,
│   │                     RaceIntelligenceSection
│   ├── analysis/      Analysis tab (Screen + ViewModel)
│   └── settings/      Settings tab (Screen + ViewModel + Developer Mode controls)
├── ApexApplication.kt Hilt application entry point
└── MainActivity.kt    Single-activity host for the Compose navigation graph
```

`domain/` holds the race data model, `RaceEngine` (owns the current race
state as a `StateFlow`), `RaceSimulator` (a development-only tool that
generates believable fake race updates once a second), `RaceTimeline`
(records every state `RaceEngine` ever holds, capped at 1000 snapshots, and
lets the UI browse that history via previous/next/seek independently of
whatever's currently live), and `RaceIntelligenceEngine` (deterministic rules
over `RaceState` — no AI, no network calls — producing `RaceInsight`s like
"VER and NOR are battling") — all pure Kotlin, no Android or networking
dependencies. The Race screen reads `RaceTimeline` for race data and
`RaceEngine` directly for intelligence (see docs/Architecture.md for why
those differ). `data/` is still absent: it will be introduced when a real
data source (e.g. a live timing feed) exists to push updates into
`RaceEngine`.

## Current screens

- **Splash** — brief branded loading screen, auto-navigates into the main shell.
- **Main shell** — a `Scaffold` with a bottom navigation bar (Race / Analysis /
  Settings) wrapping a nested `NavHost`. Race is the default tab.
- **Race** ("Live Race") — a LIVE MODE/REPLAY MODE indicator with
  Previous/Play-Pause/Next controls (`ReplayControls`), an unwrapped-track
  ribbon (`UnwrappedTrackView`) showing each car's race-distance progress and
  position, a leaderboard (`RaceLeaderboard`) with position/driver/gap, and a
  Race Intelligence section showing the top 3 detected insights. The first
  three render from whatever `RaceTimeline` is currently pointed at (empty
  until Developer Mode is started); Race Intelligence always reflects the
  live race, independent of replay position.
- **Analysis** — placeholder tab for future session analysis tooling.
- **Settings** — placeholder tab for user preferences, plus a **Developer
  Mode** card (see below).

## Developer Mode (manual testing only)

Settings has a "Developer Mode" card with **Start Simulated Race** / **Stop
Simulation** buttons and a status line. Starting it seeds a 20-car field
(VER, NOR, PIA, LEC, HAM, RUS + 14 placeholders) into `RaceEngine` and
advances it once a second — gaps drift, tyres age, laps tick over, and cars
occasionally swap adjacent positions. Switch to the Race tab while it's
running to watch the track ribbon and leaderboard animate live, or tap
"< Previous" to drop into REPLAY MODE and scrub back through the last
1000 recorded snapshots. This is entirely fake data for exercising the app
during development, not a real session.
