/**
 * MiniGolf Temporary Share – Google Apps Script / Google Drive backend.
 *
 * Die PWA verschlüsselt alle Inhalte vor dem Upload. Dieses Script speichert
 * ausschließlich verschlüsselte Binärdateien und kleine technische Metadaten.
 */

const MG_CONFIG = Object.freeze({
  ROOT_FOLDER_NAME: 'MiniGolf Temporary Shares',
  META_FILE_NAME: 'meta.json',
  SHARE_FOLDER_PREFIX: 'share_',
  MAX_DAYS: 30,
  MAX_ACTIVE_PER_OWNER: 2,
  MAX_ACTIVE_GLOBAL: 50,
  MAX_FILES_PER_SHARE: 80,
  MAX_FILE_BYTES: 3 * 1024 * 1024,
  MAX_TOTAL_BYTES: 30 * 1024 * 1024,
  ABANDONED_UPLOAD_MS: 60 * 60 * 1000,
  CLEANUP_FUNCTION: 'cleanupExpiredShares',
  API_VERSION: 1
});

function doGet() {
  const template = HtmlService.createTemplateFromFile('Bridge');
  template.allowedOriginsJson = JSON.stringify(getAllowedOrigins_());
  template.apiVersion = MG_CONFIG.API_VERSION;
  return template.evaluate()
    .setTitle('MiniGolf Drive API')
    .setXFrameOptionsMode(HtmlService.XFrameOptionsMode.ALLOWALL);
}

/**
 * Formularbasierter API-Endpunkt für lokale PWA-Tests und die spätere PWA.
 * Ein klassisches HTML-Formular benötigt keine CORS-Freigabe. Die Antwortseite
 * sendet das Ergebnis per postMessage an die aufrufende PWA zurück.
 */
function doPost(e) {
  let envelope = {};
  let replyOrigin = '*';
  try {
    const raw = e && e.parameter ? String(e.parameter.mg_request || '') : '';
    if (!raw) throw apiError_('EMPTY_REQUEST', 'Die Drive-Anfrage ist leer.');
    envelope = JSON.parse(raw);
    if (Number(envelope.protocol) !== 1) throw apiError_('INVALID_PROTOCOL', 'Nicht unterstütztes Anfrageprotokoll.');

    const requestId = requireSafeText_(envelope.requestId, 160, 'requestId');
    const sessionNonce = requireSafeText_(envelope.sessionNonce, 220, 'sessionNonce');
    const action = requireSafeText_(envelope.action, 40, 'action');
    replyOrigin = String(envelope.replyOrigin || '');
    if (!isAllowedReplyOrigin_(replyOrigin)) throw apiError_('ORIGIN_FORBIDDEN', 'Diese Herkunft ist für den Drive-Test nicht freigegeben.');

    const result = handleBridgeRequest({ action: action, payload: envelope.payload || {} });
    return createFormResponse_({
      source: 'minigolf-drive-form-response-v1',
      type: 'response',
      requestId: requestId,
      sessionNonce: sessionNonce,
      apiVersion: MG_CONFIG.API_VERSION,
      ok: true,
      result: result
    }, replyOrigin);
  } catch (error) {
    return createFormResponse_({
      source: 'minigolf-drive-form-response-v1',
      type: 'response',
      requestId: String(envelope.requestId || ''),
      sessionNonce: String(envelope.sessionNonce || ''),
      apiVersion: MG_CONFIG.API_VERSION,
      ok: false,
      error: {
        code: String(error && error.name || 'APPS_SCRIPT_ERROR'),
        message: String(error && error.message || 'Google Apps Script konnte die Anfrage nicht verarbeiten.')
      }
    }, isAllowedReplyOrigin_(replyOrigin) ? replyOrigin : '*');
  }
}

function isAllowedReplyOrigin_(origin) {
  origin = String(origin || '');
  if (/^http:\/\/(localhost|127\.0\.0\.1)(:\d+)?$/.test(origin)) return true;
  return getAllowedOrigins_().indexOf(origin) !== -1;
}

