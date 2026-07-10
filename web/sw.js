/* Project Apex service worker.
 * Network-first with cache fallback for the app shell, so the installed PWA
 * starts instantly and still opens with no signal (Demo mode works offline).
 * Timing data (/v1/, api.openf1.org) and Anthropic calls are never cached.
 */
const CACHE = "apex-v2026-07-10";
const SHELL = ["./", "./index.html", "./manifest.json", "./icon.svg"];

self.addEventListener("install", e => {
  e.waitUntil(caches.open(CACHE).then(c => c.addAll(SHELL)).then(() => self.skipWaiting()));
});

self.addEventListener("activate", e => {
  e.waitUntil(
    caches.keys()
      .then(keys => Promise.all(keys.filter(k => k !== CACHE).map(k => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener("fetch", e => {
  const url = new URL(e.request.url);
  if (e.request.method !== "GET") return;
  if (url.origin !== location.origin) return;            // API + audio hosts: straight to network
  if (url.pathname.includes("/v1/")) return;             // local mock API: never cache
  e.respondWith(
    fetch(e.request)
      .then(res => {
        if (res.ok) {
          const copy = res.clone();
          caches.open(CACHE).then(c => c.put(e.request, copy));
        }
        return res;
      })
      .catch(() =>
        caches.match(e.request).then(hit => hit || (e.request.mode === "navigate" ? caches.match("./index.html") : undefined))
      )
  );
});
