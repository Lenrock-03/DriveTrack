package de.kornelriedl.drivetrack.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import de.kornelriedl.drivetrack.data.Car
import de.kornelriedl.drivetrack.data.Trip
import de.kornelriedl.drivetrack.data.TripGroup
import de.kornelriedl.drivetrack.data.UserProfile
import de.kornelriedl.drivetrack.data.local.AppDatabase
import de.kornelriedl.drivetrack.data.local.TrackFileStore
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Ergebnis eines Backup-Imports – für eine aussagekräftige Rückmeldung an den Nutzer. */
data class ImportSummary(
    val success: Boolean,
    val usersImported: Int = 0,
    val usersSkipped: Int = 0,
    val carsImported: Int = 0,
    val carsSkipped: Int = 0,
    val tripsImported: Int = 0,
    val tripsSkipped: Int = 0,
    // Nur von restoreFromJson() befüllt: Anzahl der Fahrten, die auf den Stand der wiederhergestellten
    // Version zurückgesetzt wurden (statt neu angelegt), siehe dortiger Doc-Kommentar.
    val tripsRestored: Int = 0
)

/**
 * Vollständiges Backup (im Gegensatz zum reinen GPX-Export): enthält Nutzer, Autos UND
 * Fahrten inkl. ihrer Zuordnung zueinander, als JSON. Kann komplett wieder importiert werden.
 */
object BackupExporter {

    private const val BACKUP_VERSION = 1

    fun buildBackupJson(
        context: Context,
        users: List<UserProfile>,
        cars: List<Car>,
        trips: List<Trip>,
        groups: List<TripGroup> = emptyList()
    ): String {
        val root = JSONObject()
        root.put("version", BACKUP_VERSION)

        val usersArray = JSONArray()
        users.forEach { u ->
            usersArray.put(JSONObject().apply {
                put("id", u.id)
                put("name", u.name)
            })
        }
        root.put("users", usersArray)

        val carsArray = JSONArray()
        cars.forEach { c ->
            carsArray.put(JSONObject().apply {
                put("id", c.id)
                put("name", c.name)
            })
        }
        root.put("cars", carsArray)

        // Manuell erstellte Fahrten-Gruppen (z.B. "Urlaub Kroatien") - additiv, alte Backups ohne
        // dieses Feld importieren weiterhin fehlerfrei (siehe optJSONArray unten).
        val groupsArray = JSONArray()
        groups.forEach { g ->
            groupsArray.put(JSONObject().apply {
                put("id", g.id)
                put("name", g.name)
            })
        }
        root.put("groups", groupsArray)

        val tripsArray = JSONArray()
        trips.forEach { t ->
            tripsArray.put(JSONObject().apply {
                put("name", t.name)
                put("startTimestamp", t.startTimestamp)
                put("endTimestamp", t.endTimestamp)
                put("distanceMeters", t.distanceMeters)
                put("avgSpeedKmh", t.avgSpeedKmh)
                put("maxSpeedKmh", t.maxSpeedKmh)
                put("gpxTrackJson", TrackFileStore.read(context, t.id))
                // Autonamen statt nur der ID mitschreiben, damit die Zuordnung Fahrt→Auto
                // auch beim Import in eine andere/leere Datenbank robust nachvollzogen werden kann.
                put("carId", t.carId ?: JSONObject.NULL)
                // Zuschneiden/Markieren (TripEditScreen) - additive Felder, alte Backups ohne sie
                // importieren weiterhin fehlerfrei (siehe defensive optLong/optString/isNull-Lesung
                // unten in importBackupFromJson).
                put("labels", t.labels ?: JSONObject.NULL)
                put("pausedMinutes", t.pausedMinutes)
                put("segmentMarksJson", t.segmentMarksJson)
                put("groupId", t.groupId ?: JSONObject.NULL)
            })
        }
        root.put("trips", tripsArray)

        return root.toString()
    }

    /** Schreibt das Backup ins Cache-Verzeichnis und öffnet den Android-Share-Dialog. */
    fun shareBackup(
        context: Context,
        users: List<UserProfile>,
        cars: List<Car>,
        trips: List<Trip>,
        groups: List<TripGroup> = emptyList()
    ) {
        val gpxDir = File(context.cacheDir, "gpx").apply { mkdirs() }
        val file = File(gpxDir, "DriveTrack_Backup_${System.currentTimeMillis()}.json")
        file.writeText(buildBackupJson(context, users, cars, trips, groups))

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Backup teilen"))
    }

