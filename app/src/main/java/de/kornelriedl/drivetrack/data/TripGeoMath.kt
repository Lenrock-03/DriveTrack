package de.kornelriedl.drivetrack.data

import android.content.Context
import de.kornelriedl.drivetrack.ui.screens.toTrackPoints
import org.json.JSONArray
import org.json.JSONObject

/**
 * Compose-freie Distanz-/Geschwindigkeits-Mathematik für Fahrten, gemeinsam genutzt von
 * TripDetailScreen (Anzeige) und TripEditScreen (Zuschneiden/Markieren). Ursprünglich private in
 * TripDetailScreen.kt, hierher verschoben, um Duplikation beim Bearbeiten-Feature zu vermeiden -
 * keine Verhaltensänderung an den unten stehenden, unverändert übernommenen Funktionen.
 */

/** Ein Punkt im Geschwindigkeits-Graphen. */
data class GraphPoint(
    val offsetSeconds: Float,
    val speedKmh: Float,
    val cumulativeKm: Float,
    val timestamp: Long,
    val lat: Double,
    val lon: Double
)

/** Wandelt die gespeicherten GPS-Punkte in eine Zeit/Geschwindigkeit/Distanz-Serie für den Graphen um. */
fun Trip.toSpeedSeries(context: Context): List<GraphPoint> {
    val raw = toTrackPoints(context)
    if (raw.size < 2) return emptyList()

    val startTs = raw.first().third
    var cumulativeMeters = 0.0
    val result = mutableListOf<GraphPoint>()

    for (i in raw.indices) {
        val speedKmh = if (i == 0) {
            segmentSpeedKmh(raw[0], raw[1])
        } else {
            cumulativeMeters += haversineMetersPoints(raw[i - 1], raw[i])
            segmentSpeedKmh(raw[i - 1], raw[i])
        }
        result.add(
            GraphPoint(
                offsetSeconds = ((raw[i].third - startTs) / 1000).toFloat(),
                speedKmh = speedKmh.toFloat(),
                cumulativeKm = (cumulativeMeters / 1000.0).toFloat(),
                timestamp = raw[i].third,
                lat = raw[i].first,
                lon = raw[i].second
            )
        )
    }
    return result
}

fun segmentSpeedKmh(p1: Triple<Double, Double, Long>, p2: Triple<Double, Double, Long>): Double {
    val dtSeconds = (p2.third - p1.third) / 1000.0
    if (dtSeconds <= 0) return 0.0
    return (haversineMetersPoints(p1, p2) / dtSeconds) * 3.6
}

fun haversineMetersPoints(p1: Triple<Double, Double, Long>, p2: Triple<Double, Double, Long>): Double {
    val earthRadius = 6371000.0
    val dLat = Math.toRadians(p2.first - p1.first)
    val dLon = Math.toRadians(p2.second - p1.second)
    val a = kotlin.math.sin(dLat / 2).let { it * it } +
        kotlin.math.cos(Math.toRadians(p1.first)) * kotlin.math.cos(Math.toRadians(p2.first)) *
        kotlin.math.sin(dLon / 2).let { it * it }
    val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
    return earthRadius * c
}

/**
 * Median-Filter über die Geschwindigkeit (Fenster von 9 Punkten): einzelne GPS-Ausreißer (kurzer
 * ungenauer Fix) erzeugen sonst isolierte Nadel-Spitzen statt eines realistisch wirkenden Verlaufs
 * - der Median eines Fensters ignoriert genau solche einzelnen Ausreißer, echte Beschleunigungs-/
 * Bremstrends bleiben erhalten. cumulativeKm/Position bleiben unverändert.
 * windowRadius=4 statt 2: GPS-Aussetzer (z.B. beim Einrasten des Fixes zu Fahrtbeginn oder in einer
 * Unterführung) liefern oft mehrere aufeinanderfolgende schlechte Punkte statt nur einem einzelnen
 * - ein 5-Punkte-Fenster reißt bei 3 aufeinanderfolgenden Ausreißern durch (der Ausreißer wird selbst
 * zum Median), ein 9-Punkte-Fenster hält das deutlich zuverlässiger ab (verifiziert per Test).
 */
