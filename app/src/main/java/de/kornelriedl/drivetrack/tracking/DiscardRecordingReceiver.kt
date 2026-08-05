package de.kornelriedl.drivetrack.tracking

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

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
    }
}
