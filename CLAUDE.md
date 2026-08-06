# DriveTrack – Android-App

Strava-ähnliche App zur Aufzeichnung von Autofahrten (GPS-Tracking, Statistiken, Kartenansicht).
Kotlin + Jetpack Compose, Package `de.kornelriedl.drivetrack`.

## Zugehörige Projekte

Dieses Repo ist Teil eines Drei-Komponenten-Systems:

1. **Diese App** (hier) – Android-Client, zeichnet Fahrten per GPS auf, lokale Datenhaltung
2. **Backend-API** – `C:\Users\korne\OneDrive\Dokumente\Programmieren\DriveTrack` (Node.js/Express),
   läuft live auf `https://drivetrack-api.kornel-riedl.de`
3. **Web-App** – `C:\Users\korne\OneDrive\Dokumente\Programmieren\DriveTrack-Web` (statisches HTML/JS),
   läuft live auf `https://drivetrack.kornel-riedl.de`, reiner Lese-Client fürs Server-Backup

Alle drei teilen sich dasselbe JSON-Backup-Format und dasselbe Verschlüsselungsschema (siehe unten) –
Änderungen daran müssen in allen drei Projekten synchron gehalten werden.

## Architektur

- **Einstiegspunkt**: `MainActivity.kt` – eine einzige Activity, kein Navigation-Component, Tab-Wechsel
  über simplen Compose-State (`NavTab`-Enum: HOME, FAHRTEN, AUFZEICHNEN, KARTE, EINSTELLUNGEN)
- **Datenbank**: Room, drei Entities – `Trip`, `Car`, `UserProfile` (siehe `data/`, `data/local/`)
- **GPS-Tracking**: `tracking/LocationTracker.kt` (Singleton, FusedLocationProviderClient), läuft auch
  als Foreground-Service (`tracking/TripTrackingService.kt`), damit GPS im Hintergrund nicht gedrosselt wird
- **Karten**: osmdroid mit CartoDB-Dark-Matter-Kacheln (`ui/screens/MapScreen.kt` definiert die
  Tile-Source + einen Kontrast-Filter, `buildContrastFilter()`, gemeinsam genutzt von allen Karten-Screens)
- **Routen-Farbmodus** (`ui/screens/TripDetailScreen.kt`, seit 0.4.0): Umschalter oben rechts auf der
  Fahrt-Detail-Karte zwischen einheitlicher Farbe und Einfärbung nach Geschwindigkeit (grün→rot,
  `speedToColor()`). Bei Geschwindigkeit wird die Route in kurze `Polyline`-Segmente zerlegt (max.
  `MAX_ROUTE_COLOR_SEGMENTS` = 1500, sonst würden viele tausend Overlays das Rendering bei langen
  Fahrten spürbar verlangsamen) – dieselbe median-gefilterte Geschwindigkeits-Serie wie `SpeedGraph()`.
  Spiegelt sich 1:1 in der Web-App (`renderRouteLine()`/`speedToColor()` in `js/app.js`).
- **Android Auto**: `car/DriveTrackCarAppService.kt` + `car/RecordingCarScreen.kt`

## Server-Backup (Ende-zu-Ende-verschlüsselt)

Kompletter Code in `data/server/`:
- `ServerCrypto.kt` – PBKDF2 (150.000 Iterationen) + AES-256-GCM. Ein zufälliger Datenschlüssel (DEK)
  verschlüsselt das Backup; der DEK wird zweifach verpackt gespeichert (mit Passwort UND mit
  Recovery-Code) – der Server sieht nie Passwort, Recovery-Code oder DEK im Klartext
- `ServerSession.kt` – hält den entschlüsselten DEK für die App-Sitzung im RAM
- `ServerAuthPreferences.kt` – Token/Salt via `EncryptedSharedPreferences`; seit Auto-Sync
  (App 0.3.0) zusätzlich der entschlüsselte DEK selbst (`saveDek()`/`getDek()`, ebenfalls
  Keystore-verschlüsselt) – **bewusste Sicherheitsmodell-Änderung**: der DEK überlebt jetzt einen
  App-Neustart (vorher: nur RAM, nach jedem Neustart musste das Passwort erneut eingegeben werden).
  Nötig, damit Auto-Sync auch im Hintergrund läuft (z. B. nach Bluetooth-Auto-Start, ohne dass
  vorher je die App geöffnet wurde). Das Passwort selbst wird weiterhin nirgends gespeichert.
