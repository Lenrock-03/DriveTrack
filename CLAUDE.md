# DriveTrack – Android-App

Strava-ähnliche App zur Aufzeichnung von Autofahrten (GPS-Tracking, Statistiken, Kartenansicht).
Kotlin + Jetpack Compose, Package `de.kornelriedl.drivetrack`.

## Zugehörige Projekte

Dieses Repo ist Teil eines Drei-Komponenten-Systems:

1. **Diese App** (hier) – Android-Client, zeichnet Fahrten per GPS auf, lokale Datenhaltung
2. **Backend-API** – `C:\Users\korne\OneDrive\Dokumente\Programmieren\DriveTrack` (Node.js/Express),
   läuft live auf `https://drivetrack-api.kornel-riedl.de`
3. **Web-App** – `C:\Users\korne\OneDrive\Dokumente\Programmieren\DriveTrack-Web` (statisches HTML/JS),
   läuft live auf `https://drivetrack.kornel-riedl.de`. War bis v1.6.0 rein lesend, kann seit
   v1.7.0 auch Fahrten zuschneiden/markieren (eigener Schreibpfad, konfliktsicher mit dem
   App-Sync abgestimmt, siehe "Fahrten bearbeiten"-Abschnitt unten)

Alle drei teilen sich dasselbe JSON-Backup-Format und dasselbe Verschlüsselungsschema (siehe unten) –
Änderungen daran müssen in allen drei Projekten synchron gehalten werden.

## Architektur

- **Einstiegspunkt**: `MainActivity.kt` – eine einzige Activity, kein Navigation-Component, Tab-Wechsel
  über simplen Compose-State (`NavTab`-Enum: HOME, FAHRTEN, AUFZEICHNEN, KARTE, EINSTELLUNGEN)
- **Datenbank**: Room (aktuell Version 7), drei Entities – `Trip`, `Car`, `UserProfile`
  (siehe `data/`, `data/local/`). Migrationen additiv (`ALTER TABLE ... ADD COLUMN`), NICHT
  vergessen sie in `AppDatabase.addMigrations(...)` einzutragen – `fallbackToDestructiveMigration()`
  ist aktiv, ein Versionsbump ohne registrierte Migration löscht sonst kommentarlos alle Daten
  bestehender Installationen.
- **GPS-Tracking**: `tracking/LocationTracker.kt` (Singleton, FusedLocationProviderClient), läuft auch
  als Foreground-Service (`tracking/TripTrackingService.kt`), damit GPS im Hintergrund nicht gedrosselt wird
- **Karten**: osmdroid mit CartoDB-Dark-Matter-Kacheln (`ui/screens/MapScreen.kt` definiert die
  Tile-Source + einen Kontrast-Filter, `buildContrastFilter()`, gemeinsam genutzt von allen Karten-Screens)
- **Routen-Farbmodus** (`ui/screens/TripDetailScreen.kt`, seit 0.4.0): Umschalter oben rechts auf der
  Fahrt-Detail-Karte zwischen einheitlicher Farbe und Einfärbung nach Geschwindigkeit (grün→rot,
  `speedToColor()`). Bei Geschwindigkeit wird die Route in kurze `Polyline`-Segmente zerlegt (max.
  `MAX_ROUTE_COLOR_SEGMENTS` = 1500, sonst würden viele tausend Overlays das Rendering bei langen
  Fahrten spürbar verlangsamen) – dieselbe median-gefilterte Geschwindigkeits-Serie wie `SpeedGraph()`.
  Die Farbskala ist seit 0.4.1 **fest**, nicht relativ zur einzelnen Fahrt – dazu eine Legende
  (`RouteColorLegend()`) unten links, solange der Modus aktiv ist. Seit 0.4.2 zweistufig:
  0–130 km/h grün→rot (`ROUTE_COLOR_RED_KMH`), 130–180 km/h zusätzlich rot→lila
  (`ROUTE_COLOR_PURPLE_KMH`, danach gekappt). Spiegelt sich 1:1 in der Web-App
  (`renderRouteLine()`/`speedToColor()` in `js/app.js`). Seit 0.8.0 in `ui/components/
  RouteDetailMap.kt` (Karte inkl. `RouteColorMode`/`applyRouteColorMode`/`buildSpeedColoredSegments`)
  und `ui/components/SpeedGraphChart.kt` (`SpeedGraph`) ausgelagert, damit `TripEditScreen` dieselben
  Komponenten nutzt statt sie zu duplizieren; die zugehörige Distanz-/Geschwindigkeits-Mathematik
  (`GraphPoint`, `toSpeedSeries()`, `medianFiltered()`, `speedSeriesClamped()`, ...) liegt seitdem
  Compose-frei in `data/TripGeoMath.kt`.