function createFormResponse_(message, targetOrigin) {
  const json = JSON.stringify(message)
    .replace(/</g, '\\u003c')
    .replace(/\u2028/g, '\\u2028')
    .replace(/\u2029/g, '\\u2029');
  const originJson = JSON.stringify(String(targetOrigin || '*'));
  const html = '<!doctype html><html><head><meta charset="utf-8"><title>MiniGolf Drive Antwort</title></head><body>' +
    '<script>(function(){"use strict";const message=' + json + ';const targetOrigin=' + originJson + ';let sent=0;' +
    'function deliver(){sent++;try{if(window.parent&&window.parent!==window)window.parent.postMessage(message,targetOrigin);}catch(e){}' +
    'try{if(window.top&&window.top!==window)window.top.postMessage(message,targetOrigin);}catch(e){}' +
    'if(sent<20)setTimeout(deliver,150);}deliver();})();<\/script></body></html>';
  return HtmlService.createHtmlOutput(html)
    .setTitle('MiniGolf Drive Antwort')
    .setXFrameOptionsMode(HtmlService.XFrameOptionsMode.ALLOWALL);
}

/** Einmal manuell im Apps-Script-Editor ausführen. */
function setupMiniGolfShare() {
  const properties = PropertiesService.getScriptProperties();
  let folderId = properties.getProperty('ROOT_FOLDER_ID');
  let root = null;
  if (folderId) {
    try { root = DriveApp.getFolderById(folderId); } catch (error) { root = null; }
  }
  if (!root) {
    root = DriveApp.createFolder(MG_CONFIG.ROOT_FOLDER_NAME);
    folderId = root.getId();
    properties.setProperty('ROOT_FOLDER_ID', folderId);
  }

  if (!properties.getProperty('ALLOWED_ORIGINS')) {
    properties.setProperty('ALLOWED_ORIGINS', [
      'http://localhost:8080',
      'http://localhost:5500',
      'http://127.0.0.1:8080',
      'http://127.0.0.1:5500'
    ].join(','));
  }

  const existing = ScriptApp.getProjectTriggers().some(trigger =>
    trigger.getHandlerFunction() === MG_CONFIG.CLEANUP_FUNCTION
  );
  if (!existing) {
    ScriptApp.newTrigger(MG_CONFIG.CLEANUP_FUNCTION).timeBased().everyHours(1).create();
  }

  return {
    ok: true,
    rootFolderId: folderId,
    rootFolderName: root.getName(),
    allowedOrigins: getAllowedOrigins_(),
    cleanupTriggerInstalled: true
  };
}

/** Optional: erlaubte spätere Produktions-Origin setzen. */
function setAllowedOrigins(origins) {
  if (!Array.isArray(origins) || !origins.length) throw new Error('Mindestens eine Origin ist erforderlich.');
  const normalized = origins.map(value => String(value || '').trim()).filter(Boolean);
  PropertiesService.getScriptProperties().setProperty('ALLOWED_ORIGINS', normalized.join(','));
  return normalized;
}

function getSystemStatus() {
  cleanupExpiredShares();
  const root = getRootFolder_();
  const metas = listMetadata_(root);
  const now = Date.now();
  const active = metas.filter(meta => meta.status === 'active' && Number(meta.expiresAt) > now);
  const uploading = metas.filter(meta => meta.status === 'uploading' && Number(meta.createdAt) + MG_CONFIG.ABANDONED_UPLOAD_MS > now);
  return {
    ok: true,
    rootFolderId: root.getId(),
    activeShares: active.length,
    uploadingShares: uploading.length,
    totalBytes: active.reduce((sum, meta) => sum + Number(meta.totalBytes || 0), 0),
    allowedOrigins: getAllowedOrigins_()
  };
}

/** Einziger vom Bridge-Client aufgerufener Server-Endpunkt. */
function handleBridgeRequest(request) {
  request = request || {};
  const action = String(request.action || '');
  const payload = request.payload || {};

  switch (action) {
    case 'ping': return apiPing_();
    case 'start': return apiStart_(payload);
    case 'upload': return apiUpload_(payload);
    case 'cancel': return apiCancel_(payload);
    case 'finish': return apiFinish_(payload);
    case 'info': return apiInfo_(payload);
    case 'file': return apiFile_(payload);
    case 'delete': return apiDelete_(payload);
    default: throw apiError_('UNKNOWN_ACTION', 'Unbekannte Drive-Aktion.');
  }
}

function apiPing_() {
  const root = getRootFolder_();
  return { ok: true, apiVersion: MG_CONFIG.API_VERSION, storage: 'google-drive', rootFolderId: root.getId() };
}

