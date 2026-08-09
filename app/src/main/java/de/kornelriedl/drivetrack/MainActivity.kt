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
import de.kornelriedl.drivetrack.data.SegmentMark
import de.kornelriedl.drivetrack.data.Trip
import de.kornelriedl.drivetrack.data.TripEditPlan
import de.kornelriedl.drivetrack.data.TripGroup
import de.kornelriedl.drivetrack.data.UserPreferences
import de.kornelriedl.drivetrack.data.UserProfile
import de.kornelriedl.drivetrack.data.applyTripEditPlan
import de.kornelriedl.drivetrack.data.buildTripListEntries
import de.kornelriedl.drivetrack.data.local.AppDatabase
import de.kornelriedl.drivetrack.data.local.CarPhotoStore
import de.kornelriedl.drivetrack.data.local.TrackFileStore
import de.kornelriedl.drivetrack.data.recomputeMaxSpeedExcludingMarks
import de.kornelriedl.drivetrack.data.segmentMarks
import de.kornelriedl.drivetrack.data.server.ServerAuthPreferences
import de.kornelriedl.drivetrack.data.server.ServerSession
import de.kornelriedl.drivetrack.data.server.ServerSync
import de.kornelriedl.drivetrack.data.toJson
import de.kornelriedl.drivetrack.data.toLabelsString
import de.kornelriedl.drivetrack.data.toTrip
import de.kornelriedl.drivetrack.export.BackupExporter
import de.kornelriedl.drivetrack.export.GpxImporter
import de.kornelriedl.drivetrack.export.MapThumbnailGenerator
import de.kornelriedl.drivetrack.ui.components.DriveTrackBottomBar
import de.kornelriedl.drivetrack.ui.components.NavTab
import de.kornelriedl.drivetrack.ui.screens.CarDetailScreen
import de.kornelriedl.drivetrack.ui.screens.HomeScreen
import de.kornelriedl.drivetrack.ui.screens.ImportExportScreen
import de.kornelriedl.drivetrack.ui.screens.MapScreen
import de.kornelriedl.drivetrack.ui.screens.RecordScreen
import de.kornelriedl.drivetrack.ui.screens.ServerBackupScreen
import de.kornelriedl.drivetrack.ui.screens.SettingsScreen
import de.kornelriedl.drivetrack.ui.screens.TripDetailScreen
import de.kornelriedl.drivetrack.ui.screens.TripEditScreen
import de.kornelriedl.drivetrack.ui.screens.TripGroupDetailScreen
import de.kornelriedl.drivetrack.ui.screens.TripGroupPickerScreen
import de.kornelriedl.drivetrack.ui.screens.TripGroupRouteScreen
import de.kornelriedl.drivetrack.ui.theme.DriveTrackTheme
import de.kornelriedl.drivetrack.tracking.LocationTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
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
    val groupDao = remember { db.tripGroupDao() }
    val tracker = remember { LocationTracker.getInstance(context) }

    val trips by tripDao.getAllTrips().collectAsState(initial = emptyList())
    val cars by carDao.getAllCars().collectAsState(initial = emptyList())
    val users by userDao.getAllUsers().collectAsState(initial = emptyList())
    val groups by groupDao.getAllGroups().collectAsState(initial = emptyList())

    // DEK aus dem verschlüsselten Gerätespeicher zurückholen, falls die App neu gestartet wurde
    // (z. B. durch Bluetooth-Auto-Start) - erst dadurch kann Auto-Sync auch dann laufen, ohne
    // dass der Nutzer die App vorher manuell geöffnet und entsperrt hat.
    LaunchedEffect(Unit) {
        if (!ServerSession.isUnlocked) {
            ServerAuthPreferences.getDek(context)?.let { ServerSession.setDek(it) }
        }
    }

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
    // Nur die Id merken, nicht das Car-Objekt: CarDetailScreen liest das aktuelle Car live aus
    // dem cars-Flow, keine manuelle Nachpflege wie bei selectedTrip nötig. Bewusst NICHT
    // "selectedCarId" nennen - der Name ist bereits für den globalen Home/Karte-Filter vergeben.
    var editingCarId by remember { mutableStateOf<Long?>(null) }
    val onDeleteCar: (Car) -> Unit = { car ->
        scope.launch {
            carDao.deleteCar(car)
            // Foto-Dateien mitlöschen, sonst bleiben sie für immer verwaist in filesDir liegen.
            withContext(Dispatchers.IO) { CarPhotoStore.deleteAllFor(context, car.id) }
        }
        if (selectedCarId == car.id) {
            selectedCarId = null
            CarPreferences.setSelectedCarId(context, null)
        }
        if (defaultCarId == car.id) {
            defaultCarId = null
            CarPreferences.setDefaultCarId(context, null)
        }
        editingCarId = null
    }
    val onSetCarBluetoothDevice: (Car, String?) -> Unit = { car, address ->
        scope.launch { carDao.updateCar(car.copy(bluetoothDeviceAddress = address)) }
    }
    val onRenameCar: (Car, String) -> Unit = { car, newName ->
        scope.launch { carDao.updateCar(car.copy(name = newName)) }
    }
    val onSetCarPhoto: (Car, Uri) -> Unit = { car, uri ->
        scope.launch {
            val newFileName = withContext(Dispatchers.IO) {
                CarPhotoStore.savePhotoFromUri(context, car.id, uri)
            }
            if (newFileName != null) {
                val oldFileName = car.photoFileName
                carDao.updateCar(car.copy(photoFileName = newFileName))
                // Erst NACH dem erfolgreichen Schreiben die alte Datei entfernen.
                if (oldFileName != null && oldFileName != newFileName) {
                    withContext(Dispatchers.IO) { CarPhotoStore.delete(context, oldFileName) }
                }
            } else {
                Toast.makeText(context, "Bild konnte nicht gelesen werden", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val onRemoveCarPhoto: (Car) -> Unit = { car ->
        scope.launch {
            carDao.updateCar(car.copy(photoFileName = null))
            car.photoFileName?.let { withContext(Dispatchers.IO) { CarPhotoStore.delete(context, it) } }
        }
    }

    // Nach Auto gefilterte Fahrten für Home & Fahrten (null = "Alle Autos")
    val filteredTrips = if (selectedCarId != null) trips.filter { it.carId == selectedCarId } else trips
    val filteredTotalKm = filteredTrips.sumOf { it.distanceMeters } / 1000.0
    val filteredTripCount = filteredTrips.size
    // "Fahrzeit" = Gesamtdauer minus über TripEditScreen herausgeschnittene Pausen (siehe
    // Trip.drivingDurationMinutes) - für unbearbeitete Fahrten (pausedMinutes == 0) identisch zu
    // vorher, keine sichtbare Änderung.
    val filteredTotalDurationMinutes = filteredTrips.sumOf { it.drivingDurationMinutes }
    val filteredAvgSpeedKmh = if (filteredTrips.isNotEmpty()) filteredTrips.map { it.avgSpeedKmh }.average() else 0.0
    // Gruppierte + einzelne Fahrten gemischt, neueste zuerst (siehe data/TripGrouping.kt) - für
    // Home (Dashboard) auf die neuesten 5 EINTRÄGE begrenzt (nicht 5 rohe Fahrten, sonst könnte
    // eine Gruppe mit vielen alten Fahrten die Home-Vorschau verdrängen), Fahrten-Tab zeigt alle.
    val filteredTripListEntries = buildTripListEntries(filteredTrips, groups)

    // Nur die Id merken, nicht das Trip-Objekt - TripDetailScreen liest die aktuelle Fahrt live aus
    // dem trips-Flow (wie editingCarId/editingTripId), damit Änderungen (Umbenennen, Zuschneiden,
    // Labels/Markierungen speichern, ...) sofort sichtbar sind, sobald man zurücknavigiert, statt
    // eine beim Öffnen eingefrorene Kopie anzuzeigen.
    var selectedTripId by remember { mutableStateOf<Long?>(null) }
    var showServerBackup by remember { mutableStateOf(false) }
    var showImportExport by remember { mutableStateOf(false) }
    // Nur die Id merken, nicht das Trip-Objekt - TripEditScreen liest die aktuelle Fahrt live aus
    // dem trips-Flow (Muster identisch zu editingCarId oben).
    var editingTripId by remember { mutableStateOf<Long?>(null) }
    // Gruppen-Detailseite (Muster identisch zu editingCarId/editingTripId - nur die Id merken, live
    // aus dem groups-Flow gelesen). showGroupPicker = Erstellen-Modus (von HomeScreen aus, "+
    // Gruppe"-Button), addToGroupId = Hinzufügen-Modus (aus TripGroupDetailScreen heraus, "Fahrten
    // hinzufügen"-Button) - beides steuert denselben TripGroupPickerScreen, siehe dessen Doc-Kommentar.
    var editingGroupId by remember { mutableStateOf<Long?>(null) }
    var showGroupPicker by remember { mutableStateOf(false) }
    var addToGroupId by remember { mutableStateOf<Long?>(null) }
    // Vollbild-Karte + kombinierter Geschwindigkeits-Graph einer Gruppe (TripGroupRouteScreen),
    // erreichbar über die kleine Übersichtskarte in TripGroupDetailScreen - liegt "über" dieser
    // (editingGroupId bleibt währenddessen gesetzt), reines Bool reicht daher (Muster wie
    // showServerBackup/showImportExport, nicht wie editingGroupId selbst eine eigene Id).
    var showGroupRoute by remember { mutableStateOf(false) }
    // Zentral genutzt nach JEDER trip-relevanten lokalen Änderung (Umbenennen, Zuschneiden,
    // Labels/Markierungen speichern, Auto zuordnen, Löschen) - vorher synchronisierte nur das Ende
    // einer Aufzeichnung, alle anderen Bearbeitungen blieben bis zur nächsten Fahrt unsynchronisiert.
    // syncFullBackupIfPossible liest/mergt/pusht selbst konfliktsicher (siehe ServerSync.kt), hier
    // nur "frisch aus der DB lesen und aufrufen" - suspend, daher innerhalb eines scope.launch nach
    // dem eigentlichen lokalen Schreibvorgang aufzurufen.
    val triggerBackgroundSync: suspend () -> Unit = {
        withContext(Dispatchers.IO) {
            ServerSync.syncFullBackupIfPossible(
                context,
                userDao.getAllUsers().first(),
                carDao.getAllCars().first(),
                tripDao.getAllTrips().first(),
                groupDao.getAllGroups().first()
            )
        }
    }
    // Runterziehen auf der Fahrtenliste (Home/Fahrten) löst denselben konfliktsicheren Sync manuell
    // aus, statt auf den nächsten automatischen Anlass zu warten - "einfach und sofort" über alle
    // Geräte aktuell halten. isRefreshing steuert nur die Indikator-Animation in PullToRefreshBox.
    var isRefreshing by remember { mutableStateOf(false) }
    val onManualSync: () -> Unit = {
        scope.launch {
            isRefreshing = true
            try {
                // Sichtbares Feedback, sonst wirkt "Runterziehen" bei fehlendem Server-Login (der
                // Sync tut dann still gar nichts, siehe ServerSync.kt) wie ein Bug statt wie
                // erwartetem Verhalten.
                if (ServerAuthPreferences.isLoggedIn(context)) {
                    triggerBackgroundSync()
                    Toast.makeText(context, "Aktualisiert", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(
                        context,
                        "Nicht mit dem Server verbunden (Einstellungen → Server-Backup)",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } finally {
                isRefreshing = false
            }
        }
    }
    // Labels/Markierungen werden mit übergeben (nicht nur der Zuschneide-Plan), damit noch nicht
    // gespeicherte Änderungen aus TripEditScreen beim Anwenden eines Zuschnitts nicht verloren gehen
    // (siehe TripEditScreen-Doc-Kommentar).
    val onApplyTripEdit: (Trip, TripEditPlan, List<String>, List<SegmentMark>) -> Unit = { trip, plan, labels, marks ->
        scope.launch {
            val tripWithPendingMetadata = trip.copy(labels = labels.toLabelsString(), segmentMarksJson = marks.toJson())
            val outcome = withContext(Dispatchers.IO) { applyTripEditPlan(tripWithPendingMetadata, context, plan) }
            if (outcome != null) {
                tripDao.updateTrip(outcome.trip)
                withContext(Dispatchers.IO) {
                    TrackFileStore.write(context, outcome.trip.id, outcome.newTrackJson)
                    MapThumbnailGenerator.invalidate(context, outcome.trip.id)
                    // Falls diese Fahrt Teil einer Gruppe ist, ist deren zwischengespeichertes
                    // Übersichts-Thumbnail jetzt veraltet (enthält noch die alte Route dieser Fahrt) -
                    // der Cache-Key dort ändert sich durch einen Zuschnitt NICHT (dieselbe Fahrten-Id-
                    // Menge), ohne explizite Invalidierung würde also weiter das alte Bild angezeigt.
                    outcome.trip.groupId?.let { groupId -> MapThumbnailGenerator.invalidateGroup(context, groupId) }
                }
                triggerBackgroundSync()
            } else {
                Toast.makeText(context, "Änderung ungültig (zu wenige Punkte übrig)", Toast.LENGTH_SHORT).show()
            }
            editingTripId = null
        }
    }
    // Ein einziger Schreibvorgang für Labels + Markierungen (statt einer pro Häkchen/Chip wie vor
    // 0.10.0) - TripEditScreen sammelt beides lokal und ruft das erst beim Verlassen/Speichern auf.
    val onSaveTripMetadata: (Trip, List<String>, List<SegmentMark>) -> Unit = { trip, labels, marks ->
        scope.launch {
            val newMax = withContext(Dispatchers.IO) { recomputeMaxSpeedExcludingMarks(trip, context, marks) }
            tripDao.updateTrip(trip.copy(labels = labels.toLabelsString(), segmentMarksJson = marks.toJson(), maxSpeedKmh = newMax))
            triggerBackgroundSync()
            editingTripId = null
        }
    }

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
        BackupExporter.shareBackup(context, users, cars, trips, groups)
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

    // System-"Zurück"-Taste: im Detail-Screen zurück zur Liste statt App schließen. editingTripId
    // absichtlich NICHT hier behandelt: TripEditScreen registriert dafür seinen eigenen BackHandler
    // (Rückfrage bei ungespeicherten Änderungen) - der zuletzt registrierte/aktive BackHandler hat
    // in Compose automatisch Vorrang, dieser Handler wird also nie für editingTripId aufgerufen,
    // solange TripEditScreen angezeigt wird.
    BackHandler(
        enabled = selectedTripId != null || showServerBackup || editingCarId != null || showImportExport ||
            editingGroupId != null || showGroupPicker || addToGroupId != null || showGroupRoute
    ) {
        when {
            // Picker liegt "über" allem anderen hier (Erstellen-Modus über Home/Fahrten, Hinzufügen-
            // Modus über TripGroupDetailScreen) - zuerst geprüft, analog zu editingTripId/selectedTripId.
            showGroupPicker -> showGroupPicker = false
            addToGroupId != null -> addToGroupId = null
            showGroupRoute -> showGroupRoute = false
            selectedTripId != null -> selectedTripId = null
            editingGroupId != null -> editingGroupId = null
            editingCarId != null -> editingCarId = null
            showImportExport -> showImportExport = false
            else -> showServerBackup = false
        }
    }

    // Zurück-Taste außerhalb dieser Screens: erst immer zu Home, App erst beim zweiten
    // Drücken (innerhalb von 2s) tatsächlich schließen
    val activity = context as? android.app.Activity
    var backPressedOnce by remember { mutableStateOf(false) }
    BackHandler(
        enabled = selectedTripId == null && !showServerBackup && editingCarId == null && !showImportExport &&
            editingTripId == null && editingGroupId == null && !showGroupPicker && addToGroupId == null &&
            !showGroupRoute
    ) {
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

    // Vor dem selectedTripId-Block geprüft: liegt "über" der Detailseite (die bleibt währenddessen
    // gesetzt) und springt beim Anwenden/Zurück wieder dorthin zurück, nicht bis auf Home.
    val currentEditingTrip = trips.find { it.id == editingTripId }
    if (currentEditingTrip != null) {
        TripEditScreen(
            trip = currentEditingTrip,
            onBack = { editingTripId = null },
            onApplyEdit = { plan, labels, marks -> onApplyTripEdit(currentEditingTrip, plan, labels, marks) },
            onSaveAndClose = { labels, marks -> onSaveTripMetadata(currentEditingTrip, labels, marks) }
        )
        return
    }

    // Liegt "über" allem anderen (Erstellen-Modus direkt über Home/Fahrten, Hinzufügen-Modus über
    // TripGroupDetailScreen, siehe dortiges addToGroupId) - deshalb vor den übrigen Blöcken geprüft.
    if (showGroupPicker || addToGroupId != null) {
        TripGroupPickerScreen(
            targetGroupId = addToGroupId,
            trips = trips,
            groups = groups,
            onCreateGroup = { name, tripIds ->
                scope.launch {
                    val newGroupId = groupDao.insertGroup(TripGroup(name = name))
                    tripIds.forEach { tripId ->
                        trips.find { it.id == tripId }?.let { trip ->
                            tripDao.updateTrip(trip.copy(groupId = newGroupId))
                        }
                    }
                    triggerBackgroundSync()
                    showGroupPicker = false
                }
            },
            onAddTrips = { tripIds ->
                val targetId = addToGroupId
                if (targetId != null) {
                    scope.launch {
                        tripIds.forEach { tripId ->
                            trips.find { it.id == tripId }?.let { trip ->
                                tripDao.updateTrip(trip.copy(groupId = targetId))
                            }
                        }
                        triggerBackgroundSync()
                        addToGroupId = null
                    }
                }
            },
            onBack = {
                showGroupPicker = false
                addToGroupId = null
            }
        )
        return
    }

    // Live aus dem trips-Flow abgeleitet statt eine beim Antippen eingefrorene Kopie zu halten -
    // dadurch sind Änderungen aus TripEditScreen (Zuschneiden, Labels/Markierungen speichern) sofort
    // sichtbar, sobald man dorthin zurückkehrt, ohne die Anzeige manuell nachpflegen zu müssen.
    val currentSelectedTrip = trips.find { it.id == selectedTripId }
    if (currentSelectedTrip != null) {
        TripDetailScreen(
            trip = currentSelectedTrip,
            cars = cars,
            onChangeCar = { trip, newCarId ->
                scope.launch {
                    tripDao.updateTrip(trip.copy(carId = newCarId))
                    triggerBackgroundSync()
                }
            },
            onEdit = { editingTripId = currentSelectedTrip.id },
            onBack = { selectedTripId = null }
        )
        return
    }

    if (showServerBackup) {
        ServerBackupScreen(
            users = users,
            cars = cars,
            trips = trips,
            groups = groups,
            onImportComplete = { /* Listen aktualisieren sich automatisch über die Flows */ },
            onBack = { showServerBackup = false }
        )
        return
    }

    val currentEditingCar = cars.find { it.id == editingCarId }
    if (currentEditingCar != null) {
        val carTrips = trips.filter { it.carId == currentEditingCar.id }
        CarDetailScreen(
            car = currentEditingCar,
            isDefaultCar = currentEditingCar.id == defaultCarId,
            tripCount = carTrips.size,
            totalKm = carTrips.sumOf { it.distanceMeters } / 1000.0,
            totalDurationMinutes = carTrips.sumOf { it.drivingDurationMinutes },
            avgSpeedKmh = if (carTrips.isNotEmpty()) carTrips.map { it.avgSpeedKmh }.average() else 0.0,
            maxSpeedKmh = carTrips.maxOfOrNull { it.maxSpeedKmh } ?: 0.0,
            onRenameCar = { newName -> onRenameCar(currentEditingCar, newName) },
            onSetPhoto = { uri -> onSetCarPhoto(currentEditingCar, uri) },
            onRemovePhoto = { onRemoveCarPhoto(currentEditingCar) },
            onSetBluetoothDevice = { address -> onSetCarBluetoothDevice(currentEditingCar, address) },
            onSetDefault = { isDefault -> onSetDefaultCar(if (isDefault) currentEditingCar.id else null) },
            onDeleteCar = { onDeleteCar(currentEditingCar) },
            onBack = { editingCarId = null }
        )
        return
    }

    val currentEditingGroup = groups.find { it.id == editingGroupId }

    // Liegt "über" TripGroupDetailScreen (editingGroupId bleibt währenddessen gesetzt) - deshalb vor
    // deren Block geprüft, analog zu editingTripId vor selectedTripId.
    if (showGroupRoute && currentEditingGroup != null) {
        TripGroupRouteScreen(
            group = currentEditingGroup,
            trips = trips.filter { it.groupId == currentEditingGroup.id },
            onBack = { showGroupRoute = false }
        )
        return
    }

    if (currentEditingGroup != null) {
        val groupTrips = trips.filter { it.groupId == currentEditingGroup.id }
        TripGroupDetailScreen(
            group = currentEditingGroup,
            trips = groupTrips,
            onRenameGroup = { newName ->
                scope.launch {
                    groupDao.updateGroup(currentEditingGroup.copy(name = newName))
                    triggerBackgroundSync()
                }
            },
            onOpenTrip = { trip -> selectedTripId = trip.id },
            onRemoveTripFromGroup = { trip ->
                scope.launch {
                    tripDao.updateTrip(trip.copy(groupId = null))
                    triggerBackgroundSync()
                }
            },
            onAddTrips = { addToGroupId = currentEditingGroup.id },
            onDeleteGroup = {
                scope.launch {
                    // Nur groupId der Mitgliedsfahrten zurücksetzen, NICHT die Fahrten löschen -
                    // sie erscheinen danach automatisch wieder einzeln in der Fahrtenliste
                    // (buildTripListEntries behandelt groupId == null als Einzelfahrt).
                    trips.filter { it.groupId == currentEditingGroup.id }.forEach { trip ->
                        tripDao.updateTrip(trip.copy(groupId = null))
                    }
                    groupDao.deleteGroup(currentEditingGroup)
                    // Gecachte Gruppen-Thumbnails aufräumen, sonst bleiben sie für immer verwaist
                    // in cacheDir liegen (spiegelt CarPhotoStore.deleteAllFor() beim Auto löschen).
                    withContext(Dispatchers.IO) { MapThumbnailGenerator.invalidateGroup(context, currentEditingGroup.id) }
                    triggerBackgroundSync()
                }
                editingGroupId = null
            },
            onOpenRoute = { showGroupRoute = true },
            onBack = { editingGroupId = null }
        )
        return
    }

    if (showImportExport) {
        ImportExportScreen(
            trips = trips,
            onImportGpx = importGpx,
            onExportBackup = onExportBackup,
            onImportBackup = onImportBackup,
            onBack = { showImportExport = false }
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
                entries = filteredTripListEntries.take(5),
                onTripClick = { selectedTripId = it.id },
                onGroupClick = { group -> editingGroupId = group.id },
                onRenameTrip = { trip, newName ->
                    scope.launch {
                        tripDao.updateTrip(trip.copy(name = newName))
                        triggerBackgroundSync()
                    }
                },
                onDeleteTrip = { trip ->
                    scope.launch {
                        tripDao.deleteTrip(trip)
                        withContext(Dispatchers.IO) { TrackFileStore.delete(context, trip.id) }
                        triggerBackgroundSync()
                    }
                },
                cars = cars,
                selectedCarId = selectedCarId,
                onSelectCar = onSelectCar,
                onAddCar = onAddCar,
                showDashboard = true,
                isRefreshing = isRefreshing,
                onRefresh = onManualSync,
                onCreateGroup = { showGroupPicker = true },
                modifier = Modifier.padding(padding)
            )
            NavTab.FAHRTEN -> HomeScreen(
                userName = activeUserName,
                totalKm = filteredTotalKm,
                tripCount = filteredTripCount,
                totalDurationMinutes = filteredTotalDurationMinutes,
                avgSpeedKmh = filteredAvgSpeedKmh,
                entries = filteredTripListEntries,
                onTripClick = { selectedTripId = it.id },
                onGroupClick = { group -> editingGroupId = group.id },
                onRenameTrip = { trip, newName ->
                    scope.launch {
                        tripDao.updateTrip(trip.copy(name = newName))
                        triggerBackgroundSync()
                    }
                },
                onDeleteTrip = { trip ->
                    scope.launch {
                        tripDao.deleteTrip(trip)
                        withContext(Dispatchers.IO) { TrackFileStore.delete(context, trip.id) }
                        triggerBackgroundSync()
                    }
                },
                cars = cars,
                selectedCarId = selectedCarId,
                onSelectCar = onSelectCar,
                onAddCar = onAddCar,
                showDashboard = false,
                isRefreshing = isRefreshing,
                onRefresh = onManualSync,
                onCreateGroup = { showGroupPicker = true },
                modifier = Modifier.padding(padding)
            )
            NavTab.AUFZEICHNEN -> RecordScreen(
                tracker = tracker,
                onRecordingFinished = { result ->
                    scope.launch {
                        val trip = result.toTrip(carId = selectedCarId)
                        val newId = tripDao.insertTrip(trip)
                        withContext(Dispatchers.IO) { TrackFileStore.write(context, newId, trip.gpxTrackJson) }
                        // Frisch aus der DB laden statt der Compose-State-Listen zu nehmen: die
                        // reaktiven Flows oben haben den gerade eingefügten Trip zu diesem
                        // Zeitpunkt noch nicht garantiert nachgezogen.
                        withContext(Dispatchers.IO) {
                            ServerSync.syncFullBackupIfPossible(
                                context,
                                userDao.getAllUsers().first(),
                                carDao.getAllCars().first(),
                                tripDao.getAllTrips().first()
                            )
                        }
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
                cars = cars,
                onAddCar = onAddCar,
                defaultCarId = defaultCarId,
                onOpenCar = { editingCarId = it.id },
                users = users,
                activeUserId = activeUserId,
                onSelectUser = onSelectUser,
                onAddUser = onAddUser,
                onDeleteUser = onDeleteUser,
                onOpenServerBackup = { showServerBackup = true },
                onOpenImportExport = { showImportExport = true },
                modifier = Modifier.padding(padding)
            )
        }
    }
}
