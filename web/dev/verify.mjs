/**
 * End-to-end verification of the Project Apex web app.
 * Spawns the mock OpenF1 server, drives the app in headless Chromium via
 * Playwright, and asserts the replay, team-radio, demo, settings and mobile
 * paths all work. Exits non-zero on any failure.
 *
 *   npm install playwright && npx playwright install chromium   (once)
 *   node web/dev/verify.mjs
 *
 * Env:
 *   CHROMIUM  path to a Chromium binary (skips Playwright's managed browser)
 *   SHOT_DIR  where to write screenshots (default: OS temp dir)
 */
import { chromium } from "playwright";
import { spawn } from "node:child_process";
import { mkdtempSync } from "node:fs";
import { tmpdir } from "node:os";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const PORT = 8199;
const BASE = `http://localhost:${PORT}`;
const SHOT = process.env.SHOT_DIR || mkdtempSync(join(tmpdir(), "apex-verify-"));
const failures = [];
const pageErrors = [];
const check = (name, cond, detail = "") => {
  console.log(`${cond ? "  ok " : "FAIL "} ${name}${detail ? ` — ${detail}` : ""}`);
  if (!cond) failures.push(name);
};

/* ---- mock server ---- */
const server = spawn(process.execPath, [join(dirname(fileURLToPath(import.meta.url)), "mock-openf1.js"), String(PORT)], { stdio: "pipe" });
await new Promise((resolve, reject) => {
  server.stdout.on("data", d => { if (String(d).includes("static server")) resolve(); });
  server.on("exit", c => reject(new Error("mock server exited " + c)));
  setTimeout(() => reject(new Error("mock server didn't start")), 8000);
});

const browser = await chromium.launch({
  executablePath: process.env.CHROMIUM || undefined,
  args: ["--autoplay-policy=no-user-gesture-required"],
});