fun List<GraphPoint>.medianFiltered(windowRadius: Int = 4): List<GraphPoint> {
    val raw = map { it.speedKmh }
    return mapIndexed { i, p ->
        val lo = (i - windowRadius).coerceAtLeast(0)
        val hi = (i + windowRadius).coerceAtMost(raw.size - 1)
        val window = raw.subList(lo, hi + 1).sorted()
        p.copy(speedKmh = window[window.size / 2])
    }
}

/**
 * Wie medianFiltered() oben, nur direkt auf eine Rohliste von Geschwindigkeitswerten (kein
 * GraphPoint nötig) - genutzt beim Neuberechnen von maxSpeedKmh nach dem Zuschneiden/Markieren
 * (siehe recomputeMaxSpeedExcludingMarks()/applyTripEditPlan() weiter unten). Ohne diesen Filter
 * würde dort ein einzelner Ausreißer (kurzer ungenauer GPS-Fix ODER die künstliche "Teleport"-
 * Geschwindigkeit an einer Schnittkante, siehe applyTripEditPlan()) ungefiltert auf
 * PLAUSIBLE_MAX_CAR_KMH gekappt und exakt als dieser Wert gespeichert - ein verdächtiges 260-km/h-
 * Plateau, obwohl derselbe Ausreißer im Geschwindigkeits-Graphen (der medianFiltered() bereits
 * anwendet) durch den Median gar nicht mehr auftaucht. Gleiche Fenstergröße wie oben, damit beide
 * Werte konsistent bleiben.
 */
fun List<Double>.medianFilteredSpeeds(windowRadius: Int = 4): List<Double> =
    mapIndexed { i, _ ->
        val lo = (i - windowRadius).coerceAtLeast(0)
        val hi = (i + windowRadius).coerceAtMost(size - 1)
        val window = subList(lo, hi + 1).sorted()
        window[window.size / 2]
    }

// Letzte Sicherheitsgrenze für die Anzeige, bewusst NICHT trip.maxSpeedKmh: dieser Wert kommt zwar
// normalerweise vom GPS-Chip direkt (Doppler-basiert, robuster als Positions-Differenzen), kann
// aber selbst durch genau dasselbe GPS-Problem verfälscht sein (z.B. beim Einrasten des Fixes zu
// Fahrtbeginn) - ein Clamp darauf würde dann einen verdächtig glatten Plateau exakt auf diesem
// (falschen) Wert erzeugen, statt das Problem sichtbar zu machen. 260 km/h ist für ein normales
// Auto ohnehin unrealistisch, greift also praktisch nie bei echten Daten, nur bei Sensor-Ausfällen,
// die selbst das breitere Median-Fenster nicht abfängt. Spiegelt PLAUSIBLE_MAX_CAR_KMH in js/app.js.
const val PLAUSIBLE_MAX_CAR_KMH = 260f

/**
 * Gemeinsame Geschwindigkeits-Serie für eine Fahrt (median-gefiltert + gekappt), damit
 * Geschwindigkeits-Graph und Routen-Farbe/-Hover exakt dieselben Werte anzeigen. Spiegelt
 * getTripSpeedSeries() in js/app.js.
 */
fun speedSeriesClamped(trip: Trip, context: Context): List<GraphPoint> {
    val filtered = trip.toSpeedSeries(context).medianFiltered()
    return filtered.map { if (it.speedKmh > PLAUSIBLE_MAX_CAR_KMH) it.copy(speedKmh = PLAUSIBLE_MAX_CAR_KMH) else it }
}

