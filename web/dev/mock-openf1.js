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
  { session_key: 77003, session_name: "Qualifying", session_type: "Qualifying", meeting_key: 9001, year: 2026, country_name: "Mockland", circuit_short_name: "Mockshire", date_start: iso(-40000), date_end: iso(-37400) },
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

/* ---------- live session (77004): data streams in against real time ----------
   The driver list is withheld until a few seconds after the session is first
   requested — mirroring how OpenF1 publishes /drivers only once a session goes
   live — so the app's late-driver recovery path gets exercised end-to-end. */
const LIVE = { key: 77004, t0: Date.now() - 30000, firstTouch: null };
SESSIONS.push({ session_key: LIVE.key, session_name: "Race (live mock)", session_type: "Race", meeting_key: 9001, year: 2026, country_name: "Mockland", circuit_short_name: "Mockshire", date_start: new Date(LIVE.t0).toISOString(), date_end: new Date(LIVE.t0 + 7200000).toISOString() });

function liveRows(resource) {
  LIVE.firstTouch ??= Date.now();
  const revealed = Date.now() - LIVE.firstTouch > 5000;
  const out = { drivers: [], intervals: [], position: [], laps: [], stints: [], pit: [], race_control: [], weather: [], team_radio: [] };
  if (revealed) for (const [n, tla, name, team, colour] of GRID) {
    out.drivers.push({ driver_number: n, name_acronym: tla, full_name: name, broadcast_name: name, team_name: team, team_colour: colour, session_key: LIVE.key });
    out.stints.push({ driver_number: n, stint_number: 1, compound: "SOFT", lap_start: 1, tyre_age_at_start: 0, session_key: LIVE.key });
  }
  const isoL = s => new Date(LIVE.t0 + s * 1000).toISOString();
  const elapsed = Math.floor((Date.now() - LIVE.t0) / 1000);
  const cars = GRID.map(([n, , , , , base], i) => ({ n, base, dist: -i * 0.004, lap: 0, lastCross: 0 }));
  for (let t = 0; t <= elapsed; t += 5) {
    for (const c of cars) {
      c.dist += 5 / c.base;
      const lapNow = Math.floor(c.dist) + 1;
      if (lapNow > c.lap && c.dist > 0) {
        if (c.lap >= 1) out.laps.push({ driver_number: c.n, lap_number: c.lap, lap_duration: +(t - c.lastCross).toFixed(3), date_start: isoL(t), session_key: LIVE.key });
        c.lastCross = t; c.lap = lapNow;
      }
    }
    const order = [...cars].sort((a, b) => b.dist - a.dist);
    order.forEach((c, i) => {
      out.position.push({ driver_number: c.n, position: i + 1, date: isoL(t), session_key: LIVE.key });
      out.intervals.push({ driver_number: c.n, gap_to_leader: i ? +(((order[0].dist - c.dist) * c.base).toFixed(3)) : 0, interval: i ? +(((order[i - 1].dist - c.dist) * c.base).toFixed(3)) : 0, date: isoL(t), session_key: LIVE.key });
    });
  }
  out.weather.push({ date: isoL(Math.max(0, elapsed - 5)), air_temperature: 22, track_temperature: 35, rainfall: 0, session_key: LIVE.key });
  return out[resource] || [];
}

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

  // Qualifying (77003): three segments separated by >5 min of silence, so the
  // app's segment inference sees Q1 / Q2 / Q3. Grid order == pace order:
  // everyone runs Q1, the top four make Q2, the top two fight in Q3.
  const QT = -40000;
  GRID.forEach(([n, tla, name, team, colour, base], i) => {
    DB.drivers.push({ driver_number: n, name_acronym: tla, full_name: name, broadcast_name: name, team_name: team, team_colour: colour, session_key: 77003 });
    DB.stints.push({ driver_number: n, stint_number: 1, compound: "SOFT", lap_start: 1, tyre_age_at_start: 0, session_key: 77003 });
    let lapNo = 1;
    const put = (t, dur) => DB.laps.push({ driver_number: n, lap_number: lapNo++, lap_duration: +dur.toFixed(3), date_start: iso(QT + t), session_key: 77003 });
    put(60 + i * 12, base + 0.45);
    put(300 + i * 12, base + 0.15);
    if (i < 4) { put(1060 + i * 12, base + 0.05); put(1300 + i * 12, base - 0.15); }
    if (i < 2) { put(2060 + i * 12, base - 0.2); put(2300 + i * 12, base - 0.35); }
  });
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

