package de.kornelriedl.drivetrack.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TripOrigin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.kornelriedl.drivetrack.data.Car
import de.kornelriedl.drivetrack.data.Trip
import de.kornelriedl.drivetrack.data.labelIcon
import de.kornelriedl.drivetrack.data.labelList
import de.kornelriedl.drivetrack.data.segmentMarks
import de.kornelriedl.drivetrack.export.GpxExporter
import de.kornelriedl.drivetrack.ui.components.RouteColorLegend
import de.kornelriedl.drivetrack.ui.components.RouteColorMode
import de.kornelriedl.drivetrack.ui.components.RouteColorModeSelector
import de.kornelriedl.drivetrack.ui.components.RouteDetailMap
import de.kornelriedl.drivetrack.ui.components.SegmentMarkRow
import de.kornelriedl.drivetrack.ui.components.SpeedGraph
import de.kornelriedl.drivetrack.ui.components.formatDurationHm
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDetailScreen(
    trip: Trip,
    cars: List<Car>,
    onChangeCar: (Trip, Long?) -> Unit,
    onEdit: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var showCarDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(trip.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Filled.Edit, contentDescription = "Fahrt bearbeiten")
                    }
                    IconButton(onClick = { GpxExporter.shareTrip(context, trip) }) {
                        Icon(Icons.Filled.IosShare, contentDescription = "Als GPX exportieren")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            val dateFormat = remember(trip.startTimestamp) {
                SimpleDateFormat("EEEE, d. MMMM yyyy", Locale.GERMANY)
            }
            val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.GERMANY) }
            Text(
                text = "${dateFormat.format(Date(trip.startTimestamp))} · " +
                    "${timeFormat.format(Date(trip.startTimestamp))} – " +
                    "${timeFormat.format(Date(trip.endTimestamp))} Uhr",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val labels = trip.labelList()
            if (labels.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    labels.forEach { label ->
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                "${labelIcon(label)} $label",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            val carName = cars.find { it.id == trip.carId }?.name ?: "Kein Auto zugewiesen"
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { showCarDialog = true }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.DirectionsCar,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(carName, style = MaterialTheme.typography.labelMedium)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Routenkarte: füllt den kompletten restlichen Platz bis zum Stats/Graph-Bereich
            var scrubPoint by remember { mutableStateOf<Pair<Double, Double>?>(null) }
            var routeColorMode by remember { mutableStateOf(RouteColorMode.STANDARD) }

            Card(
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    RouteDetailMap(
                        trip = trip,
                        scrubPoint = scrubPoint,
                        routeColorMode = routeColorMode,
                        modifier = Modifier.fillMaxSize()
                    )
                    RouteColorModeSelector(
                        selected = routeColorMode,
                        onSelect = { routeColorMode = it },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                    )
                    if (routeColorMode == RouteColorMode.SPEED) {
                        RouteColorLegend(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(10.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Wischbarer Bereich: Seite 1 = Stat-Kacheln, Seite 2 = Geschwindigkeits-Graph
            TripStatsPager(
                trip = trip,
                onScrub = { scrubPoint = it },
                modifier = Modifier.fillMaxWidth()
            )

            val marks = trip.segmentMarks()
            if (marks.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Markierte Abschnitte",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                marks.forEach { mark ->
                    SegmentMarkRow(trip = trip, mark = mark, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showCarDialog) {
        AlertDialog(
            onDismissRequest = { showCarDialog = false },
            title = { Text("Auto zuordnen") },
            text = {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onChangeCar(trip, null)
                                showCarDialog = false
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = trip.carId == null, onClick = {
                            onChangeCar(trip, null)
                            showCarDialog = false
                        })
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Kein Auto")
                    }
                    cars.forEach { car ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onChangeCar(trip, car.id)
                                    showCarDialog = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = trip.carId == car.id, onClick = {
                                onChangeCar(trip, car.id)
                                showCarDialog = false
                            })
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(car.name)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCarDialog = false }) {
                    Text("Fertig")
                }
            }
        )
    }
}

/**
 * Wischbarer Bereich unter der Karte: Seite 1 klassische Stat-Kacheln,
 * Seite 2 ein Geschwindigkeits-Graph mit verschiebbarem Punkt (Uhrzeit/km-Stand/Speed).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TripStatsPager(
    trip: Trip,
    onScrub: (Pair<Double, Double>?) -> Unit,
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState(initialPage = 0) { 2 }

    // Marker auf der Karte ausblenden, sobald man nicht mehr auf der Graph-Seite ist
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage != 1) onScrub(null)
    }

    Column(modifier = modifier) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
        ) { page ->
            when (page) {
                0 -> StatsGrid(trip = trip, modifier = Modifier.fillMaxSize())
                else -> SpeedGraph(
                    trip = trip,
                    onScrub = { point -> onScrub(point?.let { it.lat to it.lon }) },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(2) { index ->
                val selected = pagerState.currentPage == index
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(if (selected) 8.dp else 7.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) MaterialTheme.colorScheme.onBackground
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                        )
                )
            }
        }
    }
}

@Composable
private fun StatsGrid(trip: Trip, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.Center) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DetailStatCard(
                icon = Icons.Filled.TripOrigin,
                value = "%.2f km".format(trip.distanceKm),
                label = "Distanz",
                modifier = Modifier.weight(1f)
            )
            DetailStatCard(
                icon = Icons.Filled.Timer,
                value = trip.durationFormatted,
                label = "Dauer",
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DetailStatCard(
                icon = Icons.Filled.Speed,
                value = "%.0f km/h".format(trip.avgSpeedKmh),
                label = "Ø Geschwindigkeit",
                modifier = Modifier.weight(1f)
            )
            DetailStatCard(
                icon = Icons.Filled.Route,
                value = "%.0f km/h".format(trip.maxSpeedKmh),
                label = "Max. Geschwindigkeit",
                modifier = Modifier.weight(1f)
            )
        }
        if (trip.pausedMinutes > 0) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "− ${formatDurationHm(trip.pausedMinutes)} Pause = " +
                    "${formatDurationHm(trip.drivingDurationMinutes)} Fahrzeit",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DetailStatCard(
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
