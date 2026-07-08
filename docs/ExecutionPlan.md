# Execution plan — ticket-level breakdown

The low-level companion to [Roadmap.md](Roadmap.md). Tickets continue the
existing APX numbering (APX-013 was the OpenF1 live data source). Within a
phase, tickets are listed in intended execution order; dependencies are
called out explicitly where they cross that order.

Conventions used below:

- **AC** = acceptance criteria. A ticket is done when every AC holds and
  `:app:testDebugUnitTest` + `:intelligence:test` pass.
- File paths are relative to the repo root. "New" marks files that don't
  exist yet.
- Every ticket that changes behaviour updates `docs/Architecture.md` (or
  the relevant spec) in the same commit — that discipline is existing
  convention, keep it.

---

## Phase 0 — Don't ship broken

### APX-014 · Release build integrity

The release build has never been run: `isMinifyEnabled = true` with an
empty `proguard-rules.pro`, and Retrofit + kotlinx.serialization both
lean on reflection/generated code that R8 can strip.

**Changes**

- `app/proguard-rules.pro`: add keep rules. Modern Retrofit, OkHttp and
  kotlinx.serialization ship consumer rules, so start minimal and add
  only what verification proves necessary. Expected baseline:
  - `-keepattributes Signature, InnerClasses, EnclosingMethod,
    AnnotationDefault, *Annotation*` (Retrofit generic reflection).
  - Keep rules for `@Serializable` DTO serializers if the shipped
    consumer rules prove insufficient (kotlinx.serialization ≥1.7 usually
    covers this; verify rather than assume).
  - `-keepattributes SourceFile,LineNumberTable` +
    `-renamesourcefileattribute SourceFile` for legible crash stacks.
- `app/src/main/java/com/projectapex/core/di/NetworkModule.kt`: gate
  `HttpLoggingInterceptor` on `BuildConfig.DEBUG` (level `BASIC` in
  debug, interceptor omitted entirely in release). Requires
  `buildFeatures { buildConfig = true }` in `app/build.gradle.kts`
  (AGP 9 defaults it off).
- `app/build.gradle.kts`: add a `debug` block only if needed for the
  above; no other build-type changes.

**AC**

1. `./gradlew :app:assembleRelease` succeeds.
2. The release APK (signed with a debug key is fine at this stage)
   installs and completes one full poll cycle against a live or recent
   session — API call, JSON decode, mapper, UI render — with R8 enabled.
3. `adb logcat` during a release-build session shows no OkHttp request
   logging.
4. A deliberately malformed field name in one DTO still fails the same
   way in release as in debug (i.e. R8 didn't silently change
   serialization behaviour).

### APX-015 · Incremental polling

`OpenF1LiveDataSource` currently refetches full session history for
`/position`, `/intervals` and `/laps` every 5 s. Payloads grow
monotonically through a race. Switch those three endpoints to
`date>`-cursored fetches merged into an accumulating per-driver store.
`/stints`, `/pit` and `/race_control` stay full-fetch (bounded, small);
`/drivers` and session resolution stay fetch-once.

**Changes**

- `app/src/main/java/com/projectapex/data/openf1/OpenF1Api.kt`: add
  cursored overload parameters. OpenF1 encodes the operator in the query
  *name* (`?date>2026-07-11T14:00:00`); Retrofit expresses that as
  `@Query(value = "date>", encoded = true) after: String?`. Verify the
  encoding against the real API on-device (part of APX-017's capture
  run).
- New `app/src/main/java/com/projectapex/data/openf1/LiveSessionCache.kt`:
  a plain accumulating store owned by the data source — per-driver
  latest `PositionDto`/`IntervalDto`/highest-lap `LapDto`, plus the
  high-water-mark `date` cursor per endpoint. Pure Kotlin, no
  synchronisation needed (single-writer from the poll loop).
- `app/src/main/java/com/projectapex/domain/livedata/OpenF1RaceStateMapper.kt`:
  accept the cache's merged views (`Map<Int, PositionDto>` etc.) instead
  of raw full lists. The "latest per driver" reduction moves into
  `LiveSessionCache`; the mapper keeps permutation/fallback logic
  unchanged.
- `app/src/main/java/com/projectapex/domain/livedata/OpenF1LiveDataSource.kt`:
  thread the cache through the poll loop; reset it on `start()`.