/**
 * Kombinierte Geschwindigkeits-/Distanz-Serie über mehrere Fahrten einer Gruppe (siehe
 * TripGroupDetailScreen/TripGroupRouteScreen) - Fahrten werden chronologisch (Start-Zeitpunkt)
 * aneinandergereiht, offsetSeconds/cumulativeKm laufen dabei durchgehend weiter (KEINE echten
 * Kalenderzeit-Lücken zwischen den Fahrten im Graphen, sonst würde die eigentliche Fahrzeit bei z.B.
 * mehreren Tagen Abstand zwischen zwei Fahrten unlesbar zusammengequetscht).
 *
 * Wichtig: die Geschwindigkeit wird NIEMALS über die Nahtstelle zwischen zwei Fahrten hinweg
 * berechnet - jede Fahrt bekommt ihre eigene, isolierte Punktreihe (`raw` unten ist pro Durchlauf
 * IMMER nur die Punkte EINER Fahrt), der erste Punkt jeder Fahrt startet wie in toSpeedSeries() mit
 * `segmentSpeedKmh(raw[0], raw[1])` statt mit dem letzten Punkt der vorherigen Fahrt verglichen zu
 * werden. Ohne das würde die "Geschwindigkeit" am Übergang aus der Luftlinien-Distanz zwischen dem
 * Ziel der einen und dem Start der nächsten Fahrt geteilt durch die (oft winzige oder über Tage
 * hinweg riesige) Zeit dazwischen berechnet - ein astronomischer, physikalisch bedeutungsloser
 * Ausreißer. Spiegelt exakt dieselbe "Läufe"-Idee wie applyTripEditPlan() für Pausen-Schnitte, hier
 * nur über Fahrt-Grenzen statt über Schnitt-Lücken innerhalb einer einzelnen Fahrt.
 */
fun buildGroupSpeedSeries(trips: List<Trip>, context: Context): List<GraphPoint> {
    var offsetBase = 0f
    var cumulativeBase = 0f
    val result = mutableListOf<GraphPoint>()

    trips.sortedBy { it.startTimestamp }.forEach { trip ->
        val raw = trip.toTrackPoints(context)
        if (raw.size < 2) return@forEach

        val tripStartTs = raw.first().third
        var tripCumulativeMeters = 0.0

        for (i in raw.indices) {
            val speedKmh = if (i == 0) {
                segmentSpeedKmh(raw[0], raw[1])
            } else {
                tripCumulativeMeters += haversineMetersPoints(raw[i - 1], raw[i])
                segmentSpeedKmh(raw[i - 1], raw[i])
            }
            result.add(
                GraphPoint(
                    offsetSeconds = offsetBase + ((raw[i].third - tripStartTs) / 1000f),
                    speedKmh = speedKmh.toFloat(),
                    cumulativeKm = cumulativeBase + (tripCumulativeMeters / 1000.0).toFloat(),
                    timestamp = raw[i].third,
                    lat = raw[i].first,
                    lon = raw[i].second
                )
            )
        }

        offsetBase += (raw.last().third - tripStartTs) / 1000f
        cumulativeBase += (tripCumulativeMeters / 1000.0).toFloat()
    }
    return result
}

/** Wie speedSeriesClamped(), nur für die kombinierte Serie mehrerer Fahrten (siehe buildGroupSpeedSeries). */
fun groupSpeedSeriesClamped(trips: List<Trip>, context: Context): List<GraphPoint> {
    val filtered = buildGroupSpeedSeries(trips, context).medianFiltered()
    return filtered.map { if (it.speedKmh > PLAUSIBLE_MAX_CAR_KMH) it.copy(speedKmh = PLAUSIBLE_MAX_CAR_KMH) else it }
}

// ---------------------------------------------------------------------------------------------
// Ab hier: neu für das Zuschneiden/Markieren von Fahrten (TripEditScreen).
// ---------------------------------------------------------------------------------------------

/** Ein markierter Streckenabschnitt (z.B. eine Fährüberfahrt), rein informativ/visuell. */
data class SegmentMark(val label: String, val startTs: Long, val endTs: Long)

/** Parst trip.segmentMarksJson, liefert emptyList() bei kaputtem/leerem JSON (siehe TrackFileStore.read). */
fun Trip.segmentMarks(): List<SegmentMark> = try {
    val arr = JSONArray(segmentMarksJson)
    (0 until arr.length()).map { i ->
        val obj = arr.getJSONObject(i)
        SegmentMark(obj.getString("label"), obj.getLong("startTs"), obj.getLong("endTs"))
    }
} catch (e: Exception) {
    emptyList()
}

fun List<SegmentMark>.toJson(): String {
    val arr = JSONArray()
    forEach { m ->
        arr.put(JSONObject().apply {
            put("label", m.label)
            put("startTs", m.startTs)
            put("endTs", m.endTs)
        })
    }
    return arr.toString()
}

