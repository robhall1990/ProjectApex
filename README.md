# Project Apex

The smartest way to watch Formula 1.

Project Apex is a native Android client for live F1 timing, circuit visualisation,
and race analysis. This repository currently contains the production foundation,
the bottom-nav app shell, a pure-Kotlin race domain model (`RaceEngine`), a
development-only race simulator, the signature "unwrapped track" race-distance
ribbon and leaderboard, a replay timeline (`RaceTimeline`) that records race
history and lets the UI scrub through it independently of the live state, and
a modular, prediction-focused race intelligence platform (the `:intelligence`
module) whose combat detectors flag battles, DRS chances, tyre cliffs, and
leader pressure and rank the three most important things happening — no AI
models, no external calls. There is still no real data source — everything on
screen today comes from the Developer Mode simulator, not a live timing feed.

See [docs/Roadmap.md](docs/Roadmap.md) for the phased route from this
prototype to a commercial-grade product, and
[docs/ExecutionPlan.md](docs/ExecutionPlan.md) for the ticket-level
breakdown (APX-014 onwards) that implements it.
See [docs/Architecture.md](docs/Architecture.md) for the architectural
decisions behind this foundation and the conventions future features should
follow. See [docs/RaceIntelligencePlatform.md](docs/RaceIntelligencePlatform.md)
for the full Race Intelligence Platform specification (the prediction-focused
engine + LLM narration layer being built out slice by slice) and
[docs/DetectionFramework.md](docs/DetectionFramework.md) for the detector /
prioritisation framework the combat detectors plug into.

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
- **Target / Compile SDK:** 37

## Getting started

### Prerequisites

- Android Studio (Ladybug or newer) with a JDK it can use for Gradle (the
  bundled JBR works).
- Android SDK platform 37 and build-tools 34+ installed via the SDK Manager.

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

# :core (domain, live data source, intelligence adapter) and :intelligence
./gradlew :core:test :intelligence:test

# Instrumented Compose UI tests (requires a connected device or emulator)
./gradlew :app:connectedDebugAndroidTest
```

### Desktop target

`:desktop` runs the same engine (`:core`) in a small Compose Multiplatform
window — no Android device/emulator needed. See
[docs/Desktop.md](docs/Desktop.md).

```bash
./gradlew :desktop:run
```

## Project structure

```
:intelligence  (pure Kotlin/JVM module — no Android dependencies)
com.projectapex.intelligence
├── api/           IntelligenceConfig — every platform constant, as data
├── ingest/        TimingFrame (canonical input), FrameValidator,
│                   FrameNormaliser, IngestPipeline (the synchronous cheap path)
├── events/        EngineEvent, EventDeriver (snapshots → edges), EventLog
├── features/      FeatureStore/FeatureView, lap/stint books, pace + tyre-deg
│                   models (fuel-corrected OLS), pit-loss model, TrafficProjector
├── detect/        Observation model, Detector SPI, DetectorEngine
│                   (registration-only extensibility, failure isolation, metrics)
└── rank/          PrioritisationEngine (configurable scoring), RacePulse

:core  (pure Kotlin/JVM module — no Android dependencies — see docs/Desktop.md)
com.projectapex
├── domain/
│   ├── DefaultDispatcher.kt, AppForegroundState.kt  Hilt qualifiers
│   ├── model/         Race domain models (Driver, CarState, RaceState, TrackStatus, ...)
│   ├── race/          RaceEngine — owns the current RaceState as a StateFlow
│   ├── simulation/    RaceSimulator — generates believable fake race updates
│   │                   for UI development (dev-only, not production data)
│   ├── timeline/      RaceTimeline — records RaceState history; previous/next/seek
│   └── livedata/      OpenF1LiveDataSource, OpenF1RaceStateMapper — the live feed
├── data/openf1/       OpenF1Api (Retrofit), DTOs, LiveSessionCache (incremental polling)
├── core/model/        SessionStatus
├── intelligence/adapter/  RaceStateAdapter (RaceState → TimingFrame) and
│                          RacePulseEngine (drives :intelligence from RaceEngine,
│                          exposes StateFlow<RacePulse>) — lives here (not :intelligence)
│                          because it wires both sides; :intelligence never imports app types
└── feature/race/  ObservationPresenter, RaceInsightUi — RacePulse → display strings,
                    shared by both :app and :desktop's UIs

