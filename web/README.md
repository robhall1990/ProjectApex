# Project Apex — the zero-faff F1 gaps & insights app

**Live now: <https://robhall1990.github.io/ProjectApex/>** — open it on your phone,
Chrome menu → *Add to Home screen* installs it like a native app.

One self-contained web page: **`index.html`**. No build step, no install, no Android Studio.
Open it on Windows, your phone, anywhere with a browser.

It shows live/replayed F1 timing — proper gaps, intervals, tyres, pit stops, a gap ladder,
the classic race trace — plus a rule-based **Race Intelligence** feed (battles, undercut/tyre-offset
threats, tyre cliffs, flags) and optional **Claude-powered strategic insights**.

Timing data comes from the community [OpenF1 API](https://openf1.org). This is an unofficial
hobby project, not affiliated with Formula 1.

## Quick start

**Fastest:** double-click `web/index.html` (or open it in any browser). That's it —
it fetches straight from `api.openf1.org`.

Slightly nicer (avoids any browser `file://` quirks):

```bash
cd web
python -m http.server 8000     # then open http://localhost:8000
```

On your Android phone: open the live URL above in Chrome → menu → **Add to Home
screen**. It ships a PWA manifest and a service worker, so it installs fullscreen with
its own icon, starts instantly, and still opens with no signal (Demo mode works
fully offline).

## What you get

| Panel | What it shows |
|---|---|
| **Timing board** | Position (with ▲▼ places gained/lost), gap to leader, interval, last lap (purple = session best, green = personal best), tyre compound + age, pit count. In practice/quali: best lap + delta ordering. Click any driver to follow them across every panel. |
| **Track gaps** | The field laid out on a strip where distance = time gap (√ scale so battles stay readable). |
| **Weather** | Track/air temperature and rain in the header, straight from the circuit. |
| **Strategy** | Live pit-stop model: observed tyre degradation (s/lap), projected pit windows, and undercut reads — "NOR jumps VER in ~2 laps if he pits now". |
| **Qualifying mode** | Q1/Q2/Q3 detection, per-segment ordering, the drop zone highlighted with each driver's gap to the cutoff. |
| **Head to head** | Pick any two drivers (auto-follows your focused driver): gap-over-time chart, tyre/pit comparison, last-five-laps delta table. |
| **Race intelligence** | Auto-detected battles, drivers closing in, tyre-offset threats, tyre cliffs, pit stops, overtakes, safety cars and flags. |
| **Team radio 📻** | Every radio clip OpenF1 publishes, playable in place and tagged with driver + lap — hear the calls the broadcast never plays. Click a driver on the timing board to spotlight their messages. |
| **✦ Ask Claude** | Sends the current timing snapshot to Claude for a strategist's read — responses stream in live as Claude writes. Every intelligence-feed event has a ✦ button: *explain this* in plain language. When the chequered flag drops on a live/demo race, Claude writes an automatic race report. Needs your own Anthropic API key (Settings ⚙︎). |
| **Race trace** | Every driver's gap to the leader's average pace, lap by lap — the chart that shows the *whole* race shape. Hover for per-lap gaps. |
| **Replay** | Any completed session back to 2023: scrub, play at 1×–120×. Great for testing before a race weekend. |
| **Demo** | Fully synthetic 30-lap race through the real pipeline — works offline. |

## Live data: what's free and what isn't

- **Completed sessions** (replay) are free on OpenF1 — no account needed.
- **Real-time data during a session** requires a paid OpenF1 account. Open Settings ⚙︎ and
  **sign in with your OpenF1 email and password** — the app exchanges them for an access
  token (via `api.openf1.org/token`, straight from your browser) and refreshes it
  automatically as it nears expiry, so you sign in once and live timing just keeps flowing.
  "Stay signed in" stores your credentials in this browser only; leave it unticked on a
  shared machine. There's also an *Advanced: paste a token manually* option if you'd rather
  manage the token yourself.
- Without sign-in the app still works — replay and demo are fully available; live-session
  requests are just rejected or delayed until shortly after the session.
- Data typically appears on OpenF1 with a few seconds' delay vs. the TV feed.

## Claude insights

1. Get an API key at [platform.claude.com](https://platform.claude.com).
2. Settings ⚙︎ → paste it, pick a model (Opus 4.8 default; Haiku 4.5 is the cheap option).
3. Hit **✦ Ask Claude** any time, or enable auto-ask (~every 2½ min during live running).

The key is stored in your browser's localStorage only and calls go directly
browser → api.anthropic.com. Don't paste it on a shared machine.

Claude's brief includes the team-radio log (who called, on which lap). The audio
itself isn't transcribed — the Claude API doesn't take audio — but radio-traffic
timing is a real strategy signal (bursts cluster around pit calls and strategy
switches), and Claude reads it together with the pit and tyre data.

## Race-weekend drill (e.g. Spa)

1. Sign in to OpenF1 once (Settings ⚙︎) — the token then refreshes itself all weekend.
2. Open the app before the session — the **Latest** button lands on the upcoming session
   with a countdown. Leave it open: polling runs, the driver list and timing appear on
   their own at lights out, and the screen stays awake while live (Wake Lock).
3. Watch the header: **● updating** (green) means data is flowing; **⚠ stalled** means
   the feed has gone quiet — check your connection or the OpenF1 status.
4. After any session: **Latest** → full replay with the race trace and intelligence feed.
5. Dry-run beforehand: pick last week's Grand Prix from the pickers and hit play, or press **Demo**.

## Development

`dev/mock-openf1.js` is a dependency-free Node server that serves this folder statically
*and* fakes the OpenF1 API with a canned 6-car race:

```bash
node web/dev/mock-openf1.js 8123
# open http://localhost:8123/index.html?api=http://localhost:8123/v1
```

The `?api=<base>` query param points the app at any OpenF1-shaped endpoint.

`dev/verify.mjs` is the end-to-end test suite: it spawns the mock server and drives
the app in headless Chromium, asserting the replay, team-radio, demo, settings and
mobile-layout paths. CI (`.github/workflows/web-tests.yml`) runs it on every PR
touching `web/`. To run locally:

```bash
npm install --no-save playwright
npx playwright install chromium     # once
node web/dev/verify.mjs
```

## Deploying to GitHub Pages

A workflow (`.github/workflows/pages.yml`) publishes this folder to GitHub Pages on every
push to `master` — the live URL at the top of this file updates itself a minute or two
after each merge. (If the repo is renamed, the URL changes to match; old links redirect.)
