package de.kornelriedl.drivetrack.tracking

import android.Manifest
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat

/**
 * Macht den Bluetooth-Auto-Start ohne Rückfrage-Notification möglich (seit 0.19.0).
 *
 * Hintergrund siehe BluetoothConnectionReceiver: seit Android 12 darf eine App einen
 * Foreground-Service vom Typ "location" nicht aus dem Hintergrund starten. Eine der wenigen
 * offiziellen Ausnahmen: die App ist über den CompanionDeviceManager mit dem Gerät verknüpft
 * ("Companion-App", einmaliger System-Bestätigungsdialog) und deklariert
 * REQUEST_COMPANION_START_FOREGROUND_SERVICES_FROM_BACKGROUND / REQUEST_COMPANION_RUN_IN_BACKGROUND
 * (siehe AndroidManifest.xml). Zusätzlich braucht der aus dem Hintergrund gestartete Service
 * Standortzugriff "Immer zulassen" (ACCESS_BACKGROUND_LOCATION) - mit "Nur während der Nutzung"
 * würde startForeground() trotz Verknüpfung mit einer SecurityException scheitern.
 *
 * Fehlt eins von beidem, bleibt alles wie vorher (Start-Notification) - kein Fehler sichtbar.
 */
object CompanionAutoStart {

    /** Ob die App per CompanionDeviceManager mit dem Gerät dieser MAC-Adresse verknüpft ist. */
    fun isAssociated(context: Context, address: String): Boolean {
        val manager = context.getSystemService(CompanionDeviceManager::class.java) ?: return false
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                manager.myAssociations.any {
                    it.deviceMacAddress?.toString().equals(address, ignoreCase = true)
                }
            } else {
                @Suppress("DEPRECATION")
                manager.associations.any { it.equals(address, ignoreCase = true) }
            }
        } catch (e: Exception) {
            false
        }
    }

    fun hasBackgroundLocation(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true // gab es vor Android 10 nicht
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /** Beides erfüllt -> BluetoothConnectionReceiver darf die Aufzeichnung direkt starten. */
    fun canStartInBackground(context: Context, address: String): Boolean =
        isAssociated(context, address) && hasBackgroundLocation(context)

    /**
     * Startet die Verknüpfung mit genau diesem (bereits gekoppelten) Gerät. Android liefert dafür
     * einen IntentSender für den System-Bestätigungsdialog, den der Aufrufer (CarDetailScreen)
     * per StartIntentSenderForResult anzeigt.
     */
    fun requestAssociation(
        context: Context,
        address: String,
        onConfirmationNeeded: (IntentSender) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val manager = context.getSystemService(CompanionDeviceManager::class.java)
        if (manager == null) {
            onFailure("Dieses Gerät unterstützt keine Companion-Verknüpfung.")
            return
        }
        val request = AssociationRequest.Builder()
            .addDeviceFilter(BluetoothDeviceFilter.Builder().setAddress(address).build())
            .setSingleDevice(true)
            .build()

        val callback = object : CompanionDeviceManager.Callback() {
            // Bis Android 12L
            @Deprecated("Ersetzt durch onAssociationPending ab Android 13")
            override fun onDeviceFound(intentSender: IntentSender) = onConfirmationNeeded(intentSender)

            // Ab Android 13 (ruft onDeviceFound dann nicht mehr auf, da hier überschrieben)
            override fun onAssociationPending(intentSender: IntentSender) = onConfirmationNeeded(intentSender)

            override fun onFailure(error: CharSequence?) =
                onFailure(error?.toString() ?: "Verknüpfung fehlgeschlagen.")
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                manager.associate(request, ContextCompat.getMainExecutor(context), callback)
            } else {
                @Suppress("DEPRECATION")
                manager.associate(request, callback, Handler(Looper.getMainLooper()))
            }
        } catch (e: Exception) {
            onFailure(e.message ?: "Verknüpfung fehlgeschlagen.")
        }
    }
}
