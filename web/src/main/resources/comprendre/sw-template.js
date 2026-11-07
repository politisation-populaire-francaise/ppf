// Service worker of Politisation Populaire Française (D-091), written by the server from this template.
// It keeps the practical guides and the pages of "Comprendre" on the device for offline reading.
// Same-origin GET requests only; nothing is ever sent anywhere. Pages: network first, kept copy when the
// network fails. Files of /static/: kept copy first.
'use strict';

const VERSION = "__VERSION__";
const PRECACHE = ["__PRECACHE__"];
const KEPT = ["__KEPT__"];
const FALLBACK = "__FALLBACK__";

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(VERSION)
      .then((cache) => Promise.all(PRECACHE.map((path) =>
        // One missing page must not stop the others from being kept.
        cache.add(new Request(path, { cache: 'reload' })).catch(() => undefined))))
      .then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys
        .filter((key) => key.startsWith('ppf-') && key !== VERSION)
        .map((key) => caches.delete(key))))
      .then(() => self.clients.claim())
  );
});

function isKeptPage(path) {
  return KEPT.some((prefix) => path === prefix || path.startsWith(prefix + '/'));
}

function keep(request, response) {
  if (response && response.ok && response.type === 'basic') {
    const copy = response.clone();
    caches.open(VERSION).then((cache) => cache.put(request, copy)).catch(() => undefined);
  }
  return response;
}

self.addEventListener('fetch', (event) => {
  const request = event.request;
  if (request.method !== 'GET') return;
  const url = new URL(request.url);
  if (url.origin !== self.location.origin) return;

  if (url.pathname.startsWith('/static/')) {
    event.respondWith(
      caches.match(request).then((hit) => hit || fetch(request).then((response) => keep(request, response)))
    );
    return;
  }

  if (request.mode === 'navigate' && isKeptPage(url.pathname)) {
    event.respondWith(
      fetch(request)
        .then((response) => keep(request, response))
        .catch(() => caches.match(request).then((hit) => hit || caches.match(FALLBACK)))
    );
  }
});
