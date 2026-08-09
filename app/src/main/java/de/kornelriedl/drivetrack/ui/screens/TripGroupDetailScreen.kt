package de.kornelriedl.drivetrack.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TripOrigin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import de.kornelriedl.drivetrack.data.Trip
import de.kornelriedl.drivetrack.data.TripGroup
import de.kornelriedl.drivetrack.data.groupStats
import de.kornelriedl.drivetrack.ui.components.GroupRouteMap
import de.kornelriedl.drivetrack.ui.components.StatCard
import de.kornelriedl.drivetrack.ui.components.TripListItem
import de.kornelriedl.drivetrack.ui.components.formatDurationHm

/**
 * Detailseite einer manuell erstellten Fahrten-Gruppe (z.B. "Urlaub Kroatien"): Gesamt-Statistik
 * über alle Mitgliedsfahrten (`List<Trip>.groupStats()`, spiegelt CarDetailScreens Statistik-
 * Kacheln), darunter die einzelnen Fahrten - Tap öffnet die normale TripDetailScreen wie überall
 * sonst, Long-Press entfernt nur aus der Gruppe (löscht die Fahrt NICHT). Umbenennen läuft NICHT
 * inline in dieser Ansicht (anders als CarDetailScreen), sondern über den Stift-Button in der
 * TopAppBar + einen eigenen Dialog (spiegelt das "Fahrt umbenennen"-Dialogmuster in
 * HomeScreen.kt) - die normale Gruppenansicht soll nicht ständig ein bearbeitbares Textfeld zeigen.
 * Callbacks nehmen bewusst keine Group/Trip-Referenz für "welche Gruppe" (MainActivity kennt sie
 * schon), analog zu CarDetailScreen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripGroupDetailScreen(
    group: TripGroup,
    trips: List<Trip>,
    onRenameGroup: (String) -> Unit,
    onOpenTrip: (Trip) -> Unit,
    onRemoveTripFromGroup: (Trip) -> Unit,
    onAddTrips: () -> Unit,
    onDeleteGroup: () -> Unit,
    onOpenRoute: () -> Unit,
    onBack: () -> Unit
) {
    var deleteDialogOpen by remember { mutableStateOf(false) }
    var removeTrip by remember { mutableStateOf<Trip?>(null) }
    var renameDialogOpen by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf(group.name) }
    val stats = trips.groupStats()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(group.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        renameText = group.name
                        renameDialogOpen = true
                    }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Gruppe umbenennen")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Übersichtskarte mit der Route jeder Mitgliedsfahrt (siehe GroupRouteMap-Doc-Kommentar).
            // interactive = false: rein statische Vorschau, sonst "klaut" jede Berührung die
            // Karten-Pan-Geste (osmdroid), noch bevor ein Klick irgendwo ankommt - das
            // Vergrößerungs-Icon oben rechts öffnet stattdessen TripGroupRouteScreen (Vollbild-
            // Karte + kombinierter Geschwindigkeits-Graph), als EIGENE Compose-Fläche über der
            // Karte platziert (kein Touch-Konflikt mit dem AndroidView darunter, spiegelt
            // RouteColorModeSelector/RouteColorLegend über RouteDetailMap in TripDetailScreen).
            // Feste Höhe statt Modifier.weight(1f) wie bei TripDetailScreen - dieser Screen ist
            // komplett vertikal scrollbar, weight() ist dort nicht kompatibel. key(...) über die
            // Fahrt-Ids sorgt dafür, dass die Karte neu aufgebaut wird, sobald sich die Mitglieder
            // ändern (z.B. nach "Fahrten hinzufügen"/Entfernen), nicht nur beim ersten Öffnen.
            if (trips.isNotEmpty()) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        key(trips.map { it.id }) {
                            GroupRouteMap(trips = trips, interactive = false, modifier = Modifier.fillMaxSize())
                        }
                        IconButton(
                            onClick = onOpenRoute,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(10.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                        ) {
                            Icon(Icons.Filled.Fullscreen, contentDescription = "Große Karte mit Graph öffnen")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            // Statistik - spiegelt CarDetailScreen/Home-Dashboard (StatCard), plus Höchstgeschwindigkeit
            Text(
                text = "Statistik",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    icon = Icons.Filled.TripOrigin,
                    value = "%.0f km".format(stats.totalKm),
                    label = "Gesamt",
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    icon = Icons.Filled.Route,
                    value = stats.tripCount.toString(),
                    label = "Fahrten",
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    icon = Icons.Filled.Timer,
                    value = formatDurationHm(stats.totalDrivingMinutes),
                    label = "Fahrzeit",
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    icon = Icons.Filled.Speed,
                    value = "%.0f km/h".format(stats.avgSpeedKmh),
                    label = "Ø Speed",
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    icon = Icons.Filled.Bolt,
                    value = "%.0f km/h".format(stats.maxSpeedKmh),
                    label = "Höchstgeschwindigkeit",
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(20.dp))

            OutlinedButton(
                onClick = onAddTrips,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.PlaylistAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Fahrten hinzufügen")
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Fahrten in dieser Gruppe",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )
            if (trips.isEmpty()) {
                Text(
                    text = "Noch keine Fahrten in dieser Gruppe.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                trips.sortedByDescending { it.startTimestamp }.forEach { trip ->
                    TripListItem(
                        trip = trip,
                        onClick = { onOpenTrip(trip) },
                        onLongClick = { removeTrip = trip }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedButton(
                onClick = { deleteDialogOpen = true },
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Gruppe löschen")
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Umbenennen-Dialog (spiegelt HomeScreens "Fahrt umbenennen"-Dialog) - bewusst NICHT inline in
    // der normalen Ansicht, siehe Doc-Kommentar oben.
    if (renameDialogOpen) {
        AlertDialog(
            onDismissRequest = { renameDialogOpen = false },
            title = { Text("Gruppe umbenennen") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    label = { Text("Name") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (renameText.isNotBlank()) {
                        onRenameGroup(renameText.trim())
                    }
                    renameDialogOpen = false
                }) {
                    Text("Speichern")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameDialogOpen = false }) {
                    Text("Abbrechen")
                }
            }
        )
    }

    // Aus Gruppe entfernen (löscht die Fahrt selbst nicht)
    removeTrip?.let { trip ->
        AlertDialog(
            onDismissRequest = { removeTrip = null },
            title = { Text("Aus Gruppe entfernen?") },
            text = {
                Text(
                    "„${trip.name}“ wird aus „${group.name}“ entfernt und erscheint danach wieder " +
                        "einzeln in der Fahrtenliste. Die Fahrt selbst bleibt erhalten."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onRemoveTripFromGroup(trip)
                    removeTrip = null
                }) {
                    Text("Entfernen")
                }
            },
            dismissButton = {
                TextButton(onClick = { removeTrip = null }) {
                    Text("Abbrechen")
                }
            }
        )
    }

    if (deleteDialogOpen) {
        AlertDialog(
            onDismissRequest = { deleteDialogOpen = false },
            title = { Text("Gruppe löschen?") },
            text = {
                Text(
                    "„${group.name}“ wird gelöscht. Die enthaltenen Fahrten bleiben erhalten und " +
                        "erscheinen danach wieder einzeln in der Fahrtenliste."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    deleteDialogOpen = false
                    onDeleteGroup()
                }) {
                    Text("Löschen", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteDialogOpen = false }) {
                    Text("Abbrechen")
                }
            }
        )
    }
}
