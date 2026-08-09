package de.kornelriedl.drivetrack.data

/**
 * Baut aus der flachen Fahrten-/Gruppen-Liste die Einträge für Fahrtenliste-Screens (Home/Fahrten):
 * gruppierte Fahrten erscheinen als EIN zusammengefasster Eintrag statt einzeln, ungruppierte
 * Fahrten unverändert einzeln. Sortiert nach der jeweils neuesten Fahrt absteigend (eine Gruppe
 * "zählt" für die Sortierung wie ihre neueste Mitgliedsfahrt), damit sich gruppierte und
 * ungruppierte Einträge in derselben Liste chronologisch nicht widersprechen.
 *
 * Leere Gruppen (z.B. nachdem die letzte Fahrt entfernt wurde) werden hier herausgefiltert, aber
 * NICHT automatisch gelöscht - die TripGroup-Zeile bleibt bestehen, bis der Nutzer sie explizit
 * löscht (siehe TripGroupDetailScreen).
 */
sealed class TripListEntry {
    abstract val sortTimestamp: Long

    data class SingleTrip(val trip: Trip) : TripListEntry() {
        override val sortTimestamp: Long = trip.startTimestamp
    }

    data class Group(val group: TripGroup, val trips: List<Trip>) : TripListEntry() {
        override val sortTimestamp: Long = trips.maxOf { it.startTimestamp }
    }
}

fun buildTripListEntries(trips: List<Trip>, groups: List<TripGroup>): List<TripListEntry> {
    val (grouped, ungrouped) = trips.partition { it.groupId != null }
    val tripsByGroupId = grouped.groupBy { it.groupId }

    val groupEntries = groups.mapNotNull { group ->
        val groupTrips = tripsByGroupId[group.id]
        if (groupTrips.isNullOrEmpty()) return@mapNotNull null
        TripListEntry.Group(group, groupTrips.sortedByDescending { it.startTimestamp })
    }
    val singleEntries = ungrouped.map { TripListEntry.SingleTrip(it) }

    return (groupEntries + singleEntries).sortedByDescending { it.sortTimestamp }
}

/**
 * Gesamt-Statistik über mehrere Fahrten (für eine Gruppe) - identische Aggregation wie
 * HomeScreen/CarDetailScreen sie schon für gefilterte bzw. Auto-Fahrten berechnen (Summe km, Summe
 * drivingDurationMinutes seit 0.11.0, einfacher Durchschnitt der Einzel-Ø-Geschwindigkeiten, Max
 * der Einzel-Höchstgeschwindigkeiten) - bewusst dieselbe, schon etablierte Formel statt einer neuen.
 */
data class GroupStats(
    val totalKm: Double,
    val tripCount: Int,
    val totalDrivingMinutes: Long,
    val avgSpeedKmh: Double,
    val maxSpeedKmh: Double
)

fun List<Trip>.groupStats(): GroupStats = GroupStats(
    totalKm = sumOf { it.distanceMeters } / 1000.0,
    tripCount = size,
    totalDrivingMinutes = sumOf { it.drivingDurationMinutes },
    avgSpeedKmh = if (isNotEmpty()) map { it.avgSpeedKmh }.average() else 0.0,
    maxSpeedKmh = maxOfOrNull { it.maxSpeedKmh } ?: 0.0
)
