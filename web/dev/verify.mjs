/**
 * End-to-end verification of the Project Velocity web app.
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
const SHOT = process.env.SHOT_DIR || mkdtempSync(join(tmpdir(), "velocity-verify-"));
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

  /* scrubbing */
  const lapBefore = await page.textContent("#chipLap").catch(() => "");
  await page.locator("#scrub").evaluate(el => { el.value = 400; el.dispatchEvent(new Event("input")); });
  await page.waitForTimeout(600);
  const lapAfter = await page.textContent("#chipLap").catch(() => "");
  check("replay: scrub changes lap chip", lapAfter !== lapBefore, `${lapBefore.trim()} → ${lapAfter.trim()}`);
  await page.screenshot({ path: `${SHOT}/replay.png`, fullPage: true });

  /* trace tooltip */
  const box = await page.locator("#trace").boundingBox();
  await page.mouse.move(box.x + box.width * 0.6, box.y + box.height * 0.5);
  await page.waitForTimeout(300);
  check("trace: tooltip on hover", await page.isVisible("#traceTip"));

  /* Claude brief carries the radio log */
  const brief = await page.evaluate(() => buildBrief());
  check("brief: includes team radio section", brief.includes("Team radio traffic"));

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
  check("settings: pollSec persisted", await page.evaluate(() => localStorage.getItem("velocity.pollSec")) === "7");

  /* ---- 4. mobile layout sanity ---- */
  await page.setViewportSize({ width: 400, height: 800 });
  await page.waitForTimeout(800);
  const hScroll = await page.evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth + 2);
  check("mobile: no horizontal overflow", !hScroll);
  await page.screenshot({ path: `${SHOT}/mobile.png` });

  check("no page errors", pageErrors.length === 0, pageErrors.join(" | "));
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