const server = http.createServer((req, res) => {
  const url = new URL(req.url, `http://localhost:${PORT}`);
  res.setHeader("Access-Control-Allow-Origin", "*");

  // OAuth2 password-grant token endpoint (mirrors api.openf1.org/token)
  if (url.pathname === "/token" && req.method === "POST") {
    let body = "";
    req.on("data", c => (body += c));
    req.on("end", () => {
      const p = new URLSearchParams(body);
      const user = p.get("username"), pass = p.get("password");
      if (!user || !pass) { res.writeHead(422, { "content-type": "application/json" }); return res.end(JSON.stringify({ detail: "missing credentials" })); }
      if (pass === "wrong") { res.writeHead(401, { "content-type": "application/json" }); return res.end(JSON.stringify({ detail: "bad credentials" })); }
      res.writeHead(200, { "content-type": "application/json" });
      res.end(JSON.stringify({ access_token: "mock-token-" + Date.now(), token_type: "bearer", expires_in: 1800 }));
    });
    return;
  }

  if (url.pathname.startsWith("/v1/")) {
    const resource = url.pathname.slice(4);
    let rows;
    if (resource === "meetings") rows = filterRows([MEETING], url.searchParams);
    else if (resource === "sessions") {
      rows = url.searchParams.get("session_key") === "latest"
        ? [SESSIONS.find(s => s.session_key === 77002)]
        : filterRows(SESSIONS, url.searchParams);
    } else if (url.searchParams.get("session_key") === String(LIVE.key)) rows = filterRows(liveRows(resource), url.searchParams);
    else if (resource in DB) rows = filterRows(DB[resource], url.searchParams);
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
});

/* ---------- mock MQTT-over-WebSocket broker ----------
   A dependency-free stand-in for OpenF1's real-time push: completes the RFC6455
   WebSocket handshake, speaks just enough MQTT 3.1.1 to accept a subscriber, and
   then streams timing PUBLISH messages so the browser client + ingest path are
   exercisable offline. Topics mirror the REST endpoints: v1/<endpoint>/<n>. */
const crypto = require("crypto");

function wsFrame(payloadBuf) {                    // server->client binary frame, unmasked
  const len = payloadBuf.length;
  let header;
  if (len < 126) header = Buffer.from([0x82, len]);
  else if (len < 65536) header = Buffer.from([0x82, 126, (len >> 8) & 0xff, len & 0xff]);
  else header = Buffer.from([0x82, 127, 0, 0, 0, 0, (len >>> 24) & 0xff, (len >> 16) & 0xff, (len >> 8) & 0xff, len & 0xff]);
  return Buffer.concat([header, payloadBuf]);
}
function wsDecode(buf) {                          // client->server, one frame; returns {payload, rest} or null
  if (buf.length < 2) return null;
  const len0 = buf[1] & 0x7f; let off = 2, len = len0;
  if (len0 === 126) { len = buf.readUInt16BE(2); off = 4; }
  else if (len0 === 127) { len = Number(buf.readBigUInt64BE(2)); off = 10; }
  const masked = buf[1] & 0x80;
  const mask = masked ? buf.slice(off, off + 4) : null; if (masked) off += 4;
  if (buf.length < off + len) return null;
  const payload = Buffer.from(buf.slice(off, off + len));
  if (masked) for (let i = 0; i < payload.length; i++) payload[i] ^= mask[i % 4];
  return { op: buf[0] & 0x0f, payload, rest: buf.slice(off + len) };
}
const mqttRemLen = (buf, i) => { let m = 1, v = 0, b; do { b = buf[i++]; v += (b & 0x7f) * m; m *= 128; } while (b & 0x80); return [v, i]; };
const mqttLenBytes = n => { const o = []; do { let d = n % 128; n = Math.floor(n / 128); if (n > 0) d |= 0x80; o.push(d); } while (n > 0); return o; };

server.on("upgrade", (req, socket) => {
  const key = req.headers["sec-websocket-key"];
  if (!key) { socket.destroy(); return; }
  const accept = crypto.createHash("sha1").update(key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").digest("base64");
  socket.write(
    "HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n" +
    `Sec-WebSocket-Accept: ${accept}\r\nSec-WebSocket-Protocol: mqtt\r\n\r\n`
  );

  let buf = Buffer.alloc(0), pubTimer = null, t = 0;
  const cars = GRID.map(([n, , , , , base], i) => ({ n, base, dist: -i * 0.004, lap: 0, lastCross: 0 }));
  const sendMqtt = bytes => socket.write(wsFrame(Buffer.from(bytes)));
  const publish = (topic, obj) => {
    const tb = Buffer.from(topic), pb = Buffer.from(JSON.stringify(obj));
    const body = [(tb.length >> 8) & 0xff, tb.length & 0xff, ...tb, ...pb];
    sendMqtt([0x30, ...mqttLenBytes(body.length), ...body]);   // PUBLISH, QoS 0
  };

  function startPublishing() {
    if (pubTimer) return;
    pubTimer = setInterval(() => {
      t += 5;
      for (const c of cars) {
        c.dist += 5 / c.base;
        const lapNow = Math.floor(c.dist) + 1;
        if (lapNow > c.lap && c.dist > 0) {
          if (c.lap >= 1) publish(`v1/laps/${c.n}`, { driver_number: c.n, lap_number: c.lap, lap_duration: +(t - c.lastCross).toFixed(3), date_start: new Date().toISOString(), session_key: LIVE.key });
          c.lastCross = t; c.lap = lapNow;
        }
      }
      const order = [...cars].sort((a, b) => b.dist - a.dist);
      order.forEach((c, i) => {
        publish(`v1/position/${c.n}`, { driver_number: c.n, position: i + 1, date: new Date().toISOString(), session_key: LIVE.key });
        publish(`v1/intervals/${c.n}`, { driver_number: c.n, gap_to_leader: i ? +(((order[0].dist - c.dist) * c.base).toFixed(3)) : 0, interval: i ? +(((order[i - 1].dist - c.dist) * c.base).toFixed(3)) : 0, date: new Date().toISOString(), session_key: LIVE.key });
      });
    }, 500);
    socket.on("close", () => clearInterval(pubTimer));
  }

  socket.on("data", chunk => {
    buf = Buffer.concat([buf, chunk]);
    let frame;
    while ((frame = wsDecode(buf))) {
      buf = frame.rest;
      if (frame.op === 0x8) { socket.end(); return; }        // WS close
      const p = frame.payload; if (!p.length) continue;
      const type = p[0] >> 4;
      if (type === 1) {                                       // CONNECT
        const [, vh] = mqttRemLen(p, 1);                      // connect flags live at varHeader+7
        const flags = p[vh + 7];
        // OpenF1 auth: token is the password. Reject if the password flag (0x40)
        // is unset, so a client that puts the token in the wrong field is caught.
        if (!(flags & 0x40)) { sendMqtt([0x20, 0x02, 0x00, 0x04]); return; }   // CONNACK bad credentials
        sendMqtt([0x20, 0x02, 0x00, 0x00]);                   // CONNACK accepted
      } else if (type === 8) {                                // SUBSCRIBE -> SUBACK, then stream
        const [, i] = mqttRemLen(p, 1);
        sendMqtt([0x90, 0x03, p[i], p[i + 1], 0x00]);
        // publish the driver + stint snapshot once the subscriber is live
        for (const [n, tla, name, team, colour] of GRID) {
          publish(`v1/drivers/${n}`, { driver_number: n, name_acronym: tla, full_name: name, team_name: team, team_colour: colour, session_key: LIVE.key });
          publish(`v1/stints/${n}`, { driver_number: n, stint_number: 1, compound: "SOFT", lap_start: 1, tyre_age_at_start: 0, session_key: LIVE.key });
        }
        startPublishing();
      } else if (type === 12) sendMqtt([0xd0, 0x00]);         // PINGREQ -> PINGRESP
      else if (type === 14) { socket.end(); return; }         // DISCONNECT
    }
  });
  socket.on("error", () => {});
});

server.listen(PORT, () => console.log(`mock OpenF1 + static server on http://localhost:${PORT}`));