/** Kommagetrennte Labels der Fahrt, getrimmt, leere Einträge rausgefiltert. */
fun Trip.labelList(): List<String> =
    labels?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()

/** Fügt Labels kommagetrennt zusammen; Kommas in Einzeleinträgen werden defensiv entfernt. */
fun List<String>.toLabelsString(): String =
    map { it.replace(",", "").trim() }.filter { it.isNotBlank() }.joinToString(",")

/** Simples Keyword-Icon für ein Label, genutzt in TripListItem/TripDetailScreen/TripEditScreen. */
fun labelIcon(label: String): String {
    val lower = label.lowercase()
    return when {
        "fähre" in lower || "faehre" in lower -> "⛴"
        "pause" in lower -> "☕"
        "nacht" in lower -> "🌙"
        else -> "🏷️"
    }
}

/**
 * Feste Signalfarbe je Markierungs-Typ (Keyword-Mapping wie labelIcon()), als ARGB-Int - für die
 * gestrichelte Routen-Linie auf der Karte UND als kleiner Farbpunkt in den Markierungs-Listen
 * (TripDetailScreen/TripEditScreen), damit beides erkennbar zusammengehört. Fähre bewusst blau
 * (assoziiert mit Wasser), sonst je Keyword unterschiedlich, damit mehrere Markierungstypen auf
 * derselben Fahrt optisch unterscheidbar bleiben.
 */
fun labelColor(label: String): Int {
    val lower = label.lowercase()
    return when {
        "fähre" in lower || "faehre" in lower -> android.graphics.Color.parseColor("#2979FF") // Blau
        "pause" in lower -> android.graphics.Color.parseColor("#FFB300") // Bernstein
        "nacht" in lower -> android.graphics.Color.parseColor("#7C4DFF") // Indigo
        else -> android.graphics.Color.parseColor("#26C6DA") // Türkis (Default)
    }
}

/** Distanz/Dauer/Geschwindigkeit nur innerhalb eines markierten Streckenabschnitts (z.B. Fähre). */
data class SegmentStats(
    val distanceKm: Double,
    val durationMinutes: Long,
    val avgSpeedKmh: Double,
    val maxSpeedKmh: Double
)

/**
 * Berechnet Distanz/Dauer/Ø-/Höchstgeschwindigkeit nur für die Punkte innerhalb eines markierten
 * Abschnitts - unabhängig von den Gesamt-Fahrt-Werten, die diesen Abschnitt weiterhin mit einschließen
 * (siehe applyTripEditPlan-Kommentar: nur maxSpeedKmh der GESAMTEN Fahrt schließt Markierungen aus,
 * die Fahrt selbst bleibt unverändert). Dauer kommt bewusst aus mark.startTs/endTs (nicht aus den
 * Punkten), damit sie auch bei nur 0-1 GPS-Punkten im Bereich sinnvoll bleibt.
 */
fun Trip.segmentStats(context: Context, mark: SegmentMark): SegmentStats {
    val durationMinutes = ((mark.endTs - mark.startTs) / 60000).coerceAtLeast(0)
    val inRange = toTrackPoints(context).filter { it.third in mark.startTs..mark.endTs }
    if (inRange.size < 2) return SegmentStats(0.0, durationMinutes, 0.0, 0.0)

    var distanceMeters = 0.0
    var maxSpeed = 0.0
    for (i in 1 until inRange.size) {
        distanceMeters += haversineMetersPoints(inRange[i - 1], inRange[i])
        val speed = segmentSpeedKmh(inRange[i - 1], inRange[i]).coerceAtMost(PLAUSIBLE_MAX_CAR_KMH.toDouble())
        if (speed > maxSpeed) maxSpeed = speed
    }
    val avgSpeedKmh = if (durationMinutes > 0) (distanceMeters / 1000.0) / (durationMinutes / 60.0) else 0.0
    return SegmentStats(distanceMeters / 1000.0, durationMinutes, avgSpeedKmh, maxSpeed)
}

