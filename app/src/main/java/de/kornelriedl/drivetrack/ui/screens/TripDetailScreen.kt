package de.kornelriedl.drivetrack.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DirectionsCar
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import de.kornelriedl.drivetrack.R
import de.kornelriedl.drivetrack.data.Car
import de.kornelriedl.drivetrack.data.Trip
import de.kornelriedl.drivetrack.export.GpxExporter
import org.osmdroid.util.BoundingBox
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.Color as ComposeColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDetailScreen(
    trip: Trip,
    cars: List<Car>,
    onChangeCar: (Trip, Long?) -> Unit,
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

            Card(
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                RouteDetailMap(trip = trip, scrubPoint = scrubPoint, modifier = Modifier.fillMaxSize())
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Wischbarer Bereich: Seite 1 = Stat-Kacheln, Seite 2 = Geschwindigkeits-Graph
            TripStatsPager(
                trip = trip,
                onScrub = { scrubPoint = it },
                modifier = Modifier.fillMaxWidth()
            )

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

@Composable
private fun RouteDetailMap(
    trip: Trip,
    scrubPoint: Pair<Double, Double>?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrubMarkerRef = remember { mutableStateOf<Marker?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            MapView(ctx).apply {
                setTileSource(DarkMatterTileSource)
                overlayManager.tilesOverlay.setColorFilter(buildContrastFilter())
                setMultiTouchControls(true)
                isTilesScaledToDpi = true
                zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
                minZoomLevel = 4.0
                maxZoomLevel = 20.0

                val points = trip.toGeoPoints()

                if (points.size >= 2) {
                    val polyline = Polyline(this).apply {
                        setPoints(points)
                        outlinePaint.color = AndroidColor.parseColor("#FF7A1A")
                        outlinePaint.strokeWidth = 10f
                        outlinePaint.isAntiAlias = true
                        // Verhindert die weiße Standard-Bubble beim Antippen der Route
                        setOnClickListener { _, _, _ -> true }
                    }
                    overlays.add(polyline)

                    val startMarker = Marker(this).apply {
                        position = points.first()
                        icon = ContextCompat.getDrawable(ctx, R.drawable.ic_start_marker)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        setOnMarkerClickListener { _, _ -> true }
                    }
                    overlays.add(startMarker)

                    val endMarker = Marker(this).apply {
                        position = points.last()
                        icon = ContextCompat.getDrawable(ctx, R.drawable.ic_finish_flag)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        setOnMarkerClickListener { _, _ -> true }
                    }
                    overlays.add(endMarker)

                    // Scrub-Marker: zeigt die Position, die im Geschwindigkeits-Graph gerade
                    // ausgewählt ist. Wird erst über update() sichtbar, sobald eine Auswahl existiert.
                    val scrubMarker = Marker(this).apply {
                        icon = ContextCompat.getDrawable(ctx, R.drawable.ic_location_dot)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        setOnMarkerClickListener { _, _ -> true }
                    }
                    scrubMarkerRef.value = scrubMarker

                    post {
                        val boundingBox = BoundingBox.fromGeoPoints(points)
                        zoomToBoundingBox(boundingBox, false, 100)
                    }
                } else {
                    controller.setZoom(15.0)
                    controller.setCenter(org.osmdroid.util.GeoPoint(47.8, 11.7))
                }

                overlays.add(DoubleTapDragZoomOverlay(ctx))
            }
        },
        update = { mapView ->
            val marker = scrubMarkerRef.value ?: return@AndroidView
            if (scrubPoint != null) {
                marker.position = org.osmdroid.util.GeoPoint(scrubPoint.first, scrubPoint.second)
                if (!mapView.overlays.contains(marker)) {
                    mapView.overlays.add(marker)
                }
            } else {
                mapView.overlays.remove(marker)
            }
            mapView.invalidate()
        }
    )
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
                else -> SpeedGraph(trip = trip, onScrub = onScrub, modifier = Modifier.fillMaxSize())
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
                value = "${trip.durationMinutes} min",
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
    }
}

/** Ein Punkt im Geschwindigkeits-Graphen. */
private data class GraphPoint(
    val offsetSeconds: Float,
    val speedKmh: Float,
    val cumulativeKm: Float,
    val timestamp: Long,
    val lat: Double,
    val lon: Double
)

