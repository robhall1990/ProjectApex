# Roadmap — from prototype to commercial-grade

This document is the strategic view: where the app is today, what separates
it from a product people pay for, and the phased route between the two.
The ticket-level breakdown lives in [ExecutionPlan.md](ExecutionPlan.md);
this document explains *why* those tickets exist and in what order the
phases must land.

Last reviewed: 2026-07 (after APX-013, the OpenF1 live data source).

## Where the app is today

**Built and working (against the Developer Mode simulator, and — pending
on-device verification — the OpenF1 live feed):**

- A cleanly layered Android client: `RaceEngine` as the single source of
  truth, `RaceTimeline` replay, the unwrapped-track ribbon, leaderboard,
  and a bottom-nav shell.
- The `:intelligence` platform — ingestion, validation, feature layer
  (pace/deg/pit-loss models), eight combat detectors, and a configurable
  prioritisation engine — pure Kotlin/JVM, heavily unit-tested, and
  KMP-portable by construction.
- A first real data source (APX-013): OpenF1 polling → `RaceState` with
  defensive mapping, track status, failure isolation, and a Live Session
  card in Settings.

**The honest assessment:** the hard, differentiating 40% of the product
(the intelligence pipeline) is built and tested. The unglamorous 60% that
separates a prototype from a product — release engineering, data
resilience, first-run UX, observability, legal footing — has not started.

## The five gaps to commercial-grade

1. **Release integrity.** `isMinifyEnabled = true` with an empty
   `proguard-rules.pro` and reflection-dependent libraries (Retrofit,
   kotlinx.serialization) means no verified release build has ever
   existed. The HTTP logging interceptor also runs unconditionally,
   including in release.

2. **Data resilience.** The live poller refetches *entire session
   history* every 5 seconds (`/position`, `/laps`, `/intervals` grow
   monotonically through a race — megabytes per poll by lap 50), keeps
   polling while backgrounded, and the OpenF1 schema has never been
   verified against a live response. There is no provider abstraction:
   OpenF1 — an unofficial project we don't control — is hard-wired in.

3. **Product UX.** Watching a race starts in *Settings* and requires
   manually typing the lap count. A real user opens the app on Sunday and
   should see the session and one button. The ribbon renders every car at
   the same progress (v1 placeholder), team colours are fetched and
   discarded, the Analysis tab is empty, and replay has no scrub bar.

4. **Operations.** No CI, no crash reporting, no analytics, no signing
   config, no store presence. The instrumented and unit tests exist but
   nothing runs them automatically.

5. **Legal/commercial footing.** F1 timing data rights are exclusively
   licensed and F1's trademarks are aggressively protected. Unofficial
   companion apps exist commercially, but positioning (naming, branding,
   disclaimers, what the data is used for) needs real legal advice
   **before any money changes hands**, and the provider abstraction in
   gap 2 is also the legal hedge.

## The phases

Each phase has a hard exit criterion. Do not start the next phase's
feature work before the previous phase's exit criterion is met — every
one of these is load-bearing for what follows.

### Phase 0 — Don't ship broken (days)

Fix the things that would embarrass us in front of the first real user:
verified minified release build, debug-gated logging, incremental
polling, lifecycle-aware polling, and on-device verification of the
OpenF1 schema (capturing real responses as test fixtures while we're at
it).

**Exit criterion:** a minified release APK follows a real live session
for a full hour on a physical device without crash, memory growth, or
runaway battery/network use.

### Phase 1 — Credible beta (2–4 weeks)

Make the app self-explanatory and trustworthy for a stranger:
session-first UX (auto-discovered sessions, one-tap go-live, no manual
lap entry), visual truth (team colours, per-car ribbon progress, real
lap times), the repository/provider abstraction, a golden-replay
integration test built from recorded real-session fixtures, CI on every
PR, crash reporting, signing, and a Play internal-testing track. Analysis
tab v1 ships here because the feature-layer data already exists and an
empty tab reads as abandonment.

**Exit criterion:** a friend with no instructions installs from the
internal track, follows a live session, and nothing requires
explanation; CI is green and Crashlytics is quiet.

### Phase 2 — Commercial product (1–2 months)

Lean into the differentiator. Live timing is a commodity; ranked,
predictive race intelligence is not. Ship the prediction layer
(RaceIntelligencePlatform.md §12) and LLM narration (§14) as the premium
tier, race alerts via notifications ("VER closing on NOR — DRS in 3
laps"), historical race browsing on Room persistence, the design/
accessibility/localisation pass, store listing, privacy policy, and the
**legal review gate** — no paid tier launches before it.

**Exit criterion:** the app is on the Play Store, the free tier is
sticky, the paid tier is legally cleared and converting.

### Phase 3 — Moat and scale (ongoing)

Multi-provider failover behind the Phase 1 abstraction, optional
server-side pulse computation (the `:intelligence` module already runs
off-device by design), KMP/iOS evaluation, widgets/Wear, and community
features. Sequenced by what Phase 2's analytics say users actually do.

## Standing risks

| Risk | Mitigation |
|------|------------|
| OpenF1 disappears, rate-limits, or changes terms | Provider abstraction (APX-018); recorded fixtures keep development unblocked; Phase 3 failover |
| F1 legal pressure on naming/data | Legal review gate before monetisation; no F1 marks in branding; "unofficial" disclaimers from Phase 1 |
| Battery/network reputation damage | Phase 0 lifecycle + incremental polling; Phase 2 foreground-service option with visible notification |
| Solo-maintainer bus factor | CI + golden replay test keep the codebase safe to change; docs discipline already strong |
| Intelligence quality on real data | Detectors were tuned on the simulator; golden fixtures (APX-017) let us re-tune `IntelligenceConfig` against reality |

## What we deliberately are not doing yet

- **No ML.** The platform spec (§13) already concluded deterministic
  models win at this data volume; revisit only with real usage data.
- **No iOS** until Android proves the product. The KMP-ready
  `:intelligence` module keeps that door open cheaply.
- **No user accounts/backend** until a feature genuinely needs one
  (alert preferences sync is the likely first candidate, Phase 2/3).
- **No car telemetry streaming** (OpenF1 `/car_data` at ~3.7Hz × 20
  cars): high cost, low insight value versus the lap-level models we
  already have. Revisit for Phase 3 replay visualisations.
