package de.kornelriedl.drivetrack.data

/**
 * Kurze, für Endnutzer verständliche "Was ist neu"-Hinweise je Version - bewusst NICHT dasselbe
 * wie `CHANGELOG.md` im Repo-Root (das ist ein technisches Entwickler-Changelog mit Datei-/
 * Funktionsnamen, ungeeignet für den in-App-Dialog). Neueste zuerst.
 *
 * Bei jedem Release, der für den Nutzer sichtbare Änderungen bringt (nicht bei reinen internen
 * Refactorings/Bugfixes ohne wahrnehmbaren Unterschied), hier oben einen neuen Eintrag ergänzen -
 * Grundlage für `WhatsNewDialog` (ausgelöst über `ReleaseNotesPreferences`/`unseenReleaseNotes()`
 * in `MainActivity.kt`, seit 0.15.0). Diese Liste startet bewusst erst bei 0.14.0 (der ersten
 * Version NACH Einführung dieses Features) statt die komplette bisherige Historie
 * nachzupflegen - bestehende Nutzer haben ältere Änderungen ja bereits im laufenden Betrieb
 * mitbekommen.
 */
val RELEASE_NOTES: List<ReleaseNote> = listOf(
    ReleaseNote(
        version = "0.15.0",
        bullets = listOf(
            "Beim ersten Öffnen nach einem Update siehst du hier kurz, was sich geändert hat."
        )
    ),
    ReleaseNote(
        version = "0.14.0",
        bullets = listOf(
            "Über den Fahrten in der Liste steht jetzt das Datum - bei mehreren Fahrten am " +
                "selben Tag nur einmal."
        )
    ),
)

data class ReleaseNote(val version: String, val bullets: List<String>)

/**
 * Liefert die Einträge, die seit `lastSeenVersion` neu hinzugekommen sind (neueste zuerst) - die
 * Versionen, die der Nutzer beim letzten Öffnen noch nicht gesehen hat. Taucht `lastSeenVersion`
 * in der Liste nicht auf (z.B. ein sehr großer Versionssprung, oder ein Update von einer Version
 * vor Einführung dieser Liste), werden vorsichtshalber ALLE bekannten Einträge gezeigt statt gar
 * keine - der Nutzer bekommt so nie eine unvollständige Auswahl serviert.
 */
fun unseenReleaseNotes(lastSeenVersion: String): List<ReleaseNote> {
    val idx = RELEASE_NOTES.indexOfFirst { it.version == lastSeenVersion }
    return if (idx == -1) RELEASE_NOTES else RELEASE_NOTES.subList(0, idx)
}
