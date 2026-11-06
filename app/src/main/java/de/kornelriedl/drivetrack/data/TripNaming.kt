package de.kornelriedl.drivetrack.data

import android.content.Context
import android.location.Geocoder
import java.util.Locale

/**
 * Schlägt anhand der Start-/Endkoordinaten einer Fahrt einen Namen "Fahrt von X nach Y" vor
 * (Reverse-Geocoding über Androids Geocoder - läuft über Google Play Services, kein eigener
 * API-Key/Account nötig, anders als z. B. die CARTO-Kartenkacheln in MapScreen.kt).
 *
 * Gibt null zurück, wenn kein Ortsname ermittelbar war (kein Netz, Geocoder auf diesem Gerät
 * nicht verfügbar, zu wenige GPS-Punkte, o.ä.) - der Aufrufer behält dann den bisherigen Namen
 * ("Fahrt"). Schlägt EINE der beiden Seiten fehl, wird bewusst gar nicht umbenannt statt eines
 * asymmetrischen Namens.
 *
 * Blockierend (klassische synchrone Geocoder-API - seit API 33 zugunsten einer async-Variante
 * mit Callback deprecated, hier bewusst trotzdem verwendet: funktioniert unverändert auf allen
 * unterstützten API-Leveln ab minSdk 26 und wird ohnehin nur aus Hintergrund-Coroutinen
 * aufgerufen) - NIE vom Main-Thread aufrufen.
 */
@Suppress("DEPRECATION")
fun suggestTripName(context: Context, points: List<Triple<Double, Double, Long>>): String? {
    if (points.size < 2 || !Geocoder.isPresent()) return null
    val geocoder = Geocoder(context, Locale.GERMANY)
    val (startLat, startLon) = points.first()
    val (endLat, endLon) = points.last()
    val startPlace = placeName(geocoder, startLat, startLon) ?: return null
    val endPlace = placeName(geocoder, endLat, endLon) ?: return null
    return "Fahrt von $startPlace nach $endPlace"
}

@Suppress("DEPRECATION")
private fun placeName(geocoder: Geocoder, lat: Double, lon: Double): String? {
    return try {
        val address = geocoder.getFromLocation(lat, lon, 1)?.firstOrNull() ?: return null
        address.locality ?: address.subAdminArea ?: address.adminArea
    } catch (e: Exception) {
        null // z.B. kein Netz - Aufrufer behält dann den bisherigen Namen
    }
}
