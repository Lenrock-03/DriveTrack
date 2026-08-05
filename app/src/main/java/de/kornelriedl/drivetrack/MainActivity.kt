package de.kornelriedl.drivetrack

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import de.kornelriedl.drivetrack.data.Car
import de.kornelriedl.drivetrack.data.CarPreferences
import de.kornelriedl.drivetrack.data.Trip
import de.kornelriedl.drivetrack.data.UserPreferences
import de.kornelriedl.drivetrack.data.UserProfile
import de.kornelriedl.drivetrack.data.local.AppDatabase
import de.kornelriedl.drivetrack.data.local.TrackFileStore
import de.kornelriedl.drivetrack.data.toTrip
import de.kornelriedl.drivetrack.export.BackupExporter
import de.kornelriedl.drivetrack.export.GpxImporter
import de.kornelriedl.drivetrack.ui.components.DriveTrackBottomBar
import de.kornelriedl.drivetrack.ui.components.NavTab
import de.kornelriedl.drivetrack.ui.screens.HomeScreen
import de.kornelriedl.drivetrack.ui.screens.MapScreen
import de.kornelriedl.drivetrack.ui.screens.RecordScreen
import de.kornelriedl.drivetrack.ui.screens.ServerBackupScreen
import de.kornelriedl.drivetrack.ui.screens.SettingsScreen
import de.kornelriedl.drivetrack.ui.screens.TripDetailScreen
import de.kornelriedl.drivetrack.ui.theme.DriveTrackTheme
import de.kornelriedl.drivetrack.tracking.LocationTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration

class MainActivity : ComponentActivity() {

    private var pendingImportUri by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // osmdroid benötigt einmalig eine Konfiguration (User-Agent + Cache-Verzeichnis)
        Configuration.getInstance().load(
            applicationContext,
            applicationContext.getSharedPreferences("osmdroid_prefs", Context.MODE_PRIVATE)
        )
        Configuration.getInstance().userAgentValue = packageName

        pendingImportUri = extractGpxUri(intent)

