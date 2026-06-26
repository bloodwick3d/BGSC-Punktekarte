/* MiniGolf Drive-Testkonfiguration
 *
 * Die bereitgestellte Google-Apps-Script-/exec-URL ist bereits eingetragen.
 * Die PWA bleibt lokal; nur verschlüsselte Freigaben werden über
 * die Apps-Script-Bridge in Google Drive gespeichert.
 */
window.MINIGOLF_DRIVE_CONFIG = Object.freeze({
  mode: 'apps-script',
  bridgeUrl: 'https://script.google.com/macros/s/AKfycbzcdkz_zPF1wa35p7xItOTpdRVHJnLJFSI3_oIfAKxgSDx3yIrqvTxGC-cTr9hXjFGm-Q/exec',
  requestTimeoutMs: 120000
});
