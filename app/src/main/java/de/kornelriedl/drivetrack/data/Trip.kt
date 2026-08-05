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
    val carId: Long? = null            // welches Auto gefahren wurde (optional)
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

    val distanceKm: Double
        get() = distanceMeters / 1000.0
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