/** Ein aus der Fahrzeit herauszuschneidender Bereich (z.B. eine Pause). */
data class PauseCut(val startTs: Long, val endTs: Long)

/** Vom TripEditScreen zusammengestellter, noch nicht angewendeter Änderungsplan. */
data class TripEditPlan(
    val trimStartTs: Long? = null,
    val trimEndTs: Long? = null,
    val pauseCuts: List<PauseCut> = emptyList()
)

/** Ergebnis von applyTripEditPlan: die aktualisierte Fahrt + der neue (gekürzte) Track-JSON-String. */
data class TripEditOutcome(val trip: Trip, val newTrackJson: String)

private fun trackPointsToJson(points: List<Triple<Double, Double, Long>>): String {
    val arr = JSONArray()
    points.forEach { (lat, lon, ts) ->
        arr.put(JSONObject().apply {
            put("lat", lat)
            put("lon", lon)
            put("ts", ts)
        })
    }
    return arr.toString()
}

/**
 * Berechnet die maximale Geschwindigkeit über die aktuellen Punkte einer Fahrt, wobei Segmente
 * innerhalb eines markierten Abschnitts (z.B. Fähre) ausgeschlossen werden - eine Fährüberfahrt ist
 * keine "Fahrt" im eigentlichen Sinn und soll die Höchstgeschwindigkeits-Statistik nicht verzerren.
 * Distanz/Dauer/Ø-Geschwindigkeit bleiben davon unberührt (siehe Plan), nur maxSpeedKmh.
 */
fun recomputeMaxSpeedExcludingMarks(trip: Trip, context: Context, marks: List<SegmentMark>): Double {
    val raw = trip.toTrackPoints(context)
    if (raw.size < 2) return trip.maxSpeedKmh

    fun inAnyMark(ts: Long) = marks.any { ts in it.startTs..it.endTs }

    // Median-gefiltert wie im Geschwindigkeits-Graphen (siehe medianFilteredSpeeds()-Kommentar) -
    // sonst kann ein einzelner Ausreißer (GPS-Glitch oder eine bereits frühere Schnittkante im
    // gespeicherten Track) hier ungefiltert auf PLAUSIBLE_MAX_CAR_KMH gekappt und gespeichert werden,
    // obwohl der Graph denselben Ausreißer längst weggefiltert hat.
    val filteredSpeeds = (1 until raw.size)
        .map { i -> segmentSpeedKmh(raw[i - 1], raw[i]) }
        .medianFilteredSpeeds()

    var max = 0.0
    for (i in 1 until raw.size) {
        if (inAnyMark(raw[i - 1].third) || inAnyMark(raw[i].third)) continue
        val speed = filteredSpeeds[i - 1].coerceAtMost(PLAUSIBLE_MAX_CAR_KMH.toDouble())
        if (speed > max) max = speed
    }
    return max
}

/**
 * Wendet einen TripEditPlan an: schneidet Anfang/Ende und/oder Pausen-Bereiche aus den gespeicherten
 * GPS-Punkten heraus und berechnet Distanz/Dauer/Geschwindigkeit neu. Gibt null zurück, wenn der Plan
 * ungültig ist oder zu wenige Punkte übrig blieben (z.B. alles rausgeschnitten).
 *
 * Wichtig: startTimestamp/endTimestamp der Fahrt ändern sich NUR durch trimStartTs/trimEndTs, nicht
 * durch pauseCuts - die Gesamtdauer (echte Uhrzeiten) bleibt beim Herausschneiden einer Pause aus der
 * Mitte unverändert, nur pausedMinutes (und damit drivingDurationMinutes/avgSpeedKmh) sinkt.
 * Akkumuliert pausedMinutes auf den bisherigen Wert der Fahrt, statt ihn zu überschreiben, damit
 * mehrere Bearbeitungs-Sessions sich korrekt aufsummieren.
 */