- Tests: extend `OpenF1RaceStateMapperTest` for the new signature; new
  `LiveSessionCacheTest` (cursor advances, merge keeps latest-by-date,
  out-of-order records don't regress state, reset clears).

**AC**

1. Steady-state poll requests carry a `date>` cursor and responses are
   small (verify with the debug logging from APX-014 during the
   APX-017 device run).
2. A driver with no new records this poll retains their previous
   position/gap/lap in the produced `RaceState`.
3. Poll payload size no longer grows with race duration.
4. Existing failure semantics unchanged (failed poll → last-good state,
   backoff, `Error` status).

### APX-016 · Lifecycle-aware polling

Polling currently continues while the app is backgrounded (singleton
scope, no lifecycle linkage) — battery and data cost for zero user value.

**Changes**

- `gradle/libs.versions.toml` + `app/build.gradle.kts`: add
  `androidx.lifecycle:lifecycle-process`.
- New `app/src/main/java/com/projectapex/core/AppForegroundMonitor.kt`:
  `@Singleton` wrapping `ProcessLifecycleOwner` into
  `isForeground: StateFlow<Boolean>`. Lives in `core/` (it touches
  Android), injected into domain via its flow only.
- `OpenF1LiveDataSource`: while running, suspend the poll loop when
  `isForeground` is false (`isForeground.first { it }` at the top of the
  loop — session stays "started", polling just idles); on return to
  foreground the next poll fires immediately. `RaceSimulator` is
  dev-only — leave it as is.
- Tests: fake the monitor's flow in `OpenF1LiveDataSourceTest` — assert
  no API calls while backgrounded and an immediate poll on foreground.

**AC**

1. Backgrounding the app stops network traffic within one poll interval;
   foregrounding resumes it immediately with status still `Live`.
2. `stop()`/`start()` behaviour unchanged.
3. No polling wake-ups appear in `adb shell dumpsys batterystats` while
   backgrounded (spot check during APX-017's run).

### APX-017 · On-device verification & golden fixture capture

The OpenF1 DTO schema (APX-013) was written from documentation, not live
responses — this sandbox couldn't reach the API. Verify everything on a
real device during a live session (practice/quali fine) and turn the
session into permanent test assets.

**Steps**

1. Debug build on a physical device during a session; confirm each
   endpoint decodes (watch for `SerializationException` /
   `MissingFieldException` in logcat), the Race screen populates, and
   track-status transitions look right.
2. Capture raw JSON per endpoint (via the debug logging or a curl
   script) at three points: session start, mid-race, and after a pit
   window. Store under
   `app/src/test/resources/fixtures/openf1/<session>/<endpoint>-<t>.json`.
3. Fix any DTO field mismatches found; keep `ignoreUnknownKeys = true`.
4. Verify the `@Query("date>")` encoding assumption from APX-015.

**AC**

1. A checklist of all eight endpoints marked verified-against-live, with
   any schema corrections landed.
2. Fixtures committed and loadable from unit tests (plain
   `Json.decodeFromString` round-trip test per endpoint).
3. Findings recorded in a short `docs/OpenF1Notes.md` (observed
   quirks: leader's `gap_to_leader` value, lapped-car strings,
   race-control message vocabulary, observed response sizes and
   latencies) — this becomes the reference for detector re-tuning.

**Phase 0 exit check** — one continuous hour of a real session on a
minified release build: no crash, stable memory (Android Studio
profiler), network idle while backgrounded.

---

## Phase 1 — Credible beta

### APX-018 · Provider abstraction & layering repair

`domain/livedata` currently imports `data/openf1` DTOs — the domain layer
depends on the data layer, against the project's own conventions, and
OpenF1 is hard-wired as the only possible source.

**Changes**

- New `app/src/main/java/com/projectapex/domain/race/RaceDataSource.kt`:
  interface owned by the domain —
  `start(config: LiveSessionConfig)` / `stop()` /
  `isRunning: StateFlow<Boolean>` /
  `connectionStatus: StateFlow<ConnectionStatus>`. `ConnectionStatus`
  moves here from `domain/livedata`.
- Move `OpenF1LiveDataSource`, `OpenF1RaceStateMapper` and
  `LiveSessionCache` into `data/openf1/` (package
  `com.projectapex.data.openf1`), implementing `RaceDataSource`. The
  domain no longer references any DTO.
- `core/di/`: bind `RaceDataSource` → `OpenF1LiveDataSource`
  (`@Binds` module).
- `SettingsViewModel` (and APX-020's Race-tab entry point) depend on
  `RaceDataSource`, not the concrete class.

**AC**

1. `grep -r "com.projectapex.data" app/src/main/java/com/projectapex/domain/`
   returns nothing.
2. All existing tests pass with only import/package updates.
3. A second `RaceDataSource` implementation (the existing
   `RaceSimulator`, adapted to implement the interface) can be swapped in
   DI without touching feature code — do this adaptation as part of the
   ticket; it also simplifies the mutual-exclusion logic to "stop the
   other `RaceDataSource`".

### APX-019 · Session discovery & the lap-count table

Kill manual total-laps entry and the hardcoded event name.

**Changes**

- `data/openf1`: extend `SessionDto` (circuit/country/location/
  `date_start`/`date_end`/`meeting_key` — fields already documented,
  verified in APX-017); add `/meetings` if the session name alone isn't
  presentable.
- New `app/src/main/java/com/projectapex/domain/session/SessionCatalog.kt`
  + data-side implementation: exposes
  `StateFlow<SessionInfo?>` (current-or-next session: name, circuit,
  type, start time, live-now flag), refreshed on app foreground and
  every few minutes while the Race tab shows the pre-session state.
- New `app/src/main/java/com/projectapex/domain/session/RaceDistances.kt`:
  static circuit → scheduled-laps table for the current season
  (~24 entries; a plain `Map<String, Int>` keyed by
  `circuit_short_name`, values from the published calendar). Fallback:
  0 (header shows lap count only, as today). Add a unit test asserting
  every entry is positive and circuit keys are unique.
- `RaceState` (or a parallel `SessionInfo` flow into the ViewModel —
  prefer the latter to keep `RaceState` per-tick data only): event name
  reaches `SessionHeader` from live data; delete
  `R.string.race_session_event_name` usage in `RaceScreen` and the
  unused `core/model/SessionState.kt`.

**AC**

1. During a live session the header shows the real event name with zero
   configuration.
2. Total laps for a race session comes from the table; the Live Session
   card's manual field is deleted.
3. `SessionState.kt` is gone; no orphaned strings remain.

### APX-020 · Race-tab go-live UX

The watching flow must not start in Settings.

**Changes**

- `feature/race/RaceScreen.kt` + `RaceViewModel`: pre-session/idle state
  renders a session banner card (from `SessionCatalog`): live session →
  "● LIVE — <session name>" with a **Watch Live** button calling
  `RaceDataSource.start`; upcoming → name + countdown, button disabled
  until `date_start − ~5 min`; nothing scheduled → next-race info.
  Connection status surfaces here too (move the status-string mapping in
  `SettingsScreen` into a small shared presenter).
- Developer Mode: `SettingsScreen` wraps the card in
  `if (BuildConfig.DEBUG)`. The Live Session card slims down to a
  stop/status affordance (start now lives on the Race tab); keep it as
  the manual fallback.
- Strings: new `race_golive_*` set; `race_track_no_session` reworded
  (it references Settings).

**AC**

1. Fresh install during a live session: open app → Race tab → one tap →
   data flowing. No Settings visit.
2. Developer Mode absent from release builds.
3. Compose UI test: banner renders correct state for live / upcoming /
   none (fake `SessionCatalog`).

### APX-021 · Team colours & driver identity

OpenF1's `team_colour` and `name_acronym` are fetched and discarded;
placeholders render as "#87".

**Changes**

- `DriverDto`: add `team_colour`, `name_acronym`, `headshot_url`
  (nullable, verified in APX-017).
- `domain/model/Driver.kt`: add `acronym: String` and
  `teamColorArgb: Int?` (parse `"3671C6"` hex once in the mapper;
  domain stays Android-free with a plain Int). Simulator's
  `SyntheticGridFactory` supplies real-ish colours.
- `UnwrappedTrackView`: marker background = team colour (leader keeps
  the size distinction — colour alone already wasn't the differentiator,
  which APX-013's review noted approvingly); label uses `acronym`.
  Contrast-guard: pick black/white content colour by luminance.
- `LeaderboardRow`: leading team-colour bar + acronym; keep full name in
  the driver detail (APX-024's sheet).

**AC**

1. Live session shows correct team colours on ribbon + leaderboard.
2. Missing colour/acronym degrades to today's rendering (no crash, no
   blank marker) — unit test on the mapper, UI test with a null-colour
   driver.
3. WCAG-ish contrast: marker text luminance check has a unit test.

### APX-022 · Real lap times end-to-end

Delete the simulator-era wall-clock lap-time synthesis now that the feed
carries `lap_duration`.

**Changes**

- `domain/model/CarState.kt`: add `lastLapTimeSeconds: Double?`.
- Mapper: populate from the cache's latest `LapDto.lapDuration`.
- `RaceSimulator`: synthesise a plausible value directly (it owns fake
  data generation; the adapter shouldn't).
- `intelligence/adapter/RaceStateAdapter.kt`: pass
  `Seconds(car.lastLapTimeSeconds)` through; delete `synthesiseLapTime`,
  `lastLapEdge`, `LapEdge` and their tests; update the class doc.
- `LeaderboardRow` or driver detail: display last lap.

**AC**

1. `RaceStateAdapter` contains no lap-time synthesis; adapter tests
   updated to assert pass-through (null stays null).
2. Pace-based detectors (fastest-pace, tyre-cliff) fire against golden
   fixtures (APX-025) with plausible values.

### APX-023 · Per-car ribbon progress

Every marker currently sits at the same X (race-level progress). Compute
per-car progress: `(lapsCompleted + intraLapFraction) / totalLaps`, with
`intraLapFraction` estimated from time-into-lap ÷ recent lap time (from
APX-022's data). `CarState.trackProgress()` in `UnwrappedTrackView.kt`
was deliberately shaped for exactly this swap — the layout code doesn't
change.

**AC**

1. During a live session, markers spread according to real relative race
   distance; lapped cars visibly trail.
2. `totalLaps == 0` (unknown) degrades to lap-count-only positioning,
   not a crash or a 0-width pile-up.
3. Unit test the progress function (new home: a small
   `RibbonProgress.kt` beside the view, or the ViewModel).

### APX-024 · Replay scrub bar & driver detail sheet

Two UX quick-wins batched: a `Slider` bound to
`RaceTimeline.seek(index)` replacing prev/next as the primary replay
control (buttons stay for fine stepping), and a bottom sheet on
leaderboard-row tap (full name, team, tyre history from stint data, last
lap, gap trend sparkline if cheap).

**AC**

1. Dragging the slider scrubs snapshots smoothly (1000-snapshot
   timeline); releasing at the right edge returns to live.
2. Row tap opens the sheet; content renders from `RaceState` +
   accumulated stint data only (no new network).
3. Compose UI tests for both.

### APX-025 · Golden replay integration test

The highest-value test in the plan: replay APX-017's captured session
through the entire real pipeline — cache → mapper → `RaceEngine` →
`RaceStateAdapter` → `IngestPipeline` → detectors → prioritisation — and
assert on the output.

**Changes**

- New `app/src/test/java/com/projectapex/integration/GoldenSessionTest.kt`:
  feeds fixture frames in order, asserts (a) zero `Rejected` frames from
  `FrameValidator`, (b) zero detector failures, (c) a hand-checked set of
  expected observations at known points ("battle X vs Y visible by frame
  N"), (d) pulse headline non-empty mid-race.
- Extract fixture-loading helpers shared with APX-017's round-trip
  tests.

**AC**

1. Test runs in `:app:testDebugUnitTest` in <10 s (no network, no
   Android).
2. It fails if someone breaks the mapper's permutation guarantee, the
   adapter mapping, or a detector's real-data behaviour — verify by
   mutation (temporarily break each, watch it fail).

### APX-026 · CI

New `.github/workflows/ci.yml`: on PR and `master` push —
`:intelligence:test`, `:app:testDebugUnitTest`, `:app:lintDebug`,
`:app:assembleRelease` (catches R8 regressions from APX-014 forever),
Gradle cache, JDK 17, test-report artifact upload. Instrumented tests
stay manual until Phase 2 (emulator CI is slow; revisit).

**AC**: a PR with a failing unit test or a release-build breakage goes
red; current branch goes green.

### APX-027 · Crash reporting & minimal analytics

Sentry over Crashlytics (no Google services plumbing, better Kotlin
coroutine stack rendering) unless there's a preference for the Firebase
suite later. DSN via CI secret / `local.properties`, absent in debug.
Events at v1: crash-free-session metric, `live_session_started`,
`live_session_duration`, `connection_error` (with status-code class, no
URLs). Consent/opt-out toggle in Settings from day one — cheaper now
than retrofitted for the Play privacy form.

**AC**: a forced release-build crash appears in Sentry symbolicated;
analytics events visible; opt-out verified to stop emission.

### APX-028 · Release engineering

Signing config from environment (keystore outside repo, CI secret),
version name/code derived from git tag, Play Console internal-testing
track set up, `docs/Release.md` runbook (how to cut a release, tag,
upload, roll back). First tagged build `0.1.0` to the internal track.

**AC**: `git tag v0.1.0 && <documented command>` produces an installable,
Play-accepted AAB; a second person could follow the runbook.

### APX-029 · Analysis tab v1

The tab is a placeholder; the data (lap times, gaps, stints) already
flows through the app. v1: driver-selectable lap-time chart and
gap-to-leader chart across the session, tyre-stint strip. Data source:
accumulate per-lap series in a `SessionHistory` domain service fed from
`RaceEngine` (same collector pattern as `RaceTimeline`) — do **not**
reach into `:intelligence` internals from the UI. Charts: hand-rolled
Compose `Canvas` (two line charts and a strip — a charting dependency is
not warranted yet).

**AC**: during/after a session the tab shows real charts; empty state is
designed (not blank); ViewModel unit tests + one UI test.

**Phase 1 exit check** — stranger-install test on the internal track
(see Roadmap); CI green ≥1 week; crash-free rate ≥99% on internal
testers.

---

## Phase 2 — Commercial product (outline)

Sized and sequenced properly at Phase 1 exit; current intended shape:

| Ticket | Scope |
|--------|-------|
| APX-030 | Room persistence: completed-session store (frames or per-endpoint tables), powering replay-after-close and the historical browser |
| APX-031 | Historical race browser (OpenF1 back-catalogue → same pipeline, `sessionStatus = REPLAY`) |
| APX-032 | Notification/alert engine on `RacePulse` (foreground-service session following, alert-worthiness thresholds, per-type user preferences) |
| APX-033 | Prediction layer per RaceIntelligencePlatform.md §12 (pit windows, undercut/overcut, overtake probability) — pure `:intelligence` work, heavily unit-tested like APX-011/012 |
| APX-034 | LLM narration per §14 (server-proxied; on-device fact payload already designed as `Observation.metadata`) |
| APX-035 | Monetisation: billing integration, free/premium gating (premium = predictions + narration + alerts; timing + basic insights stay free) |
| APX-036 | Design pass: iconography (replace emoji), motion, tablet/landscape, dark-theme audit, accessibility (TalkBack walkthrough, touch targets, dynamic type) |
| APX-037 | Localisation scaffolding + first languages; store listing; privacy policy + data-safety form |
| APX-038 | **Legal gate**: counsel review of data sourcing, naming, marks — blocks APX-035's launch, start it early |

## Phase 3 — Moat and scale (outline)

Second data provider + failover behind `RaceDataSource`; server-side
pulse computation option; KMP evaluation for `:intelligence` +
`domain`; home-screen widgets / Wear; community features. Prioritised by
Phase 2 analytics.

---

## Working agreements (all phases)

- **Definition of done**: ACs met, unit tests updated/added, docs updated
  in-commit, CI green (from APX-026), no new lint warnings.
- **The golden fixtures are sacred**: any pipeline change that alters
  golden-test expectations must explain *why the new behaviour is more
  correct* in the PR description, not just re-record the expectations.
- **`:intelligence` stays pure**: no Android, no network, no DI
  framework. App-side adapters own all bridging, as today.
- **One data-source writer**: exactly one `RaceDataSource` may drive
  `RaceEngine` at a time; the mutual-exclusion rule from APX-013 holds
  for every future source.
