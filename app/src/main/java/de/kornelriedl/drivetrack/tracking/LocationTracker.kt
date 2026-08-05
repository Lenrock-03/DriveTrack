package de.kornelriedl.drivetrack.tracking

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Ergebnis einer abgeschlossenen Aufzeichnung – wird nach stop() zurückgegeben
 * und kann direkt in ein Trip-Objekt für die Datenbank umgewandelt werden.
 */
data class RecordingResult(
    val startTimestamp: Long,
    val endTimestamp: Long,
    val distanceMeters: Double,
    val avgSpeedKmh: Double,
    val maxSpeedKmh: Double,
    val points: List<Triple<Double, Double, Long>> // lat, lon, timestamp
)

class LocationTracker(context: Context) {
    private val appContext = context.applicationContext
    private val fusedClient = LocationServices.getFusedLocationProviderClient(appContext)
    // Eigener Scope: läuft unabhängig davon, ob Handy-UI oder Android-Auto-Screen gerade aktiv ist
    private val trackerScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    var isRecording by mutableStateOf(false)
        private set
    var isPaused by mutableStateOf(false)
        private set
    /** True, wenn diese Aufzeichnung durch BluetoothConnectionReceiver ausgelöst wurde (nicht manuell). */
    var startedViaBluetooth by mutableStateOf(false)
    var elapsedSeconds by mutableStateOf(0L)
        private set
    var distanceMeters by mutableStateOf(0.0)
        private set
    var currentSpeedKmh by mutableStateOf(0.0)
        private set

    private var maxSpeedKmh = 0.0
    /** Für den Live-Sync während der Aufzeichnung (siehe ServerSync) - schreibgeschützte Sicht auf maxSpeedKmh. */
    val maxSpeedKmhSoFar: Double get() = maxSpeedKmh

    private var startTime = 0L
    /** Zeitpunkt des Aufzeichnungsstarts - für den Live-Sync während der Aufzeichnung. */
    val startTimeMillis: Long get() = startTime

    private var lastLocation: Location? = null
    private val points = mutableListOf<Triple<Double, Double, Long>>()
    /** Für den Live-Sync während der Aufzeichnung (siehe ServerSync) - Kopie der bisherigen Punkte. */
    val pointsSoFar: List<Triple<Double, Double, Long>> get() = points.toList()
    private var timerJob: Job? = null

    // Live-Streckenverlauf für die Kartenanzeige während der Aufzeichnung (lat, lon)
    private val _trackPoints = mutableStateListOf<Pair<Double, Double>>()
    val trackPoints: List<Pair<Double, Double>> get() = _trackPoints

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            lastLocation?.let { previous ->
                distanceMeters += previous.distanceTo(loc)
            }
            lastLocation = loc
            currentSpeedKmh = (loc.speed * 3.6).toDouble().coerceAtLeast(0.0)
            if (currentSpeedKmh > maxSpeedKmh) maxSpeedKmh = currentSpeedKmh
            points.add(Triple(loc.latitude, loc.longitude, System.currentTimeMillis()))
            _trackPoints.add(loc.latitude to loc.longitude)
        }
    }

    @SuppressLint("MissingPermission") // Permission wird vorher im UI geprüft
    fun start() {
        if (isRecording) return
        isRecording = true
        isPaused = false
        startedViaBluetooth = false // wird vom Aufrufer (BluetoothConnectionReceiver) danach ggf. auf true gesetzt
        startTime = System.currentTimeMillis()
        distanceMeters = 0.0
        currentSpeedKmh = 0.0
        maxSpeedKmh = 0.0
        lastLocation = null
        points.clear()
        _trackPoints.clear()

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L)
            .setMinUpdateIntervalMillis(1000L)
            .build()
        fusedClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())

        timerJob = trackerScope.launch {
            while (isRecording) {
                elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000
                delay(1000)
            }
        }
    }

    /** Pausiert Standort-Updates und die Zeitzählung, ohne die Aufzeichnung zu beenden. */
    fun pause() {
        if (!isRecording || isPaused) return
        isPaused = true
        currentSpeedKmh = 0.0
        fusedClient.removeLocationUpdates(locationCallback)
        timerJob?.cancel()
    }

    @SuppressLint("MissingPermission") // Permission wird vorher im UI geprüft
    fun resume() {
        if (!isRecording || !isPaused) return
        isPaused = false
        // Startzeit so verschieben, dass die bisher verstrichene Zeit erhalten bleibt
        startTime = System.currentTimeMillis() - (elapsedSeconds * 1000)

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L)
            .setMinUpdateIntervalMillis(1000L)
            .build()
        fusedClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())

        timerJob = trackerScope.launch {
            while (isRecording && !isPaused) {
                elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000
                delay(1000)
            }
        }
    }

    fun stop(): RecordingResult {
        isRecording = false
        isPaused = false
        startedViaBluetooth = false
        fusedClient.removeLocationUpdates(locationCallback)
        timerJob?.cancel()

        val durationHours = elapsedSeconds / 3600.0
        val avgSpeed = if (durationHours > 0) (distanceMeters / 1000.0) / durationHours else 0.0

        return RecordingResult(
            startTimestamp = startTime,
            endTimestamp = System.currentTimeMillis(),
            distanceMeters = distanceMeters,
            avgSpeedKmh = avgSpeed,
            maxSpeedKmh = maxSpeedKmh,
            points = points.toList()
        )
    }

    companion object {
        @Volatile private var INSTANCE: LocationTracker? = null

        /** Ein einziger geteilter Tracker für Handy-UI und Android-Auto-Screen. */
        fun getInstance(context: Context): LocationTracker =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: LocationTracker(context.applicationContext).also { INSTANCE = it }
            }
    }
}
