package de.kornelriedl.drivetrack.tracking

import android.Manifest
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import de.kornelriedl.drivetrack.data.CarPreferences

/**
 * Startet die Aufzeichnung, wenn der Nutzer auf die Bluetooth-Vorschlags-Notification tippt (siehe
 * BluetoothConnectionReceiver). Ein Tipp auf eine Notification-Aktion ist eine der von Android
 * ausdrücklich erlaubten Ausnahmen, um einen Foreground-Service vom Typ "location" aus dem
 * Hintergrund heraus zu starten.
 */
class StartRecordingFromNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        context.getSystemService(NotificationManager::class.java)
            .cancel(BluetoothConnectionReceiver.NOTIFICATION_ID)

        val tracker = LocationTracker.getInstance(context)
        if (tracker.isRecording) return // z.B. zwischenzeitlich schon manuell gestartet

        if (ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        val carId = intent.getLongExtra(BluetoothConnectionReceiver.EXTRA_CAR_ID, -1L)
        if (carId != -1L) {
            CarPreferences.setSelectedCarId(context, carId)
        }

        tracker.start()
        tracker.startedViaBluetooth = true
        TripTrackingService.start(context)
    }
}
