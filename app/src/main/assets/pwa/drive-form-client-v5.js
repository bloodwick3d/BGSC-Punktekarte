(() => {
  'use strict';

  const config = window.MINIGOLF_DRIVE_CONFIG || {};
  const RESPONSE_SOURCE = 'minigolf-drive-form-response-v1';
  const CLIENT_BUILD = '5.0.0';
  const sessionNonce = crypto.randomUUID ? crypto.randomUUID() : `${Date.now()}-${Math.random()}`;
  const pending = new Map();
  let requestCounter = 0;

  const diagnostics = {
    startedAt: new Date().toISOString(),
    configuredUrl: String(config.bridgeUrl || ''),
    transport: 'form-post',
    clientBuild: CLIENT_BUILD,
    submitted: 0,
    responses: 0,
    messages: []
  };

  function configured() {
    return config.mode === 'apps-script' && /^https:\/\/script\.google\.com\/macros\/s\/[A-Za-z0-9_-]+\/exec(?:[?#].*)?$/.test(String(config.bridgeUrl || ''));
  }

  function isPossibleGoogleOrigin(origin) {
    if (origin === 'null') return true;
    try {
      const url = new URL(String(origin || ''));
      return url.protocol === 'https:' && (
        url.hostname === 'script.google.com' ||
        url.hostname === 'script.googleusercontent.com' ||
        url.hostname.endsWith('.googleusercontent.com')
      );
    } catch (_) {
      return false;
    }
  }

  function makeError(message, code) {
    const error = new Error(message || 'Google-Drive-Anfrage fehlgeschlagen.');
    if (code) error.code = code;
    error.diagnostics = JSON.parse(JSON.stringify(diagnostics));
    return error;
  }

  function cleanupJob(job) {
    window.clearTimeout(job.timeout);
    job.form?.remove();
    job.iframe?.remove();
  }

  function rememberMessage(event, data) {
    diagnostics.messages.push({
      at: new Date().toISOString(),
      origin: event.origin,
      type: String(data?.type || ''),
      source: String(data?.source || ''),
      requestId: String(data?.requestId || '')
    });
    if (diagnostics.messages.length > 30) diagnostics.messages.shift();
  }

  window.addEventListener('message', event => {
    const data = event.data || {};
    rememberMessage(event, data);
    if (data.source !== RESPONSE_SOURCE || !isPossibleGoogleOrigin(event.origin)) return;
    if (data.sessionNonce !== sessionNonce || data.type !== 'response' || !data.requestId) return;

    const job = pending.get(data.requestId);
    if (!job) return;
    pending.delete(data.requestId);
    diagnostics.responses += 1;
    cleanupJob(job);

    if (data.ok) job.resolve(data.result);
    else job.reject(makeError(data.error?.message || 'Google-Drive-Anfrage fehlgeschlagen.', data.error?.code));
  });

  function request(action, payload = {}) {
    if (!configured()) {
      return Promise.reject(makeError('Google Drive ist noch nicht eingerichtet. Prüfe drive-config.js.', 'DRIVE_NOT_CONFIGURED'));
    }

    const requestId = `${Date.now().toString(36)}-${(++requestCounter).toString(36)}-${Math.random().toString(36).slice(2, 9)}`;
    const frameName = `mg-drive-response-${requestId.replace(/[^A-Za-z0-9_-]/g, '')}`;
    const envelope = {
      protocol: 1,
      requestId,
      sessionNonce,
      replyOrigin: window.location.origin,
      action: String(action || ''),
      payload: payload || {}
    };

    return new Promise((resolve, reject) => {
      const iframe = document.createElement('iframe');
      iframe.name = frameName;
      iframe.title = 'Google Drive Antwort';
      iframe.tabIndex = -1;
      iframe.setAttribute('aria-hidden', 'true');
      iframe.style.cssText = 'position:fixed;width:1px;height:1px;left:-10000px;top:-10000px;border:0;opacity:.01;pointer-events:none';

      const form = document.createElement('form');
      form.method = 'POST';
      form.action = String(config.bridgeUrl);
      form.target = frameName;
      form.acceptCharset = 'UTF-8';
      form.enctype = 'application/x-www-form-urlencoded';
      form.style.display = 'none';

      const field = document.createElement('textarea');
      field.name = 'mg_request';
      field.value = JSON.stringify(envelope);
      form.appendChild(field);

      const timeout = window.setTimeout(() => {
        const job = pending.get(requestId);
        if (!job) return;
        pending.delete(requestId);
        cleanupJob(job);
        reject(makeError(`Zeitüberschreitung bei Drive-Aktion „${action}“. Prüfe, ob Code.gs mit doPost() als neue Web-App-Version bereitgestellt wurde.`, 'REQUEST_TIMEOUT'));
      }, Number(config.requestTimeoutMs || 120000));

      pending.set(requestId, { resolve, reject, timeout, form, iframe });
      document.body.appendChild(iframe);
      document.body.appendChild(form);
      diagnostics.submitted += 1;
      form.submit();
    });
  }

  function bytesToBase64(bytes) {
    const chunkSize = 0x8000;
    let binary = '';
    for (let offset = 0; offset < bytes.length; offset += chunkSize) {
      binary += String.fromCharCode(...bytes.subarray(offset, Math.min(bytes.length, offset + chunkSize)));
    }
    return btoa(binary);
  }

  function base64ToBytes(value) {
    const binary = atob(String(value || ''));
    const bytes = new Uint8Array(binary.length);
    for (let index = 0; index < binary.length; index += 1) bytes[index] = binary.charCodeAt(index);
    return bytes;
  }

  async function upload(uploadId, file, uploadToken) {
    const bytes = new Uint8Array(await file.blob.arrayBuffer());
    return request('upload', {
      uploadId,
      uploadToken,
      fileId: file.id,
      sha256: file.sha256,
      dataBase64: bytesToBase64(bytes)
    });
  }

  async function download(locator, fileId) {
    const result = await request('file', { locator, fileId });
    if (!result?.dataBase64) throw makeError('Drive-Datei enthält keine Daten.', 'EMPTY_FILE');
    return new Blob([base64ToBytes(result.dataBase64)], { type: 'application/octet-stream' });
  }

  window.MiniGolfDriveBridge = Object.freeze({
    configured,
    request,
    upload,
    download,
    ping: () => request('ping', {}),
    getDiagnostics: () => JSON.parse(JSON.stringify(diagnostics))
  });
})();
