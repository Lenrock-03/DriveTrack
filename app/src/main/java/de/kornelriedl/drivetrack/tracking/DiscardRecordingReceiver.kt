package de.kornelriedl.drivetrack.tracking

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import de.kornelriedl.drivetrack.data.server.ServerSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Wird ausschließlich vom "Verwerfen"-Button in der Aufzeichnungs-Notification ausgelöst
 * (siehe TripTrackingService.buildNotification), der nur erscheint, wenn die Aufzeichnung
 * automatisch per Bluetooth gestartet wurde. Im Unterschied zum normalen Stopp-Button in
 * RecordScreen wird das Ergebnis bewusst NICHT gespeichert – es landet also kein Fehlstart
 * (z. B. Kopfhörer statt Auto-Radio verbunden) als Fahrt in der Datenbank.
 */
class DiscardRecordingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val tracker = LocationTracker.getInstance(context)
        if (tracker.isRecording) {
            tracker.stop() // Rückgabewert wird absichtlich ignoriert
        }
        TripTrackingService.stop(context)

        // Live-Zwischenstand auf dem Server war ja nur für DIESE (jetzt verworfene) Aufzeichnung
        // gedacht - aufräumen, damit da kein Geister-Zwischenstand liegen bleibt.
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ServerSync.deleteLiveTripIfPossible(context)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
