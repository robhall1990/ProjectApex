# Project Apex

The smartest way to watch Formula 1.

Project Apex is a native Android client for live F1 timing, circuit visualisation,
and race analysis. This repository currently contains the production foundation:
architecture, navigation, theming, and four placeholder screens. No business
logic (live timing, replay, strategy insights, etc.) has been implemented yet.

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
│   └── navigation/    NavHost and route definitions
├── feature/
│   ├── splash/        Splash screen (Screen + ViewModel)
│   ├── home/          Home screen (Screen + ViewModel)
│   ├── race/          Race screen (Screen + ViewModel)
│   └── settings/      Settings screen (Screen + ViewModel)
├── ApexApplication.kt Hilt application entry point
└── MainActivity.kt    Single-activity host for the Compose navigation graph
```

`data/` and `domain/` packages are intentionally not present yet — they will
be introduced when the first real feature (e.g. live timing) needs a
repository or use case, rather than scaffolded empty.

## Current screens

- **Splash** — brief branded loading screen, auto-navigates to Home.
- **Home** — "Project Apex" title, tagline, and an "Enter Garage" button that
  navigates to Race. A settings icon in the top bar navigates to Settings.
- **Race** — placeholder for the live timing / circuit visualisation surface.
- **Settings** — placeholder for user preferences.
