package de.kornelriedl.drivetrack.tracking

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import de.kornelriedl.drivetrack.MainActivity
import de.kornelriedl.drivetrack.data.CarPreferences
import de.kornelriedl.drivetrack.data.local.AppDatabase
import de.kornelriedl.drivetrack.data.local.TrackFileStore
import de.kornelriedl.drivetrack.data.server.ServerSync
import de.kornelriedl.drivetrack.data.suggestTripName
import de.kornelriedl.drivetrack.data.toTrip
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Vordergrund-Service, der ausschließlich dafür sorgt, dass Android die App während
 * einer laufenden Aufzeichnung als "im Vordergrund aktiv" einstuft – dadurch werden
 * GPS-Updates NICHT gedrosselt, auch wenn der Bildschirm aus ist oder die App im
 * Hintergrund läuft. Die eigentliche GPS-Logik bleibt komplett im LocationTracker.
 */
class TripTrackingService : Service() {

    private lateinit var tracker: LocationTracker

    private val handler = Handler(Looper.getMainLooper())
    private val notificationUpdater = object : Runnable {
        override fun run() {
            updateNotification()
            handler.postDelayed(this, 3000)
        }
    }

    // Eigener Scope für den periodischen Live-Sync - läuft unabhängig von der Notification-Schleife
    private val serviceScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private var liveSyncJob: Job? = null

    // Verzögertes Beenden bei Bluetooth-Trennung (siehe BluetoothDisconnectionReceiver) - lebt
    // hier statt in einem BroadcastReceiver, weil goAsync() dort kein ausreichend großes
    // Zeitfenster für eine mehrsekündige Verzögerung hätte (System-ANR-Timeout).
    private var pendingDisconnectStopRunnable: Runnable? = null