        setContent {
            DriveTrackTheme {
                DriveTrackApp(
                    pendingImportUri = pendingImportUri,
                    onImportHandled = { pendingImportUri = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingImportUri = extractGpxUri(intent)
    }

    private fun extractGpxUri(intent: Intent?): Uri? {
        if (intent == null) return null
        return when (intent.action) {
            Intent.ACTION_SEND -> intent.getParcelableExtra(Intent.EXTRA_STREAM)
            Intent.ACTION_VIEW -> intent.data
            else -> null
        }
    }
}

@Composable
fun DriveTrackApp(
    pendingImportUri: Uri? = null,
    onImportHandled: () -> Unit = {}
) {
    var currentTab by remember { mutableStateOf(NavTab.HOME) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val db = remember { AppDatabase.getInstance(context) }
    val tripDao = remember { db.tripDao() }
    val carDao = remember { db.carDao() }
    val userDao = remember { db.userDao() }
    val tracker = remember { LocationTracker.getInstance(context) }

    val trips by tripDao.getAllTrips().collectAsState(initial = emptyList())
    val cars by carDao.getAllCars().collectAsState(initial = emptyList())
    val users by userDao.getAllUsers().collectAsState(initial = emptyList())

    var activeUserId by remember { mutableStateOf(UserPreferences.getActiveUserId(context)) }
    val onSelectUser: (Long?) -> Unit = { id ->
        activeUserId = id
        UserPreferences.setActiveUserId(context, id)
    }
    val onAddUser: (String) -> Unit = { name ->
        scope.launch {
            val newId = userDao.insertUser(UserProfile(name = name))
            activeUserId = newId
            UserPreferences.setActiveUserId(context, newId)
        }
    }
    val onDeleteUser: (UserProfile) -> Unit = { user ->
        scope.launch { userDao.deleteUser(user) }
        if (activeUserId == user.id) {
            activeUserId = null
            UserPreferences.setActiveUserId(context, null)
        }
    }
    val activeUserName = users.find { it.id == activeUserId }?.name ?: "Fahrer"

    var selectedCarId by remember { mutableStateOf(CarPreferences.getSelectedCarId(context)) }
    val onSelectCar: (Long?) -> Unit = { id ->
        selectedCarId = id
        CarPreferences.setSelectedCarId(context, id)
    }
    val onAddCar: (String) -> Unit = { name ->
        scope.launch {
            val newId = carDao.insertCar(Car(name = name))
            selectedCarId = newId
            CarPreferences.setSelectedCarId(context, newId)
        }
    }
    var defaultCarId by remember { mutableStateOf(CarPreferences.getDefaultCarId(context)) }
    val onSetDefaultCar: (Long?) -> Unit = { id ->
        defaultCarId = id
        CarPreferences.setDefaultCarId(context, id)
    }
    val onDeleteCar: (Car) -> Unit = { car ->
        scope.launch { carDao.deleteCar(car) }
        if (selectedCarId == car.id) {
            selectedCarId = null
            CarPreferences.setSelectedCarId(context, null)
        }
        if (defaultCarId == car.id) {
            defaultCarId = null
            CarPreferences.setDefaultCarId(context, null)
        }
    }

    // Nach Auto gefilterte Fahrten für Home & Fahrten (null = "Alle Autos")
    val filteredTrips = if (selectedCarId != null) trips.filter { it.carId == selectedCarId } else trips
    val filteredTotalKm = filteredTrips.sumOf { it.distanceMeters } / 1000.0
    val filteredTripCount = filteredTrips.size
    val filteredTotalDurationMinutes = filteredTrips.sumOf { it.durationMinutes }
    val filteredAvgSpeedKmh = if (filteredTrips.isNotEmpty()) filteredTrips.map { it.avgSpeedKmh }.average() else 0.0

    var selectedTrip by remember { mutableStateOf<Trip?>(null) }
    var showServerBackup by remember { mutableStateOf(false) }

    // Zentrale Import-Funktion: von Share-Intent UND vom manuellen Button in den Einstellungen genutzt
    val importGpx: (Uri) -> Unit = { uri ->
        scope.launch {
            val trip = withContext(Dispatchers.IO) { GpxImporter.importFromUri(context, uri) }
            if (trip != null) {
                val newId = tripDao.insertTrip(trip)
                withContext(Dispatchers.IO) { TrackFileStore.write(context, newId, trip.gpxTrackJson) }
                currentTab = NavTab.HOME
                Toast.makeText(context, "„${trip.name}“ importiert", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "GPX-Datei konnte nicht gelesen werden", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Eingehende Teilen-/Öffnen-Intents automatisch importieren
    LaunchedEffect(pendingImportUri) {
        pendingImportUri?.let { uri ->
            importGpx(uri)
            onImportHandled()
        }
    }

    val onExportBackup: () -> Unit = {
        BackupExporter.shareBackup(context, users, cars, trips)
    }
    val onImportBackup: (Uri) -> Unit = { uri ->
        scope.launch {
            val result = withContext(Dispatchers.IO) { BackupExporter.importBackup(context, uri) }
            val message = if (result.success) {
                buildString {
                    append("${result.tripsImported} Fahrt(en) importiert")
                    if (result.tripsSkipped > 0) append(", ${result.tripsSkipped} Duplikate übersprungen")
                    if (result.carsImported > 0) append(" · ${result.carsImported} neue Autos")
                    if (result.usersImported > 0) append(" · ${result.usersImported} neue Nutzer")
                }
            } else {
                "Backup konnte nicht gelesen werden"
            }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    // System-"Zurück"-Taste: im Detail-Screen zurück zur Liste statt App schließen
    BackHandler(enabled = selectedTrip != null || showServerBackup) {
        if (selectedTrip != null) selectedTrip = null else showServerBackup = false
    }

    // Zurück-Taste außerhalb dieser Screens: erst immer zu Home, App erst beim zweiten
    // Drücken (innerhalb von 2s) tatsächlich schließen
    val activity = context as? android.app.Activity
    var backPressedOnce by remember { mutableStateOf(false) }
    BackHandler(enabled = selectedTrip == null && !showServerBackup) {
        when {
            currentTab != NavTab.HOME -> {
                currentTab = NavTab.HOME
            }
            backPressedOnce -> {
                activity?.finish()
            }
            else -> {
                backPressedOnce = true
                Toast.makeText(context, "Nochmal drücken zum Beenden", Toast.LENGTH_SHORT).show()
                scope.launch {
                    delay(2000)
                    backPressedOnce = false
                }
            }
        }
    }

    val currentSelectedTrip = selectedTrip
    if (currentSelectedTrip != null) {
        TripDetailScreen(
            trip = currentSelectedTrip,
            cars = cars,
            onChangeCar = { trip, newCarId ->
                scope.launch { tripDao.updateTrip(trip.copy(carId = newCarId)) }
                selectedTrip = trip.copy(carId = newCarId)
            },
            onBack = { selectedTrip = null }
        )
        return
    }

    if (showServerBackup) {
        ServerBackupScreen(
            users = users,
            cars = cars,
            trips = trips,
            onImportComplete = { /* Listen aktualisieren sich automatisch über die Flows */ },
            onBack = { showServerBackup = false }
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
                userName = activeUserName,
                totalKm = filteredTotalKm,
                tripCount = filteredTripCount,
                totalDurationMinutes = filteredTotalDurationMinutes,
                avgSpeedKmh = filteredAvgSpeedKmh,
                recentTrips = filteredTrips.take(5),
                onTripClick = { selectedTrip = it },
                onRenameTrip = { trip, newName ->
                    scope.launch { tripDao.updateTrip(trip.copy(name = newName)) }
                },
                onDeleteTrip = { trip ->
                    scope.launch {
                        tripDao.deleteTrip(trip)
                        withContext(Dispatchers.IO) { TrackFileStore.delete(context, trip.id) }
                    }
                },
                cars = cars,
                selectedCarId = selectedCarId,
                onSelectCar = onSelectCar,
                onAddCar = onAddCar,
                showDashboard = true,
                modifier = Modifier.padding(padding)
            )
            NavTab.FAHRTEN -> HomeScreen(
                userName = activeUserName,
                totalKm = filteredTotalKm,
                tripCount = filteredTripCount,
                totalDurationMinutes = filteredTotalDurationMinutes,
                avgSpeedKmh = filteredAvgSpeedKmh,
                recentTrips = filteredTrips,
                onTripClick = { selectedTrip = it },
                onRenameTrip = { trip, newName ->
                    scope.launch { tripDao.updateTrip(trip.copy(name = newName)) }
                },
                onDeleteTrip = { trip ->
                    scope.launch {
                        tripDao.deleteTrip(trip)
                        withContext(Dispatchers.IO) { TrackFileStore.delete(context, trip.id) }
                    }
                },
                cars = cars,
                selectedCarId = selectedCarId,
                onSelectCar = onSelectCar,
                onAddCar = onAddCar,
                showDashboard = false,
                modifier = Modifier.padding(padding)
            )
            NavTab.AUFZEICHNEN -> RecordScreen(
                tracker = tracker,
                onRecordingFinished = { result ->
                    scope.launch {
                        val trip = result.toTrip(carId = selectedCarId)
                        val newId = tripDao.insertTrip(trip)
                        withContext(Dispatchers.IO) { TrackFileStore.write(context, newId, trip.gpxTrackJson) }
                    }
                    currentTab = NavTab.HOME
                },
                cars = cars,
                selectedCarId = selectedCarId,
                onSelectCar = onSelectCar,
                defaultCarId = defaultCarId,
                modifier = Modifier.padding(padding)
            )
            NavTab.KARTE -> MapScreen(
                trips = filteredTrips,
                cars = cars,
                selectedCarId = selectedCarId,
                onSelectCar = onSelectCar,
                onAddCar = onAddCar,
                modifier = Modifier.padding(padding)
            )
            NavTab.EINSTELLUNGEN -> SettingsScreen(
                trips = trips,
                onImportGpx = importGpx,
                cars = cars,
                onAddCar = onAddCar,
                onDeleteCar = onDeleteCar,
                defaultCarId = defaultCarId,
                onSetDefaultCar = onSetDefaultCar,
                users = users,
                activeUserId = activeUserId,
                onSelectUser = onSelectUser,
                onAddUser = onAddUser,
                onDeleteUser = onDeleteUser,
                onExportBackup = onExportBackup,
                onImportBackup = onImportBackup,
                onOpenServerBackup = { showServerBackup = true },
                modifier = Modifier.padding(padding)
            )
        }
    }
}
