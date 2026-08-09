# Changelog

Alle nennenswerten Änderungen an diesem Projekt werden hier dokumentiert.
Format angelehnt an [Keep a Changelog](https://keepachangelog.com/), Versionierung nach
[Semantic Versioning](https://semver.org/) (`MAJOR.MINOR.PATCH`, sichtbar als `versionName` in
`build.gradle.kts` sowie unten in den Einstellungen der App).

## [0.12.0] - 2026-08-09

### Hinzugefügt
- **Runterziehen zum Aktualisieren** auf der Fahrtenliste (Home & Fahrten): löst denselben
  konfliktsicheren Sync wie bisher schon automatisch (Pull-Check-Merge-Push, siehe 0.11.0) manuell
  aus, statt auf den nächsten automatischen Anlass zu warten - Änderungen von anderen Geräten
  (z. B. der Web-App) sollen sich einfach und sofort abholen lassen.

### Geändert
- Compose-BOM auf 2024.10.01 angehoben (vorher 2024.06.00), nötig für `PullToRefreshBox`
  (Material3, erst ab 1.3.0 verfügbar). Dabei `rememberRipple()` (wurde mit der neuen Version zum
  Hard-Compile-Error, vorher nur deprecated) durch `LocalIndication.current` ersetzt
  (`TripListItem.kt`) - keine sichtbare Verhaltensänderung.

## [0.11.0] - 2026-08-09

### Geändert
- **Konfliktsicherer Server-Sync**: `ServerSync.syncFullBackupIfPossible()` war bisher ein reiner,
  ungeprüfter Push (überschrieb serverseitig blind alles). Prüft jetzt vorher, ob der Server
  inzwischen eine diesem Gerät unbekannte, neuere Version hat (z. B. von der Web-App gepusht) - falls
  ja, wird sie erst additiv in die lokale Datenbank gemergt, bevor der (jetzt vollständige) Stand
  gepusht wird. Nichts geht dabei verloren: jede Server-Version bleibt für immer in der Backup-
  Historie erhalten (`POST /api/backup` fügte schon immer nur hinzu, nie überschrieben).
- Bisher synchronisierte nur das Ende einer Aufzeichnung automatisch - Umbenennen, Zuschneiden,
  Labels/Markierungen speichern, Auto-Zuordnen und Löschen einer Fahrt lösen jetzt ebenfalls
  zeitnah einen Sync aus, statt bis zur nächsten Fahrt zu warten.

### Hinzugefügt
- **Versionsverlauf** in den Server-Backup-Einstellungen: Liste aller bisherigen Server-Versionen,
  antippen setzt Fahrten mit übereinstimmender Start-/Endzeit gezielt auf diesen (z. B. bekannt
  sauberen) Stand zurück - das eigentliche Sicherheitsnetz bei Synchronisierungskonflikten.
  (Behebt nebenbei einen bislang nie aufgefallenen Bug: der zugehörige Backend-Endpunkt lieferte
  ein JSON-*Array*, der Client parste die Antwort aber immer als JSON-*Objekt* und scheiterte
  dadurch bei jedem Aufruf - die Funktion wurde bis jetzt einfach nie aufgerufen.)

## [0.10.0] - 2026-08-09

### Geändert
- Labels und markierte Streckenabschnitte im Bearbeiten-Screen werden nicht mehr sofort bei jedem
  Antippen gespeichert, sondern erst beim Verlassen der Seite (Zurück-Pfeil oder System-Zurück-
  Taste): bei ungespeicherten Änderungen fragt ein Dialog "Änderungen speichern?" nach. Nach
  "Speichern" sind die Änderungen sofort auf der Fahrt-Detailseite sichtbar – dafür liest diese
  (wie schon Fahrzeug-Bearbeiten) die aktuelle Fahrt jetzt live aus der Datenbank statt eine beim
  Öffnen eingefrorene Kopie anzuzeigen (betraf bisher auch das Zuschneiden: nach "Änderungen
  anwenden" zeigte die Detailseite kurzzeitig noch die alten Werte).
- Fahrtenliste: Labels einer Fahrt (z. B. "⛴ Fähre") erscheinen jetzt zusätzlich als kleines
  Icon-Badge direkt auf der Karten-Vorschau statt nur als Text darunter.

## [0.9.0] - 2026-08-09

### Hinzugefügt
- Markierte Streckenabschnitte (⛴ Fähre, ☕ Pause, 🌙 Nachtfahrt, eigene Labels) bekommen jetzt eine
  **eigene, feste Signalfarbe** je Typ (Fähre z. B. blau) statt einer einzelnen Türkis-Farbe für
  alle – Linie auf der Karte und der Farbpunkt in der Markierungs-Liste nutzen dieselbe Farbe.
- Jeder markierte Abschnitt zeigt jetzt **eigene Statistiken** (Distanz, Dauer, Ø- und
  Höchstgeschwindigkeit nur für diesen Abschnitt) – sichtbar sowohl in der Fahrt-Detailseite
  (neuer Abschnitt "Markierte Abschnitte" unter der Statistik) als auch im Bearbeiten-Screen.
  Neue gemeinsame Komponente `ui/components/SegmentMarkRow.kt`. Die Gesamt-Fahrt-Statistik
  (Distanz/Dauer/Ø-Geschwindigkeit) bleibt davon unberührt – nur die Höchstgeschwindigkeit der
  gesamten Fahrt schließt markierte Abschnitte weiterhin aus (seit 0.8.0).

## [0.8.0] - 2026-08-09

### Hinzugefügt
- **Fahrten bearbeiten**: neuer "Bearbeiten"-Screen (über den Stift-Button in der Fahrt-
  Detailseite), erreichbar über zwei markierbare Punkte A/B im Geschwindigkeits-Graph:
  - **Zuschneiden**: Anfang bis A abschneiden, B bis Ende abschneiden, oder A–B als Pause aus der
    Mitte entfernen. Alle geplanten Schnitte erscheinen zuerst in einer einsehbaren, einzeln
    löschbaren Änderungsliste und werden erst nach Bestätigung ("kann nicht rückgängig gemacht
    werden") tatsächlich angewendet.
  - Beim Entfernen einer Pause aus der Mitte bleibt die **Gesamtdauer** der Fahrt (echte Start-/
    Endzeit) unverändert – nur die **Fahrzeit** (und damit Ø-Geschwindigkeit) sinkt um die
    herausgeschnittene Pause. Fahrt-Detailseite zeigt bei bearbeiteten Fahrten zusätzlich "− X min
    Pause = Y Fahrzeit" an. Die "Fahrzeit"-Kachel auf Startbildschirm/Fahrzeug-Statistik nutzt jetzt
    ebenfalls die Fahrzeit statt der reinen Gesamtdauer (für unbearbeitete Fahrten identisch).
  - **Markieren**: Fahrten können Labels bekommen (z. B. "⛴ Fähre", "☕ Pause gemacht", "🌙
    Nachtfahrt" oder frei Text), sichtbar als Badges in Fahrtenliste und -Detail. Zusätzlich lässt
    sich ein Streckenabschnitt (z. B. eine Fährüberfahrt) markieren – auf der Karte gestrichelt
    hervorgehoben und automatisch aus der Höchstgeschwindigkeits-Statistik ausgeschlossen (Distanz/
    Dauer/Ø-Geschwindigkeit bleiben davon unberührt).
- Routen-Karte und Geschwindigkeits-Graph aus `TripDetailScreen.kt` in eigene, wiederverwendbare
  Komponenten ausgelagert (`RouteDetailMap.kt`, `SpeedGraphChart.kt`), damit der neue Bearbeiten-
  Screen dieselbe Karte/denselben Graphen nutzt statt sie zu duplizieren.
- Room-Datenbank auf Version 7 (additive Migration: `labels`, `pausedMinutes`,
  `segmentMarksJson` an Fahrten, bestehende Daten bleiben erhalten).

## [0.7.0] - 2026-08-06

### Hinzugefügt
- Fahrzeug-Detailseite zeigt jetzt Statistik-Kacheln wie das Home-Dashboard (Gesamt-km, Fahrten,
  Fahrzeit, Ø Geschwindigkeit), zusätzlich **Höchstgeschwindigkeit** – jeweils nur über die
  Fahrten dieses Fahrzeugs. `StatCard`/`formatDurationHm` aus `HomeScreen.kt` in eine gemeinsame
  Komponente (`ui/components/StatCard.kt`) ausgelagert, damit beide Screens dieselbe Kachel nutzen.

## [0.6.0] - 2026-08-06

### Hinzugefügt
- Beim Setzen eines Fahrzeug-Fotos lässt sich der Bildausschnitt jetzt wählen (zuschneiden/
  zoomen/schwenken, festes Seitenverhältnis 16:9 passend zum Foto-Header) statt nur das ganze
  Originalbild zu übernehmen. Neue Abhängigkeit `Android-Image-Cropper`.

### Behoben
- Die Cropper-Bibliothek ist View-basiert (nicht Compose) und verlangt zwingend ein
  Theme.AppCompat-Theme – die App selbst ist reines Compose/Material3 ohne AppCompat und stürzte
  beim Öffnen des Zuschneide-Dialogs deshalb ab. Theme-Override nur für diese eine Activity
  ergänzt (`CropperTheme` in `themes.xml` + Manifest-Override), Rest der App unverändert.

## [0.5.1] - 2026-08-06

### Behoben
- Fahrzeug-Foto ließ sich nicht hochladen: `BitmapFactory.decodeStream()` gibt beim reinen
  Abmessungen-Lesen (`inJustDecodeBounds=true`) immer `null` zurück (so gewollt, man liest danach
  `outWidth`/`outHeight`) – der Code hatte das fälschlich als Fehler behandelt und brach deshalb
  bei jedem Foto sofort ab, bevor überhaupt dekodiert wurde.

## [0.5.0] - 2026-08-06

### Hinzugefügt
- Fahrzeuge sind jetzt ein Untermenü: Antippen eines Autos in den Einstellungen öffnet eine
  eigene Fahrzeug-Detailseite mit Name, **Foto** (neu – nur lokal auf dem Gerät gespeichert,
  nicht Teil des Backups, geht bei Neuinstallation verloren), Bluetooth-Auto-Start, Standard-
  Schalter und Löschen (inkl. Foto).
- Import & Export (GPX-Import, GPX-Export, lokales Backup) ist jetzt ein eigenes Untermenü statt
  drei einzelner Karten direkt in den Einstellungen.

### Geändert
- Einstellungen komplett neu gruppiert: Konto / Fahrzeuge / Daten / Über statt 7 gleichrangiger,
  unsortierter Karten. Abschnitte haben jetzt Icons zur schnelleren Wiedererkennung. Karten-
  Boilerplate in eine gemeinsame Komponente ausgelagert (vorher 7x kopiert).
- "Fahrzeug hinzufügen"-Dialog nur noch einmal im Code (vorher fast identisch dupliziert
  zwischen Einstellungen und dem Auto-Auswähler auf Home/Karte).
- Room-Datenbank auf Version 6 (additive Migration, bestehende Daten bleiben erhalten).

## [0.4.6] - 2026-08-06

### Behoben
- Der letzte Fix (0.4.5) kappte GPS-Ausreißer auf `trip.maxSpeedKmh` – bei mehreren aufeinander-
  folgenden Ausreißer-Punkten (z. B. beim Einrasten des GPS-Fixes zu Fahrtbeginn) erzeugte das
  einen verdächtig glatten Plateau exakt auf diesem Wert statt das Problem zu beheben, und
  `trip.maxSpeedKmh` selbst kann durch dasselbe GPS-Problem verfälscht sein. Jetzt: Median-Filter
  von 5 auf 9 Punkte verbreitert (hält deutlich mehr aufeinanderfolgende Ausreißer ab, verifiziert
  per Test), Kappung nutzt eine fahrtunabhängige, für Autos generell unrealistische Grenze
  (260 km/h) statt `trip.maxSpeedKmh`.

## [0.4.5] - 2026-08-06

### Behoben
- Route-Hover/Route-Farbe konnten bei GPS-Aussetzern über mehrere aufeinanderfolgende Punkte
  (nicht nur einen einzelnen) absurd hohe Geschwindigkeiten anzeigen (z. B. "451 km/h" bei einer
  Fahrt mit tatsächlich 140 km/h Maximum) – der 5-Punkte-Median-Filter allein reicht dafür nicht
  immer aus. Zusätzliche harte Kappung auf `trip.maxSpeedKmh` (GPS-Chip-Wert, robuster) ergänzt.

## [0.4.4] - 2026-08-06

### Behoben
- Legende der Geschwindigkeits-Farbskala: "130"- und "180+ km/h"-Label überlappten sich sichtbar
  (verschmolzener/unlesbarer Text), weil der "130"-Tick bei ~72 % sitzt und der lange Endtext
  "180+ km/h" zu breit für den schmalen Legende-Kasten war. Endtext auf "180+" gekürzt, Kasten
  etwas breiter (150dp → 175dp).

## [0.4.3] - 2026-08-06

### Behoben
- Legende der Geschwindigkeits-Farbskala: der "130"-Tick saß per Row/SpaceBetween in der Mitte des
  Balkens, obwohl 130 tatsächlich bei 130/180 ≈ 72 % des Gradienten liegt – Farbe an der Tick-
  Position stimmte dadurch nicht mit dem Label überein. Jetzt an der echten Position platziert.

## [0.4.2] - 2026-08-06

### Geändert
- Geschwindigkeits-Farbskala der Route zweistufig statt einer einzelnen Rampe: 0–130 km/h weiterhin
  grün→rot (130 = Richtgeschwindigkeit Autobahn), 130–180 km/h zusätzlich rot→lila zur klaren
  Abhebung sehr hoher Geschwindigkeiten (vorher 130–250, war spürbar zu träge). Legende angepasst.

## [0.4.1] - 2026-08-06

### Geändert
- Geschwindigkeits-Farbskala der Route ist jetzt fest (0–150 km/h, grün→rot) statt relativ zur
  jeweiligen Fahrt – dieselbe Farbe bedeutet dadurch bei jeder Fahrt dieselbe Geschwindigkeit,
  vergleichbar zwischen z. B. Stadt- und Autobahnfahrten. Dazu eine Legende unten links auf der
  Karte, solange der Geschwindigkeits-Modus aktiv ist.

## [0.4.0] - 2026-08-06

### Hinzugefügt
- Routen-Linie in der Fahrt-Detail-Karte kann jetzt nach Geschwindigkeit eingefärbt werden
  (grün = langsam, rot = schnell) statt der einheitlichen orangenen Farbe – Umschalter oben rechts
  auf der Karte. Bei sehr langen Fahrten werden die Segmente auf max. 1500 heruntergesampelt, damit
  das Kartenrendering flüssig bleibt.

## [0.3.0] - 2026-08-05

### Hinzugefügt
- **Automatischer Server-Sync**: Fahrten werden nach dem Beenden automatisch gesichert (kein
  manueller "Backup sichern"-Tipp mehr nötig), während der Aufzeichnung läuft zusätzlich alle
  3 Minuten ein Zwischen-Sync als Sicherheitsnetz (falls Handy/App mittendrin ausfällt)
- Neuer Backend-Endpunkt dafür: `PUT/GET/DELETE /api/live-trip` (Backend v1.1.0)

### Geändert
- **Sicherheitsmodell**: Der Verschlüsselungs-Schlüssel (DEK) wird jetzt dauerhaft (Android
  Keystore-verschlüsselt) auf dem Gerät gespeichert statt nur im RAM – nötig, damit Auto-Sync auch
  im Hintergrund funktioniert (z. B. nach automatischem Bluetooth-Start). Das Passwort selbst wird
  weiterhin nirgends gespeichert.

## [0.2.3] - 2026-08-05

### Behoben
- Geschwindigkeits-Graph (Fahrt-Detail) wirkte durch GPS-Ausreißer unrealistisch (isolierte Nadel-
  Spitzen statt echtem Verlauf): Median-Filter (5-Punkte-Fenster) über die Geschwindigkeit ergänzt

### Hinzugefügt
- Achsenbeschriftung am Geschwindigkeits-Graph (Gitterlinien + km/h-Werte), damit sich die Skala
  ablesen lässt. Derselbe Fix wie in der Web-App v1.1.3, da beide dieselbe Berechnung nutzen.

## [0.2.2] - 2026-08-05

### Behoben
- Geschwindigkeits-Graph (Fahrt-Detail): einzelne GPS-Ausreißer (kurzer ungenauer Fix, rechnerisch
  absurd hohe Distanz/Zeit-Geschwindigkeit) stauchten die ganze Skala, sodass der Rest der Fahrt
  am unteren Rand "klebte". Skala nutzt jetzt `trip.maxSpeedKmh` (GPS-Chip, Doppler-basiert,
  robuster) statt des eigenen Segment-Maximums; einzelne Ausreißer werden beim Zeichnen oben
  gekappt. Derselbe Fix wie in der Web-App v1.1.2, da beide dieselbe Berechnung nutzen.

## [0.2.1] - 2026-08-05

Erster offiziell getaggter Release seit Einführung des Versionssystems – bündelt alles seit 0.1.0.

### Hinzugefügt
- Automatischer Aufzeichnungsstart bei Verbindung mit einem Auto zugeordneten Bluetooth-Gerät
  (z. B. Auto-Radio), inkl. "Verwerfen"-Aktion in der Notification bei Fehlstarts
- Versionsnummer wird unten in den Einstellungen angezeigt

### Geändert
- Fahrtdauer in Fahrtenliste und Detailansicht: ab über 60 Minuten als "Xh Ym" (z. B. "3h 20m")
  statt nur in Minuten

### Behoben
- Absturz bei sehr langen Fahrten (`SQLiteBlobTooBigException: Row too big to fit into
  CursorWindow`) – GPS-Tracks liegen jetzt als Datei pro Fahrt statt als Room-Spalte,
  bestehende Daten werden per Migration automatisch gerettet

## [0.1.0] - 2026-07-16

Erste funktionsfähige Version: GPS-Aufzeichnung, Fahrtenliste, Kartenansicht, Fahrzeuge/Nutzer,
GPX-Import/Export, Ende-zu-Ende-verschlüsseltes Server-Backup, Android-Auto-Unterstützung.
