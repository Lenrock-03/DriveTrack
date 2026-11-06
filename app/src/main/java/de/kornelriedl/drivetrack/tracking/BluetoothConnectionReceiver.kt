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
import de.kornelriedl.drivetrack.data.CarPreferences
import de.kornelriedl.drivetrack.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Zeigt beim Verbinden mit einem Auto zugeordneten Bluetooth-Gerät eine Notification mit
 * Start-Button an, statt die Aufzeichnung direkt zu starten - es sei denn, die App ist mit dem
 * Gerät per CompanionDeviceManager verknüpft (seit 0.19.0, siehe CompanionAutoStart), dann startet
 * die Aufzeichnung direkt.
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

        // onReceive muss schnell zurückkehren; die DB-Abfrage ist aber suspend/async -> goAsync()
        // hält den Receiver-Prozess am Leben, bis die Coroutine fertig ist. Läuft bewusst auch bei
        // bereits laufender Aufzeichnung durch (statt hier synchron abzubrechen) - ein
        // Wiederverbinden desselben Auto-Geräts muss einen ggf. vorgemerkten automatischen Stopp
        // (siehe TripTrackingService/BluetoothDisconnectionReceiver) noch aufheben können.
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val car = AppDatabase.getInstance(context).carDao().getCarByBluetoothAddress(address)
                if (car == null) return@launch // kein zugeordnetes Auto -> nichts tun

                if (tracker.isRecording) {
                    // Läuft schon eine Aufzeichnung - falls wegen eines kurzen Bluetooth-Aussetzers
                    // gerade ein automatischer Stopp vorgemerkt ist (Aussetzer-Schutz), hebt das
                    // Wiederverbinden desselben Auto-Geräts diesen wieder auf.
                    TripTrackingService.cancelPendingAutoStop(context)
                    return@launch
                }

                if (ContextCompat.checkSelfPermission(
                        context, Manifest.permission.ACCESS_FINE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED
                ) return@launch // Standortzugriff (noch) nicht erteilt -> kann so oder so nicht aufzeichnen

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(
                        context, Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) return@launch // ohne Notification-Berechtigung können wir den Nutzer nicht fragen

                // Seit 0.19.0: mit Companion-Verknüpfung + "Immer zulassen"-Standort direkt starten
                // (siehe CompanionAutoStart) - sonst wie bisher per Start-Notification fragen.
                if (CompanionAutoStart.canStartInBackground(context, address)) {
                    val started = withContext(Dispatchers.Main) { startRecordingDirectly(context, car) }
                    if (started) return@launch
                }
                showStartPrompt(context, car.id, car.name)
            } finally {
                pendingResult.finish()
            }
        }
    }

    /**
     * Läuft auf dem Main-Thread (LocationTracker-State wird dort verändert, siehe
     * StartRecordingFromNotificationReceiver). Service zuerst starten: lehnt Android den Start doch
     * ab, wirft startForegroundService() sofort - dann wurde der Tracker noch gar nicht angefasst.
     * onStartCommand() läuft erst nach diesem Block (ebenfalls Main-Thread), sieht also bereits
     * isRecording/startedViaBluetooth.
     */
    private fun startRecordingDirectly(context: Context, car: Car): Boolean {
        val tracker = LocationTracker.getInstance(context)
        if (tracker.isRecording) return true
        return try {
            TripTrackingService.startAutomatically(context, car.id, car.name)
            CarPreferences.setSelectedCarId(context, car.id)
            tracker.start()
            tracker.startedViaBluetooth = true
            true
        } catch (e: IllegalStateException) { // u. a. ForegroundServiceStartNotAllowedException
            false
        } catch (e: SecurityException) {
            false
        }
    }

    companion object {
        const val CHANNEL_ID = "bluetooth_autostart_channel"
        const val NOTIFICATION_ID = 2001
        const val EXTRA_CAR_ID = "car_id"

        /** Auch von TripTrackingService genutzt, falls ein Direktstart doch scheitert. */
        fun showStartPrompt(context: Context, carId: Long, carName: String) {
            ensureChannel(context)

            val startIntent = Intent(context, StartRecordingFromNotificationReceiver::class.java).apply {
                putExtra(EXTRA_CAR_ID, carId)
            }
            val startPendingIntent = PendingIntent.getBroadcast(
                context, carId.toInt(),
                startIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("$carName verbunden")
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
    }
}
