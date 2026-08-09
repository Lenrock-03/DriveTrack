package de.kornelriedl.drivetrack.data

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey

@Entity(tableName = "trips")
data class Trip(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,                 // z.B. "Fahrt nach München" (später editierbar)
    val startTimestamp: Long,
    val endTimestamp: Long,
    val distanceMeters: Double,
    val avgSpeedKmh: Double,
    val maxSpeedKmh: Double,
    val carId: Long? = null,           // welches Auto gefahren wurde (optional)
    // Kommagetrennte Freitext-Tags (z.B. "Fähre,Pause gemacht"), siehe Trip.labelList()/
    // List<String>.toLabelsString() in TripGeoMath.kt. Kommas in Einzeleinträgen werden dort entfernt.
    val labels: String? = null,
    // Summe der über TripEditScreen aus der Fahrzeit herausgeschnittenen Pausen-Minuten, akkumuliert
    // über mehrere Bearbeitungs-Sessions. durationMinutes (Gesamtdauer) bleibt davon unberührt -
    // drivingDurationMinutes (Fahrzeit) unten zieht sie ab.
    val pausedMinutes: Long = 0,
    // Kleines JSON-Array markierter Streckenabschnitte (z.B. Fährüberfahrt), siehe
    // Trip.segmentMarks()/SegmentMark in TripGeoMath.kt. Bewusst normale TEXT-Spalte (nicht wie
    // gpxTrackJson als Datei) - nur wenige kurze Einträge pro Fahrt, kein CursorWindow-Risiko.
    val segmentMarksJson: String = "[]",
    // Manuell erstellte Gruppe mehrerer Fahrten (z.B. "Urlaub Kroatien"), siehe TripGroup.kt -
    // spiegelt exakt das carId-Muster: ein Trip gehört zu höchstens einer Gruppe.
    val groupId: Long? = null
) {
    // GPS-Punkte (lat,lon,timestamp) als JSON-Array – bewusst KEINE Room-Spalte (daher außerhalb
    // des Konstruktors, @Ignore): einzelne Fahrten (z.B. ganztägig, viele tausend Punkte) sprengen
    // sonst das ca. 2 MB CursorWindow-Limit von Android beim Laden der Fahrtenliste
    // (SQLiteBlobTooBigException). Stattdessen als Datei pro Fahrt gespeichert, siehe
    // TrackFileStore. Muss nach dem Insert separat über TrackFileStore.write() persistiert und
    // bei Bedarf über TrackFileStore.read() geladen werden (Default "" = "noch nicht geladen").
    @Ignore
    var gpxTrackJson: String = ""

    val durationMinutes: Long
        get() = (endTimestamp - startTimestamp) / 60000

    /** "Fahrzeit": Gesamtdauer minus über TripEditScreen herausgeschnittene Pausen. */
    val drivingDurationMinutes: Long
        get() = (durationMinutes - pausedMinutes).coerceAtLeast(0)

    val distanceKm: Double
        get() = distanceMeters / 1000.0

    /** "42 min" bis 60 Minuten, danach "Xh Ym" (z. B. "3h 20m") für bessere Lesbarkeit langer Fahrten. */
    val durationFormatted: String
        get() {
            val minutes = durationMinutes
            return if (minutes > 60) {
                "${minutes / 60}h ${minutes % 60}m"
            } else {
                "$minutes min"
            }
        }
}

/**
 * Wandelt das Ergebnis einer Aufzeichnung (LocationTracker) in ein speicherbares Trip-Objekt um.
 * Die GPS-Punkte werden als einfacher JSON-Array-String serialisiert (lat,lon,timestamp).
 */
fun de.kornelriedl.drivetrack.tracking.RecordingResult.toTrip(name: String = "Fahrt", carId: Long? = null): Trip {
    val pointsJson = points.joinToString(prefix = "[", postfix = "]") { (lat, lon, ts) ->
        "{\"lat\":$lat,\"lon\":$lon,\"ts\":$ts}"
    }
    return Trip(
        name = name,
        startTimestamp = startTimestamp,
        endTimestamp = endTimestamp,
        distanceMeters = distanceMeters,
        avgSpeedKmh = avgSpeedKmh,
        maxSpeedKmh = maxSpeedKmh,
        carId = carId
    ).apply { gpxTrackJson = pointsJson }
}