function apiStart_(payload) {
  const ownerId = requireSafeText_(payload.ownerId, 120, 'ownerId');
  const locator = requireLocator_(payload.locator);
  const days = Number(payload.expiresInDays);
  const files = normalizeFileList_(payload.files);
  if (!Number.isInteger(days) || days < 1 || days > MG_CONFIG.MAX_DAYS) {
    throw apiError_('INVALID_DURATION', 'Die Laufzeit muss zwischen 1 und 30 Tagen liegen.');
  }

  const lock = LockService.getScriptLock();
  lock.waitLock(20000);
  try {
    cleanupExpiredShares_();
    const root = getRootFolder_();
    if (findShareFolder_(root, locator)) throw apiError_('CODE_COLLISION', 'Code-Kollision – bitte erneut versuchen.');

    const now = Date.now();
    const ownerHash = sha256Text_(ownerId);
    const active = listMetadata_(root).filter(meta =>
      Number(meta.expiresAt || 0) > now &&
      (meta.status === 'active' || (meta.status === 'uploading' && Number(meta.createdAt || 0) + MG_CONFIG.ABANDONED_UPLOAD_MS > now))
    );
    if (active.filter(meta => meta.ownerHash === ownerHash).length >= MG_CONFIG.MAX_ACTIVE_PER_OWNER) {
      throw apiError_('OWNER_LIMIT', 'Du hast bereits zwei aktive Freigaben.');
    }
    if (active.length >= MG_CONFIG.MAX_ACTIVE_GLOBAL) {
      throw apiError_('GLOBAL_LIMIT', 'Der Freigabespeicher hat das globale Limit erreicht.');
    }

    const uploadToken = randomToken_(32);
    const deleteToken = randomToken_(32);
    const uploadId = `${locator}.${randomToken_(18)}`;
    const folder = root.createFolder(MG_CONFIG.SHARE_FOLDER_PREFIX + locator);
    const metadata = {
      schemaVersion: 1,
      locator,
      ownerHash,
      uploadId,
      uploadTokenHash: sha256Text_(uploadToken),
      deleteTokenHash: sha256Text_(deleteToken),
      createdAt: now,
      expiresAt: now + days * 86400000,
      status: 'uploading',
      files,
      totalBytes: files.reduce((sum, file) => sum + file.size, 0)
    };
    writeMetadata_(folder, metadata);
    return {
      ok: true,
      uploadId,
      uploadToken,
      expiresAt: new Date(metadata.expiresAt).toISOString()
    };
  } finally {
    lock.releaseLock();
  }
}

function apiUpload_(payload) {
  const uploadId = requireSafeText_(payload.uploadId, 100, 'uploadId');
  const uploadToken = requireSafeText_(payload.uploadToken, 200, 'uploadToken');
  const fileId = requireFileId_(payload.fileId);
  const locator = requireLocator_(uploadId.split('.')[0]);
  const root = getRootFolder_();
  const folder = requireShareFolder_(root, locator);
  const meta = readMetadata_(folder);

  if (meta.status !== 'uploading' || meta.uploadId !== uploadId || Number(meta.expiresAt) <= Date.now()) {
    throw apiError_('UPLOAD_EXPIRED', 'Der Upload ist nicht mehr gültig.');
  }
  if (!constantTimeEqual_(sha256Text_(uploadToken), meta.uploadTokenHash)) {
    throw apiError_('UPLOAD_FORBIDDEN', 'Upload nicht erlaubt.');
  }

  const expected = (meta.files || []).find(file => file.id === fileId);
  if (!expected) throw apiError_('UNKNOWN_FILE', 'Die Datei gehört nicht zu diesem Upload.');
  const requestedHash = String(payload.sha256 || '').toLowerCase();
  if (requestedHash !== expected.sha256) throw apiError_('HASH_MISMATCH', 'Dateiprüfsumme stimmt nicht.');

  const dataBase64 = String(payload.dataBase64 || '');
  if (!dataBase64) throw apiError_('EMPTY_FILE', 'Die Datei ist leer.');
  let bytes;
  try { bytes = Utilities.base64Decode(dataBase64); }
  catch (error) { throw apiError_('INVALID_BASE64', 'Die Datei konnte nicht dekodiert werden.'); }
  if (bytes.length !== expected.size) throw apiError_('SIZE_MISMATCH', 'Dateigröße stimmt nicht.');
  const hash = sha256Bytes_(bytes);
  if (hash !== expected.sha256) throw apiError_('HASH_MISMATCH', 'Dateiprüfsumme stimmt nicht.');

  deleteFilesByName_(folder, fileId);
  folder.createFile(Utilities.newBlob(bytes, 'application/octet-stream', fileId));
  return { ok: true, fileId, size: bytes.length };
}

