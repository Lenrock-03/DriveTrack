package de.kornelriedl.drivetrack.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TripOrigin
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.kornelriedl.drivetrack.data.Car
import de.kornelriedl.drivetrack.data.Trip
import de.kornelriedl.drivetrack.data.TripGroup
import de.kornelriedl.drivetrack.data.TripListEntry
import de.kornelriedl.drivetrack.ui.components.CarSelector
import de.kornelriedl.drivetrack.ui.components.StatCard
import de.kornelriedl.drivetrack.ui.components.TripGroupListItem
import de.kornelriedl.drivetrack.ui.components.TripListItem
import de.kornelriedl.drivetrack.ui.components.formatDurationHm

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    userName: String,
    totalKm: Double,
    tripCount: Int,
    totalDurationMinutes: Long,
    avgSpeedKmh: Double,
    entries: List<TripListEntry>,
    onTripClick: (Trip) -> Unit,
    onGroupClick: (TripGroup) -> Unit,
    onRenameTrip: (Trip, String) -> Unit,
    onDeleteTrip: (Trip) -> Unit,
    cars: List<Car>,
    selectedCarId: Long?,
    onSelectCar: (Long?) -> Unit,
    onAddCar: (String) -> Unit,
    showDashboard: Boolean = true,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    onCreateGroup: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Long-Press-Aktionsmenü: welche Fahrt ist gerade "ausgewählt"
    var actionTrip by remember { mutableStateOf<Trip?>(null) }
    var renameTrip by remember { mutableStateOf<Trip?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteTrip by remember { mutableStateOf<Trip?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Hallo $userName",
                style = MaterialTheme.typography.headlineMedium
            )
            CarSelector(
                cars = cars,
                selectedCarId = selectedCarId,
                onSelectCar = onSelectCar,
                onAddCar = onAddCar
            )
        }

        if (showDashboard) {
            Spacer(modifier = Modifier.height(20.dp))

            // Dashboard: die wichtigsten Kennzahlen auf einen Blick
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    icon = Icons.Filled.TripOrigin,
                    value = "%.0f km".format(totalKm),
                    label = "Gesamt",
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    icon = Icons.Filled.Route,
                    value = tripCount.toString(),
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
                    value = formatDurationHm(totalDurationMinutes),
                    label = "Fahrzeit",
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    icon = Icons.Filled.Speed,
                    value = "%.0f km/h".format(avgSpeedKmh),
                    label = "Ø Speed",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        Divider()
        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Letzte Fahrten:",
                style = MaterialTheme.typography.titleLarge
            )
            // Nur im Fahrten-Tab (nicht auf dem Dashboard) - Gruppieren ist eine "Verwaltungs"-
            // Aktion, die auf Home nicht im Weg stehen soll.
            if (!showDashboard) {
                IconButton(onClick = onCreateGroup) {
                    Icon(Icons.Filled.CreateNewFolder, contentDescription = "Fahrten gruppieren")
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Runterziehen aktualisiert manuell mit dem Server (pull-check-merge-push, siehe
        // ServerSync.kt) - "Runterziehen" auf beiden Geräten + der "Aktualisieren"-Button in der
        // Web-App sollen sich einfach/sofort anfühlen, statt auf den nächsten automatischen
        // Sync-Anlass warten zu müssen. Box mit Modifier.weight(1f), damit PullToRefreshBox eine
        // konkrete Höhe innerhalb der äußeren (nicht scrollenden) Column bekommt.
        Box(modifier = Modifier.weight(1f)) {
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize()
            ) {
                if (entries.isEmpty()) {
                    // Muss selbst scrollbar sein, damit die Pull-Geste hier erkannt wird
                    // (PullToRefreshBox braucht einen Scroll-Container als Kind).
                    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        Text(
                            text = "Noch keine Fahrten aufgezeichnet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(entries) { entry ->
                            when (entry) {
                                is TripListEntry.SingleTrip -> TripListItem(
                                    trip = entry.trip,
                                    onClick = { onTripClick(entry.trip) },
                                    onLongClick = { actionTrip = entry.trip }
                                )
                                // Gruppen bekommen kein Long-Press-Aktionsmenü hier - Umbenennen/
                                // Löschen einer Gruppe lebt bewusst im TripGroupDetailScreen, nicht
                                // auf der zusammengefassten Zeile (vermeidet ein zweites, Gruppen-
                                // spezifisches Aktionsmenü direkt in dieser Liste).
                                is TripListEntry.Group -> TripGroupListItem(
                                    group = entry.group,
                                    trips = entry.trips,
                                    onClick = { onGroupClick(entry.group) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Aktionsmenü nach Long-Press: Umbenennen oder Löschen
    actionTrip?.let { trip ->
        AlertDialog(
            onDismissRequest = { actionTrip = null },
            title = { Text(trip.name) },
            text = { Text("Was möchtest du mit dieser Fahrt tun?") },
            confirmButton = {
                TextButton(onClick = {
                    renameText = trip.name
                    renameTrip = trip
                    actionTrip = null
                }) {
                    Text("Umbenennen")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    deleteTrip = trip
                    actionTrip = null
                }) {
                    Text("Löschen", color = MaterialTheme.colorScheme.error)
                }
            }
        )
    }

    // Umbenennen-Dialog
    renameTrip?.let { trip ->
        AlertDialog(
            onDismissRequest = { renameTrip = null },
            title = { Text("Fahrt umbenennen") },
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
                        onRenameTrip(trip, renameText.trim())
                    }
                    renameTrip = null
                }) {
                    Text("Speichern")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTrip = null }) {
                    Text("Abbrechen")
                }
            }
        )
    }

    // Lösch-Bestätigung
    deleteTrip?.let { trip ->
        AlertDialog(
            onDismissRequest = { deleteTrip = null },
            title = { Text("Fahrt löschen?") },
            text = { Text("\u201E${trip.name}\u201C wird endgültig gelöscht. Das kann nicht rückgängig gemacht werden.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteTrip(trip)
                    deleteTrip = null
                }) {
                    Text("Löschen", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTrip = null }) {
                    Text("Abbrechen")
                }
            }
        )
    }
}