- `ServerApi.kt` – simpler HTTP-Client (nur `java.net` + `org.json`, keine externe Lib) gegen die Backend-API
- `ServerSync.kt` – Auto-Sync-Orchestrierung (siehe eigener Abschnitt unten)
- UI: `ui/screens/ServerBackupScreen.kt` (Login/Registrieren/Entsperren/Sichern/Wiederherstellen/Passwort vergessen) –
  bleibt als manueller Fallback bestehen, wird durch Auto-Sync aber seltener gebraucht

Backup-JSON-Struktur (muss mit Backend + Web-App übereinstimmen): `{ version, users[], cars[], trips[] }`,
gebaut von `export/BackupExporter.kt` (`buildBackupJson()` / `importBackupFromJson()`).

## Auto-Sync mit dem Server (seit 0.3.0)

Fahrten werden automatisch synchronisiert, ohne dass der Nutzer manuell "Sichern" tippen muss – nur
falls eingeloggt (Token vorhanden) und der DEK verfügbar ist (siehe oben), sonst passiert einfach
still nichts (kein Fehler sichtbar, manueller Button bleibt als Fallback):

- **Während der Aufzeichnung**: `TripTrackingService` lädt alle 3 Minuten (`LIVE_SYNC_INTERVAL_MS`)
  einen Zwischenstand der laufenden Fahrt an `PUT /api/live-trip` hoch (eigenes, leichtgewichtiges
  JSON – nicht das volle Backup-Format). Sicherheitsnetz falls Handy/App mittendrin ausfällt.
  **Thread-Achtung**: Der Snapshot (`ServerSync.LiveTripSnapshot`) muss auf dem Main-Thread gezogen
  werden, BEVOR auf `Dispatchers.IO` gewechselt wird – `LocationTracker.points` wird vom
  GPS-Callback ebenfalls auf dem Main-Thread verändert, ein direkter Zugriff aus einer
  Hintergrund-Coroutine wäre nicht thread-sicher.
- **Nach dem Beenden**: `MainActivity` ruft nach dem lokalen Speichern
  `ServerSync.syncFullBackupIfPossible()` auf – normaler Voll-Sync (wie der bisherige manuelle
  Button), holt Nutzer/Autos/Fahrten frisch aus der DB (nicht die Compose-State-Listen, die den
  gerade eingefügten Trip evtl. noch nicht enthalten). Löscht danach den Live-Zwischenstand auf dem
  Server (`DELETE /api/live-trip`) – die fertige Fahrt ist jetzt Teil des echten Backups.
- **Beim Verwerfen** (Bluetooth-Fehlstart, siehe `DiscardRecordingReceiver`): nur der
  Live-Zwischenstand wird gelöscht, kein Backup-Sync (die Fahrt wurde ja nie gespeichert).

## Bekannte Stolpersteine (bereits gelöst, für Kontext)

- **Room 2.6.1 + neuere KSP-Versionen**: Absturz beim Build mit `unexpected jvm signature V` in
  `DeletionMethodProcessor`. Fix: Room auf **2.8.4+** hochgezogen (Bug ist dort behoben).
- **osmdroid-Karten springen beim Öffnen**: `animateTo()` beim ersten GPS-Fix erzeugt ein sichtbares
  "Wegfliegen". Fix: `setCenter()` statt `animateTo()`, plus `LocationManager.getLastKnownLocation()`
  als sofortiger Startpunkt statt eines Platzhalter-Orts.
- **Weiße Info-Bubble beim Antippen einer Route**: osmdroid zeigt sonst eine hässliche Standard-Bubble.
  Fix: `polyline.setOnClickListener { _, _, _ -> true }` (Event konsumieren, nichts anzeigen).