    override fun onCreate() {
        super.onCreate()
        tracker = LocationTracker.getInstance(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        handler.post(notificationUpdater)
        startLiveSyncLoop()
        when (intent?.action) {
            ACTION_SCHEDULE_DISCONNECT_STOP -> {
                val carId = intent.getLongExtra(EXTRA_CAR_ID, -1L)
                if (carId != -1L) scheduleDisconnectStop(carId)
            }
            ACTION_CANCEL_DISCONNECT_STOP -> cancelDisconnectStop()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(notificationUpdater)
        liveSyncJob?.cancel()
        cancelDisconnectStop()
        super.onDestroy()
    }

    /**
     * Merkt vor, die Aufzeichnung nach DISCONNECT_GRACE_MS automatisch zu beenden und zu
     * speichern - mit Verzögerung statt sofort, damit ein kurzer Bluetooth-Aussetzer (z. B.
     * schwaches Signal für 1-2s) nicht sofort eine noch laufende Fahrt abschneidet. Ein
     * zwischenzeitliches Wiederverbinden hebt das über cancelDisconnectStop() wieder auf (siehe
     * BluetoothConnectionReceiver).
     */
    private fun scheduleDisconnectStop(carId: Long) {
        cancelDisconnectStop() // evtl. schon laufenden Timer ersetzen statt zu verdoppeln
        val runnable = Runnable {
            pendingDisconnectStopRunnable = null
            serviceScope.launch {
                if (!tracker.isRecording) return@launch // zwischenzeitlich schon manuell gestoppt
                val result = tracker.stop()
                withContext(Dispatchers.IO) {
                    val suggestedName = suggestTripName(applicationContext, result.points)
                    val trip = result.toTrip(name = suggestedName ?: "Fahrt", carId = carId)
                    val db = AppDatabase.getInstance(applicationContext)
                    val newId = db.tripDao().insertTrip(trip)
                    TrackFileStore.write(applicationContext, newId, trip.gpxTrackJson)
                    ServerSync.syncFullBackupIfPossible(
                        applicationContext,
                        db.userDao().getAllUsers().first(),
                        db.carDao().getAllCars().first(),
                        db.tripDao().getAllTrips().first(),
                        db.tripGroupDao().getAllGroups().first()
                    )
                }
                stopSelf()
            }
        }
        pendingDisconnectStopRunnable = runnable
        handler.postDelayed(runnable, DISCONNECT_GRACE_MS)
    }

    private fun cancelDisconnectStop() {
        pendingDisconnectStopRunnable?.let { handler.removeCallbacks(it) }
        pendingDisconnectStopRunnable = null
    }

    /**
     * Sicherheitsnetz falls Handy/App mittendrin ausfällt: lädt während der Aufzeichnung alle
     * paar Minuten einen Zwischenstand hoch (siehe ServerSync.syncLiveTripIfPossible). Läuft
     * best-effort - ohne Login/Netz passiert einfach nichts, kein Fehler sichtbar für den Nutzer.
     */
    private fun startLiveSyncLoop() {
        if (liveSyncJob?.isActive == true) return
        liveSyncJob = serviceScope.launch {
            while (tracker.isRecording) {
                delay(LIVE_SYNC_INTERVAL_MS)
                if (!tracker.isRecording) break
                // Snapshot MUSS auf dem Main-Thread gezogen werden - LocationTracker.points wird
                // vom GPS-Callback ebenfalls dort verändert (siehe LiveTripSnapshot-Doku).
                val snapshot = ServerSync.LiveTripSnapshot(
                    startTimeMillis = tracker.startTimeMillis,
                    distanceMeters = tracker.distanceMeters,
                    maxSpeedKmh = tracker.maxSpeedKmhSoFar,
                    points = tracker.pointsSoFar
                )
                val carId = CarPreferences.getSelectedCarId(applicationContext)
                withContext(Dispatchers.IO) {
                    ServerSync.syncLiveTripIfPossible(applicationContext, snapshot, carId)
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Fahrtaufzeichnung",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Zeigt an, dass DriveTrack gerade eine Fahrt aufzeichnet"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = when {
            tracker.isPaused -> "Aufzeichnung pausiert"
            tracker.startedViaBluetooth -> "Aufzeichnung automatisch gestartet"
            else -> "Aufzeichnung läuft"
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(statusText())
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setContentIntent(openAppIntent)

        // Nur bei automatischem Start per Bluetooth: Möglichkeit, einen Fehlstart (z. B. Kopfhörer
        // statt Auto-Radio verbunden) direkt zu verwerfen, ohne die Fahrt zu speichern.
        if (tracker.startedViaBluetooth) {
            val discardIntent = PendingIntent.getBroadcast(
                this, 0,
                Intent(this, DiscardRecordingReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Verwerfen",
                discardIntent
            )
        }

        return builder.build()
    }

    private fun updateNotification() {
        if (!tracker.isRecording) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun statusText(): String {
        val minutes = tracker.elapsedSeconds / 60
        val seconds = tracker.elapsedSeconds % 60
        val km = tracker.distanceMeters / 1000.0
        return "%02d:%02d · %.2f km".format(minutes, seconds, km)
    }

    companion object {
        private const val CHANNEL_ID = "trip_tracking_channel"
        private const val NOTIFICATION_ID = 1001
        private const val LIVE_SYNC_INTERVAL_MS = 3 * 60 * 1000L // alle 3 Minuten

        private const val ACTION_SCHEDULE_DISCONNECT_STOP = "de.kornelriedl.drivetrack.action.SCHEDULE_DISCONNECT_STOP"
        private const val ACTION_CANCEL_DISCONNECT_STOP = "de.kornelriedl.drivetrack.action.CANCEL_DISCONNECT_STOP"
        private const val EXTRA_CAR_ID = "car_id"
        private const val DISCONNECT_GRACE_MS = 10_000L // Aussetzer-Schutz, siehe scheduleDisconnectStop()

        fun start(context: Context) {
            val intent = Intent(context, TripTrackingService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TripTrackingService::class.java))
        }

        /** Siehe BluetoothDisconnectionReceiver - merkt ein verzögertes automatisches Beenden vor. */
        fun scheduleAutoStopOnDisconnect(context: Context, carId: Long) {
            val intent = Intent(context, TripTrackingService::class.java)
                .setAction(ACTION_SCHEDULE_DISCONNECT_STOP)
                .putExtra(EXTRA_CAR_ID, carId)
            ContextCompat.startForegroundService(context, intent)
        }

        /** Siehe BluetoothConnectionReceiver - hebt einen vorgemerkten automatischen Stopp wieder auf. */
        fun cancelPendingAutoStop(context: Context) {
            val intent = Intent(context, TripTrackingService::class.java)
                .setAction(ACTION_CANCEL_DISCONNECT_STOP)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