function apiCancel_(payload) {
  const uploadId = requireSafeText_(payload.uploadId, 100, 'uploadId');
  const uploadToken = requireSafeText_(payload.uploadToken, 200, 'uploadToken');
  const locator = requireLocator_(uploadId.split('.')[0]);
  const root = getRootFolder_();
  const folder = findShareFolder_(root, locator);
  if (!folder) return { ok: true, alreadyDeleted: true };
  const meta = readMetadata_(folder);
  if (meta.status !== 'uploading' || meta.uploadId !== uploadId || !constantTimeEqual_(sha256Text_(uploadToken), meta.uploadTokenHash)) {
    throw apiError_('CANCEL_FORBIDDEN', 'Stornierung nicht erlaubt.');
  }
  deleteFolderPermanently_(folder);
  return { ok: true };
}

function apiFinish_(payload) {
  const uploadId = requireSafeText_(payload.uploadId, 100, 'uploadId');
  const uploadToken = requireSafeText_(payload.uploadToken, 200, 'uploadToken');
  const locator = requireLocator_(uploadId.split('.')[0]);
  const root = getRootFolder_();
  const folder = requireShareFolder_(root, locator);
  const meta = readMetadata_(folder);
  if (meta.status !== 'uploading' || meta.uploadId !== uploadId || !constantTimeEqual_(sha256Text_(uploadToken), meta.uploadTokenHash)) {
    throw apiError_('FINISH_FORBIDDEN', 'Upload nicht erlaubt.');
  }

  (meta.files || []).forEach(expected => {
    const file = findSingleFile_(folder, expected.id);
    if (!file || file.getSize() !== expected.size) throw apiError_('FILE_MISSING', `Datei fehlt: ${expected.id}`);
  });

  const deleteToken = randomToken_(32);
  meta.status = 'active';
  meta.activatedAt = Date.now();
  meta.deleteTokenHash = sha256Text_(deleteToken);
  delete meta.uploadTokenHash;
  writeMetadata_(folder, meta);
  return {
    ok: true,
    locator,
    expiresAt: new Date(meta.expiresAt).toISOString(),
    deleteToken
  };
}

function apiInfo_(payload) {
  const locator = requireLocator_(payload.locator);
  const data = requireActiveShare_(locator);
  return {
    ok: true,
    locator,
    expiresAt: new Date(data.meta.expiresAt).toISOString(),
    totalBytes: Number(data.meta.totalBytes || 0),
    fileCount: (data.meta.files || []).length
  };
}

function apiFile_(payload) {
  const locator = requireLocator_(payload.locator);
  const fileId = requireFileId_(payload.fileId);
  const data = requireActiveShare_(locator);
  const expected = (data.meta.files || []).find(file => file.id === fileId);
  if (!expected) throw apiError_('FILE_NOT_FOUND', 'Datei nicht gefunden.');
  const file = findSingleFile_(data.folder, fileId);
  if (!file) throw apiError_('FILE_NOT_FOUND', 'Datei nicht gefunden.');
  const bytes = file.getBlob().getBytes();
  if (bytes.length !== expected.size || sha256Bytes_(bytes) !== expected.sha256) {
    throw apiError_('CORRUPT_FILE', 'Die gespeicherte Datei ist beschädigt.');
  }
  return {
    ok: true,
    fileId,
    size: bytes.length,
    sha256: expected.sha256,
    dataBase64: Utilities.base64Encode(bytes)
  };
}

function apiDelete_(payload) {
  const locator = requireLocator_(payload.locator);
  const deleteToken = requireSafeText_(payload.deleteToken, 200, 'deleteToken');
  const root = getRootFolder_();
  const folder = findShareFolder_(root, locator);
  if (!folder) return { ok: true, alreadyDeleted: true };
  const meta = readMetadata_(folder);
  if (!constantTimeEqual_(sha256Text_(deleteToken), meta.deleteTokenHash)) {
    throw apiError_('DELETE_FORBIDDEN', 'Löschen nicht erlaubt.');
  }
  deleteFolderPermanently_(folder);
  return { ok: true };
}