- **Server-Backup-Fehler 413 (Payload Too Large)**: Nginx vor der API hat ein eigenes Body-Size-Limit,
  unabhängig vom Express-Limit. Muss auf dem VPS in der Nginx-Config mit `client_max_body_size 15M;`
  gesetzt sein.

- **Absturz nach sehr langer Fahrt (ganztägig, viele tausend GPS-Punkte), auch beim Neustart** –
  gelöst 2026-08-03. Ursache per `adb logcat` gefunden: `SQLiteBlobTooBigException: Row too big to
  fit into CursorWindow` in `TripDao.getAllTrips()`. `gpxTrackJson` lag als `TEXT`-Spalte in der
  `trips`-Zeile; Android begrenzt eine einzelne per Cursor gelesene Zeile auf ca. 2 MB, unabhängig
  von Tabellen-/Query-Aufbau. Fix: `gpxTrackJson` ist keine Room-Spalte mehr, sondern liegt als
  Datei pro Fahrt unter `filesDir/tracks/trip_<id>.json` (siehe `data/local/TrackFileStore.kt`).
  Bestehende Nutzerdaten (auch der betroffenen Zeile) wurden per Room-`Migration` (`AppDatabase.kt`,
  v3→v4) automatisch mit `compileStatement().simpleQueryForString()` gerettet – das umgeht das
  CursorWindow-Limit ebenfalls, da es keinen Cursor verwendet. `Trip.gpxTrackJson` ist im Kotlin-
  Modell weiterhin vorhanden (für den Moment direkt nach Aufzeichnung/Import), aber `@Ignore` für
  Room; alle Lesestellen (Karte, Thumbnail, Speed-Graph, GPX-/Backup-Export) holen den Track jetzt
  explizit über `TrackFileStore.read(context, trip.id)`.

## Deployment

Keine automatisierte Pipeline – Debug-Build über Android Studio (`Gradle sync` + `Run`). Kein Play-Store-Release bisher.

## Versionierung

Seit 2026-08-05 einheitlich über alle drei Projekte (App, Backend, Web):

- **Semantic Versioning** (`MAJOR.MINOR.PATCH`) – `versionName` in `app/build.gradle.kts` ist die
  einzige Quelle der Wahrheit, `versionCode` bei jedem Release ebenfalls um 1 erhöhen (auch ohne
  Play Store relevant für Update-Reihenfolge). Wird zur Laufzeit per `PackageManager` gelesen und
  unten in den Einstellungen angezeigt.
- **MAJOR**: Breaking Change am Backup-JSON-Format/Verschlüsselungsschema (betrifft dann zwangsläufig
  auch Backend + Web-App) oder DB-Migration, die alte Daten nicht mehr rettet
- **MINOR**: neues Feature, abwärtskompatibel
- **PATCH**: Bugfix, kein Verhaltensunterschied
- Bei jedem Bump: `CHANGELOG.md` ergänzen (Format: [Keep a Changelog](https://keepachangelog.com/)),
  Git-Tag `vX.Y.Z` setzen, `git push --tags`
- Releases aktuell **manuell** per `gh release create vX.Y.Z <apk-datei> --notes-file CHANGELOG.md` –
  kein CI/CD dafür eingerichtet, APK wird lokal per `./gradlew assembleDebug` gebaut (kein
  Release-Signing-Setup bisher, daher Debug-Build auch für Releases)
- Repo: `github.com/Lenrock-03/DriveTrack`

**Wichtig beim Verteilen an andere Geräte** (z. B. Familie): Die APK immer als **Update** über die
bestehende Installation einspielen (`adb install -r` oder Datei antippen und "Aktualisieren" wählen),
niemals vorher deinstallieren – sonst geht die lokale Room-Datenbank (alle Fahrten) verloren. Nur bei
Signatur-Mismatch (App wurde ursprünglich mit einem anderen Debug-Keystore installiert) geht das nicht
automatisch; dann vorher `adb run-as de.kornelriedl.drivetrack cat databases/drivetrack.db` sichern.
