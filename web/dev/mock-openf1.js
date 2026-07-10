#!/usr/bin/env node
/**
 * Mock OpenF1 server for local development and end-to-end testing.
 * Serves the static app from web/ plus /v1/* endpoints shaped like api.openf1.org,
 * with a canned 12-lap race. Supports the operator-suffixed query keys the app
 * uses (e.g. `date>`).
 *
 *   node web/dev/mock-openf1.js [port]
 *   open http://localhost:8123/index.html   (the app auto-targets same-origin /v1 via ?api=)
 */
const http = require("http");
const fs = require("fs");
const path = require("path");

const PORT = parseInt(process.argv[2] || "8123", 10);
const WEB_ROOT = path.join(__dirname, "..");

/* ---------- canned data ---------- */
const T0 = Date.parse("2026-07-05T14:03:00Z"); // race start (in the past => replay path)
const iso = s => new Date(T0 + s * 1000).toISOString();

const MEETING = { meeting_key: 9001, meeting_name: "Mockshire Grand Prix", country_name: "Mockland", circuit_short_name: "Mockshire", year: 2026, date_start: iso(-7200) };
const SESSIONS = [
  { session_key: 77001, session_name: "Practice 1", session_type: "Practice", meeting_key: 9001, year: 2026, country_name: "Mockland", circuit_short_name: "Mockshire", date_start: iso(-90000), date_end: iso(-86400) },
  { session_key: 77002, session_name: "Race", session_type: "Race", meeting_key: 9001, year: 2026, country_name: "Mockland", circuit_short_name: "Mockshire", date_start: iso(0), date_end: iso(1300) },
];

const GRID = [
  [1,  "VER", "Max Verstappen",  "Red Bull Racing", "3671C6", 91.8],
  [4,  "NOR", "Lando Norris",    "McLaren",         "FF8000", 91.9],
  [16, "LEC", "Charles Leclerc", "Ferrari",         "E80020", 92.2],
  [44, "HAM", "Lewis Hamilton",  "Ferrari",         "E80020", 92.4],
  [63, "RUS", "George Russell",  "Mercedes",        "27F4D2", 92.5],
  [14, "ALO", "Fernando Alonso", "Aston Martin",    "229971", 92.9],
];

const DB = { drivers: [], intervals: [], position: [], laps: [], stints: [], pit: [], race_control: [], weather: [], team_radio: [] };

/* Tiny synthesized WAV (0.7s two-tone beep) served at /audio/radio.wav so the
   app's team-radio play buttons are exercisable offline. */
function makeBeepWav() {
  const rate = 8000, secs = 0.7, n = Math.floor(rate * secs);
  const data = Buffer.alloc(n * 2);
  for (let i = 0; i < n; i++) {
    const f = i < n / 2 ? 660 : 880;
    const amp = Math.sin(2 * Math.PI * f * i / rate) * 0.35 * 32767;
    data.writeInt16LE(Math.round(amp), i * 2);
  }
  const h = Buffer.alloc(44);
  h.write("RIFF", 0); h.writeUInt32LE(36 + data.length, 4); h.write("WAVE", 8);
  h.write("fmt ", 12); h.writeUInt32LE(16, 16); h.writeUInt16LE(1, 20); h.writeUInt16LE(1, 22);
  h.writeUInt32LE(rate, 24); h.writeUInt32LE(rate * 2, 28); h.writeUInt16LE(2, 32); h.writeUInt16LE(16, 34);
  h.write("data", 36); h.writeUInt32LE(data.length, 40);
  return Buffer.concat([h, data]);
}
const BEEP_WAV = makeBeepWav();