- **Fahrten bearbeiten** (seit 0.8.0): `ui/screens/TripEditScreen.kt`, erreichbar über den Stift-
  Button in `TripDetailScreen`s TopAppBar (`MainActivity`-State `editingTripId`, gleiches Muster wie
  `editingCarId` – liegt "über" der Detailseite, `selectedTripId` bleibt währenddessen gesetzt).
  `MainActivity` hält seit 0.10.0 nur noch `selectedTripId: Long?` (nicht mehr das `Trip`-Objekt
  selbst) und liest die Fahrt für `TripDetailScreen` live aus dem `trips`-Flow (`trips.find { it.id
  == selectedTripId }`, analog zu `editingCarId`/`editingTripId`) – dadurch sind alle Änderungen
  (Umbenennen, Auto zuordnen, Zuschneiden, Labels/Markierungen speichern) sofort nach dem
  Zurücknavigieren sichtbar, ohne den angezeigten Trip manuell nachzupflegen (vorher pro Aktion
  einzeln nötig, z. B. `selectedTrip = trip.copy(...)`, war leicht zu vergessen).
  Bedienung: im wiederverwendeten `SpeedGraph` scrubben, "Punkt A/B setzen" merkt sich den jeweils
  aktuell gescrubbten Zeitstempel (`SpeedGraph.onScrub` liefert seit 0.8.0 den vollen `GraphPoint`
  statt nur Lat/Lon, damit der Zeitstempel verfügbar ist). Zwei Kategorien von Änderungen:
  - **Zuschneiden** (destruktiv, entfernt GPS-Punkte endgültig): Anfang bis A / B bis Ende
    abschneiden, oder A–B als Pause aus der Mitte entfernen. Sammelt sich erst in einer einsehbaren,
    einzeln löschbaren Änderungsliste, wird erst nach Bestätigungsdialog über
    `data/TripGeoMath.kt::applyTripEditPlan()` angewendet. Dabei bleibt `startTimestamp`/
    `endTimestamp` (Gesamtdauer) durch einen Pause-Cut **unverändert** – nur `pausedMinutes`
    (akkumuliert über mehrere Bearbeitungs-Sessions) steigt, `Trip.drivingDurationMinutes`
    ("Fahrzeit" = Gesamtdauer − pausedMinutes) sinkt entsprechend, `avgSpeedKmh` wird darüber neu
    berechnet. Punkte werden in zusammenhängende Läufe gruppiert, damit Distanz nie über eine
    Schnittlücke hinweg summiert wird. Nach jedem Schnitt wird
    `MapThumbnailGenerator.invalidate()` aufgerufen (sonst zeigt die Fahrtenliste ein veraltetes
    Vorschaubild).
  - **Markieren** (nicht-destruktiv): Fahrt-Labels (`Trip.labels`, kommagetrennt,
    Presets wie "⛴ Fähre" + Freitext) als Badges in `TripListItem`/`TripDetailScreen`, und
    Streckenabschnitts-Markierungen (`Trip.segmentMarksJson`, `SegmentMark(label, startTs, endTs)`)
    als gestrichelte Linie auf der Karte (`RouteDetailMap`) in einer je Typ festen Farbe
    (`data/TripGeoMath.kt::labelColor()` – Fähre blau, Pause bernstein, Nacht indigo, sonst türkis;
    dieselbe Farbe auch als Punkt in der Markierungs-Liste). Ein markierter Abschnitt bleibt Teil von
    Distanz/Dauer/Ø-Geschwindigkeit der GESAMTEN Fahrt, wird aber automatisch aus deren `maxSpeedKmh`
    ausgeschlossen (`recomputeMaxSpeedExcludingMarks()`) – Grundmotivation: eine kurze Autofähre soll
    die Höchstgeschwindigkeits-Statistik der Fahrt nicht verfälschen. Seit 0.9.0 zusätzlich **eigene**
    Statistik je Abschnitt (`Trip.segmentStats()` – Distanz/Dauer/Ø-/Höchstgeschwindigkeit nur
    innerhalb des markierten Zeitraums), angezeigt über die gemeinsame Komponente
    `ui/components/SegmentMarkRow.kt` (genutzt von `TripDetailScreen` read-only und `TripEditScreen`
    mit Lösch-Button). Seit 0.10.0 nicht mehr sofort gespeichert, sondern nur lokaler Bildschirm-
    State (`pendingLabels`/`pendingMarks`) bis zum Verlassen des Screens: `TripEditScreen` registriert
    einen eigenen `BackHandler` (überschreibt automatisch `MainActivity`s äußeren Handler, solange
    der Screen sichtbar ist – Compose gibt dem zuletzt registrierten aktiven `BackHandler` Vorrang),
    der bei ungespeicherten Änderungen "Änderungen speichern?" nachfragt (Speichern/Verwerfen) statt
    direkt zurückzunavigieren. "Speichern" schreibt Labels+Markierungen in einem einzigen
    `tripDao.updateTrip()` (`onSaveTripMetadata` in `MainActivity`, ersetzt die früheren
    Einzel-Callbacks `onUpdateLabels`/`onAddSegmentMark`/`onDeleteSegmentMark`). Damit beim
    "Änderungen anwenden" für Zuschnitte keine noch nicht gespeicherten Label-/Markierungs-Änderungen
    verloren gehen, werden `pendingLabels`/`pendingMarks` dort ebenfalls mit übergeben und vor
    `applyTripEditPlan()` in die Fahrt gemerged.
  - "Fahrzeit"-Kacheln auf Home-Dashboard/`CarDetailScreen` nutzen seit 0.8.0
    `Trip.drivingDurationMinutes` statt der reinen Gesamtdauer (für Fahrten ohne Pause-Schnitt
    identisches Ergebnis wie vorher).
