package de.kornelriedl.drivetrack.tracking

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import de.kornelriedl.drivetrack.data.TrackingPreferences
import de.kornelriedl.drivetrack.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Gegenstück zu BluetoothConnectionReceiver: merkt für TripTrackingService vor, eine laufende
 * Aufzeichnung zu beenden, sobald sich das Handy von einem Auto zugeordneten Bluetooth-Gerät
 * trennt (typisch: Motor aus) - unabhängig davon, ob die Aufzeichnung per Bluetooth automatisch
 * oder manuell gestartet wurde (siehe TrackingPreferences für den Ein-/Ausschalter dafür).
 *
 * Bewusst nur ein schlanker Dispatcher: das eigentliche Stoppen läuft mit einer kurzen
 * Verzögerung (Schutz vor kurzen Bluetooth-Aussetzern) in TripTrackingService, das dafür bereits
 * einen langlebigen Handler/Scope hat - goAsync() hier hätte dafür kein ausreichend großes
 * Zeitfenster (System-Timeout nach wenigen Sekunden), siehe TripTrackingService-Doc-Kommentar.
 */
class BluetoothDisconnectionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != BluetoothDevice.ACTION_ACL_DISCONNECTED) return
        if (!TrackingPreferences.isAutoStopOnDisconnectEnabled(context)) return

        val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
        val address = device?.address ?: return

        val tracker = LocationTracker.getInstance(context)
        if (!tracker.isRecording) return // nichts zu beenden

        // onReceive muss schnell zurückkehren; die DB-Abfrage ist aber suspend/async -> goAsync()
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val car = AppDatabase.getInstance(context).carDao().getCarByBluetoothAddress(address)
                // Nur vormerken, wenn sich wirklich ein einem Auto zugeordnetes Gerät getrennt hat -
                // sonst würde z.B. ein sich trennendes Kopfhörer-Headset fälschlich die Fahrt beenden.
                // isRecording erneut prüfen: könnte zwischen dem Check oben und hier (z.B. durch
                // manuelles Stoppen im selben Moment) bereits beendet worden sein.
                if (car != null && tracker.isRecording) {
                    TripTrackingService.scheduleAutoStopOnDisconnect(context, car.id)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