@Composable
private fun SpeedGraph(
    trip: Trip,
    onScrub: (Pair<Double, Double>?) -> Unit,
    modifier: Modifier = Modifier
) {
    val points = remember(trip.id) { trip.toSpeedSeries() }

    if (points.size < 2) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                text = "Keine Geschwindigkeitsdaten vorhanden.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    var selectedIndex by remember(trip.id) { mutableStateOf(points.size - 1) }
    val maxSpeed = remember(points) { points.maxOf { it.speedKmh }.coerceAtLeast(1f) }
    val totalDuration = remember(points) { points.last().offsetSeconds.coerceAtLeast(1f) }
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.GERMANY) }

    // Meldet die aktuell ausgewählte Position an die Karte, damit dort der Scrub-Marker mitwandert
    LaunchedEffect(selectedIndex, points) {
        points.getOrNull(selectedIndex)?.let { onScrub(it.lat to it.lon) }
    }

    val accent = MaterialTheme.colorScheme.primary
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier = modifier) {
        // Info-Chip zum aktuell ausgewählten Punkt
        val info = points[selectedIndex]
        Row(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clip(RoundedCornerShape(14.dp))
                .background(surfaceVariant)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("🕐 ${timeFormat.format(Date(info.timestamp))}", style = MaterialTheme.typography.labelSmall)
            Text("📍 %.2f km".format(info.cumulativeKm), style = MaterialTheme.typography.labelSmall)
            Text("⚡ %.0f km/h".format(info.speedKmh), style = MaterialTheme.typography.labelSmall)
        }

        Spacer(modifier = Modifier.height(10.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .pointerInput(points) {
                    detectTapGestures { offset ->
                        val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                        selectedIndex = (fraction * (points.size - 1)).toInt().coerceIn(0, points.size - 1)
                    }
                }
                .pointerInput(points) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        val fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                        selectedIndex = (fraction * (points.size - 1)).toInt().coerceIn(0, points.size - 1)
                    }
                }
        ) {
            val w = size.width
            val h = size.height

            val path = Path()
            points.forEachIndexed { i, p ->
                val x = (p.offsetSeconds / totalDuration) * w
                val y = h - (p.speedKmh / maxSpeed) * h
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = accent,
                style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            val sel = points[selectedIndex]
            val selX = (sel.offsetSeconds / totalDuration) * w
            val selY = h - (sel.speedKmh / maxSpeed) * h

            drawLine(
                color = onSurfaceVariant.copy(alpha = 0.4f),
                start = Offset(selX, 0f),
                end = Offset(selX, h),
                strokeWidth = 2f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
            )
            drawCircle(color = ComposeColor.White, radius = 12f, center = Offset(selX, selY))
            drawCircle(
                color = accent,
                radius = 12f,
                center = Offset(selX, selY),
                style = Stroke(width = 4f)
            )
        }
    }
}

/** Wandelt die gespeicherten GPS-Punkte in eine Zeit/Geschwindigkeit/Distanz-Serie für den Graphen um. */
private fun Trip.toSpeedSeries(): List<GraphPoint> {
    val raw = toTrackPoints()
    if (raw.size < 2) return emptyList()

    val startTs = raw.first().third
    var cumulativeMeters = 0.0
    val result = mutableListOf<GraphPoint>()

    for (i in raw.indices) {
        val speedKmh = if (i == 0) {
            segmentSpeedKmh(raw[0], raw[1])
        } else {
            cumulativeMeters += haversineMetersPoints(raw[i - 1], raw[i])
            segmentSpeedKmh(raw[i - 1], raw[i])
        }
        result.add(
            GraphPoint(
                offsetSeconds = ((raw[i].third - startTs) / 1000).toFloat(),
                speedKmh = speedKmh.toFloat(),
                cumulativeKm = (cumulativeMeters / 1000.0).toFloat(),
                timestamp = raw[i].third,
                lat = raw[i].first,
                lon = raw[i].second
            )
        )
    }
    return result
}

private fun segmentSpeedKmh(p1: Triple<Double, Double, Long>, p2: Triple<Double, Double, Long>): Double {
    val dtSeconds = (p2.third - p1.third) / 1000.0
    if (dtSeconds <= 0) return 0.0
    return (haversineMetersPoints(p1, p2) / dtSeconds) * 3.6
}

private fun haversineMetersPoints(p1: Triple<Double, Double, Long>, p2: Triple<Double, Double, Long>): Double {
    val earthRadius = 6371000.0
    val dLat = Math.toRadians(p2.first - p1.first)
    val dLon = Math.toRadians(p2.second - p1.second)
    val a = kotlin.math.sin(dLat / 2).let { it * it } +
        kotlin.math.cos(Math.toRadians(p1.first)) * kotlin.math.cos(Math.toRadians(p2.first)) *
        kotlin.math.sin(dLon / 2).let { it * it }
    val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
    return earthRadius * c
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
