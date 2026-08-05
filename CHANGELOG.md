# Changelog

Alle nennenswerten Änderungen an diesem Projekt werden hier dokumentiert.
Format angelehnt an [Keep a Changelog](https://keepachangelog.com/), Versionierung nach
[Semantic Versioning](https://semver.org/) (`MAJOR.MINOR.PATCH`, sichtbar als `versionName` in
`build.gradle.kts` sowie unten in den Einstellungen der App).

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
