package de.kornelriedl.drivetrack.data

import androidx.room.Entity
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
    val gpxTrackJson: String,          // GPS-Punkte serialisiert (lat,lon,timestamp)
    val carId: Long? = null            // welches Auto gefahren wurde (optional)
) {
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
        gpxTrackJson = pointsJson,
        carId = carId
    )
}
