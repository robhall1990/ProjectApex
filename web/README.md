# Project Velocity — the zero-faff F1 gaps & insights app

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

On your Android phone: host it (GitHub Pages, see below) and open the URL in Chrome →
menu → **Add to Home screen**. It ships a PWA manifest, so it installs fullscreen with
its own icon — behaves like a native app.

## What you get

| Panel | What it shows |
|---|---|
| **Timing board** | Position (with ▲▼ places gained/lost), gap to leader, interval, last lap (purple = session best, green = personal best), tyre compound + age, pit count. In practice/quali: best lap + delta ordering. Click any driver to follow them across every panel. |
| **Track gaps** | The field laid out on a strip where distance = time gap (√ scale so battles stay readable). |
| **Weather** | Track/air temperature and rain in the header, straight from the circuit. |
| **Race intelligence** | Auto-detected battles, drivers closing in, tyre-offset threats, tyre cliffs, pit stops, overtakes, safety cars and flags. |
| **✦ Ask Claude** | Sends the current timing snapshot to Claude for a strategist's read (undercut windows, threats, what to watch). Needs your own Anthropic API key (Settings ⚙︎). |
| **Race trace** | Every driver's gap to the leader's average pace, lap by lap — the chart that shows the *whole* race shape. Hover for per-lap gaps. |
| **Replay** | Any completed session back to 2023: scrub, play at 1×–120×. Great for testing before a race weekend. |
| **Demo** | Fully synthetic 30-lap race through the real pipeline — works offline. |

## Live data: what's free and what isn't

- **Completed sessions** (replay) are free on OpenF1 — no account needed.
- **Real-time data during a session** requires a paid OpenF1 account
  ([openf1.org](https://openf1.org) → get a token, paste it in Settings ⚙︎).
  Without a token the app still works, but live-session requests may be rejected or delayed
  until shortly after the session.
- Data typically appears on OpenF1 with a few seconds' delay vs. the TV feed.

## Claude insights

1. Get an API key at [platform.claude.com](https://platform.claude.com).
2. Settings ⚙︎ → paste it, pick a model (Opus 4.8 default; Haiku 4.5 is the cheap option).
3. Hit **✦ Ask Claude** any time, or enable auto-ask (~every 2½ min during live running).

The key is stored in your browser's localStorage only and calls go directly
browser → api.anthropic.com. Don't paste it on a shared machine.

## Race-weekend drill (e.g. Spa)

1. Open the app before FP1 — the **Latest** button lands on the upcoming session with a countdown.
2. Leave it open; polling starts automatically and data flows as the session goes live
   (paid token for true real-time, otherwise expect it right after the session).
3. After any session: **Latest** → full replay with the race trace and intelligence feed.
4. Dry-run beforehand: pick last week's Grand Prix from the pickers and hit play, or press **Demo**.

## Development

`dev/mock-openf1.js` is a dependency-free Node server that serves this folder statically
*and* fakes the OpenF1 API with a canned 6-car race:

```bash
node web/dev/mock-openf1.js 8123
# open http://localhost:8123/index.html?api=http://localhost:8123/v1
```

The `?api=<base>` query param points the app at any OpenF1-shaped endpoint.

## Deploying to GitHub Pages

A workflow (`.github/workflows/pages.yml`) publishes this folder to GitHub Pages on every
push to `master`. Merge this branch, then check the repo's **Actions** tab — the run prints
your public URL (Settings → Pages shows it too). That URL is what you open on your phone.