:app  (Android — depends on :core and :intelligence)
com.projectapex
├── core/
│   ├── theme/        Material 3 theme, color scheme, typography
│   ├── ui/            Reusable Compose components (e.g. ApexCard)
│   ├── navigation/    Top-level NavHost, bottom-nav shell, route definitions
│   ├── di/            Hilt modules (NetworkModule, DomainModule)
│   └── AppForegroundMonitor.kt  Android ProcessLifecycleOwner → StateFlow<Boolean>
├── feature/
│   ├── splash/        Splash screen (Screen + ViewModel)
│   ├── race/          Screen + RaceViewModel combining race data (timeline) and
│   │   │               intelligence (RacePulse via :core's ObservationPresenter)
│   │   └── components/  SessionHeader, RaceStatusBar, UnwrappedTrackView,
│   │                     RaceIntelligenceSection, RaceInsightCard, RaceLeaderboard,
│   │                     LeaderboardRow, PanelHeader, SectionCard, StatusChip, InfoRow
│   ├── analysis/      Analysis tab (Screen + ViewModel)
│   └── settings/      Settings tab (Screen + ViewModel, Developer Mode + Live Session cards)
├── ApexApplication.kt Hilt application entry point
└── MainActivity.kt    Single-activity host for the Compose navigation graph

:desktop  (Compose Multiplatform Desktop — depends on :core and :intelligence,
           no Hilt/Android — dev tool for exercising the engine off-device;
           see docs/Desktop.md)
com.projectapex.desktop
├── AppContainer.kt    Manual composition root (mirrors :app's Hilt modules by hand)
├── Main.kt             Window entry point
└── ui/                 DesktopApp, ControlsBar, LeaderboardTable, InsightsPanel
```

The `:intelligence` module implements the
[Race Intelligence Platform](docs/RaceIntelligencePlatform.md) in slices:
APX-010 built the ingestion and features layers; APX-011 added the
[detection framework and prioritisation engine](docs/DetectionFramework.md) —
a registration-only detector platform (`Detector` → `DetectorEngine` →
`Observation`s → `PrioritisationEngine` → `RacePulse`) with failure isolation,
per-detector metrics, and configurable multiplicative scoring; APX-012 added
the first eight **combat detectors** (battle, DRS active/imminent, gap
closing/increasing, leader pressure, fastest pace, tyre concern/cliff) and
wired the platform into the app, replacing the legacy `RaceIntelligenceEngine`.
It is deliberately a plain Kotlin/JVM module — no Android plugin — so an
accidental `android.*` import cannot compile, and the same artifact runs
on-device, in fast JVM tests, or server-side. Prediction and narration land in
later tickets on top of these foundations.

`domain/` holds the race data model, `RaceEngine` (owns the current race
state as a `StateFlow`), `RaceSimulator` (a development-only tool that
generates believable fake race updates once a second), and `RaceTimeline`
(records every state `RaceEngine` ever holds, capped at 1000 snapshots, and
lets the UI browse that history via previous/next/seek independently of
whatever's currently live) — all pure Kotlin, no Android or networking
dependencies. `RaceViewModel` reads `RaceTimeline` for race data and the
`RacePulseEngine` (fed from the live `RaceEngine`) for intelligence, combining
both into one `RaceUiState`. `data/` is still absent: it will be introduced
when a real data source (e.g. a live timing feed) exists to push updates into
`RaceEngine`.

## Current screens

- **Splash** — brief branded loading screen, auto-navigates into the main shell.
- **Main shell** — a `Scaffold` with a bottom navigation bar (Race / Analysis /
  Settings) wrapping a nested `NavHost`. Race is the default tab.
- **Race** — a premium motorsport-dashboard layout of five sections: a
  **Session Header** (event name, lap counter, LIVE/REPLAY banner,
  Previous/Play-Pause/Next controls), a **Race Status Bar** (wrapping chips:
  Session, Simulation, Replay-if-active, plus disabled Track/Weather/DRS
  placeholders for future features), the **Unwrapped Track** ribbon (leader
  rendered larger, not just a different colour), **Race Intelligence** (up
  to 3 insight cards — emoji, title, description, priority dot — as a
  divided feed, highest priority first), and the **Leaderboard**
  (Pos/Driver/Gap/Tyre columns with headers, leader row highlighted). The
  header, status bar, track, and leaderboard render from whatever
  `RaceTimeline` is currently pointed at (empty until Developer Mode is
  started); Race Intelligence always reflects the live race, independent of
  replay position.
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
"< Previous" to drop into REPLAY mode and scrub back through the last
1000 recorded snapshots. This is entirely fake data for exercising the app
during development, not a real session.
