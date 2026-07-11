package de.kornelriedl.drivetrack.car

import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator
import de.kornelriedl.drivetrack.data.local.AppDatabase
import de.kornelriedl.drivetrack.tracking.LocationTracker

/**
 * Android-Auto-Einstiegspunkt. Nutzt denselben LocationTracker und dieselbe
 * Datenbank wie die Handy-App (Singletons), damit eine von hier gestartete
 * Aufzeichnung auch auf dem Handy sichtbar ist – und umgekehrt.
 */
class DriveTrackCarAppService : CarAppService() {

    override fun createHostValidator(): HostValidator {
        // ACHTUNG: Für den produktiven Play-Store-Release müsste hier eine
        // echte Allow-List der erlaubten Hosts (Android Auto, Android Automotive)
        // stehen. Für privaten Gebrauch über "Unbekannte Quellen zulassen" reicht das.
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    }

    override fun onCreateSession(): Session {
        return object : Session() {
            override fun onCreateScreen(intent: Intent): Screen {
                val tracker = LocationTracker.getInstance(carContext)
                val tripDao = AppDatabase.getInstance(carContext).tripDao()
                return RecordingCarScreen(carContext, tracker, tripDao)
            }
        }
    }
}
