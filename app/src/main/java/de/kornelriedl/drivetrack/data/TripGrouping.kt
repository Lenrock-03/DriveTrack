package de.kornelriedl.drivetrack.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

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

/** Volles Datum mit Wochentag - gemeinsam genutzt von der Fahrt-Detail-Überschrift
 * (TripDetailScreen.kt) und den Datums-Überschriften der Fahrtenliste (withDateHeaders(), seit
 * 0.14.0) - ein einziges gepflegtes Format statt zweier identischer SimpleDateFormat-Instanzen. */
fun formatTripDateHeading(timestampMillis: Long): String =
    SimpleDateFormat("EEEE, d. MMMM yyyy", Locale.GERMANY).format(Date(timestampMillis))

/** Zeile für die Fahrtenliste mit Datums-Überschriften (seit 0.14.0, siehe withDateHeaders()) -
 * ein DateHeader kommt vor der jeweils ersten (neuesten) Fahrt/Gruppe eines Kalendertags. */
sealed class TripListRow {
    data class DateHeader(val label: String) : TripListRow()
    data class Item(val entry: TripListEntry) : TripListRow()
}

/**
 * Fügt vor der jeweils ersten (neuesten) Fahrt/Gruppe eines Kalendertags eine DateHeader-Zeile ein -
 * bei mehreren Einträgen desselben Tages nur einmal, da die Liste bereits absteigend nach
 * sortTimestamp sortiert ist (buildTripListEntries()). Kalendertag-Vergleich über
 * Calendar.get(YEAR/DAY_OF_YEAR) in LOKALER Zeitzone (bewusst NICHT timestamp / 86_400_000L - das
 * würde bei Zeitzonen-Offsets ungleich UTC falsche Tagesgrenzen ziehen), spiegelt localDayKey() in
 * js/app.js der Web-App.
 */
fun List<TripListEntry>.withDateHeaders(): List<TripListRow> {
    val calendar = Calendar.getInstance()
    var lastYear = -1
    var lastDayOfYear = -1
    val rows = mutableListOf<TripListRow>()
    forEach { entry ->
        calendar.timeInMillis = entry.sortTimestamp
        val year = calendar.get(Calendar.YEAR)
        val dayOfYear = calendar.get(Calendar.DAY_OF_YEAR)
        if (year != lastYear || dayOfYear != lastDayOfYear) {
            rows.add(TripListRow.DateHeader(formatTripDateHeading(entry.sortTimestamp)))
            lastYear = year
            lastDayOfYear = dayOfYear
        }
        rows.add(TripListRow.Item(entry))
    }
    return rows
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
