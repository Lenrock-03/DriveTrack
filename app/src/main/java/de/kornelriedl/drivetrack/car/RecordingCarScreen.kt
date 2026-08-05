package de.kornelriedl.drivetrack.car

import android.Manifest
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import de.kornelriedl.drivetrack.data.local.TrackFileStore
import de.kornelriedl.drivetrack.data.local.TripDao
import de.kornelriedl.drivetrack.data.toTrip
import de.kornelriedl.drivetrack.tracking.LocationTracker
import de.kornelriedl.drivetrack.tracking.TripTrackingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RecordingCarScreen(
    carContext: CarContext,
    private val tracker: LocationTracker,
    private val tripDao: TripDao
) : Screen(carContext) {

    // Alle 1s die Anzeige aktualisieren (Dauer/Distanz/Speed live), solange der Screen sichtbar ist
    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            invalidate()
            handler.postDelayed(this, 1000)
        }
    }

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                handler.post(ticker)
            }

            override fun onPause(owner: LifecycleOwner) {
                handler.removeCallbacks(ticker)
            }
        })
    }

    override fun onGetTemplate(): Template {
        val hasPermission = ContextCompat.checkSelfPermission(
            carContext, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            return MessageTemplate.Builder(
                "Bitte öffne DriveTrack einmal auf dem Handy und erlaube den Standortzugriff."
            )
                .setTitle("Standort benötigt")
                .setHeaderAction(Action.APP_ICON)
                .build()
        }

        val pane = Pane.Builder()
            .addRow(
                Row.Builder()
                    .setTitle("Dauer")
                    .addText(formatDuration(tracker.elapsedSeconds))
                    .build()
            )
            .addRow(
                Row.Builder()
                    .setTitle("Distanz")
                    .addText("%.2f km".format(tracker.distanceMeters / 1000.0))
                    .build()
            )
            .addRow(
                Row.Builder()
                    .setTitle("Geschwindigkeit")
                    .addText("%.0f km/h".format(tracker.currentSpeedKmh))
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle(if (tracker.isRecording) "Stop" else "Start")
                    .setOnClickListener {
                        if (tracker.isRecording) {
                            val result = tracker.stop()
                            TripTrackingService.stop(carContext)
                            CoroutineScope(Dispatchers.IO).launch {
                                val trip = result.toTrip()
                                val newId = tripDao.insertTrip(trip)
                                TrackFileStore.write(carContext, newId, trip.gpxTrackJson)
                            }
                        } else {
                            tracker.start()
                            TripTrackingService.start(carContext)
                        }
                        invalidate()
                    }
                    .build()
            )
            .build()

        return PaneTemplate.Builder(pane)
            .setTitle("DriveTrack")
            .setHeaderAction(Action.APP_ICON)
            .build()
    }

    private fun formatDuration(totalSeconds: Long): String {
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d".format(minutes, seconds)
    }
}