    /**
     * Liest ein zuvor exportiertes Backup (als Datei-URI) ein.
     */
    suspend fun importBackup(context: Context, uri: Uri): ImportSummary {
        val text = context.contentResolver.openInputStream(uri)
            ?.bufferedReader()?.readText() ?: return ImportSummary(success = false)
        return importBackupFromJson(context, text)
    }

    /**
     * Eigentliche Import-Logik, unabhängig von der Quelle (lokale Datei ODER vom Server
     * heruntergeladenes + bereits entschlüsseltes Backup). Nutzer/Autos werden anhand des
     * Namens abgeglichen – existiert bereits einer mit demselben Namen, wird kein Duplikat
     * angelegt, sondern die bestehende ID weiterverwendet (damit auch die Fahrt→Auto-Zuordnung
     * korrekt bleibt). Fahrten werden anhand von Start-/Endzeitpunkt abgeglichen und bei exakter
     * Übereinstimmung übersprungen (= dieselbe Aufzeichnung).
     */
    suspend fun importBackupFromJson(context: Context, jsonText: String): ImportSummary {
        val root = try {
            JSONObject(jsonText)
        } catch (e: Exception) {
            return ImportSummary(success = false)
        }

        val db = AppDatabase.getInstance(context)
        val userDao = db.userDao()
        val carDao = db.carDao()
        val tripDao = db.tripDao()
        val groupDao = db.tripGroupDao()

        val existingUsers = userDao.getAllUsers().first()
        val existingCars = carDao.getAllCars().first()
        val existingGroups = groupDao.getAllGroups().first()
        val existingTrips = tripDao.getAllTrips().first()

        // Nutzer: nach Name abgleichen
        val userIdMap = mutableMapOf<Long, Long>()
        var usersImported = 0
        var usersSkipped = 0
        val usersArray = root.optJSONArray("users") ?: JSONArray()
        for (i in 0 until usersArray.length()) {
            val obj = usersArray.getJSONObject(i)
            val name = obj.getString("name")
            val oldId = obj.getLong("id")
            val existing = existingUsers.find { it.name.equals(name, ignoreCase = true) }
            if (existing != null) {
                userIdMap[oldId] = existing.id
                usersSkipped++
            } else {
                val newId = userDao.insertUser(UserProfile(name = name))
                userIdMap[oldId] = newId
                usersImported++
            }
        }

        // Autos: nach Name abgleichen (entscheidend für eine korrekte Fahrt→Auto-Zuordnung)
        val carIdMap = mutableMapOf<Long, Long>()
        var carsImported = 0
        var carsSkipped = 0
        val carsArray = root.optJSONArray("cars") ?: JSONArray()
        for (i in 0 until carsArray.length()) {
            val obj = carsArray.getJSONObject(i)
            val name = obj.getString("name")
            val oldId = obj.getLong("id")
            val existing = existingCars.find { it.name.equals(name, ignoreCase = true) }
            if (existing != null) {
                carIdMap[oldId] = existing.id
                carsSkipped++
            } else {
                val newId = carDao.insertCar(Car(name = name))
                carIdMap[oldId] = newId
                carsImported++
            }
        }

        // Fahrten-Gruppen: nach Name abgleichen (entscheidend für eine korrekte Fahrt→Gruppe-Zuordnung).
        // optJSONArray statt getJSONArray - alte Backups (vor diesem Feature) haben kein "groups"-Feld.
        val groupIdMap = mutableMapOf<Long, Long>()
        val groupsArray = root.optJSONArray("groups") ?: JSONArray()
        for (i in 0 until groupsArray.length()) {
            val obj = groupsArray.getJSONObject(i)
            val name = obj.getString("name")
            val oldId = obj.getLong("id")
            val existing = existingGroups.find { it.name.equals(name, ignoreCase = true) }
            if (existing != null) {
                groupIdMap[oldId] = existing.id
            } else {
                val newId = groupDao.insertGroup(TripGroup(name = name))
                groupIdMap[oldId] = newId
            }
        }

        // Fahrten: Start-/Endzeitpunkt identisch = dieselbe Aufzeichnung -> überspringen
        var tripsImported = 0
        var tripsSkipped = 0
        val tripsArray = root.optJSONArray("trips") ?: JSONArray()
        for (i in 0 until tripsArray.length()) {
            val obj = tripsArray.getJSONObject(i)
            val startTimestamp = obj.getLong("startTimestamp")
            val endTimestamp = obj.getLong("endTimestamp")

            val isDuplicate = existingTrips.any {
                it.startTimestamp == startTimestamp && it.endTimestamp == endTimestamp
            }
            if (isDuplicate) {
                tripsSkipped++
                continue
            }

            val oldCarId = if (obj.isNull("carId")) null else obj.getLong("carId")
            val newCarId = oldCarId?.let { carIdMap[it] }
            val oldGroupId = if (obj.has("groupId") && !obj.isNull("groupId")) obj.getLong("groupId") else null
            val newGroupId = oldGroupId?.let { groupIdMap[it] }
            val gpxTrackJson = obj.getString("gpxTrackJson")

            val newTripId = tripDao.insertTrip(
                Trip(
                    name = obj.getString("name"),
                    startTimestamp = startTimestamp,
                    endTimestamp = endTimestamp,
                    distanceMeters = obj.getDouble("distanceMeters"),
                    avgSpeedKmh = obj.getDouble("avgSpeedKmh"),
                    maxSpeedKmh = obj.getDouble("maxSpeedKmh"),
                    carId = newCarId,
                    // Defensiv gelesen (optLong/optString/isNull) - Backups von vor diesem Feature
                    // (v0.8.0) haben diese Schlüssel noch nicht, sollen aber trotzdem importierbar bleiben.
                    labels = if (obj.has("labels") && !obj.isNull("labels")) obj.getString("labels") else null,
                    pausedMinutes = obj.optLong("pausedMinutes", 0),
                    segmentMarksJson = obj.optString("segmentMarksJson", "[]"),
                    groupId = newGroupId
                )
            )
            TrackFileStore.write(context, newTripId, gpxTrackJson)
            tripsImported++
        }

        return ImportSummary(
            success = true,
            usersImported = usersImported,
            usersSkipped = usersSkipped,
            carsImported = carsImported,
            carsSkipped = carsSkipped,
            tripsImported = tripsImported,
            tripsSkipped = tripsSkipped
        )
    }

