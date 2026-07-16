package de.kornelriedl.drivetrack.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import de.kornelriedl.drivetrack.data.Car
import de.kornelriedl.drivetrack.tracking.LocationTracker
import de.kornelriedl.drivetrack.tracking.RecordingResult
import de.kornelriedl.drivetrack.tracking.TripTrackingService

@Composable
fun RecordScreen(
    tracker: LocationTracker,
    onRecordingFinished: (RecordingResult) -> Unit,
    cars: List<Car>,
    selectedCarId: Long?,
    onSelectCar: (Long?) -> Unit,
    defaultCarId: Long? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var showCarPicker by remember { mutableStateOf(false) }
    var pendingCarId by remember(selectedCarId) { mutableStateOf(selectedCarId) }

    fun beginRecording() {
        tracker.start()
        TripTrackingService.start(context)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Aufzeichnung funktioniert auch ohne, dann nur ohne sichtbare Notification */ }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Aufzeichnen",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Live-Statistik-Karte (jetzt oben)
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatColumn("Dauer", formatDuration(tracker.elapsedSeconds))
                StatColumn("Distanz", "%.2f km".format(tracker.distanceMeters / 1000.0))
                StatColumn("Speed", "%.0f km/h".format(tracker.currentSpeedKmh))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Live-Karte: füllt den kompletten restlichen Platz bis zum Aufzeichnen-Button
        Card(
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            LiveRouteMap(trackPoints = tracker.trackPoints, modifier = Modifier.fillMaxSize())
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (!hasPermission) {
            Text(
                text = "Standortzugriff wird benötigt, um Fahrten aufzuzeichnen.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        if (!tracker.isRecording) {
            // Noch keine Aufzeichnung aktiv: ein großer Start-Button
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        if (!hasPermission) {
                            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                            return@clickable
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        if (cars.isNotEmpty()) {
                            pendingCarId = defaultCarId ?: selectedCarId ?: cars.first().id
                            showCarPicker = true
                        } else {
                            beginRecording()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.FiberManualRecord,
                    contentDescription = "Aufzeichnung starten",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(40.dp)
                )
            }
        } else {
            // Aufzeichnung läuft: Pause/Fortsetzen + Stopp nebeneinander
            Row(
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            if (tracker.isPaused) tracker.resume() else tracker.pause()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (tracker.isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                        contentDescription = if (tracker.isPaused) "Fortsetzen" else "Pausieren",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            val result = tracker.stop()
                            TripTrackingService.stop(context)
                            onRecordingFinished(result)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Stop,
                        contentDescription = "Aufzeichnung stoppen",
                        tint = MaterialTheme.colorScheme.onError,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = when {
                !tracker.isRecording -> "Tippen zum Starten"
                tracker.isPaused -> "Pausiert – tippen zum Fortsetzen"
                else -> "Läuft – Pause oder Stopp tippen"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))
    }

    if (showCarPicker) {
        AlertDialog(
            onDismissRequest = { showCarPicker = false },
            title = { Text("Welches Auto fährst du?") },
            text = {
                Column {
                    cars.forEach { car ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { pendingCarId = car.id }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = pendingCarId == car.id,
                                onClick = { pendingCarId = car.id }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(car.name)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onSelectCar(pendingCarId)
                    showCarPicker = false
                    beginRecording()
                }) {
                    Text("Los geht's")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCarPicker = false }) {
                    Text("Abbrechen")
                }
            }
        )
    }
}

@Composable
private fun StatColumn(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
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

private fun formatDuration(totalSeconds: Long): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
