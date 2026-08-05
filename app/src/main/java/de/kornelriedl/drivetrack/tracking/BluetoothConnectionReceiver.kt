package de.kornelriedl.drivetrack.tracking

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import de.kornelriedl.drivetrack.data.Car
import de.kornelriedl.drivetrack.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Zeigt beim Verbinden mit einem Auto zugeordneten Bluetooth-Gerät eine Notification mit
 * Start-Button an, statt die Aufzeichnung direkt zu starten.
 *
 * Warum nicht direkt starten: Seit Android 12 (neuere Versionen noch strenger) darf eine App
 * KEINEN Foreground-Service vom Typ "location" aus dem Hintergrund heraus starten – auch nicht aus
 * einem Broadcast-Receiver wie diesem, selbst mit allen Berechtigungen erteilt (führte hier zu
 * ForegroundServiceStartNotAllowedException). Ein Tipp auf eine Notification-Aktion zählt dagegen
 * als Nutzer-Interaktion und ist von dieser Einschränkung ausdrücklich ausgenommen – siehe
 * StartRecordingFromNotificationReceiver, der den eigentlichen Start übernimmt.
 */
class BluetoothConnectionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != BluetoothDevice.ACTION_ACL_CONNECTED) return

        val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
        val address = device?.address ?: return

        val tracker = LocationTracker.getInstance(context)
        if (tracker.isRecording) return // läuft schon eine Aufzeichnung -> nichts tun

        if (ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) return // Standortzugriff (noch) nicht erteilt -> kann so oder so nicht aufzeichnen

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) return // ohne Notification-Berechtigung können wir den Nutzer nicht fragen

        // onReceive muss schnell zurückkehren; die DB-Abfrage ist aber suspend/async -> goAsync()
        // hält den Receiver-Prozess am Leben, bis die Coroutine fertig ist.
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val car = AppDatabase.getInstance(context).carDao().getCarByBluetoothAddress(address)
                if (car != null && !tracker.isRecording) {
                    showStartPrompt(context, car)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun showStartPrompt(context: Context, car: Car) {
        ensureChannel(context)

        val startIntent = Intent(context, StartRecordingFromNotificationReceiver::class.java).apply {
            putExtra(EXTRA_CAR_ID, car.id)
        }
        val startPendingIntent = PendingIntent.getBroadcast(
            context, car.id.toInt(),
            startIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("${car.name} verbunden")
            .setContentText("Tippen, um die Aufzeichnung zu starten")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(startPendingIntent)
            .addAction(android.R.drawable.ic_media_play, "Aufzeichnung starten", startPendingIntent)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Bluetooth-Auto-Start-Vorschlag",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Vorschlag, die Aufzeichnung zu starten, wenn sich das Handy mit " +
                    "einem Auto zugeordneten Gerät verbindet"
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "bluetooth_autostart_channel"
        const val NOTIFICATION_ID = 2001
        const val EXTRA_CAR_ID = "car_id"
    }
}
