package de.kornelriedl.drivetrack

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import de.kornelriedl.drivetrack.data.Trip
import de.kornelriedl.drivetrack.data.local.AppDatabase
import de.kornelriedl.drivetrack.data.toTrip
import de.kornelriedl.drivetrack.ui.components.DriveTrackBottomBar
import de.kornelriedl.drivetrack.ui.components.NavTab
import de.kornelriedl.drivetrack.ui.screens.HomeScreen
import de.kornelriedl.drivetrack.ui.screens.MapScreen
import de.kornelriedl.drivetrack.ui.screens.RecordScreen
import de.kornelriedl.drivetrack.ui.screens.SettingsScreen
import de.kornelriedl.drivetrack.ui.screens.TripDetailScreen
import de.kornelriedl.drivetrack.ui.theme.DriveTrackTheme
import de.kornelriedl.drivetrack.tracking.LocationTracker
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // osmdroid benötigt einmalig eine Konfiguration (User-Agent + Cache-Verzeichnis)
        Configuration.getInstance().load(
            applicationContext,
            applicationContext.getSharedPreferences("osmdroid_prefs", Context.MODE_PRIVATE)
        )
        Configuration.getInstance().userAgentValue = packageName

        setContent {
            DriveTrackTheme {
                DriveTrackApp()
            }
        }
    }
}

@Composable
fun DriveTrackApp() {
    var currentTab by remember { mutableStateOf(NavTab.HOME) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val db = remember { AppDatabase.getInstance(context) }
    val tripDao = remember { db.tripDao() }
    val tracker = remember { LocationTracker.getInstance(context) }

    val trips by tripDao.getAllTrips().collectAsState(initial = emptyList())
    val totalDistanceMeters by tripDao.getTotalDistanceMeters().collectAsState(initial = 0.0)

    val totalKm = (totalDistanceMeters ?: 0.0) / 1000.0
    val tripCount = trips.size
    val totalDurationMinutes = trips.sumOf { it.durationMinutes }
    val avgSpeedKmh = if (trips.isNotEmpty()) trips.map { it.avgSpeedKmh }.average() else 0.0

    var selectedTrip by remember { mutableStateOf<Trip?>(null) }

    // System-"Zurück"-Taste: im Detail-Screen zurück zur Liste statt App schließen
    BackHandler(enabled = selectedTrip != null) {
        selectedTrip = null
    }

    val currentSelectedTrip = selectedTrip
    if (currentSelectedTrip != null) {
        TripDetailScreen(
            trip = currentSelectedTrip,
            onBack = { selectedTrip = null }
        )
        return
    }

    Scaffold(
        bottomBar = {
            DriveTrackBottomBar(current = currentTab, onTabSelected = { currentTab = it })
        }
    ) { padding ->
        when (currentTab) {
            NavTab.HOME -> HomeScreen(
                userName = "Kornel",
                totalKm = totalKm,
                tripCount = tripCount,
                totalDurationMinutes = totalDurationMinutes,
                avgSpeedKmh = avgSpeedKmh,
                recentTrips = trips.take(5),
                onTripClick = { selectedTrip = it },
                onRenameTrip = { trip, newName ->
                    scope.launch { tripDao.updateTrip(trip.copy(name = newName)) }
                },
                onDeleteTrip = { trip ->
                    scope.launch { tripDao.deleteTrip(trip) }
                },
                showDashboard = true,
                modifier = Modifier.padding(padding)
            )
            NavTab.FAHRTEN -> HomeScreen(
                userName = "Kornel",
                totalKm = totalKm,
                tripCount = tripCount,
                totalDurationMinutes = totalDurationMinutes,
                avgSpeedKmh = avgSpeedKmh,
                recentTrips = trips,
                onTripClick = { selectedTrip = it },
                onRenameTrip = { trip, newName ->
                    scope.launch { tripDao.updateTrip(trip.copy(name = newName)) }
                },
                onDeleteTrip = { trip ->
                    scope.launch { tripDao.deleteTrip(trip) }
                },
                showDashboard = false,
                modifier = Modifier.padding(padding)
            )
            NavTab.AUFZEICHNEN -> RecordScreen(
                tracker = tracker,
                onRecordingFinished = { result ->
                    scope.launch {
                        tripDao.insertTrip(result.toTrip())
                    }
                    currentTab = NavTab.HOME
                },
                modifier = Modifier.padding(padding)
            )
            NavTab.KARTE -> MapScreen(trips = trips, modifier = Modifier.padding(padding))
            NavTab.EINSTELLUNGEN -> SettingsScreen(trips = trips, modifier = Modifier.padding(padding))
        }
    }
}