fun applyTripEditPlan(trip: Trip, context: Context, plan: TripEditPlan): TripEditOutcome? {
    val raw = trip.toTrackPoints(context)
    if (raw.size < 2) return null

    val newStartTs = plan.trimStartTs ?: raw.first().third
    val newEndTs = plan.trimEndTs ?: raw.last().third
    if (newStartTs >= newEndTs) return null

    val excluded = plan.pauseCuts
        .map { minOf(it.startTs, it.endTs) to maxOf(it.startTs, it.endTs) }
        .map { (s, e) -> s.coerceIn(newStartTs, newEndTs) to e.coerceIn(newStartTs, newEndTs) }
        .filter { it.first < it.second }

    val keptIndices = raw.indices.filter { i ->
        val ts = raw[i].third
        ts in newStartTs..newEndTs && excluded.none { ts in it.first..it.second }
    }
    if (keptIndices.size < 2) return null

    // In zusammenhängende Läufe gruppieren (Original-Index-Nachbarschaft), damit Distanz nie über
    // eine Schnittlücke hinweg summiert wird (sonst Distanz-Artefakt durch "Teleport"-Strecke).
    val runs = mutableListOf<MutableList<Int>>()
    keptIndices.forEach { idx ->
        val lastRun = runs.lastOrNull()
        if (lastRun != null && idx == lastRun.last() + 1) {
            lastRun.add(idx)
        } else {
            runs.add(mutableListOf(idx))
        }
    }

    var totalMeters = 0.0
    var maxSpeed = 0.0
    val marksForMaxSpeed = trip.segmentMarks()
    runs.forEach { run ->
        val runSpeeds = mutableListOf<Double>()
        val runTouchesMark = mutableListOf<Boolean>()
        for (k in 1 until run.size) {
            val p1 = raw[run[k - 1]]
            val p2 = raw[run[k]]
            totalMeters += haversineMetersPoints(p1, p2)
            runSpeeds.add(segmentSpeedKmh(p1, p2))
            runTouchesMark.add(marksForMaxSpeed.any { m -> p1.third in m.startTs..m.endTs || p2.third in m.startTs..m.endTs })
        }
        // Median-Filter je Lauf (nicht über den gesamten, ggf. lückenhaften Track hinweg - siehe
        // Läufe-Kommentar oben) - sonst würde ein einzelner Ausreißer (GPS-Glitch) hier ungefiltert
        // auf PLAUSIBLE_MAX_CAR_KMH gekappt und exakt als dieser Wert gespeichert, siehe
        // medianFilteredSpeeds()-Kommentar.
        runSpeeds.medianFilteredSpeeds().forEachIndexed { idx, speed ->
            if (!runTouchesMark[idx]) {
                val clamped = speed.coerceAtMost(PLAUSIBLE_MAX_CAR_KMH.toDouble())
                if (clamped > maxSpeed) maxSpeed = clamped
            }
        }
    }

    val keptPoints = keptIndices.map { raw[it] }
    val newTrackJson = trackPointsToJson(keptPoints)

    val newDurationMinutes = (newEndTs - newStartTs) / 60000
    val cutMinutes = excluded.sumOf { (s, e) -> (e - s) / 60000 }
    val newPausedMinutes = trip.pausedMinutes + cutMinutes
    val drivingMinutes = (newDurationMinutes - newPausedMinutes).coerceAtLeast(0)
    val avgSpeedKmh = if (drivingMinutes > 0) (totalMeters / 1000.0) / (drivingMinutes / 60.0) else 0.0

    val newMarks = trip.segmentMarks().mapNotNull { m ->
        val s = m.startTs.coerceIn(newStartTs, newEndTs)
        val e = m.endTs.coerceIn(newStartTs, newEndTs)
        when {
            s >= e -> null // komplett außerhalb der neuen Grenzen
            excluded.any { s >= it.first && e <= it.second } -> null // komplett in einer rausgeschnittenen Pause
            else -> m.copy(startTs = s, endTs = e)
        }
    }

    val updatedTrip = trip.copy(
        startTimestamp = newStartTs,
        endTimestamp = newEndTs,
        distanceMeters = totalMeters,
        avgSpeedKmh = avgSpeedKmh,
        maxSpeedKmh = if (maxSpeed > 0) maxSpeed else trip.maxSpeedKmh,
        pausedMinutes = newPausedMinutes,
        segmentMarksJson = newMarks.toJson()
    )
    return TripEditOutcome(updatedTrip, newTrackJson)
}
