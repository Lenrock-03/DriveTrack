package de.kornelriedl.drivetrack.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TripOrigin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.kornelriedl.drivetrack.data.Car
import de.kornelriedl.drivetrack.data.Trip
import de.kornelriedl.drivetrack.ui.components.CarSelector
import de.kornelriedl.drivetrack.ui.components.TripListItem

@Composable
fun HomeScreen(
    userName: String,
    totalKm: Double,
    tripCount: Int,
    totalDurationMinutes: Long,
    avgSpeedKmh: Double,
    recentTrips: List<Trip>,
    onTripClick: (Trip) -> Unit,
    onRenameTrip: (Trip, String) -> Unit,
    onDeleteTrip: (Trip) -> Unit,
    cars: List<Car>,
    selectedCarId: Long?,
    onSelectCar: (Long?) -> Unit,
    onAddCar: (String) -> Unit,
    showDashboard: Boolean = true,
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

        Text(
            text = "Letzte Fahrten:",
            style = MaterialTheme.typography.titleLarge
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (recentTrips.isEmpty()) {
            Text(
                text = "Noch keine Fahrten aufgezeichnet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn {
                items(recentTrips) { trip ->
                    TripListItem(
                        trip = trip,
                        onClick = { onTripClick(trip) },
                        onLongClick = { actionTrip = trip }
                    )
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

@Composable
private fun StatCard(
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatDurationHm(totalMinutes: Long): String {
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}