- **Android Auto**: `car/DriveTrackCarAppService.kt` + `car/RecordingCarScreen.kt`
- **Einstellungen** (seit 0.5.0): `ui/screens/SettingsScreen.kt` ist nur noch der Einstiegspunkt
  (Konto/Fahrzeuge/Daten/Über, ~200 Zeilen), mit gemeinsamer `SettingsSectionCard`/`SettingsNavCard`
  (`ui/components/SettingsSectionCard.kt`) statt vorher 7x kopierter Card-Boilerplate. Fahrzeuge
  und Import/Export sind eigene Untermenüs (kein Navigation-Component, gleiches `MainActivity`-
  State-Muster wie `selectedTrip`/`showServerBackup`):
  - `ui/screens/CarDetailScreen.kt` – Fahrzeug bearbeiten (Name, Bluetooth-Auto-Start via dem
    hierher verschobenen `BluetoothDevicePickerDialog`, Standard-Schalter, Löschen) plus optional
    ein **Foto** (`data/local/CarPhotoStore.kt`, spiegelt `TrackFileStore` – Datei statt DB-Blob).
    Das Foto ist bewusst **nur lokal** (`filesDir/car_photos/`), NICHT Teil des Backups (weder
    lokal noch Server) – vermeidet Binärdaten im JSON-Backup-Format, das sich alle drei Repos
    teilen. Geht dadurch bei Neuinstallation/Wiederherstellung verloren (in der UI kommuniziert).
    Bildausschnitt wählbar (seit 0.6.0) über `com.canhub.cropper` (`CropImageContract`,
    16:9 fest) statt nur das Originalbild zu übernehmen – **View-basierte Lib, kein Compose**,
    braucht zwingend ein `Theme.AppCompat`-Theme, das die App sonst nirgends hat (reines
    Compose/Material3). Deshalb `CropperTheme` in `res/values/themes.xml` +
    `<activity android:name="com.canhub.cropper.CropImageActivity" android:theme="@style/CropperTheme">`-
    Override in `AndroidManifest.xml`, nur für diese eine fremde Activity – ohne das crasht der
    Zuschneide-Dialog mit `IllegalStateException: You need to use a Theme.AppCompat theme`.
    `CarPhotoStore.savePhotoFromUri()` bekommt danach die schon zugeschnittene Uri und
    verkleinert/EXIF-normalisiert wie zuvor. Seit 0.7.0 außerdem Statistik-Kacheln (Gesamt-km,
    Fahrten, Fahrzeit, Ø Speed, Höchstgeschwindigkeit) nur über die Fahrten dieses Fahrzeugs -
    nutzt die aus `HomeScreen.kt` ausgelagerte `ui/components/StatCard.kt`.
  - `ui/screens/ImportExportScreen.kt` – GPX-Import, GPX-Export, lokales Gesamt-Backup (rein
    organisatorisch aus `SettingsScreen.kt` ausgelagert, keine funktionale Änderung)
  - `ui/components/AddCarDialog.kt` – ein gemeinsamer "Fahrzeug hinzufügen"-Dialog statt vorher
    zweier fast identischer (Settings + `CarSelector.kt`)

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
gebaut von `export/BackupExporter.kt` (`buildBackupJson()` / `importBackupFromJson()`). Neue
Trip-Felder (z. B. `labels`/`pausedMinutes`/`segmentMarksJson` seit 0.8.0) werden additiv ergänzt und
beim Import defensiv gelesen (`optLong`/`optString`/`isNull`-Prüfung) – alte Backups ohne diese
Schlüssel bleiben importierbar. Das Backend speichert das Backup nur als E2E-verschlüsselten Blob
(sieht die Feldstruktur nie im Klartext), die Web-App ignoriert unbekannte JSON-Schlüssel beim
Parsen – rein additive Trip-Felder brauchen deshalb **keine** Backend-/Web-Änderung.

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
- **Nach dem Beenden UND nach jeder Fahrt-Bearbeitung** (seit 0.11.0 – Umbenennen, Zuschneiden,
  Labels/Markierungen speichern, Auto-Zuordnen, Löschen; vorher synchronisierte nur das
  Aufzeichnungsende): ruft `triggerBackgroundSync()` in `MainActivity.kt` →
  `ServerSync.syncFullBackupIfPossible()` auf, holt Nutzer/Autos/Fahrten dafür frisch aus der DB
  (nicht die Compose-State-Listen, die z. B. den gerade eingefügten Trip evtl. noch nicht
  enthalten). Löscht danach den Live-Zwischenstand auf dem Server (`DELETE /api/live-trip`).