/** Wird stündlich durch den installierten Trigger ausgeführt. */
function cleanupExpiredShares() {
  return cleanupExpiredShares_();
}

function cleanupExpiredShares_() {
  const root = getRootFolder_();
  const now = Date.now();
  let deleted = 0;
  const folders = root.getFolders();
  while (folders.hasNext()) {
    const folder = folders.next();
    if (!folder.getName().startsWith(MG_CONFIG.SHARE_FOLDER_PREFIX)) continue;
    try {
      const meta = readMetadata_(folder);
      const expired = Number(meta.expiresAt || 0) <= now;
      const abandoned = meta.status === 'uploading' && Number(meta.createdAt || 0) + MG_CONFIG.ABANDONED_UPLOAD_MS <= now;
      if (expired || abandoned) {
        deleteFolderPermanently_(folder);
        deleted += 1;
      }
    } catch (error) {
      // Beschädigte Freigabeordner werden ebenfalls entfernt, damit kein Speicher hängen bleibt.
      deleteFolderPermanently_(folder);
      deleted += 1;
    }
  }
  return { ok: true, deleted };
}

function getRootFolder_() {
  const properties = PropertiesService.getScriptProperties();
  const id = properties.getProperty('ROOT_FOLDER_ID');
  if (!id) throw apiError_('NOT_SETUP', 'Das Drive-Backend wurde noch nicht eingerichtet. Führe setupMiniGolfShare() aus.');
  try { return DriveApp.getFolderById(id); }
  catch (error) { throw apiError_('ROOT_MISSING', 'Der MiniGolf-Drive-Ordner wurde nicht gefunden.'); }
}

function getAllowedOrigins_() {
  const raw = PropertiesService.getScriptProperties().getProperty('ALLOWED_ORIGINS') || '';
  return raw.split(',').map(value => value.trim()).filter(Boolean);
}

function normalizeFileList_(files) {
  if (!Array.isArray(files) || !files.length || files.length > MG_CONFIG.MAX_FILES_PER_SHARE) {
    throw apiError_('INVALID_FILE_LIST', 'Ungültige Dateiliste.');
  }
  const seen = {};
  const normalized = files.map(item => {
    const id = requireFileId_(item.id);
    if (seen[id]) throw apiError_('DUPLICATE_FILE', 'Doppelte Datei-ID.');
    seen[id] = true;
    const size = Number(item.size);
    const sha256 = String(item.sha256 || '').toLowerCase();
    if (!Number.isInteger(size) || size < 1 || size > MG_CONFIG.MAX_FILE_BYTES) {
      throw apiError_('FILE_TOO_LARGE', `Datei ist zu groß: ${id}`);
    }
    if (!/^[a-f0-9]{64}$/.test(sha256)) throw apiError_('INVALID_HASH', 'Ungültige Dateiprüfsumme.');
    return { id, size, sha256 };
  });
  const total = normalized.reduce((sum, file) => sum + file.size, 0);
  if (total > MG_CONFIG.MAX_TOTAL_BYTES) throw apiError_('SHARE_TOO_LARGE', 'Die Freigabe ist zu groß.');
  return normalized;
}

function listMetadata_(root) {
  const result = [];
  const folders = root.getFolders();
  while (folders.hasNext()) {
    const folder = folders.next();
    if (!folder.getName().startsWith(MG_CONFIG.SHARE_FOLDER_PREFIX)) continue;
    try { result.push(readMetadata_(folder)); } catch (error) { /* wird bei Cleanup behandelt */ }
  }
  return result;
}

function shareFolderName_(locator) {
  return MG_CONFIG.SHARE_FOLDER_PREFIX + locator;
}

function findShareFolder_(root, locator) {
  const folders = root.getFoldersByName(shareFolderName_(locator));
  return folders.hasNext() ? folders.next() : null;
}

function requireShareFolder_(root, locator) {
  const folder = findShareFolder_(root, locator);
  if (!folder) throw apiError_('SHARE_NOT_FOUND', 'Code falsch, abgelaufen oder gelöscht.');
  return folder;
}

