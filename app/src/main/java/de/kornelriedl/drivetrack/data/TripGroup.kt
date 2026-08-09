package de.kornelriedl.drivetrack.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Eine manuell erstellte Gruppe mehrerer Fahrten (z.B. "Urlaub Kroatien"), siehe
 * `data/TripGrouping.kt` für die Gesamt-Statistik-Berechnung. Ein Trip gehört zu höchstens einer
 * Gruppe (`Trip.groupId`, spiegelt das bestehende `carId`-Muster) - keine Mehrfachzuordnung.
 */
@Entity(tableName = "trip_groups")
data class TripGroup(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String
)