- **Beim Verwerfen** (Bluetooth-Fehlstart, siehe `DiscardRecordingReceiver`): nur der
  Live-Zwischenstand wird gelöscht, kein Backup-Sync (die Fahrt wurde ja nie gespeichert).
- **Manuell per Runterziehen** (seit 0.12.0): `HomeScreen.kt` umschließt die Fahrtenliste (Home
  UND Fahrten) mit `PullToRefreshBox` (Material3, braucht Compose-BOM 2024.10.01+ - deshalb der
  Bump von vorher 2024.06.00), `MainActivity`s `onManualSync` ruft dafür genau denselben
  `triggerBackgroundSync()`/`syncFullBackupIfPossible()` auf wie die automatischen Anlässe oben -
  kein separater Code-Pfad. Pendant zum "🔄 Aktualisieren"-Button in der Web-App-Kopfzeile
  (`js/app.js`, ruft dort nur `loadAndRenderBackup()`, da die Web-App lokale Bearbeitungen bereits
  beim Speichern selbst pusht statt sie zwischenzuhalten).
- **Konfliktsicher seit 0.11.0** (Anlass: die Web-App kann seitdem ebenfalls Backups pushen):
  `syncFullBackupIfPossible()` ist kein blinder Push mehr, sondern Pull-Check-Merge-Push - lädt
  erst `GET /api/backup` (liefert auch die Server-`id`), vergleicht sie mit der zuletzt bekannten
  (`ServerAuthPreferences.getLastKnownBackupId()`). Weicht sie ab (ein anderes Gerät hat inzwischen
  gepusht), wird diese Server-Version erst additiv gemergt (`BackupExporter.importBackupFromJson()`
  - dedupliziert Trips über Start-/Endzeitpunkt, Users/Cars über Namen, überschreibt/löscht nie)
  und die DB danach frisch gelesen, BEVOR der eigentliche Push passiert - sonst würde jeder
  Sync-Zyklus unbemerkt fremde Bearbeitungen überschreiben. Bekannte Grenze: kein Feld-Level-Merge
  - wird *dieselbe* Fahrt zwischen zwei Syncs auf zwei Geräten unterschiedlich bearbeitet, gewinnt
  die zeitlich letzte (die überschriebene Zwischenversion bleibt aber über den Versionsverlauf
  wiederherstellbar, siehe unten). Löschungen werden ebenfalls nicht über Merges propagiert (rein
  additiver Merge kann eine woanders gelöschte Fahrt bei einem Konflikt wieder auferstehen lassen,
  falls just in diesem Moment ein Konflikt eintritt - seltener Edge-Case, bewusst nicht gelöst).
- **Versionsverlauf** (`ServerBackupScreen.kt`, seit 0.11.0): nutzt die bis dahin nie aufgerufenen
  `ServerApi.backupHistory()`/`downloadBackupVersion()` (Backend speichert jede gepushte Version für
  immer, `POST /api/backup` überschreibt nie - `backups`-Tabelle ist bereits Append-only). Zeigt
  Zeitstempel vergangener Versionen, Antippen ruft `BackupExporter.restoreFromJson()` auf - anders
  als der normale additive Import werden dabei bestehende Fahrten mit übereinstimmender Start-/
  Endzeit gezielt auf den gewählten (älteren) Stand ZURÜCKGESETZT statt als Duplikat übersprungen -
  das eigentliche manuelle Sicherheitsnetz bei einem Sync-Konflikt oder einer fehlerhaften
  Bearbeitung. `ServerApi.backupHistory()` parst die Antwort bewusst NICHT über `safeRequest()`/
  `ApiResult` (die immer `JSONObject(text)` erwarten) - der Endpunkt liefert ein rohes JSON-Array,
  was vor 0.11.0 dazu geführt hätte, dass die (bis dahin nie aufgerufene) Funktion immer fehlgeschlagen wäre.

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