try {
  const page = await browser.newPage({ viewport: { width: 1360, height: 900 } });
  page.on("console", m => { if (m.type() === "error") pageErrors.push("console: " + m.text()); });
  page.on("pageerror", e => pageErrors.push("pageerror: " + e.message));

  // Seed a fake key (so Claude features activate) and a legacy velocity.* value
  // (so we can assert the one-time key migration). Stub the Anthropic API with
  // a canned SSE stream — CI exercises the real streaming render path, no key needed.
  await page.addInitScript(() => {
    localStorage.setItem("apex.anthropicKey", "sk-test-not-real");
    localStorage.setItem("apex.pollSec", "3");   // fast polls so the live-mode section stays quick
    if (!localStorage.getItem("apex.model")) localStorage.setItem("velocity.model", "claude-test-model");
  });
  const sse = [
    `data: {"type":"message_start","message":{"model":"claude-test-model"}}`,
    `data: {"type":"content_block_delta","delta":{"type":"text_delta","text":"- **Undercut watch**: NOR is inside "}}`,
    `data: {"type":"content_block_delta","delta":{"type":"text_delta","text":"the pit-loss window behind VER."}}`,
    `data: {"type":"message_delta","delta":{"stop_reason":"end_turn"}}`,
    `data: {"type":"message_stop"}`,
    "",
  ].join("\n\n");
  await page.route("**/api.anthropic.com/**", route =>
    route.fulfill({ status: 200, contentType: "text/event-stream", body: sse }));

  /* ---- 1. replay path against the mock OpenF1 API ---- */
  await page.goto(`${BASE}/index.html?api=${BASE}/v1`);
  await page.waitForTimeout(3000);

  check("replay: mode chip = REPLAY", (await page.textContent("#chipMode")).trim() === "REPLAY");
  check("replay: 6 drivers on the board", await page.locator("#board tbody tr").count() === 6);
  check("replay: replay bar shown", await page.isVisible("#replaybar"));
  check("replay: race trace shown", await page.isVisible("#traceCard"));
  check("replay: gap ladder shown", await page.isVisible("#ladderCard"));
  const wx = await page.isVisible("#chipWx") ? await page.textContent("#chipWx") : "";
  check("replay: weather chip populated", /trk/.test(wx), wx.trim());

  /* team radio: rows render, playback toggles, resets when the clip ends */
  check("radio: card visible", await page.isVisible("#radioCard"));
  const radioRows = await page.locator("#radioList .radio-row").count();
  check("radio: rows rendered", radioRows > 0, `${radioRows} rows`);
  await page.locator("#radioList .radio-play[data-url]").first().click();
  await page.waitForTimeout(400);
  check("radio: clip playing after click", await page.locator("#radioList .radio-row.playing").count() === 1);
  await page.waitForTimeout(900);              // beep is 0.7 s
  check("radio: playback resets when clip ends", await page.locator("#radioList .radio-row.playing").count() === 0);

  /* driver focus */
  await page.locator("#board tbody tr").nth(1).click();
  await page.waitForTimeout(300);
  check("focus: row selected on click", await page.locator("#board tbody tr.sel").count() === 1);
  await page.screenshot({ path: `${SHOT}/replay-focus.png`, fullPage: true });
  await page.locator("#board tbody tr").nth(1).click();

  /* head to head: default pair, chart, lap table, manual pick */
  check("h2h: card visible", await page.isVisible("#h2hCard"));
  const pairA = await page.inputValue("#h2hA"), pairB = await page.inputValue("#h2hB");
  check("h2h: default pair populated", pairA !== "" && pairB !== "" && pairA !== pairB, `${pairA} vs ${pairB}`);
  check("h2h: lap-delta rows", await page.locator("#h2hLaps tbody tr").count() >= 3);
  const lit = await page.evaluate(() => {
    const cv = document.querySelector("#h2h");
    const d = cv.getContext("2d").getImageData(0, 0, cv.width, cv.height).data;
    let n = 0; for (let i = 3; i < d.length; i += 4) if (d[i] > 0) n++;
    return n;
  });
  check("h2h: chart painted", lit > 1000, `${lit} px`);
  await page.selectOption("#h2hB", { index: 5 });
  await page.waitForTimeout(300);
  check("h2h: manual pick sticks", await page.inputValue("#h2hB") !== pairB);

  /* scrubbing */
  const lapBefore = await page.textContent("#chipLap").catch(() => "");
  await page.locator("#scrub").evaluate(el => { el.value = 600; el.dispatchEvent(new Event("input")); });
  await page.waitForTimeout(600);
  const lapAfter = await page.textContent("#chipLap").catch(() => "");
  check("replay: scrub changes lap chip", lapAfter !== lapBefore, `${lapBefore.trim()} → ${lapAfter.trim()}`);

  /* strategy panel appears mid-race (hidden after the chequered flag) */
  check("strategy: card visible mid-race", await page.isVisible("#stratCard"));
  check("strategy: one row per driver", await page.locator("#strat tbody tr").count() === 6);
  const stratRow = await page.locator("#strat tbody tr").first().innerText();
  check("strategy: pit window projected", /L\d+–L\d+|open now|overdue/.test(stratRow), stratRow.replace(/\s+/g, " "));
  await page.screenshot({ path: `${SHOT}/replay.png`, fullPage: true });

  /* trace tooltip */
  await page.locator("#trace").scrollIntoViewIfNeeded();
  const box = await page.locator("#trace").boundingBox();
  await page.mouse.move(box.x + box.width * 0.6, box.y + box.height * 0.5);
  await page.waitForTimeout(300);
  check("trace: tooltip on hover", await page.isVisible("#traceTip"));

  /* Claude brief carries radio + strategy */
  const brief = await page.evaluate(() => buildBrief());
  check("brief: includes team radio section", brief.includes("Team radio traffic"));
  check("brief: includes strategy model", brief.includes("Strategy model"));

  /* ---- 1a. Claude integration (stubbed SSE endpoint) ---- */
  check("brand: page title is Project Apex", (await page.title()).includes("Project Apex"));
  check("config: velocity.* key migrated to apex.*",
    await page.evaluate(() => localStorage.getItem("apex.model")) === "claude-test-model");

  await page.click("#btnAsk");
  await page.waitForTimeout(1200);
  const aiCards = await page.locator("#aiFeed .ai-card").count();
  check("ai: card appears after Ask Claude", aiCards === 1);
  const aiBody = aiCards ? await page.locator("#aiFeed .ai-card .body").first().innerHTML() : "";
  check("ai: streamed markdown rendered", aiBody.includes("<b>Undercut watch</b>") && aiBody.includes("pit-loss window"), aiBody.slice(0, 80));

  await page.evaluate(() => addInsight({ icon: "⚔️", prio: 2, title: "Battle for P1", desc: "Verification battle event." }));
  await page.locator("#feed .explain").first().click();
  await page.waitForTimeout(1200);
  const explainHdr = await page.locator("#aiFeed .ai-card .hdr b").first().textContent();
  check("ai: explain-this card streams in", explainHdr.includes("Claude explains"), explainHdr);

  await page.evaluate(() => { S.chequered = true; raceReport(); });
  await page.waitForTimeout(1200);
  const reportHdr = await page.locator("#aiFeed .ai-card .hdr b").first().textContent();
  check("ai: race report card streams in", reportHdr.includes("Race report"), reportHdr);
  await page.evaluate(() => { S.chequered = false; });

  /* ---- 1b. qualifying: segment inference, drop zone, cutoff gaps ---- */
  await page.goto(`${BASE}/index.html?api=${BASE}/v1&session=77003`);
  await page.waitForTimeout(2500);
  check("quali: Q3 chip at end of replay", (await page.textContent("#chipLap")).trim().startsWith("Q3"));
  check("quali: no drop zone in Q3", await page.locator("#board tbody tr.drop").count() === 0);
  await page.locator("#scrub").evaluate(el => { el.value = 550; el.dispatchEvent(new Event("input")); });
  await page.waitForTimeout(600);
  const qChip = (await page.textContent("#chipLap")).trim();
  check("quali: Q2 chip with cutoff after scrub", /^Q2/.test(qChip) && /cut/.test(qChip), qChip);
  check("quali: drop-zone rows highlighted", await page.locator("#board tbody tr.drop").count() === 4);
  const cutCells = await page.locator("#board .cut-out").count();
  check("quali: cutoff deltas shown", cutCells >= 2, `${cutCells} cells`);
  const qBrief = await page.evaluate(() => buildBrief());
  check("quali: brief carries segment + cutoff", qBrief.includes("Qualifying segment") && qBrief.includes("OUT"));
  await page.screenshot({ path: `${SHOT}/quali.png`, fullPage: true });

  /* ---- 1c. live mode: late driver list, streaming data, freshness chip ----
     The mock withholds /drivers for ~5s after first touch, exactly like OpenF1
     does before a session goes live — the board must fill in on its own. */
  await page.goto(`${BASE}/index.html?api=${BASE}/v1&session=77004`);
  await page.waitForTimeout(2000);
  check("live: mode chip = LIVE", (await page.textContent("#chipMode")).trim() === "LIVE");
  check("live: board empty before drivers publish", await page.locator("#board tbody tr").count() === 0);
  check("live: freshness chip visible", await page.isVisible("#chipSync"));
  await page.waitForTimeout(10000);
  const liveCount = await page.locator("#board tbody tr").count();
  check("live: board fills once drivers publish", liveCount === 6, `${liveCount} rows`);
  const liveP2 = liveCount >= 2 ? await page.locator("#board tbody tr").nth(1).innerText() : "";
  check("live: gaps populated", /\+\d/.test(liveP2), liveP2.replace(/\s+/g, " "));
  check("live: freshness chip green", ((await page.textContent("#chipSync")) || "").includes("updating"));
  await page.screenshot({ path: `${SHOT}/live.png`, fullPage: true });

  /* ---- 2. demo mode (full synthetic pipeline) ---- */
  await page.click("#btnDemo");
  await page.waitForTimeout(20000);
  check("demo: mode chip = DEMO", (await page.textContent("#chipMode")).trim() === "DEMO");
  check("demo: 20 drivers on the board", await page.locator("#board tbody tr").count() === 20);
  const demoRadio = await page.locator("#radioList .radio-row").count();
  check("demo: scripted radio appears", demoRadio > 0, `${demoRadio} rows`);
  if (demoRadio) {
    const txt = await page.locator("#radioList .radio-text").first().textContent();
    check("demo: radio has transcript text", /Tyres|Box|Gap|DRS|position/i.test(txt), txt);
  }
  await page.screenshot({ path: `${SHOT}/demo.png`, fullPage: true });

  /* ---- 3. settings persistence ---- */
  await page.click("#btnSettings");
  await page.fill("#inPoll", "7");
  await page.click("#btnSaveSettings");
  check("settings: pollSec persisted", await page.evaluate(() => localStorage.getItem("apex.pollSec")) === "7");

  /* ---- 3a. OpenF1 sign-in against the mock /token endpoint ---- */
  await page.click("#btnSettings");
  await page.fill("#inOF1User", "test@example.com");
  await page.fill("#inOF1Pass", "wrong");
  await page.click("#btnOF1Login");
  await page.waitForTimeout(400);
  check("signin: wrong password shows error", (await page.locator("#of1Status").getAttribute("class")).includes("err"));
  await page.fill("#inOF1Pass", "correct-horse");
  await page.check("#inOF1Stay");
  await page.click("#btnOF1Login");
  await page.waitForTimeout(500);
  check("signin: success status shown", (await page.locator("#of1Status").getAttribute("class")).includes("in"));
  check("signin: token stored", (await page.evaluate(() => localStorage.getItem("apex.of1Token")) || "").startsWith("mock-token-"));
  check("signin: expiry stored in the future", await page.evaluate(() => parseInt(localStorage.getItem("apex.of1Exp") || "0", 10)) > Date.now());
  check("signin: credentials stored for auto-refresh", await page.evaluate(() => localStorage.getItem("apex.of1Pass")) === "correct-horse");
  // token is sent as a bearer on the next OpenF1 request
  const auth = await page.evaluate(async () => {
    let seen = null;
    const orig = window.fetch;
    window.fetch = (u, o) => { if (String(u).includes("/v1/")) seen = (o?.headers?.Authorization) || null; return orig(u, o); };
    await of("/sessions", { session_key: "latest" });
    window.fetch = orig;
    return seen;
  });
  check("signin: bearer sent on OpenF1 calls", (auth || "").startsWith("Bearer mock-token-"), auth || "(none)");
  // sign out clears everything (dialog is still open from the sign-in above)
  await page.click("#btnOF1Logout");
  await page.waitForTimeout(200);
  check("signin: sign-out clears token + creds", await page.evaluate(() =>
    !localStorage.getItem("apex.of1Token") && !localStorage.getItem("apex.of1Pass")));
  await page.click("#btnCloseSettings");

  /* ---- 4. mobile layout sanity ---- */
  await page.setViewportSize({ width: 400, height: 800 });
  await page.waitForTimeout(800);
  const hScroll = await page.evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth + 2);
  check("mobile: no horizontal overflow", !hScroll);
  await page.screenshot({ path: `${SHOT}/mobile.png` });

  // the wrong-password sign-in test deliberately provokes one 401 — expected, not a bug
  const realErrors = pageErrors.filter(e => !/401 \(Unauthorized\)/.test(e));
  check("no page errors", realErrors.length === 0, realErrors.join(" | "));
} finally {
  await browser.close();
  server.kill();
}

console.log(`\nscreenshots: ${SHOT}`);
if (failures.length) {
  console.error(`\n${failures.length} FAILURE(S):\n- ` + failures.join("\n- "));
  process.exit(1);
}
console.log("\nall checks passed ✅");
