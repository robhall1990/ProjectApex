# Desktop target

A secondary, non-shipping target: the same engine that drives the Android
app (`RaceEngine`, `OpenF1LiveDataSource`, the intelligence adapter — all in
the `:core` module) wrapped in a small [Compose Multiplatform
Desktop](https://www.jetbrains.com/compose-multiplatform/) window, so the
pipeline can be exercised on a Windows/Mac/Linux dev machine without an
Android emulator or device.

**This is a developer tool, not a product surface.** It exists to answer
"does the engine work" quickly. It is not the Roadmap's Android UI ported to
desktop, has no design polish, and should stay out of anything user-facing
until (if ever) a deliberate decision is made to productize it.

## What it is

- `:core` — extracted from `:app` specifically to make this possible: plain
  Kotlin/JVM, zero Android imports, contains `domain/`, `data/openf1/`,
  `intelligence/adapter/`, `core/model/`, and the presentation-formatting
  `ObservationPresenter`/`RaceInsightUi`. `:app` (Android) and `:desktop`
  both depend on it; neither depends on the other.
- `:desktop` — `desktop/src/main/kotlin/com/projectapex/desktop/`:
  - `AppContainer.kt` — manual composition root (no Hilt; the object graph
    is small enough that hand-wiring is simpler than introducing a second
    DI framework for one module). Desktop has no foreground/background
    concept, so `OpenF1LiveDataSource` gets an always-`true` flow.
  - `Main.kt` — the `application { Window { ... } }` entry point.
  - `ui/DesktopApp.kt`, `ControlsBar.kt`, `LeaderboardTable.kt`,
    `InsightsPanel.kt` — a fresh, minimal screen (start simulator / start
    live session / stop, a leaderboard table, an insight feed).
    Deliberately not a port of `feature/race`'s mobile Composables.

## Running it

On a machine with normal internet access (this does **not** work from an
environment that blocks `dl.google.com` — see Verification below):

```bash
./gradlew :desktop:run
```

Or open the project in IntelliJ IDEA / Android Studio and run the
`com.projectapex.desktop.MainKt` configuration directly.

To build a native installer (`.msi`/`.dmg`/`.deb`, per
`desktop/build.gradle.kts`'s `nativeDistributions`):

```bash
./gradlew :desktop:packageDistributionForCurrentOS
```

## Verification

This module was built and reviewed in a sandboxed environment whose network
policy blocks `dl.google.com` — the same constraint that's blocked verifying
`:app`'s Android build throughout this project's history. What was and
wasn't actually confirmed:

- **Compiled for real**, via a standalone throwaway Gradle project (Maven
  Central + Gradle Plugin Portal only, no `:app`/AGP involved) with the
  actual `:core` and `:desktop` sources copied in and the real Compose
  Multiplatform Desktop dependencies resolved from Maven Central. This
  caught and fixed two real bugs (a bad import, a missing transitive
  assumption) before they'd have surfaced on a real machine.
- **Not run** — `compose.material3`/`compose.foundation` pull in genuine
  `androidx.lifecycle`/`androidx.annotation`/`androidx.collection` artifacts
  at *runtime* (not just compile time) that are only published to
  `dl.google.com`, not Maven Central. `:desktop:run`'s dependency
  resolution needs that host reachable. This is not expected to be a
  problem on a normal developer machine (Android Studio's default repo
  configuration includes `google()`, and this project's own
  `settings.gradle.kts` already declares it) — it's specifically a gap in
  what could be verified from this sandbox.
- **No display was available either** — even with `runtimeClasspath`
  resolved, rendering a real window needs a display server (a plain X11
  session or `Xvfb` is fine; this sandbox has `Xvfb` available but that's
  moot without the dependency resolution above succeeding first).

**Before relying on this for real engine testing:** run `:desktop:run`
once on your machine and confirm the window opens, "Start Simulator"
populates the leaderboard within a couple of seconds, and "Start Live
(OpenF1)" (with a total-laps value) connects during a real session. If
anything in `AppContainer.kt`'s manual wiring is subtly wrong, this is
where it'll show up — it was written by careful inspection against
`core/di/NetworkModule.kt`/`DomainModule.kt`'s Hilt-based wiring, not
verified against a running instance.

## Known gaps / deliberately out of scope for v1

- No tests for the `:desktop` module itself (`AppContainer`'s wiring and
  the UI composables) — the engine it drives is already covered by
  `:core`'s test suite; this module is thin enough that manual
  verification (above) was judged sufficient for a dev tool. Revisit if
  `:desktop` grows real logic of its own.
- No settings persistence, no window-state restore, no packaging/signing
  for distribution — not needed for a local dev tool.
- Logging is always on (`HttpLoggingInterceptor` unconditionally added in
  `AppContainer`) — there's no debug/release distinction for a desktop dev
  tool the way `:app` has (APX-014).