function build() {
  const SK = 77002;
  for (const [n, tla, name, team, colour] of GRID) {
    DB.drivers.push({ driver_number: n, name_acronym: tla, full_name: name, broadcast_name: name, team_name: team, team_colour: colour, session_key: SK });
    DB.stints.push({ driver_number: n, stint_number: 1, compound: "SOFT", lap_start: 1, tyre_age_at_start: 0, session_key: SK });
  }
  const cars = GRID.map(([n, , , , , base], i) => ({ n, base, dist: -i * 0.004, lap: 0, lastCross: 0, pitted: false }));
  const LAPS = 12;
  for (let t = 0; t <= 1300; t += 5) {
    for (const c of cars) {
      let lapTime = c.base + 0.05 * c.lap;
      if (c.n === 4 && t > 300 && t < 800) lapTime -= 0.5;   // NOR chases VER
      c.dist += 5 / lapTime;
      const lapNow = Math.floor(c.dist) + 1;
      if (lapNow > c.lap && c.dist > 0) {
        if (c.lap >= 1) DB.laps.push({ driver_number: c.n, lap_number: c.lap, lap_duration: +(t - c.lastCross).toFixed(3), date_start: iso(t), session_key: 77002 });
        c.lastCross = t; c.lap = lapNow;
        if (!c.pitted && c.lap === 7) {
          c.pitted = true; c.dist -= 21 / lapTime;
          DB.pit.push({ driver_number: c.n, lap_number: c.lap, pit_duration: 2.4, date: iso(t), session_key: 77002 });
          DB.stints.push({ driver_number: c.n, stint_number: 2, compound: "MEDIUM", lap_start: c.lap, tyre_age_at_start: 0, session_key: 77002 });
          DB.team_radio.push({ driver_number: c.n, date: iso(t - 20), recording_url: "/audio/radio.wav", session_key: 77002, meeting_key: 9001 });
        }
      }
    }
    const order = [...cars].sort((a, b) => b.dist - a.dist);
    order.forEach((c, i) => {
      DB.position.push({ driver_number: c.n, position: i + 1, date: iso(t), session_key: 77002 });
      const gap = (order[0].dist - c.dist) * c.base;
      const intv = i ? +(((order[i - 1].dist - c.dist) * c.base).toFixed(3)) : 0;
      DB.intervals.push({ driver_number: c.n, gap_to_leader: i ? +gap.toFixed(3) : 0, interval: intv, date: iso(t), session_key: 77002 });
    });
  }
  for (let t = 0; t <= 1300; t += 120) {
    DB.weather.push({ date: iso(t), air_temperature: 23.5, track_temperature: 38 + t / 200, rainfall: 0, wind_speed: 2.1, session_key: 77002 });
  }
  for (const [t, n] of [[150, 1], [420, 4], [700, 1], [980, 16]]) {
    DB.team_radio.push({ driver_number: n, date: iso(t), recording_url: "/audio/radio.wav", session_key: 77002, meeting_key: 9001 });
  }
  DB.race_control.push({ date: iso(200), category: "Flag", flag: "YELLOW", message: "YELLOW IN SECTOR 2", session_key: 77002 });
  DB.race_control.push({ date: iso(260), category: "Flag", flag: "CLEAR", message: "TRACK CLEAR", session_key: 77002 });
  DB.race_control.push({ date: iso(1250), category: "Flag", flag: "CHEQUERED", message: "CHEQUERED FLAG", session_key: 77002 });
}
build();
console.log(`mock data: ${DB.laps.length} laps, ${DB.intervals.length} intervals, ${DB.position.length} positions`);

/* ---------- request handling ---------- */
function filterRows(rows, params) {
  return rows.filter(r => {
    for (const [rawKey, val] of params) {
      const m = rawKey.match(/^(.*?)(>=|<=|>|<)?$/);
      const key = m[1], op = m[2];
      if (!(key in r)) {
        if (key === "session_key" && val === "latest") continue;
        continue;
      }
      const rv = r[key];
      if (!op) { if (String(rv) !== String(val)) return false; }
      else {
        const a = isNaN(Date.parse(rv)) ? Number(rv) : Date.parse(rv);
        const b = isNaN(Date.parse(val)) ? Number(val) : Date.parse(val);
        if (op === ">" && !(a > b)) return false;
        if (op === "<" && !(a < b)) return false;
        if (op === ">=" && !(a >= b)) return false;
        if (op === "<=" && !(a <= b)) return false;
      }
    }
    return true;
  });
}

const MIME = { ".html": "text/html", ".js": "text/javascript", ".css": "text/css", ".json": "application/json", ".svg": "image/svg+xml", ".png": "image/png" };

http.createServer((req, res) => {
  const url = new URL(req.url, `http://localhost:${PORT}`);
  res.setHeader("Access-Control-Allow-Origin", "*");

  if (url.pathname.startsWith("/v1/")) {
    const resource = url.pathname.slice(4);
    let rows;
    if (resource === "meetings") rows = filterRows([MEETING], url.searchParams);
    else if (resource === "sessions") {
      rows = url.searchParams.get("session_key") === "latest" ? [SESSIONS[1]] : filterRows(SESSIONS, url.searchParams);
    } else if (resource in DB) rows = filterRows(DB[resource], url.searchParams);
    else { res.writeHead(404); return res.end("[]"); }
    res.writeHead(200, { "content-type": "application/json" });
    return res.end(JSON.stringify(rows));
  }

  if (url.pathname === "/audio/radio.wav") {
    res.writeHead(200, { "content-type": "audio/wav", "content-length": BEEP_WAV.length });
    return res.end(BEEP_WAV);
  }

  // static
  let p = url.pathname === "/" ? "/index.html" : url.pathname;
  const file = path.join(WEB_ROOT, path.normalize(p).replace(/^([/\\])+/, ""));
  if (!file.startsWith(WEB_ROOT) || !fs.existsSync(file) || fs.statSync(file).isDirectory()) {
    res.writeHead(404); return res.end("not found");
  }
  res.writeHead(200, { "content-type": MIME[path.extname(file)] || "application/octet-stream" });
  fs.createReadStream(file).pipe(res);
}).listen(PORT, () => console.log(`mock OpenF1 + static server on http://localhost:${PORT}`));
