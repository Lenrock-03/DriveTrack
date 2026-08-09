package de.kornelriedl.drivetrack.data.server

import android.content.Context
import de.kornelriedl.drivetrack.data.Car
import de.kornelriedl.drivetrack.data.UserProfile
import de.kornelriedl.drivetrack.data.Trip
import de.kornelriedl.drivetrack.data.local.AppDatabase
import de.kornelriedl.drivetrack.export.BackupExporter
import kotlinx.coroutines.flow.first
import org.json.JSONObject

/**
 * Zentrale Stelle für automatisches (Hintergrund-)Sync mit dem Server - im Unterschied zum
 * manuellen "Backup sichern"-Button in ServerBackupScreen läuft hier alles "best effort": Ist der
 * Nutzer nicht eingeloggt/entsperrt oder schlägt der Request fehl (kein Netz o.ä.), wird einfach
 * nichts gemacht - keine Fehlermeldung, kein Absturz, nächster Versuch beim nächsten Anlass.
 */
object ServerSync {

    /**
     * Voller Sync (alle Fahrten) - PULL-CHECK-MERGE-PUSH statt eines blinden Push: seit die
     * Web-App (v0.11.0) ebenfalls Backups hochladen kann, würde ein reiner Push sonst
     * stillschweigend jede Web-Bearbeitung überschreiben, die dieses Gerät noch nicht kennt.
     *
     * 1. Aktuellste Server-Version abrufen (`GET /api/backup`, liefert auch deren `id`).
     * 2. Keine Version vorhanden ODER deren `id` == der zuletzt von diesem Gerät gesehenen `id`
     *    (`ServerAuthPreferences.getLastKnownBackupId`) → kein Konflikt, wie bisher pushen.
     * 3. `id` weicht ab → ein anderes Gerät hat inzwischen gepusht: Server-Version entschlüsseln
     *    und additiv in die lokale DB mergen (dieselbe, bereits etablierte Merge-Logik wie beim
     *    manuellen Wiederherstellen, `BackupExporter.importBackupFromJson()` - dedupliziert Trips
     *    über Start-/Endzeitpunkt, Users/Cars über Namen, überschreibt nie, löscht nie), danach
     *    den so gemergten (jetzt vollständigen) Gesamtstand pushen. Die überholte Server-Version
     *    bleibt dabei unangetastet in der Backup-Historie erhalten (`POST /api/backup` fügt immer
     *    nur eine neue Zeile hinzu) - das ist die eigentliche "Sicherung ohne Bearbeitungen, die
     *    bei Konflikten greift": nichts geht je verloren, nur automatisch gemergt statt überschrieben.
     *
     * `users`/`cars`/`trips` werden bei einem Merge NICHT verwendet (könnten durch den Merge
     * veraltet sein) - stattdessen wird danach frisch aus der DB gelesen. Ohne Konflikt werden sie
     * unverändert wie bisher direkt verwendet (spart einen unnötigen Extra-Read im Normalfall).
     */
    suspend fun syncFullBackupIfPossible(context: Context, users: List<UserProfile>, cars: List<Car>, trips: List<Trip>) {
        val token = ServerAuthPreferences.getToken(context) ?: return
        val dek = ServerSession.dek ?: return
        try {
            var finalUsers = users
            var finalCars = cars
            var finalTrips = trips

            val latest = ServerApi.downloadLatestBackup(token)
            if (latest.success && latest.json != null) {
                val remoteId = latest.json.optLong("id", -1L)
                val lastKnownId = ServerAuthPreferences.getLastKnownBackupId(context)
                if (remoteId != -1L && remoteId != lastKnownId) {
                    // Konflikt: ein anderes Gerät hat seitdem gepusht - erst mergen, dann weiter unten pushen.
                    val blob = ServerCrypto.EncryptedBlob(
                        ciphertextBase64 = latest.json.getString("ciphertext"),
                        ivBase64 = latest.json.getString("iv")
                    )
                    val remoteJson = ServerCrypto.decryptWithDek(blob, dek)
                    BackupExporter.importBackupFromJson(context, remoteJson)

                    val db = AppDatabase.getInstance(context)
                    finalUsers = db.userDao().getAllUsers().first()
                    finalCars = db.carDao().getAllCars().first()
                    finalTrips = db.tripDao().getAllTrips().first()
                }
            }

            val json = BackupExporter.buildBackupJson(context, finalUsers, finalCars, finalTrips)
            val blob = ServerCrypto.encryptWithDek(json, dek)
            val uploadResult = ServerApi.uploadBackup(token, blob.ciphertextBase64, blob.ivBase64)
            if (uploadResult.success) {
                val newId = uploadResult.json?.optLong("id", -1L) ?: -1L
                if (newId != -1L) ServerAuthPreferences.setLastKnownBackupId(context, newId)
            }
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
