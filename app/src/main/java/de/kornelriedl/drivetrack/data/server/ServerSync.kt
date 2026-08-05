package de.kornelriedl.drivetrack.data.server

import android.content.Context
import de.kornelriedl.drivetrack.data.Car
import de.kornelriedl.drivetrack.data.UserProfile
import de.kornelriedl.drivetrack.data.Trip
import de.kornelriedl.drivetrack.export.BackupExporter
import org.json.JSONObject

/**
 * Zentrale Stelle für automatisches (Hintergrund-)Sync mit dem Server - im Unterschied zum
 * manuellen "Backup sichern"-Button in ServerBackupScreen läuft hier alles "best effort": Ist der
 * Nutzer nicht eingeloggt/entsperrt oder schlägt der Request fehl (kein Netz o.ä.), wird einfach
 * nichts gemacht - keine Fehlermeldung, kein Absturz, nächster Versuch beim nächsten Anlass.
 */
object ServerSync {

    /**
     * Voller Sync (alle Fahrten), z. B. direkt nachdem eine Aufzeichnung lokal gespeichert wurde.
     * Löscht anschließend den Live-Zwischenstand auf dem Server, falls einer existiert - die
     * gerade beendete Fahrt ist jetzt Teil dieses vollständigen Backups.
     */
    fun syncFullBackupIfPossible(context: Context, users: List<UserProfile>, cars: List<Car>, trips: List<Trip>) {
        val token = ServerAuthPreferences.getToken(context) ?: return
        val dek = ServerSession.dek ?: return
        try {
            val json = BackupExporter.buildBackupJson(context, users, cars, trips)
            val blob = ServerCrypto.encryptWithDek(json, dek)
            ServerApi.uploadBackup(token, blob.ciphertextBase64, blob.ivBase64)
            ServerApi.deleteLiveTrip(token)
        } catch (e: Exception) {
            // Best effort - der manuelle "Backup sichern"-Button bleibt als Fallback verfügbar
        }
    }

    /** Löscht nur den Live-Zwischenstand, z. B. wenn eine Aufzeichnung verworfen statt gespeichert wurde. */
    fun deleteLiveTripIfPossible(context: Context) {
        val token = ServerAuthPreferences.getToken(context) ?: return
        try {
            ServerApi.deleteLiveTrip(token)
        } catch (e: Exception) {
            // Best effort
        }
    }

    /**
     * Unveränderliche Momentaufnahme einer laufenden Aufzeichnung für den Live-Sync. Bewusst ein
     * eigener, simpler Datentyp statt direkt LocationTracker durchzureichen: LocationTracker.points
     * wird vom GPS-Callback auf dem Main-Thread verändert - der Snapshot muss deshalb ebenfalls auf
     * dem Main-Thread gezogen werden (siehe TripTrackingService), der eigentliche Upload läuft
     * dann sicher auf einem Hintergrund-Thread.
     */
    data class LiveTripSnapshot(
        val startTimeMillis: Long,
        val distanceMeters: Double,
        val maxSpeedKmh: Double,
        val points: List<Triple<Double, Double, Long>>
    )

    /**
     * Periodischer Zwischenstand WÄHREND einer laufenden Aufzeichnung - Sicherheitsnetz, falls
     * Handy/App mittendrin ausfällt. Bewusst ein eigenes, leichtgewichtiges JSON (nur die eine
     * Fahrt, kein Nutzer/Auto-Kontext nötig) statt des vollen Backup-Formats.
     */
    fun syncLiveTripIfPossible(context: Context, snapshot: LiveTripSnapshot, carId: Long?) {
        val token = ServerAuthPreferences.getToken(context) ?: return
        val dek = ServerSession.dek ?: return
        try {
            val pointsJson = snapshot.points.joinToString(prefix = "[", postfix = "]") { (lat, lon, ts) ->
                "{\"lat\":$lat,\"lon\":$lon,\"ts\":$ts}"
            }
            val json = JSONObject().apply {
                put("version", 1)
                put("startTimestamp", snapshot.startTimeMillis)
                put("updatedAt", System.currentTimeMillis())
                put("carId", carId ?: JSONObject.NULL)
                put("distanceMeters", snapshot.distanceMeters)
                put("maxSpeedKmh", snapshot.maxSpeedKmh)
                put("gpxTrackJson", pointsJson)
            }.toString()
            val blob = ServerCrypto.encryptWithDek(json, dek)
            ServerApi.uploadLiveTrip(token, blob.ciphertextBase64, blob.ivBase64)
        } catch (e: Exception) {
            // Best effort - beim nächsten Intervall wird es einfach erneut versucht
        }
    }
}
