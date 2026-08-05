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

    override fun onCreate() {
        super.onCreate()
        tracker = LocationTracker.getInstance(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        handler.post(notificationUpdater)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(notificationUpdater)
        super.onDestroy()
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

        fun start(context: Context) {
            val intent = Intent(context, TripTrackingService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TripTrackingService::class.java))
        }
    }
}