function requireActiveShare_(locator) {
  const root = getRootFolder_();
  const folder = requireShareFolder_(root, locator);
  const meta = readMetadata_(folder);
  if (meta.status !== 'active' || Number(meta.expiresAt || 0) <= Date.now()) {
    if (Number(meta.expiresAt || 0) <= Date.now()) deleteFolderPermanently_(folder);
    throw apiError_('SHARE_NOT_FOUND', 'Code falsch, abgelaufen oder gelöscht.');
  }
  return { folder, meta };
}

function readMetadata_(folder) {
  const file = findSingleFile_(folder, MG_CONFIG.META_FILE_NAME);
  if (!file) throw new Error('Metadaten fehlen.');
  return JSON.parse(file.getBlob().getDataAsString('UTF-8'));
}

function writeMetadata_(folder, metadata) {
  const text = JSON.stringify(metadata);
  const file = findSingleFile_(folder, MG_CONFIG.META_FILE_NAME);
  if (file) file.setContent(text);
  else folder.createFile(MG_CONFIG.META_FILE_NAME, text, MimeType.PLAIN_TEXT);
}

function findSingleFile_(folder, name) {
  const files = folder.getFilesByName(name);
  return files.hasNext() ? files.next() : null;
}

function deleteFilesByName_(folder, name) {
  const files = folder.getFilesByName(name);
  while (files.hasNext()) deleteFilePermanently_(files.next());
}


function deleteFolderPermanently_(folder) {
  try {
    Drive.Files.remove(folder.getId());
  } catch (error) {
    // Fallback, falls der erweiterte Drive-Dienst noch nicht aktiviert wurde.
    // Die Datei bleibt dann bis zur automatischen Drive-Papierkorb-Löschung
    // verschlüsselt im Papierkorb.
    folder.setTrashed(true);
  }
}

function deleteFilePermanently_(file) {
  try {
    Drive.Files.remove(file.getId());
  } catch (error) {
    file.setTrashed(true);
  }
}

function requireLocator_(value) {
  value = String(value || '').toUpperCase();
  if (!/^[A-Z2-9]{8}$/.test(value)) throw apiError_('INVALID_LOCATOR', 'Ungültiger Freigabecode.');
  return value;
}

function requireFileId_(value) {
  value = String(value || '');
  if (!/^[A-Za-z0-9._-]{1,80}$/.test(value) || value === MG_CONFIG.META_FILE_NAME) {
    throw apiError_('INVALID_FILE_ID', 'Ungültige Datei-ID.');
  }
  return value;
}

function requireSafeText_(value, maxLength, fieldName) {
  value = String(value || '');
  if (!value || value.length > maxLength || /[\u0000-\u001f]/.test(value)) {
    throw apiError_('INVALID_FIELD', `Ungültiges Feld: ${fieldName}`);
  }
  return value;
}

function randomToken_(byteLength) {
  // Apps Script stellt keine Web-Crypto-API bereit. Mehrere UUIDs liefern hier
  // ausreichend zufälliges Material; anschließend wird auf die gewünschte
  // Länge gekürzt. Die öffentlich sichtbaren Freigabecodes werden zusätzlich
  // im Browser mit crypto.getRandomValues() erzeugt.
  let material = '';
  while (material.length < byteLength * 2) material += Utilities.getUuid().replace(/-/g, '');
  return material.slice(0, byteLength * 2);
}

function sha256Text_(text) {
  return bytesToHex_(Utilities.computeDigest(Utilities.DigestAlgorithm.SHA_256, String(text), Utilities.Charset.UTF_8));
}

function sha256Bytes_(bytes) {
  return bytesToHex_(Utilities.computeDigest(Utilities.DigestAlgorithm.SHA_256, bytes));
}

function bytesToHex_(bytes) {
  return bytes.map(value => ((value < 0 ? value + 256 : value).toString(16).padStart(2, '0'))).join('');
}

function constantTimeEqual_(left, right) {
  left = String(left || '');
  right = String(right || '');
  if (left.length !== right.length) return false;
  let result = 0;
  for (let i = 0; i < left.length; i += 1) result |= left.charCodeAt(i) ^ right.charCodeAt(i);
  return result === 0;
}

function apiError_(code, message) {
  const error = new Error(message);
  error.name = code;
  return error;
}
