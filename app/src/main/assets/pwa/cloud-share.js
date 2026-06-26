/* MiniGolf PWA V84 – lokaler Google-Drive-Freigabetest.
 *
 * - Die PWA läuft auf localhost.
 * - Formular-POST-Anfragen verbinden die lokale PWA mit Google Apps Script.
 * - Notiztexte und Bilder werden bereits im Browser verschlüsselt.
 * - Der geheime Teil des Freigabecodes wird nicht an Google gesendet.
 */
(() => {
  'use strict';

  const DRIVE_BRIDGE = window.MiniGolfDriveBridge || null;
  const USE_APPS_SCRIPT = window.MINIGOLF_DRIVE_CONFIG?.mode === 'apps-script';
  const OWNER_KEY = 'mg-cloud-owner-v1';
  const CODE_ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  const MAX_DAYS = 30;
  const WEBP_MAX_WIDTH = 900;
  const WEBP_MAX_HEIGHT = 1200;
  const WEBP_QUALITY = 0.82;
  const CONCURRENCY = 3;

  function randomBytes(length) {
    const bytes = new Uint8Array(length);
    crypto.getRandomValues(bytes);
    return bytes;
  }

  function base32Encode(bytes) {
    let bits = 0;
    let value = 0;
    let output = '';
    for (const byte of bytes) {
      value = (value << 8) | byte;
      bits += 8;
      while (bits >= 5) {
        output += CODE_ALPHABET[(value >>> (bits - 5)) & 31];
        bits -= 5;
      }
    }
    if (bits > 0) output += CODE_ALPHABET[(value << (5 - bits)) & 31];
    return output;
  }

  function base32Decode(value) {
    const clean = String(value || '').toUpperCase().replace(/[^A-Z2-9]/g, '');
    let bits = 0;
    let buffer = 0;
    const out = [];
    for (const char of clean) {
      const index = CODE_ALPHABET.indexOf(char);
      if (index < 0) throw new Error('Ungültiger Freigabecode');
      buffer = (buffer << 5) | index;
      bits += 5;
      if (bits >= 8) {
        out.push((buffer >>> (bits - 8)) & 255);
        bits -= 8;
      }
    }
    return new Uint8Array(out);
  }

  function formatCode(raw) {
    return String(raw || '').replace(/[^A-Z2-9]/gi, '').toUpperCase().match(/.{1,4}/g)?.join('-') || '';
  }

  function createShareCode() {
    const locator = base32Encode(randomBytes(5)).slice(0, 8);
    const secret = base32Encode(randomBytes(12)).slice(0, 20);
    return { locator, secret, code: formatCode(locator + secret) };
  }

  function parseShareCode(value) {
    const clean = String(value || '').toUpperCase().replace(/[^A-Z2-9]/g, '');
    if (clean.length !== 28) throw new Error('Der Freigabecode muss 28 Zeichen enthalten.');
    return { locator: clean.slice(0, 8), secret: clean.slice(8), code: formatCode(clean) };
  }

  function getOwnerId() {
    let id = localStorage.getItem(OWNER_KEY);
    if (!id) {
      id = `${base32Encode(randomBytes(10))}-${Date.now().toString(36)}`;
      localStorage.setItem(OWNER_KEY, id);
    }
    return id;
  }

  async function sha256Hex(data) {
    const buffer = data instanceof ArrayBuffer ? data : await data.arrayBuffer();
    const digest = new Uint8Array(await crypto.subtle.digest('SHA-256', buffer));
    return [...digest].map(byte => byte.toString(16).padStart(2, '0')).join('');
  }

  async function keyFromSecret(secret) {
    const material = new TextEncoder().encode(`MGPK-CLOUD-V1:${secret}`);
    const digest = await crypto.subtle.digest('SHA-256', material);
    return crypto.subtle.importKey('raw', digest, { name: 'AES-GCM' }, false, ['encrypt', 'decrypt']);
  }

  async function encryptBlob(blob, key, fileId) {
    const iv = randomBytes(12);
    const plain = await blob.arrayBuffer();
    const cipher = await crypto.subtle.encrypt(
      { name: 'AES-GCM', iv, additionalData: new TextEncoder().encode(fileId) },
      key,
      plain
    );
    const output = new Uint8Array(iv.length + cipher.byteLength);
    output.set(iv, 0);
    output.set(new Uint8Array(cipher), iv.length);
    return new Blob([output], { type: 'application/octet-stream' });
  }

  async function decryptBlob(blob, key, fileId, type = 'application/octet-stream') {
    const bytes = new Uint8Array(await blob.arrayBuffer());
    if (bytes.length < 29) throw new Error('Verschlüsselte Datei ist beschädigt.');
    const iv = bytes.slice(0, 12);
    const plain = await crypto.subtle.decrypt(
      { name: 'AES-GCM', iv, additionalData: new TextEncoder().encode(fileId) },
      key,
      bytes.slice(12)
    );
    return new Blob([plain], { type });
  }

  function imageFromBlob(blob) {
    return new Promise((resolve, reject) => {
      const url = URL.createObjectURL(blob);
      const image = new Image();
      image.decoding = 'async';
      image.onload = () => { URL.revokeObjectURL(url); resolve(image); };
      image.onerror = () => { URL.revokeObjectURL(url); reject(new Error('Bild konnte nicht verarbeitet werden.')); };
      image.src = url;
    });
  }

  function canvasBlob(canvas, type, quality) {
    return new Promise((resolve, reject) => {
      canvas.toBlob(blob => blob ? resolve(blob) : reject(new Error('Bild konnte nicht konvertiert werden.')), type, quality);
    });
  }

  async function toOptimizedWebp(sourceBlob) {
    const image = await imageFromBlob(sourceBlob);
    const sourceWidth = image.naturalWidth || image.width;
    const sourceHeight = image.naturalHeight || image.height;
    const scale = Math.min(1, WEBP_MAX_WIDTH / sourceWidth, WEBP_MAX_HEIGHT / sourceHeight);
    const width = Math.max(1, Math.round(sourceWidth * scale));
    const height = Math.max(1, Math.round(sourceHeight * scale));
    const canvas = document.createElement('canvas');
    canvas.width = width;
    canvas.height = height;
    const context = canvas.getContext('2d', { alpha: false });
    context.fillStyle = '#fff';
    context.fillRect(0, 0, width, height);
    context.drawImage(image, 0, 0, width, height);
    const webp = await canvasBlob(canvas, 'image/webp', WEBP_QUALITY);
    if (webp.type === 'image/webp') return { blob: webp, width, height, type: 'image/webp' };
    const jpeg = await canvasBlob(canvas, 'image/jpeg', 0.84);
    return { blob: jpeg, width, height, type: 'image/jpeg' };
  }

  function parsedBody(options) {
    if (!options?.body) return {};
    if (typeof options.body === 'string') {
      try { return JSON.parse(options.body); } catch (_) { return {}; }
    }
    return options.body;
  }

  function requireDriveBridge() {
    if (!USE_APPS_SCRIPT || !DRIVE_BRIDGE) {
      throw new Error('Die Google-Drive-Bridge wurde nicht geladen.');
    }
    return DRIVE_BRIDGE;
  }

  async function apiJson(path, options = {}) {
    const bridge = requireDriveBridge();
    if (path === '/start') return bridge.request('start', parsedBody(options));
    if (path === '/finish') return bridge.request('finish', parsedBody(options));
    if (path === '/cancel') return bridge.request('cancel', parsedBody(options));
    if (path === '/delete') return bridge.request('delete', parsedBody(options));
    if (path.startsWith('/info/')) return bridge.request('info', { locator: decodeURIComponent(path.slice('/info/'.length)) });
    throw new Error(`Unbekannte Drive-Anfrage: ${path}`);
  }

  async function apiUpload(uploadId, file, uploadToken) {
    return requireDriveBridge().upload(uploadId, file, uploadToken);
  }

  async function apiFile(locator, fileId) {
    return requireDriveBridge().download(locator, fileId);
  }

  async function runPool(items, worker, concurrency = CONCURRENCY, onProgress = null) {
    let next = 0;
    let completed = 0;
    const results = new Array(items.length);
    async function runner() {
      while (true) {
        const index = next++;
        if (index >= items.length) return;
        results[index] = await worker(items[index], index);
        completed += 1;
        onProgress?.(completed, items.length, items[index]);
      }
    }
    await Promise.all(Array.from({ length: Math.min(concurrency, items.length || 1) }, runner));
    return results;
  }

  function addStyles() {
    if (document.getElementById('cloudShareStyles')) return;
    const style = document.createElement('style');
    style.id = 'cloudShareStyles';
    style.textContent = `
      .cloudShareLayer{position:fixed;inset:0;z-index:620;display:flex;align-items:flex-end;justify-content:center;background:rgba(0,0,0,.36);padding:18px 12px calc(12px + env(safe-area-inset-bottom,0px))}
      .cloudShareConfirmLayer{z-index:700;align-items:center}
      .cloudShareSheet{width:min(430px,100%);max-height:min(82dvh,720px);overflow:auto;box-sizing:border-box;border-radius:24px;background:#fff;color:#111;padding:20px;box-shadow:0 18px 55px rgba(0,0,0,.38);text-shadow:none}
      .cloudShareSheet h2{font-size:21px;line-height:1.25;margin:0 0 7px}.cloudShareSheet p{font-size:14px;line-height:1.45;margin:0 0 14px;color:#454545}
      .cloudShareMeta{padding:12px 14px;border-radius:14px;background:#f2f3f4;margin:12px 0;font-size:14px;line-height:1.5}
      .cloudShareDurations{display:grid;grid-template-columns:repeat(3,1fr);gap:8px;margin:14px 0}.cloudShareDurations button{min-height:44px;border:1px solid #d1d5d8;border-radius:14px;background:#fff;font-weight:750}.cloudShareDurations button.selected{background:#e3f2fd;border-color:#2196f3;color:#0b63a5}
      .cloudShareActions{display:flex;gap:10px;margin-top:16px}.cloudShareActions button{flex:1;min-height:48px;border:0;border-radius:24px;font-size:15px;font-weight:760}.cloudShareCancel{background:#eceff1;color:#263238}.cloudSharePrimary{background:#2196f3;color:#fff}.cloudShareDanger{background:#e53935;color:#fff}
      .cloudShareProgress{height:10px;border-radius:6px;background:#e5e7e9;overflow:hidden;margin:14px 0 7px}.cloudShareProgress i{display:block;height:100%;width:0;background:#2196f3;transition:width .18s ease}.cloudShareProgressText{font-size:13px;font-weight:700;color:#555}
      .cloudShareCode{display:flex;align-items:center;gap:8px;margin:12px 0}.cloudShareCode code{flex:1;overflow-wrap:anywhere;padding:12px;border-radius:12px;background:#eef3f7;font:700 15px/1.35 ui-monospace,SFMono-Regular,Consolas,monospace}.cloudShareCode button{width:48px;height:48px;border:0;border-radius:50%;background:#2196f3;color:#fff;display:grid;place-items:center}.cloudShareCode .mi{width:24px;height:24px;fill:currentColor}
      .cloudShareInput{width:100%;height:54px;box-sizing:border-box;border:1px solid #9aa1a6;border-radius:14px;padding:0 14px;font:700 17px ui-monospace,SFMono-Regular,Consolas,monospace;text-transform:uppercase;outline:none}
      .cloudSharePreviewList{margin:10px 0 0;padding:0;list-style:none}.cloudSharePreviewList li{padding:6px 0;border-bottom:1px solid #eceff1;font-size:14px}.cloudSharePreviewList li:last-child{border-bottom:0}
      .tourCloudStatus{position:absolute;left:18px;right:8px;bottom:7px;display:flex;align-items:center;gap:7px;height:25px;font-size:11px;font-weight:760;color:#1976d2;text-shadow:none}.tourCloudStatus .mi{width:17px;height:17px;fill:currentColor}.tourCloudStatus button{margin-left:auto;width:34px;height:28px;border:0;border-radius:14px;background:transparent;color:inherit;display:grid;place-items:center}.tourCloudStatus button .mi{width:18px;height:18px}.tourNoteCard.hasCloudShare{min-height:98px!important;height:98px!important;padding-bottom:27px!important}.tourNoteCard.hasCloudShare .tourNoteMenuButton{top:38px!important}

      .cloudTransferSheet{padding:18px 18px calc(18px + env(safe-area-inset-bottom,0px));border-radius:26px}
      .cloudTransferHandle{width:44px;height:5px;border-radius:4px;background:#d8dde1;margin:0 auto 13px}
      .cloudTransferHeader{display:flex;align-items:flex-start;gap:12px}.cloudTransferHeaderIcon{width:47px;height:47px;border-radius:15px;background:#e3f2fd;color:#2196f3;display:grid;place-items:center;flex:0 0 auto}.cloudTransferHeaderIcon .mi{width:27px;height:27px;fill:currentColor}.cloudTransferHeaderText{min-width:0;flex:1}.cloudTransferHeader h2{font-size:22px;line-height:1.15;margin:1px 0 4px}.cloudTransferHeader p{font-size:13.5px;line-height:1.35;color:#68737d;margin:0}
      .cloudTransferSummary{border-radius:16px;background:#f3f6f8;padding:11px 13px;margin:16px 0 13px;display:flex;align-items:center;gap:11px}.cloudTransferSummaryIcon{width:36px;height:36px;border-radius:12px;background:#fff;display:grid;place-items:center;color:#67737d;flex:0 0 auto}.cloudTransferSummaryIcon .mi{width:21px;height:21px;fill:currentColor}.cloudTransferSummaryText{min-width:0}.cloudTransferSummaryText b{display:block;font-size:14px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.cloudTransferSummaryText span{display:block;color:#68737d;font-size:12px;margin-top:2px}
      .cloudTransferProgressWrap{margin:2px 1px 15px}.cloudTransferProgressTop{display:flex;justify-content:space-between;align-items:end;margin-bottom:7px}.cloudTransferProgressTop b{font-size:13px}.cloudTransferProgressTop span{font-size:12px;color:#68737d}.cloudTransferTrack{height:9px;border-radius:8px;background:#e5e9ed;overflow:hidden}.cloudTransferBar{height:100%;width:0;background:linear-gradient(90deg,#2196f3,#4bb4ff);border-radius:8px;transition:width .35s ease}
      .cloudTransferSteps{list-style:none;margin:0;padding:0}.cloudTransferStep{display:grid;grid-template-columns:36px 1fr auto;gap:10px;align-items:center;min-height:55px;position:relative}.cloudTransferStep:not(:last-child)::after{content:"";position:absolute;left:17px;top:39px;bottom:-4px;width:2px;background:#e4e8eb}.cloudTransferStep.done:not(:last-child)::after{background:#8ad2a5}.cloudTransferStepDot{position:relative;z-index:1;width:36px;height:36px;border-radius:50%;display:grid;place-items:center;background:#edf1f4;color:#84909a;border:2px solid #edf1f4;font-size:13px;font-weight:800}.cloudTransferStepDot .mi{width:19px;height:19px;fill:currentColor}.cloudTransferStep.active .cloudTransferStepDot{background:#e3f2fd;color:#2196f3;border-color:#9ed2fa}.cloudTransferStep.done .cloudTransferStepDot{background:#e5f6eb;color:#2eaf62;border-color:#b9e6c9}.cloudTransferStep.failed .cloudTransferStepDot{background:#ffebee;color:#e53935;border-color:#ffcdd2}.cloudTransferSpinner{width:18px;height:18px;border:2.5px solid #b8ddf7;border-top-color:#2196f3;border-radius:50%;animation:cloudTransferSpin .75s linear infinite}.cloudTransferStepText b{font-size:14.5px;display:block}.cloudTransferStepText span{font-size:12.5px;color:#68737d;display:block;margin-top:2px}.cloudTransferStepState{font-size:11px;font-weight:750;color:#89939b}.cloudTransferStep.active .cloudTransferStepState{color:#2196f3}.cloudTransferStep.done .cloudTransferStepState{color:#2eaf62}.cloudTransferStep.failed .cloudTransferStepState{color:#e53935}
      .cloudTransferLive{margin-top:12px;min-height:49px;border-radius:14px;background:#eef7fe;color:#2a668f;padding:10px 12px;display:flex;align-items:center;gap:9px;font-size:12.5px;line-height:1.35}.cloudTransferPulse{width:9px;height:9px;border-radius:50%;background:#2196f3;box-shadow:0 0 0 0 rgba(33,150,243,.45);animation:cloudTransferPulse 1.5s infinite;flex:0 0 auto}.cloudTransferLive.done{background:#eaf7ee;color:#267344}.cloudTransferLive.done .cloudTransferPulse{background:#2eaf62;animation:none}.cloudTransferLive.failed{background:#ffebee;color:#a51d2b}.cloudTransferLive.failed .cloudTransferPulse{background:#e53935;animation:none}.cloudTransferLive.idle{background:#f3f5f6;color:#68737d}.cloudTransferLive.idle .cloudTransferPulse{background:#a7b0b7;animation:none}
      .cloudTransferResult{display:none;margin-top:13px;border-radius:18px;padding:15px;background:linear-gradient(135deg,#e9f8ee,#f7fcf8);border:1px solid #cdebd7}.cloudTransferResult.show{display:block;animation:cloudTransferRise .28s ease-out}.cloudTransferResult.failed{background:linear-gradient(135deg,#fff0f1,#fff8f8);border-color:#ffcdd2}.cloudTransferResultHead{display:flex;gap:11px;align-items:center}.cloudTransferResultCheck{width:44px;height:44px;border-radius:50%;display:grid;place-items:center;background:#2eaf62;color:#fff;flex:0 0 auto}.cloudTransferResult.failed .cloudTransferResultCheck{background:#e53935}.cloudTransferResultCheck .mi{width:25px;height:25px;fill:currentColor}.cloudTransferResult h3{font-size:18px;margin:0 0 3px;color:#215f38}.cloudTransferResult.failed h3{color:#9b1c28}.cloudTransferResult p{font-size:12.5px;color:#4d745a;margin:0}.cloudTransferResult.failed p{color:#7e3b42}.cloudTransferResultMeta{margin-top:10px;font-size:12.5px;color:#4d745a}.cloudTransferCode{display:flex;align-items:center;gap:8px;margin-top:12px;background:#fff;border-radius:12px;padding:9px 10px}.cloudTransferCode code{font:700 12px/1.3 ui-monospace,SFMono-Regular,Consolas,monospace;word-break:break-all;flex:1;color:#2f4858}.cloudTransferCode button{border:0;border-radius:10px;background:#e3f2fd;color:#1565c0;width:37px;height:37px;display:grid;place-items:center}.cloudTransferCode button .mi{width:19px;height:19px;fill:currentColor}.cloudTransferCode[hidden],.cloudTransferActions[hidden],.cloudTransferResultMeta[hidden]{display:none!important}.cloudTransferActions{display:flex;gap:10px;margin-top:15px}.cloudTransferActions button{height:49px;border:0;border-radius:25px;font-size:15px;font-weight:760;cursor:pointer;flex:1}.cloudTransferDone{background:#2eaf62;color:#fff;box-shadow:0 5px 14px rgba(46,175,98,.24)}.cloudTransferClose{background:#eceff1;color:#35434d}
      @keyframes cloudTransferSpin{to{transform:rotate(360deg)}}@keyframes cloudTransferPulse{70%{box-shadow:0 0 0 8px rgba(33,150,243,0)}}@keyframes cloudTransferRise{from{opacity:0;transform:translateY(5px)}to{opacity:1;transform:none}}
      @media (max-height:740px){.cloudTransferSheet{padding-top:12px}.cloudTransferHandle{margin-bottom:8px}.cloudTransferStep{min-height:49px}.cloudTransferStep:not(:last-child)::after{top:36px}.cloudTransferLive{margin-top:8px}}
      @media (min-width:700px){.cloudShareLayer{align-items:center}}
    `;
    document.head.appendChild(style);
  }

  function closeLayer(layer) { layer?.remove(); }

  function createSheet(html) {
    addStyles();
    const layer = document.createElement('div');
    layer.className = 'cloudShareLayer';
    layer.innerHTML = `<section class="cloudShareSheet" role="dialog" aria-modal="true">${html}</section>`;
    layer.addEventListener('pointerdown', event => { if (event.target === layer && !layer.classList.contains('cloudShareBusy')) closeLayer(layer); });
    document.body.appendChild(layer);
    return layer;
  }

  function nextPaint() {
    return new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)));
  }

  function countNoteImages(note) {
    return mediaNormalizeNote(note || {}).holes.reduce((sum, hole) => sum + hole.images.length, 0);
  }

  function createTransferChecklist(sheet, config) {
    const steps = Array.isArray(config.steps) ? config.steps : [];
    sheet.classList.add('cloudTransferSheet');
    sheet.innerHTML = `
      <div class="cloudTransferHandle"></div>
      <div class="cloudTransferHeader">
        <div class="cloudTransferHeaderIcon">${icon(config.mode === 'download' ? 'cloud_download' : 'cloud_upload')}</div>
        <div class="cloudTransferHeaderText"><h2>${esc(config.title)}</h2><p>${esc(config.subtitle)}</p></div>
      </div>
      <div class="cloudTransferSummary">
        <div class="cloudTransferSummaryIcon">${icon(config.mode === 'download' ? 'file_download' : 'file_upload')}</div>
        <div class="cloudTransferSummaryText"><b>${esc(config.summaryTitle)}</b><span>${esc(config.summaryMeta)}</span></div>
      </div>
      <div class="cloudTransferProgressWrap">
        <div class="cloudTransferProgressTop"><b data-transfer-label>Bereit</b><span data-transfer-percent>0 %</span></div>
        <div class="cloudTransferTrack"><div class="cloudTransferBar" data-transfer-bar></div></div>
      </div>
      <ol class="cloudTransferSteps">${steps.map((step, index) => `
        <li class="cloudTransferStep" data-transfer-step="${index}">
          <div class="cloudTransferStepDot"><span>${index + 1}</span></div>
          <div class="cloudTransferStepText"><b>${esc(step.title)}</b><span>${esc(step.detail)}</span></div>
          <div class="cloudTransferStepState">Wartet</div>
        </li>`).join('')}</ol>
      <div class="cloudTransferLive idle" data-transfer-live role="status" aria-live="polite" aria-atomic="true"><span class="cloudTransferPulse"></span><span data-transfer-live-text>Der Vorgang wird gestartet …</span></div>
      <section class="cloudTransferResult" data-transfer-result>
        <div class="cloudTransferResultHead"><div class="cloudTransferResultCheck" data-transfer-result-icon>${icon('check')}</div><div><h3 data-transfer-result-title>Fertig</h3><p data-transfer-result-text></p></div></div>
        <div class="cloudTransferResultMeta" data-transfer-result-meta hidden></div>
        <div class="cloudTransferCode" data-transfer-code hidden><code></code><button type="button" aria-label="Freigabecode kopieren">${icon('content_copy')}</button></div>
      </section>
      <div class="cloudTransferActions" data-transfer-actions hidden><button type="button" class="cloudTransferDone" data-transfer-close>Fertig</button></div>`;

    const bar = sheet.querySelector('[data-transfer-bar]');
    const progressLabel = sheet.querySelector('[data-transfer-label]');
    const progressPercent = sheet.querySelector('[data-transfer-percent]');
    const live = sheet.querySelector('[data-transfer-live]');
    const liveText = sheet.querySelector('[data-transfer-live-text]');
    const result = sheet.querySelector('[data-transfer-result]');
    const resultIcon = sheet.querySelector('[data-transfer-result-icon]');
    const resultTitle = sheet.querySelector('[data-transfer-result-title]');
    const resultText = sheet.querySelector('[data-transfer-result-text]');
    const resultMeta = sheet.querySelector('[data-transfer-result-meta]');
    const codeBox = sheet.querySelector('[data-transfer-code]');
    const actions = sheet.querySelector('[data-transfer-actions]');
    let activeIndex = -1;

    function stepElement(index) { return sheet.querySelector(`[data-transfer-step="${index}"]`); }
    function setStep(index, state, detail) {
      const item = stepElement(index);
      if (!item) return;
      item.className = `cloudTransferStep ${state || ''}`.trim();
      const dot = item.querySelector('.cloudTransferStepDot');
      const stateText = item.querySelector('.cloudTransferStepState');
      if (detail) item.querySelector('.cloudTransferStepText span').textContent = detail;
      if (state === 'active') { dot.innerHTML = '<span class="cloudTransferSpinner"></span>'; stateText.textContent = 'Läuft'; }
      else if (state === 'done') { dot.innerHTML = icon('check'); stateText.textContent = 'Erledigt'; }
      else if (state === 'failed') { dot.innerHTML = icon('close'); stateText.textContent = 'Fehler'; }
      else { dot.innerHTML = `<span>${index + 1}</span>`; stateText.textContent = 'Wartet'; }
    }
    function setProgress(percent, label, liveMessage) {
      const value = Math.max(0, Math.min(100, Math.round(Number(percent) || 0)));
      bar.style.width = `${value}%`;
      progressPercent.textContent = `${value} %`;
      if (label) progressLabel.textContent = label;
      if (liveMessage) { live.className = 'cloudTransferLive'; liveText.textContent = liveMessage; }
    }
    function start(index, options = {}) {
      if (activeIndex >= 0 && activeIndex !== index) setStep(activeIndex, 'done');
      activeIndex = index;
      setStep(index, 'active', options.detail);
      setProgress(options.percent ?? 0, options.label || steps[index]?.title || 'In Arbeit', options.live || options.detail || steps[index]?.detail);
    }
    function update(options = {}) {
      if (activeIndex >= 0 && options.detail) setStep(activeIndex, 'active', options.detail);
      const current = Number(progressPercent.textContent.replace(/\D/g, '')) || 0;
      setProgress(options.percent ?? current, options.label, options.live || options.detail);
    }
    function done(index = activeIndex, detail) {
      if (index >= 0) setStep(index, 'done', detail);
      if (index === activeIndex) activeIndex = -1;
    }
    function finish(options = {}) {
      if (activeIndex >= 0) done(activeIndex);
      steps.forEach((_, index) => {
        const item = stepElement(index);
        if (item && !item.classList.contains('done')) setStep(index, 'done');
      });
      setProgress(100, 'Abgeschlossen');
      live.className = 'cloudTransferLive done';
      liveText.textContent = options.live || 'Alle Schritte wurden erfolgreich abgeschlossen.';
      result.className = 'cloudTransferResult show';
      resultIcon.innerHTML = icon('check');
      resultTitle.textContent = options.title || 'Fertig';
      resultText.textContent = options.text || '';
      if (options.meta) { resultMeta.hidden = false; resultMeta.innerHTML = options.meta; }
      if (options.code) {
        codeBox.hidden = false;
        codeBox.querySelector('code').textContent = options.code;
        codeBox.querySelector('button').onclick = options.onCopy || null;
      }
      actions.hidden = false;
      const closeButton = actions.querySelector('[data-transfer-close]');
      closeButton.textContent = options.buttonText || 'Fertig';
      closeButton.onclick = options.onClose || null;
      result.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    }
    function fail(message, onClose) {
      if (activeIndex >= 0) setStep(activeIndex, 'failed');
      live.className = 'cloudTransferLive failed';
      liveText.textContent = message || 'Der Vorgang konnte nicht abgeschlossen werden.';
      result.className = 'cloudTransferResult show failed';
      resultIcon.innerHTML = icon('close');
      resultTitle.textContent = 'Vorgang fehlgeschlagen';
      resultText.textContent = message || 'Unbekannter Fehler';
      actions.hidden = false;
      const closeButton = actions.querySelector('[data-transfer-close]');
      closeButton.className = 'cloudTransferClose';
      closeButton.textContent = 'Schließen';
      closeButton.onclick = onClose || null;
      result.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    }
    return { start, update, done, finish, fail };
  }

  function formatRemaining(expiresAt) {
    const remaining = new Date(expiresAt).getTime() - Date.now();
    if (remaining <= 0) return 'Freigabe abgelaufen';
    const hours = Math.ceil(remaining / 3600000);
    if (hours < 24) return `Noch ${hours} Std.`;
    const days = Math.ceil(remaining / 86400000);
    return `Noch ${days} Tag${days === 1 ? '' : 'e'}`;
  }

  function isActive(note) {
    return !!(note?.cloudShare?.code && new Date(note.cloudShare.expiresAt).getTime() > Date.now());
  }

  function cardStatusHtml(note, index) {
    if (!isActive(note)) return '';
    return `<div class="tourCloudStatus" data-no-swipe data-cloud-manage="${index}">${icon('cloud')}<span>${esc(formatRemaining(note.cloudShare.expiresAt))}</span><button type="button" data-cloud-copy="${index}" aria-label="Freigabecode kopieren" title="Code kopieren">${icon('content_copy')}</button></div>`;
  }

  async function copyText(text) {
    if (navigator.clipboard?.writeText) await navigator.clipboard.writeText(text);
    else {
      const area = document.createElement('textarea');
      area.value = text;
      area.style.position = 'fixed';
      area.style.opacity = '0';
      document.body.appendChild(area);
      area.select();
      document.execCommand('copy');
      area.remove();
    }
  }

  async function copyShareCode(note) {
    if (!note?.cloudShare?.code) return;
    try { await copyText(note.cloudShare.code); toast('Freigabecode kopiert'); }
    catch (_) { toast('Code konnte nicht kopiert werden'); }
  }

  function emitChanged() {
    window.dispatchEvent(new CustomEvent('mg-cloud-share-changed'));
  }

  async function prepareEncryptedShare(note, codeInfo, onProgress) {
    const normalized = mediaNormalizeNote(JSON.parse(JSON.stringify(note)));
    const key = await keyFromSecret(codeInfo.secret);
    const files = [];
    const exportedHoles = [];
    const totalImages = normalized.holes.reduce((sum, hole) => sum + hole.images.length, 0);
    let imageCount = 0;

    for (let holeIndex = 0; holeIndex < normalized.holes.length; holeIndex += 1) {
      const hole = normalized.holes[holeIndex];
      const exportedImages = [];
      for (let imageIndex = 0; imageIndex < hole.images.length; imageIndex += 1) {
        const image = hole.images[imageIndex];
        const sourceId = image.editedId || image.originalId;
        const sourceBlob = await mediaGetBlob(sourceId);
        if (!sourceBlob) continue;
        const optimized = await toOptimizedWebp(sourceBlob);
        const fileId = `img-${String(holeIndex + 1).padStart(2, '0')}-${String(imageIndex + 1).padStart(2, '0')}.bin`;
        const encrypted = await encryptBlob(optimized.blob, key, fileId);
        files.push({ id: fileId, blob: encrypted, size: encrypted.size, sha256: await sha256Hex(encrypted) });
        exportedImages.push({
          fileId,
          mimeType: optimized.type,
          width: optimized.width,
          height: optimized.height,
          annotations: []
        });
        imageCount += 1;
        onProgress?.({ stage: 'prepare', completed: imageCount, total: totalImages, label: `Bild ${imageCount} von ${Math.max(1, totalImages)} wird optimiert und verschlüsselt …` });
      }
      exportedHoles.push({ ball: hole.ball || '', start: hole.start || '', notes: hole.notes || '', images: exportedImages });
    }

    const manifest = {
      format: 'minigolf-cloud-note',
      schemaVersion: 1,
      createdAt: new Date().toISOString(),
      sourceNoteId: String(note.id || ''),
      note: {
        date: note.date || new Date().toISOString(),
        location: note.location || '',
        system: note.system || '',
        holes: exportedHoles
      },
      imageCount,
      imageMode: 'webp-or-jpeg-optimized',
      annotationMode: 'reserved-v1'
    };
    const manifestBlob = new Blob([JSON.stringify(manifest)], { type: 'application/json' });
    const encryptedManifest = await encryptBlob(manifestBlob, key, 'manifest.bin');
    files.unshift({ id: 'manifest.bin', blob: encryptedManifest, size: encryptedManifest.size, sha256: await sha256Hex(encryptedManifest) });
    return { manifest, files, totalBytes: files.reduce((sum, file) => sum + file.size, 0) };
  }

  async function createShare(note, days, layer) {
    const sheet = layer.querySelector('.cloudShareSheet');
    layer.classList.add('cloudShareBusy');
    const expectedImages = countNoteImages(note);
    const view = createTransferChecklist(sheet, {
      mode: 'upload',
      title: 'Onlinefreigabe erstellen',
      subtitle: 'Die verschlüsselte Kopie wird Schritt für Schritt vorbereitet.',
      summaryTitle: note.location || 'Turniernotiz',
      summaryMeta: `${expectedImages} Bild${expectedImages === 1 ? '' : 'er'} · ${days} Tag${days === 1 ? '' : 'e'} verfügbar`,
      steps: [
        { title: 'Google Drive verbinden', detail: 'Speicher und Berechtigung prüfen' },
        { title: 'Notiz vorbereiten', detail: 'Bilder optimieren und verschlüsseln' },
        { title: 'Freigabe anlegen', detail: 'Dateiliste und Ablaufdatum reservieren' },
        { title: 'Dateien hochladen', detail: 'Verschlüsselte Daten nach Drive übertragen' },
        { title: 'Upload prüfen', detail: 'Dateien kontrollieren und Code aktivieren' }
      ]
    });

    let start = null;
    try {
      await nextPaint();
      view.start(0, { percent: 2, live: 'Verbindung zum privaten Google Drive wird geprüft …' });
      await requireDriveBridge().ping();
      view.done(0, 'Verbindung hergestellt');

      view.start(1, { percent: 7, live: 'Notiz und Bilder werden lokal vorbereitet …' });
      const codeInfo = createShareCode();
      const prepared = await prepareEncryptedShare(note, codeInfo, state => {
        const ratio = state.total ? state.completed / Math.max(1, state.total) : 0;
        view.update({
          percent: 7 + Math.round(ratio * 25),
          label: 'Notiz vorbereiten',
          detail: state.label,
          live: state.label
        });
      });
      view.done(1, `${prepared.manifest.imageCount} Bilder vorbereitet und verschlüsselt`);

      view.start(2, { percent: 34, live: 'Freigabeplatz und Ablaufdatum werden reserviert …' });
      start = await apiJson('/start', {
        method: 'POST',
        body: JSON.stringify({
          ownerId: getOwnerId(),
          locator: codeInfo.locator,
          expiresInDays: days,
          files: prepared.files.map(file => ({ id: file.id, size: file.size, sha256: file.sha256 }))
        })
      });
      view.done(2, `${prepared.files.length} Dateien eingeplant`);

      view.start(3, { percent: 39, live: `0 von ${prepared.files.length} Dateien hochgeladen` });
      await runPool(prepared.files, file => apiUpload(start.uploadId, file, start.uploadToken), CONCURRENCY, (done, total) => {
        view.update({
          percent: 39 + Math.round((done / Math.max(1, total)) * 51),
          label: 'Dateien hochladen',
          detail: `${done} von ${total} Dateien übertragen`,
          live: `${done} von ${total} Dateien hochgeladen · ${(prepared.totalBytes / 1048576).toFixed(1)} MB gesamt`
        });
      });
      view.done(3, `${prepared.files.length} Dateien sicher übertragen`);

      view.start(4, { percent: 93, live: 'Prüfsummen und Vollständigkeit werden kontrolliert …' });
      const finish = await apiJson('/finish', {
        method: 'POST',
        body: JSON.stringify({ uploadId: start.uploadId, uploadToken: start.uploadToken })
      });
      note.cloudShare = {
        status: 'active',
        code: codeInfo.code,
        locator: codeInfo.locator,
        expiresAt: finish.expiresAt,
        deleteToken: finish.deleteToken,
        createdAt: new Date().toISOString(),
        totalBytes: prepared.totalBytes,
        imageCount: prepared.manifest.imageCount
      };
      persistTournament();
      emitChanged();
      view.done(4, 'Freigabecode aktiviert');
      view.finish({
        title: 'Freigabe erstellt',
        text: `Die verschlüsselte Kopie ist ${days} Tag${days === 1 ? '' : 'e'} verfügbar.`,
        live: 'Alle Dateien wurden sicher übertragen und geprüft.',
        code: codeInfo.code,
        meta: `${esc(formatRemaining(finish.expiresAt))}<br>${prepared.manifest.imageCount} Bilder · ${(prepared.totalBytes / 1048576).toFixed(1)} MB verschlüsselt`,
        onCopy: () => copyShareCode(note),
        onClose: () => closeLayer(layer)
      });
    } catch (error) {
      if (start?.uploadId && start?.uploadToken) {
        apiJson('/cancel', { method: 'POST', body: JSON.stringify({ uploadId: start.uploadId, uploadToken: start.uploadToken }) }).catch(() => {});
      }
      console.error(error);
      view.fail(error.message || 'Die Notiz konnte nicht hochgeladen werden.', () => closeLayer(layer));
    }
  }

  function openShareDialog(note) {
    if (isActive(note)) { openManageDialog(note); return; }
    const layer = createSheet(`
      <h2>Temporär online teilen</h2>
      <p>Die Notiz wird nur für die gewählte Zeit verschlüsselt in Google Drive abgelegt. Maximal sind 30 Tage und zwei aktive Freigaben pro Gerät erlaubt.</p>
      <div class="cloudShareMeta"><b>${esc(note.location || 'Unbekannter Ort')}</b><br>${esc(String(note.system || '').replace(/\s+/g, ' '))}</div>
      <div class="cloudShareDurations" aria-label="Gültigkeitsdauer">
        ${[1, 3, 7, 14, 30].map(days => `<button type="button" data-days="${days}" class="${days === 7 ? 'selected' : ''}">${days} Tag${days === 1 ? '' : 'e'}</button>`).join('')}
      </div>
      <div class="cloudShareActions"><button type="button" class="cloudShareCancel" data-cancel>Abbrechen</button><button type="button" class="cloudSharePrimary" data-start>Freigabe erstellen</button></div>`);
    let selectedDays = 7;
    layer.querySelectorAll('[data-days]').forEach(button => button.onclick = () => {
      selectedDays = Number(button.dataset.days);
      layer.querySelectorAll('[data-days]').forEach(item => item.classList.toggle('selected', item === button));
    });
    layer.querySelector('[data-cancel]').onclick = () => closeLayer(layer);
    layer.querySelector('[data-start]').onclick = () => {
      createShare(note, Math.min(MAX_DAYS, selectedDays), layer);
    };
  }

  function openDeleteShareConfirm(note, manageLayer) {
    const confirmLayer = createSheet(`
      <h2>Online-Freigabe löschen?</h2>
      <p>Die lokale Notiz bleibt erhalten. Der Freigabecode funktioniert danach nicht mehr.</p>
      <div class="cloudShareActions"><button type="button" class="cloudShareCancel" data-cancel>Abbrechen</button><button type="button" class="cloudShareDanger" data-confirm>Online löschen</button></div>`);
    confirmLayer.classList.add('cloudShareConfirmLayer');
    confirmLayer.querySelector('[data-cancel]').onclick = () => closeLayer(confirmLayer);
    confirmLayer.querySelector('[data-confirm]').onclick = () => {
      closeLayer(confirmLayer);
      deleteShare(note, manageLayer);
    };
  }

  async function deleteShare(note, layer) {
    try {
      await apiJson('/delete', {
        method: 'POST',
        body: JSON.stringify({ locator: note.cloudShare.locator, deleteToken: note.cloudShare.deleteToken })
      });
      delete note.cloudShare;
      persistTournament();
      emitChanged();
      closeLayer(layer);
      toast('Online-Freigabe gelöscht');
    } catch (error) {
      console.error(error);
      toast(error.message || 'Freigabe konnte nicht gelöscht werden');
    }
  }

  function openManageDialog(note) {
    const share = note?.cloudShare;
    if (!share) return openShareDialog(note);
    const layer = createSheet(`
      <h2>Online-Freigabe verwalten</h2>
      <p>${esc(formatRemaining(share.expiresAt))}</p>
      <div class="cloudShareCode"><code>${esc(share.code)}</code><button type="button" data-copy>${icon('content_copy')}</button></div>
      <div class="cloudShareMeta">Läuft ab: ${esc(new Date(share.expiresAt).toLocaleString('de-DE'))}<br>${Number(share.imageCount || 0)} Bilder · ${((Number(share.totalBytes || 0)) / 1048576).toFixed(1)} MB</div>
      <div class="cloudShareActions"><button type="button" class="cloudShareCancel" data-close>Schließen</button><button type="button" class="cloudShareDanger" data-delete>Online löschen</button></div>`);
    layer.querySelector('[data-copy]').onclick = () => copyShareCode(note);
    layer.querySelector('[data-close]').onclick = () => closeLayer(layer);
    layer.querySelector('[data-delete]').onclick = () => openDeleteShareConfirm(note, layer);
  }

  async function decryptManifest(locator, secret) {
    const key = await keyFromSecret(secret);
    const encrypted = await apiFile(locator, 'manifest.bin');
    const plain = await decryptBlob(encrypted, key, 'manifest.bin', 'application/json');
    const manifest = JSON.parse(await plain.text());
    if (manifest?.format !== 'minigolf-cloud-note' || manifest?.schemaVersion !== 1) throw new Error('Unbekanntes Freigabeformat.');
    return { key, manifest };
  }

  async function importShare(codeInfo, info, key, manifest, layer) {
    const sheet = layer.querySelector('.cloudShareSheet');
    layer.classList.add('cloudShareBusy');
    const imageEntries = [];
    manifest.note.holes.forEach((hole, holeIndex) => hole.images.forEach((image, imageIndex) => imageEntries.push({ holeIndex, imageIndex, ...image })));
    const view = createTransferChecklist(sheet, {
      mode: 'download',
      title: 'Onlinefreigabe importieren',
      subtitle: 'Die freigegebene Notiz wird sicher geladen und lokal gespeichert.',
      summaryTitle: manifest.note.location || 'Geteilte Turniernotiz',
      summaryMeta: `${imageEntries.length} Bild${imageEntries.length === 1 ? '' : 'er'} · ${(Number(info.totalBytes || 0) / 1048576).toFixed(1)} MB · ${formatRemaining(info.expiresAt)}`,
      steps: [
        { title: 'Freigabe prüfen', detail: 'Code und Ablaufdatum kontrollieren' },
        { title: 'Dateiliste laden', detail: 'Manifest und Bildliste vorbereiten' },
        { title: 'Dateien herunterladen', detail: 'Verschlüsselte Daten aus Drive laden' },
        { title: 'Daten entschlüsseln', detail: 'Notiz und Bilder lokal öffnen' },
        { title: 'Lokal speichern', detail: 'Unabhängige Kopie auf diesem Gerät anlegen' }
      ]
    });
    const storedIds = [];
    try {
      await nextPaint();
      view.start(0, { percent: 3, live: 'Freigabecode und Ablaufdatum werden geprüft …' });
      const currentInfo = await apiJson(`/info/${encodeURIComponent(codeInfo.locator)}`);
      view.done(0, formatRemaining(currentInfo.expiresAt));

      view.start(1, { percent: 10, live: 'Manifest und Bildliste werden ausgewertet …' });
      if (manifest?.format !== 'minigolf-cloud-note' || manifest?.schemaVersion !== 1) throw new Error('Unbekanntes Freigabeformat.');
      await nextPaint();
      view.done(1, `${imageEntries.length + 1} Dateien gefunden`);

      view.start(2, { percent: 16, live: `0 von ${imageEntries.length} Bildern geladen` });
      const downloaded = await runPool(imageEntries, async entry => ({
        entry,
        encrypted: await apiFile(codeInfo.locator, entry.fileId)
      }), CONCURRENCY, (done, total) => {
        view.update({
          percent: 16 + Math.round((done / Math.max(1, total)) * 37),
          label: 'Dateien herunterladen',
          detail: `${done} von ${total} Dateien geladen`,
          live: `${done} von ${total} verschlüsselten Bildern heruntergeladen`
        });
      });
      view.done(2, `${downloaded.length} Bilder heruntergeladen`);

      view.start(3, { percent: 57, live: 'Heruntergeladene Bilder werden entschlüsselt …' });
      const decrypted = await runPool(downloaded, async item => ({
        entry: item.entry,
        plain: await decryptBlob(item.encrypted, key, item.entry.fileId, item.entry.mimeType || 'image/webp')
      }), CONCURRENCY, (done, total) => {
        view.update({
          percent: 57 + Math.round((done / Math.max(1, total)) * 22),
          label: 'Daten entschlüsseln',
          detail: `${done} von ${total} Bildern entschlüsselt`,
          live: `${done} von ${total} Bildern lokal geöffnet`
        });
      });
      view.done(3, 'Alle Daten erfolgreich entschlüsselt');

      view.start(4, { percent: 82, live: 'Unabhängige lokale Kopie wird gespeichert …' });
      const stored = await runPool(decrypted, async item => {
        const id = await mediaPutBlob(item.plain);
        storedIds.push(id);
        return { entry: item.entry, id };
      }, CONCURRENCY, (done, total) => {
        view.update({
          percent: 82 + Math.round((done / Math.max(1, total)) * 15),
          label: 'Lokal speichern',
          detail: `${done} von ${total} Bildern gespeichert`,
          live: `${done} von ${total} Bildern in der lokalen Datenbank gespeichert`
        });
      });

      const holes = manifest.note.holes.map(hole => ({ ball: hole.ball || '', start: hole.start || '', notes: hole.notes || '', images: [] }));
      for (const item of stored) {
        holes[item.entry.holeIndex].images[item.entry.imageIndex] = { id: uid(), originalId: item.id, editedId: item.id, createdAt: Date.now() };
      }
      holes.forEach(hole => { hole.images = hole.images.filter(Boolean); });
      const imported = mediaNormalizeNote({
        id: uid(),
        date: manifest.note.date || new Date().toISOString(),
        location: manifest.note.location || '',
        system: manifest.note.system || '',
        holes,
        cloudSource: { locator: codeInfo.locator, importedAt: new Date().toISOString() }
      });
      tournamentNotes.unshift(imported);
      persistTournament();
      emitChanged();
      view.done(4, 'Notiz und Bilder lokal gespeichert');
      view.finish({
        title: 'Import abgeschlossen',
        text: 'Die Notiz wurde als unabhängige lokale Kopie gespeichert und funktioniert nun offline.',
        live: 'Alle Daten wurden geladen, entschlüsselt und lokal gespeichert.',
        meta: `<b>${esc(imported.location || 'Unbekannter Ort')}</b><br>${manifest.imageCount} Bilder importiert`,
        onClose: () => closeLayer(layer)
      });
    } catch (error) {
      for (const id of storedIds) await mediaDelete(id).catch(() => {});
      console.error(error);
      view.fail(error.message || 'Die Freigabe konnte nicht importiert werden.', () => closeLayer(layer));
    }
  }

  function openImportDialog() {
    const layer = createSheet(`
      <h2>Geteilte Notiz laden</h2>
      <p>Gib den vollständigen Freigabecode ein. Der geheime Teil des Codes bleibt im Browser und wird nicht an den Speicher-Server gesendet.</p>
      <input class="cloudShareInput" data-code autocomplete="off" autocapitalize="characters" spellcheck="false" placeholder="XXXX-XXXX-…">
      <div class="cloudShareActions"><button type="button" class="cloudShareCancel" data-cancel>Abbrechen</button><button type="button" class="cloudSharePrimary" data-search>Laden</button></div>`);
    const input = layer.querySelector('[data-code]');
    input.addEventListener('input', () => { input.value = formatCode(input.value); });
    layer.querySelector('[data-cancel]').onclick = () => closeLayer(layer);
    layer.querySelector('[data-search]').onclick = async () => {
      try {
        const codeInfo = parseShareCode(input.value);
        const sheet = layer.querySelector('.cloudShareSheet');
        sheet.innerHTML = '<h2>Freigabe wird geprüft</h2><p>Metadaten und Vorschau werden geladen …</p><div class="cloudShareProgress"><i style="width:45%"></i></div>';
        const info = await apiJson(`/info/${encodeURIComponent(codeInfo.locator)}`);
        const { key, manifest } = await decryptManifest(codeInfo.locator, codeInfo.secret);
        sheet.innerHTML = `
          <h2>Geteilte Turniernotiz</h2>
          <div class="cloudShareMeta"><b>${esc(manifest.note.location || 'Unbekannter Ort')}</b><br>${esc(String(manifest.note.system || '').replace(/\s+/g, ' '))}<br>${esc(formatGameDate(manifest.note.date))}</div>
          <ul class="cloudSharePreviewList"><li>${manifest.imageCount} Bilder</li><li>${(Number(info.totalBytes || 0) / 1048576).toFixed(1)} MB Download</li><li>${esc(formatRemaining(info.expiresAt))}</li></ul>
          <div class="cloudShareActions"><button type="button" class="cloudShareCancel" data-close>Abbrechen</button><button type="button" class="cloudSharePrimary" data-import>Lokal speichern</button></div>`;
        sheet.querySelector('[data-close]').onclick = () => closeLayer(layer);
        sheet.querySelector('[data-import]').onclick = () => importShare(codeInfo, info, key, manifest, layer);
      } catch (error) {
        console.error(error);
        const sheet = layer.querySelector('.cloudShareSheet');
        sheet.innerHTML = `<h2>Freigabe nicht verfügbar</h2><p>${esc(error.message || 'Code falsch, abgelaufen oder gelöscht.')}</p><div class="cloudShareActions"><button type="button" class="cloudShareCancel" data-close>Schließen</button></div>`;
        sheet.querySelector('[data-close]').onclick = () => closeLayer(layer);
      }
    };
    requestAnimationFrame(() => input.focus());
  }

  addStyles();
  window.CloudShare = {
    isActive,
    cardStatusHtml,
    copyShareCode,
    openShareDialog,
    openManageDialog,
    openImportDialog,
    formatRemaining
  };
})();
