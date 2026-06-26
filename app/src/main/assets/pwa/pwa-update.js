/* Kontrollierte PWA-Updates mit lokalem Changelog.
 * App-Caches werden aktualisiert; localStorage und IndexedDB bleiben erhalten.
 */
(() => {
  'use strict';

  if (!('serviceWorker' in navigator)) return;

  const IS_LOCAL_TEST = location.hostname === 'localhost' || location.hostname === '127.0.0.1';
  const CHECK_INTERVAL_MS = 60 * 60 * 1000;
  const CHANGELOG_SEEN_KEY = 'mg-pwa-changelog-seen';
  const UPDATE_PENDING_KEY = 'mg-pwa-update-pending';
  let registration = null;
  let reloading = false;
  let dismissedWorker = null;
  let currentNoticeWorker = null;

  function addUpdateStyles() {
    if (document.getElementById('pwaUpdateStyles')) return;

    const style = document.createElement('style');
    style.id = 'pwaUpdateStyles';
    style.textContent = `
      .pwaUpdateNotice{
        position:fixed;
        left:50%;
        bottom:calc(16px + env(safe-area-inset-bottom, 0px));
        transform:translateX(-50%);
        z-index:2147483647;
        width:min(410px, calc(100vw - 24px));
        max-height:min(72dvh, 610px);
        overflow:auto;
        overscroll-behavior:contain;
        box-sizing:border-box;
        padding:16px;
        border:1px solid rgba(255,255,255,.12);
        border-radius:18px;
        background:rgba(24,31,25,.98);
        color:#fff;
        box-shadow:0 12px 38px rgba(0,0,0,.48);
        font-family:system-ui,-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif;
        -webkit-overflow-scrolling:touch;
      }
      .pwaUpdateNotice[hidden]{display:none!important}
      .pwaUpdateEyebrow{font-size:12px;font-weight:750;letter-spacing:.03em;color:#9fd6a5;margin:0 0 4px}
      .pwaUpdateTitle{font-size:17px;font-weight:780;line-height:1.25;margin:0 0 5px}
      .pwaUpdateText{font-size:13px;line-height:1.4;color:rgba(255,255,255,.84);margin:0}
      .pwaUpdateChanges{margin:12px 0 0;padding:11px 12px 11px 30px;border-radius:12px;background:rgba(255,255,255,.075)}
      .pwaUpdateChanges[hidden]{display:none!important}
      .pwaUpdateChanges li{font-size:13px;line-height:1.38;margin:0 0 7px;color:rgba(255,255,255,.92)}
      .pwaUpdateChanges li:last-child{margin-bottom:0}
      .pwaUpdateSafety{display:flex;align-items:flex-start;gap:8px;margin:11px 0 0;font-size:12px;line-height:1.35;color:rgba(255,255,255,.7)}
      .pwaUpdateSafetyIcon{flex:0 0 auto;color:#9fd6a5;font-size:15px;line-height:1.2}
      .pwaUpdateActions{display:flex;justify-content:flex-end;gap:8px;margin-top:14px;position:sticky;bottom:-16px;padding:8px 0 0;background:linear-gradient(transparent,rgba(24,31,25,.98) 32%)}
      .pwaUpdateButton{
        min-height:42px;
        border:0;
        border-radius:21px;
        padding:0 17px;
        font:inherit;
        font-size:14px;
        font-weight:720;
        cursor:pointer;
        touch-action:manipulation;
      }
      .pwaUpdateLater{background:transparent;color:#b7d8ba}
      .pwaUpdateNow{background:#8bcf91;color:#102313}
      .pwaUpdateButton:disabled{opacity:.62;cursor:default}
      @media (hover:hover) and (pointer:fine) and (min-width:700px){
        .pwaUpdateNotice{width:min(410px, calc(430px - 24px))}
      }
    `;
    document.head.appendChild(style);
  }

  function getUpdateNotice() {
    let notice = document.getElementById('pwaUpdateNotice');
    if (notice) return notice;

    addUpdateStyles();
    notice = document.createElement('aside');
    notice.id = 'pwaUpdateNotice';
    notice.className = 'pwaUpdateNotice';
    notice.hidden = true;
    notice.setAttribute('role', 'dialog');
    notice.setAttribute('aria-live', 'polite');
    notice.setAttribute('aria-labelledby', 'pwaUpdateTitle');
    notice.innerHTML = `
      <p class="pwaUpdateEyebrow">APP-UPDATE</p>
      <p class="pwaUpdateTitle" id="pwaUpdateTitle">Neue Version verfügbar</p>
      <p class="pwaUpdateText">Die Details werden geladen …</p>
      <ul class="pwaUpdateChanges" hidden></ul>
      <p class="pwaUpdateSafety"><span class="pwaUpdateSafetyIcon" aria-hidden="true">✓</span><span>Deine lokalen Spielstände, Einstellungen und Bilder bleiben erhalten.</span></p>
      <div class="pwaUpdateActions">
        <button class="pwaUpdateButton pwaUpdateLater" type="button">Später</button>
        <button class="pwaUpdateButton pwaUpdateNow" type="button">Jetzt aktualisieren</button>
      </div>
    `;
    document.body.appendChild(notice);
    return notice;
  }

  function normalizeRelease(info) {
    if (!info || typeof info !== 'object') return null;
    const changes = Array.isArray(info.changes)
      ? info.changes.filter(item => typeof item === 'string' && item.trim()).slice(0, 8)
      : [];
    return {
      version: String(info.version || '').trim(),
      title: String(info.title || '').trim(),
      summary: String(info.summary || '').trim(),
      changes
    };
  }

  function requestWorkerInfo(worker, timeoutMs = 1800) {
    return new Promise(resolve => {
      if (!worker || typeof MessageChannel === 'undefined') {
        resolve(null);
        return;
      }

      const channel = new MessageChannel();
      let settled = false;
      const finish = value => {
        if (settled) return;
        settled = true;
        clearTimeout(timer);
        channel.port1.onmessage = null;
        resolve(normalizeRelease(value));
      };
      const timer = window.setTimeout(() => finish(null), timeoutMs);
      channel.port1.onmessage = event => finish(event.data);

      try {
        worker.postMessage({ type: 'GET_UPDATE_INFO' }, [channel.port2]);
      } catch (_) {
        finish(null);
      }
    });
  }

  async function fetchLatestRelease() {
    try {
      const response = await fetch(`./changelog.json?update-check=${Date.now()}`, { cache: 'no-store' });
      if (!response.ok) return null;
      const changelog = await response.json();
      const releases = Array.isArray(changelog?.releases) ? changelog.releases : [];
      const latest = releases.find(item => String(item?.version) === String(changelog?.latestVersion)) || releases[0];
      return normalizeRelease(latest);
    } catch (_) {
      return null;
    }
  }

  function renderRelease(notice, release, installed = false) {
    const eyebrow = notice.querySelector('.pwaUpdateEyebrow');
    const title = notice.querySelector('.pwaUpdateTitle');
    const text = notice.querySelector('.pwaUpdateText');
    const list = notice.querySelector('.pwaUpdateChanges');
    const safety = notice.querySelector('.pwaUpdateSafety');

    eyebrow.textContent = installed ? 'UPDATE INSTALLIERT' : 'APP-UPDATE';

    if (release?.version) {
      title.textContent = installed ? `Neu in PWA V${release.version}` : `PWA V${release.version} verfügbar`;
    } else {
      title.textContent = installed ? 'Update installiert' : 'Neue Version verfügbar';
    }

    text.textContent = release?.summary || release?.title || (installed
      ? 'Die neue Version ist jetzt aktiv.'
      : 'Die App wird einmal neu geladen, sobald du das Update bestätigst.');

    list.replaceChildren();
    for (const change of release?.changes || []) {
      const item = document.createElement('li');
      item.textContent = change;
      list.appendChild(item);
    }
    list.hidden = list.childElementCount === 0;
    safety.hidden = installed;
  }

  async function showUpdateNotice(worker) {
    if (!worker || worker === dismissedWorker) return;
    if (IS_LOCAL_TEST) {
      worker.postMessage({ type: 'SKIP_WAITING' });
      return;
    }
    if (currentNoticeWorker === worker && !getUpdateNotice().hidden) return;
    currentNoticeWorker = worker;

    const notice = getUpdateNotice();
    const laterButton = notice.querySelector('.pwaUpdateLater');
    const updateButton = notice.querySelector('.pwaUpdateNow');

    let offeredRelease = null;
    laterButton.hidden = false;
    laterButton.disabled = false;
    updateButton.disabled = false;
    laterButton.textContent = 'Später';
    updateButton.textContent = 'Jetzt aktualisieren';

    laterButton.onclick = () => {
      dismissedWorker = worker;
      currentNoticeWorker = null;
      notice.hidden = true;
    };

    updateButton.onclick = () => {
      updateButton.disabled = true;
      laterButton.disabled = true;
      updateButton.textContent = 'Aktualisiere …';
      if (offeredRelease?.version) {
        try { sessionStorage.setItem(UPDATE_PENDING_KEY, offeredRelease.version); } catch (_) {}
      }
      worker.postMessage({ type: 'SKIP_WAITING' });
    };

    renderRelease(notice, null, false);
    notice.hidden = false;

    offeredRelease = await requestWorkerInfo(worker) || await fetchLatestRelease();
    if (currentNoticeWorker === worker && !notice.hidden) renderRelease(notice, offeredRelease, false);
  }

  async function showInstalledChangesOnce(reg) {
    if (IS_LOCAL_TEST) return;
    if (!reg?.active || reg.waiting) return;
    const release = await requestWorkerInfo(reg.active) || await fetchLatestRelease();
    if (!release?.version) return;

    let seen = '';
    let pending = '';
    try { seen = localStorage.getItem(CHANGELOG_SEEN_KEY) || ''; } catch (_) {}
    try { pending = sessionStorage.getItem(UPDATE_PENDING_KEY) || ''; } catch (_) {}
    if (pending === release.version) {
      try { localStorage.setItem(CHANGELOG_SEEN_KEY, release.version); } catch (_) {}
      try { sessionStorage.removeItem(UPDATE_PENDING_KEY); } catch (_) {}
      return;
    }
    if (seen === release.version) return;

    const notice = getUpdateNotice();
    const laterButton = notice.querySelector('.pwaUpdateLater');
    const updateButton = notice.querySelector('.pwaUpdateNow');
    currentNoticeWorker = null;
    renderRelease(notice, release, true);
    laterButton.hidden = true;
    updateButton.disabled = false;
    updateButton.textContent = 'Verstanden';
    updateButton.onclick = () => {
      try { localStorage.setItem(CHANGELOG_SEEN_KEY, release.version); } catch (_) {}
      notice.hidden = true;
      laterButton.hidden = false;
    };
    notice.hidden = false;
  }

  function watchRegistration(reg) {
    if (reg.waiting) showUpdateNotice(reg.waiting);

    reg.addEventListener('updatefound', () => {
      const worker = reg.installing;
      if (!worker) return;

      worker.addEventListener('statechange', () => {
        if (worker.state === 'installed' && navigator.serviceWorker.controller) {
          showUpdateNotice(worker);
        }
      });
    });
  }

  async function checkForUpdate() {
    if (!registration || !navigator.onLine) return;
    try {
      await registration.update();
      if (registration.waiting) showUpdateNotice(registration.waiting);
    } catch (error) {
      console.debug('PWA-Updateprüfung derzeit nicht möglich:', error);
    }
  }

  navigator.serviceWorker.addEventListener('controllerchange', () => {
    if (reloading) return;
    reloading = true;
    window.location.reload();
  });

  window.addEventListener('load', async () => {
    try {
      registration = await navigator.serviceWorker.register('./sw.js', {
        scope: './',
        updateViaCache: 'none'
      });

      watchRegistration(registration);

      // Neue Versionen prüfen, ohne lokale Nutzerdaten anzufassen.
      window.setTimeout(checkForUpdate, 1500);
      window.setInterval(checkForUpdate, CHECK_INTERVAL_MS);

      // Zeigt den Changelog einer frisch installierten Version genau einmal.
      window.setTimeout(() => {
        if (!registration.waiting) showInstalledChangesOnce(registration);
      }, 900);
    } catch (error) {
      console.error('Service Worker konnte nicht registriert werden:', error);
    }
  });

  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'visible') checkForUpdate();
  });

  window.addEventListener('online', checkForUpdate);
})();
