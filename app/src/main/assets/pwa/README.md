# MiniGolf Punktekarte – lokaler Google-Drive-Test

Diese Testfassung läuft ausschließlich lokal. Temporäre Freigaben werden über
Google Apps Script verschlüsselt im privaten Google Drive gespeichert.
Die sichtbare App-Version bleibt **PWA V84**; diese Fassung ist noch nicht für
ein Firebase-Deployment vorgesehen.

## Start

Unter Windows `START_DRIVE_TEST.bat` doppelklicken.

Alternativ im Projektordner:

```powershell
npm start
```

Danach öffnen:

- PWA: `http://localhost:8092/`
- optionaler Verbindungstest: `http://localhost:8092/DRIVE_CONNECTION_TEST.html`

## Wichtige Dateien

- `index.html`, `script.js` – Oberfläche und Spiellogik
- `cloud-share.js` – temporäre Onlinefreigaben und Cloud-Import
- `drive-form-client-v5.js` – Kommunikation mit Google Apps Script
- `drive-config.js` – veröffentlichte `/exec`-Adresse
- `google-apps-script/` – Quellcode des Drive-Backends
- `dev-server.js` – lokaler Testserver

## Apps Script aktualisieren

Nach Änderungen an `google-apps-script/Code.gs`, `Bridge.html` oder
`appsscript.json` im Apps-Script-Editor immer eine **neue Version** der
bestehenden Web-App-Bereitstellung veröffentlichen. Die `/exec`-Adresse bleibt
normalerweise gleich.

## Upload- und Downloadanzeige

Echte Cloud-Übertragungen zeigen jetzt eine Schritt-für-Schritt-Checkliste mit
Fortschrittsbalken, laufenden Detailmeldungen und einer abschließenden
Fertig- oder Fehleranzeige. Für diese reine Oberflächenänderung muss das
Apps-Script-Backend nicht erneut bereitgestellt werden.

## Datenschutz

Notizdaten und Bilder werden vor dem Upload im Browser verschlüsselt. Das
Google-Drive-Konto speichert nur verschlüsselte Dateien. Lokale Spielstände und
Notizen bleiben in der IndexedDB des jeweiligen Geräts.
