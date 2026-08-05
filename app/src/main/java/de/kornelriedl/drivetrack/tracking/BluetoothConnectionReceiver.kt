package de.kornelriedl.drivetrack.tracking

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import de.kornelriedl.drivetrack.data.CarPreferences
import de.kornelriedl.drivetrack.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Startet die Fahrtaufzeichnung automatisch, sobald sich das Handy mit einem Bluetooth-Gerät
 * verbindet, das in den Fahrzeug-Einstellungen einem Auto zugeordnet wurde (z. B. das Auto-Radio).
 *
 * ACL_CONNECTED gehört zu Androids "protected broadcasts" und wird deshalb trotz der seit
 * Android 8 geltenden Einschränkungen für Manifest-registrierte Broadcast-Receiver weiterhin
 * zuverlässig zugestellt – auch wenn die App gerade nicht läuft.
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
        if (tracker.isRecording) return // läuft schon eine Aufzeichnung -> nichts tun, keinen Doppelstart auslösen

        if (ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) return // Standortzugriff (noch) nicht erteilt -> kann nicht automatisch starten

        // onReceive muss schnell zurückkehren; die DB-Abfrage ist aber suspend/async -> goAsync()
        // hält den Receiver-Prozess am Leben, bis die Coroutine fertig ist.
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val car = AppDatabase.getInstance(context).carDao().getCarByBluetoothAddress(address)
                if (car != null) {
                    withContext(Dispatchers.Main) {
                        if (tracker.isRecording) return@withContext // Sicherheitscheck, falls in der Zwischenzeit doch gestartet
                        CarPreferences.setSelectedCarId(context, car.id)
                        tracker.start()
                        tracker.startedViaBluetooth = true
                        TripTrackingService.start(context)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