    /**
     * Wie importBackupFromJson(), aber für den "Versionsverlauf"-Wiederherstellen-Fall gedacht:
     * Fahrten, die anhand von Start-/Endzeitpunkt bereits lokal existieren, werden NICHT
     * übersprungen, sondern auf den Stand der gewählten (i.d.R. älteren) Version zurückgesetzt
     * (überschreibt Name/Distanz/Geschwindigkeiten/Auto/Labels/Markierungen/GPS-Track). Das ist der
     * eigentliche "Konflikt-Sicherheitsnetz"-Anwendungsfall: eine fehlerhafte Bearbeitung (egal ob
     * von der App oder der Web-Ansicht) auf eine bekannt saubere Server-Version zurückdrehen -
     * importBackupFromJson() allein würde die fehlerhafte lokale Fahrt als "Duplikat" überspringen
     * und nichts reparieren. Nutzer/Autos bleiben bewusst additiv (nur ergänzen, nie überschreiben) -
     * unwahrscheinlich, dass die je in Konflikt geraten, und ein Namens-Overwrite dort wäre riskanter
     * als nützlich.
     */
    suspend fun restoreFromJson(context: Context, jsonText: String): ImportSummary {
        val root = try {
            JSONObject(jsonText)
        } catch (e: Exception) {
            return ImportSummary(success = false)
        }

        val db = AppDatabase.getInstance(context)
        val userDao = db.userDao()
        val carDao = db.carDao()
        val tripDao = db.tripDao()
        val groupDao = db.tripGroupDao()

        val existingUsers = userDao.getAllUsers().first()
        val existingCars = carDao.getAllCars().first()
        val existingGroups = groupDao.getAllGroups().first()
        val existingTrips = tripDao.getAllTrips().first()

        val userIdMap = mutableMapOf<Long, Long>()
        var usersImported = 0
        var usersSkipped = 0
        val usersArray = root.optJSONArray("users") ?: JSONArray()
        for (i in 0 until usersArray.length()) {
            val obj = usersArray.getJSONObject(i)
            val name = obj.getString("name")
            val oldId = obj.getLong("id")
            val existing = existingUsers.find { it.name.equals(name, ignoreCase = true) }
            if (existing != null) {
                userIdMap[oldId] = existing.id
                usersSkipped++
            } else {
                val newId = userDao.insertUser(UserProfile(name = name))
                userIdMap[oldId] = newId
                usersImported++
            }
        }

        val carIdMap = mutableMapOf<Long, Long>()
        var carsImported = 0
        var carsSkipped = 0
        val carsArray = root.optJSONArray("cars") ?: JSONArray()
        for (i in 0 until carsArray.length()) {
            val obj = carsArray.getJSONObject(i)
            val name = obj.getString("name")
            val oldId = obj.getLong("id")
            val existing = existingCars.find { it.name.equals(name, ignoreCase = true) }
            if (existing != null) {
                carIdMap[oldId] = existing.id
                carsSkipped++
            } else {
                val newId = carDao.insertCar(Car(name = name))
                carIdMap[oldId] = newId
                carsImported++
            }
        }

        val groupIdMap = mutableMapOf<Long, Long>()
        val groupsArray = root.optJSONArray("groups") ?: JSONArray()
        for (i in 0 until groupsArray.length()) {
            val obj = groupsArray.getJSONObject(i)
            val name = obj.getString("name")
            val oldId = obj.getLong("id")
            val existing = existingGroups.find { it.name.equals(name, ignoreCase = true) }
            if (existing != null) {
                groupIdMap[oldId] = existing.id
            } else {
                val newId = groupDao.insertGroup(TripGroup(name = name))
                groupIdMap[oldId] = newId
            }
        }

        var tripsImported = 0
        var tripsRestored = 0
        val tripsArray = root.optJSONArray("trips") ?: JSONArray()
        for (i in 0 until tripsArray.length()) {
            val obj = tripsArray.getJSONObject(i)
            val startTimestamp = obj.getLong("startTimestamp")
            val endTimestamp = obj.getLong("endTimestamp")
            val oldCarId = if (obj.isNull("carId")) null else obj.getLong("carId")
            val newCarId = oldCarId?.let { carIdMap[it] }
            val oldGroupId = if (obj.has("groupId") && !obj.isNull("groupId")) obj.getLong("groupId") else null
            val newGroupId = oldGroupId?.let { groupIdMap[it] }
            val gpxTrackJson = obj.getString("gpxTrackJson")
            val labels = if (obj.has("labels") && !obj.isNull("labels")) obj.getString("labels") else null
            val pausedMinutes = obj.optLong("pausedMinutes", 0)
            val segmentMarksJson = obj.optString("segmentMarksJson", "[]")

            val existingTrip = existingTrips.find {
                it.startTimestamp == startTimestamp && it.endTimestamp == endTimestamp
            }
            if (existingTrip != null) {
                tripDao.updateTrip(
                    existingTrip.copy(
                        name = obj.getString("name"),
                        distanceMeters = obj.getDouble("distanceMeters"),
                        avgSpeedKmh = obj.getDouble("avgSpeedKmh"),
                        maxSpeedKmh = obj.getDouble("maxSpeedKmh"),
                        carId = newCarId,
                        labels = labels,
                        pausedMinutes = pausedMinutes,
                        segmentMarksJson = segmentMarksJson,
                        groupId = newGroupId
                    )
                )
                TrackFileStore.write(context, existingTrip.id, gpxTrackJson)
                tripsRestored++
            } else {
                val newTripId = tripDao.insertTrip(
                    Trip(
                        name = obj.getString("name"),
                        startTimestamp = startTimestamp,
                        endTimestamp = endTimestamp,
                        distanceMeters = obj.getDouble("distanceMeters"),
                        avgSpeedKmh = obj.getDouble("avgSpeedKmh"),
                        maxSpeedKmh = obj.getDouble("maxSpeedKmh"),
                        carId = newCarId,
                        labels = labels,
                        pausedMinutes = pausedMinutes,
                        segmentMarksJson = segmentMarksJson,
                        groupId = newGroupId
                    )
                )
                TrackFileStore.write(context, newTripId, gpxTrackJson)
                tripsImported++
            }
        }

        return ImportSummary(
            success = true,
            usersImported = usersImported,
            usersSkipped = usersSkipped,
            carsImported = carsImported,
            carsSkipped = carsSkipped,
            tripsImported = tripsImported,
            tripsRestored = tripsRestored
        )
    }
}
