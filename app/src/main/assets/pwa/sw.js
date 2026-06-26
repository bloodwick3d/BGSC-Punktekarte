/* MiniGolf Punktekarte – update-safe service worker (PWA V84)
 *
 * App-Dateien und Nutzerdaten sind strikt getrennt:
 * - Dieser Worker verwaltet ausschließlich Cache Storage für die App-Dateien.
 * - localStorage und IndexedDB werden weder gelesen noch gelöscht.
 */
const APP_VERSION = '84';
const IS_LOCAL_TEST = self.location.hostname === 'localhost' || self.location.hostname === '127.0.0.1';
const APP_CACHE_PREFIX = 'mg-pwa-';
const LOCAL_BUILD_ID = 'local-google-drive-transfer-checklist-1';
const APP_CACHE = `${APP_CACHE_PREFIX}app-v${APP_VERSION}-${LOCAL_BUILD_ID}`;
const UPDATE_META_CACHE = `${APP_CACHE_PREFIX}update-meta-v1`;

const APP_SHELL = [
  './',
  './index.html',
  './script.js',
  './pwa-update.js',
  './bitmap-share.js',
  './tournament-media.js',
  './mgpk-compat.js',
  './drive-config.js',
  './drive-form-client-v5.js',
  './cloud-share.js',
  './manifest.webmanifest',
  './changelog.json',
  './assets/bg_minigolf.jpg',
  './assets/minigolf_logo.png',
  './assets/minigolf_logo_192.png',
  './assets/minigolf_logo_512.png'
];

async function readCurrentRelease() {
  try {
    const cache = await caches.open(APP_CACHE);
    const response = await cache.match('./changelog.json');
    if (!response) return { version: APP_VERSION };
    const changelog = await response.json();
    const releases = Array.isArray(changelog?.releases) ? changelog.releases : [];
    return releases.find(item => String(item?.version) === APP_VERSION) || { version: APP_VERSION };
  } catch (_) {
    return { version: APP_VERSION };
  }
}

self.addEventListener('install', event => {
  event.waitUntil((async () => {
    const cache = await caches.open(APP_CACHE);

    // "reload" verhindert, dass beim Installieren eines neuen Workers alte
    // HTTP-Cache-Versionen in den neuen App-Cache übernommen werden.
    await cache.addAll(APP_SHELL.map(url => new Request(new URL(url, self.location.href), { cache: 'reload' })));

    // Einmalige automatische Umstellung vom alten V81-Updateverfahren.
    // Danach wartet jeder neue Worker auf die Bestätigung des Nutzers.
    const updateSystemWasAlreadyInstalled = await caches.has(UPDATE_META_CACHE);
    if (IS_LOCAL_TEST || !updateSystemWasAlreadyInstalled) await self.skipWaiting();
  })());
});

self.addEventListener('activate', event => {
  event.waitUntil((async () => {
    const cacheNames = await caches.keys();

    // Nur alte App-Caches löschen. Andere Cache-Bereiche sowie IndexedDB und
    // localStorage bleiben vollständig unangetastet.
    await Promise.all(
      cacheNames
        .filter(name =>
          name.startsWith(APP_CACHE_PREFIX) &&
          name !== APP_CACHE &&
          name !== UPDATE_META_CACHE
        )
        .map(name => caches.delete(name))
    );

    const metaCache = await caches.open(UPDATE_META_CACHE);
    await metaCache.put(
      './update-system-ready',
      new Response(APP_VERSION, { headers: { 'Content-Type': 'text/plain' } })
    );

    await self.clients.claim();
  })());
});

self.addEventListener('message', event => {
  if (event.data?.type === 'SKIP_WAITING') {
    self.skipWaiting();
    return;
  }

  if (event.data?.type === 'GET_UPDATE_INFO') {
    event.waitUntil((async () => {
      const info = await readCurrentRelease();
      event.ports?.[0]?.postMessage(info);
    })());
  }
});

self.addEventListener('fetch', event => {
  const request = event.request;

  if (request.method !== 'GET') return;

  const url = new URL(request.url);
  if (url.origin !== self.location.origin) return;

  event.respondWith((async () => {
    // Lokale Entwicklungsdateien immer frisch vom Node-Server laden. Dadurch
    // wird insbesondere eine neu eingetragene Apps-Script-URL in
    // drive-config.js nicht von einem alten Testcache verdeckt.
    if (IS_LOCAL_TEST) return fetch(request, { cache: 'no-store' });

    const cache = await caches.open(APP_CACHE);

    // Navigationen erhalten immer die zur aktiven Worker-Version gehörende
    // index.html. So werden niemals alte und neue JavaScript-Dateien gemischt.
    if (request.mode === 'navigate') {
      const appShell = await cache.match('./index.html');
      if (appShell) return appShell;

      try {
        return await fetch(request);
      } catch (_) {
        return new Response('Die MiniGolf Punktekarte ist derzeit offline nicht verfügbar.', {
          status: 503,
          headers: { 'Content-Type': 'text/plain; charset=utf-8' }
        });
      }
    }

    // App-Dateien zuerst aus dem versionsgebundenen Cache laden.
    const cached = await cache.match(request, { ignoreSearch: true });
    if (cached) return cached;

    return fetch(request);
  })());
});
