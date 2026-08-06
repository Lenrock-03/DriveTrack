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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import kotlin.math.roundToInt
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

/** Anzeigemodus der Routen-Linie auf der Detail-Karte, umschaltbar über RouteColorModeSelector. */
private enum class RouteColorMode { STANDARD, SPEED }

@Composable
private fun RouteDetailMap(
    trip: Trip,
    scrubPoint: Pair<Double, Double>?,
    routeColorMode: RouteColorMode,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrubMarkerRef = remember { mutableStateOf<Marker?>(null) }
    // Aktuell gezeichnete Routen-Layer (Standard-Linie oder Geschwindigkeits-Segmente), damit sie
    // sich beim Umschalten des Modus gezielt entfernen lassen, ohne Start-/Ziel-/Scrub-Marker anzufassen.
    val routeOverlaysRef = remember { mutableStateOf<List<Polyline>>(emptyList()) }
    val appliedModeRef = remember { mutableStateOf<RouteColorMode?>(null) }

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

                val points = trip.toGeoPoints(ctx)

                if (points.size >= 2) {
                    applyRouteColorMode(this, trip, ctx, points, routeColorMode, routeOverlaysRef)
                    appliedModeRef.value = routeColorMode

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
            val marker = scrubMarkerRef.value
            if (marker != null) {
                if (scrubPoint != null) {
                    marker.position = org.osmdroid.util.GeoPoint(scrubPoint.first, scrubPoint.second)
                    if (!mapView.overlays.contains(marker)) {
                        mapView.overlays.add(marker)
                    }
                } else {
                    mapView.overlays.remove(marker)
                }
            }

            // Route nur neu einfärben, wenn sich der Modus seit dem letzten Durchlauf geändert hat
            // (nicht bei jedem Scrub-Update während des Ziehens im Graphen).
            if (appliedModeRef.value != routeColorMode) {
                val points = trip.toGeoPoints(context)
                if (points.size >= 2) {
                    applyRouteColorMode(mapView, trip, context, points, routeColorMode, routeOverlaysRef)
                }
                appliedModeRef.value = routeColorMode
            }

            mapView.invalidate()
        }
    )
}

/**
 * Entfernt die zuvor gezeichnete(n) Routen-Linie(n) und zeichnet sie im gewählten Modus neu
 * (Standard-Farbe oder nach Geschwindigkeit eingefärbt). Wird an Index 0 eingefügt, damit sie
 * immer unter Start-/Ziel-/Scrub-Marker liegt, egal in welcher Reihenfolge das passiert.
 */
private fun applyRouteColorMode(
    mapView: MapView,
    trip: Trip,
    context: android.content.Context,
    points: List<org.osmdroid.util.GeoPoint>,
    mode: RouteColorMode,
    routeOverlaysRef: MutableState<List<Polyline>>
) {
    routeOverlaysRef.value.forEach { mapView.overlays.remove(it) }

    val newOverlays = when (mode) {
        RouteColorMode.STANDARD -> listOf(
            Polyline(mapView).apply {
                setPoints(points)
                outlinePaint.color = AndroidColor.parseColor("#FF7A1A")
                outlinePaint.strokeWidth = 10f
                outlinePaint.isAntiAlias = true
                // Verhindert die weiße Standard-Bubble beim Antippen der Route
                setOnClickListener { _, _, _ -> true }
            }
        )
        RouteColorMode.SPEED -> buildSpeedColoredSegments(mapView, trip, context)
    }

    mapView.overlays.addAll(0, newOverlays)
    routeOverlaysRef.value = newOverlays
}

// Bei sehr langen Fahrten (viele tausend GPS-Punkte) würde ein Overlay pro Segment das
// Kartenrendering spürbar verlangsamen (jedes Polyline-Overlay wird bei jedem Pan/Zoom neu
// gezeichnet) - deshalb auf maximal so viele Segmente heruntersampeln.
private const val MAX_ROUTE_COLOR_SEGMENTS = 1500

// Feste, einheitliche Geschwindigkeits-Farbskala (bewusst NICHT relativ zur einzelnen Fahrt) -
// dieselbe Farbe bedeutet dadurch bei jeder Fahrt dieselbe Geschwindigkeit, vergleichbar zwischen
// z.B. einer Stadtfahrt und einer Autobahnfahrt. Zweistufig: 0-130 km/h grün->rot (130 = Richt-
// geschwindigkeit Autobahn), 130-180 km/h zusätzlich rot->lila zur klaren Abhebung sehr hoher
// Geschwindigkeiten. Alles über 180 km/h wird auf volles Lila gekappt. Spiegelt
// ROUTE_COLOR_RED_KMH/ROUTE_COLOR_PURPLE_KMH in js/app.js.
private const val ROUTE_COLOR_RED_KMH = 130f
private const val ROUTE_COLOR_PURPLE_KMH = 180f

/** Baut die Route als mehrere kurze, nach Geschwindigkeit eingefärbte Segmente (grün -> rot). */
private fun buildSpeedColoredSegments(mapView: MapView, trip: Trip, context: android.content.Context): List<Polyline> {
    val series = trip.toSpeedSeries(context).medianFiltered()
    if (series.size < 2) return emptyList()
    val step = ((series.size - 1) / MAX_ROUTE_COLOR_SEGMENTS).coerceAtLeast(1)

    val segments = mutableListOf<Polyline>()
    var i = 0
    while (i < series.size - 1) {
        val end = (i + step).coerceAtMost(series.size - 1)
        val segmentPoints = (i..end).map { org.osmdroid.util.GeoPoint(series[it].lat, series[it].lon) }
        val avgSpeed = (i..end).map { series[it].speedKmh }.average().toFloat()
        segments.add(
            Polyline(mapView).apply {
                setPoints(segmentPoints)
                outlinePaint.color = speedToColor(avgSpeed)
                outlinePaint.strokeWidth = 10f
                outlinePaint.isAntiAlias = true
                setOnClickListener { _, _, _ -> true }
            }
        )
        i = end
    }
    return segments
}

/** Grün (langsam) -> Rot (130 km/h) -> Lila (ab 180 km/h) auf der festen Skala. */
private fun speedToColor(speedKmh: Float): Int {
    val hue = if (speedKmh <= ROUTE_COLOR_RED_KMH) {
        val fraction = (speedKmh / ROUTE_COLOR_RED_KMH).coerceIn(0f, 1f)
        120f * (1f - fraction) // 120 (grün) .. 0 (rot)
    } else {
        val fraction = ((speedKmh - ROUTE_COLOR_RED_KMH) / (ROUTE_COLOR_PURPLE_KMH - ROUTE_COLOR_RED_KMH)).coerceIn(0f, 1f)
        360f - 75f * fraction // 360/0 (rot) .. 285 (lila), kurzer Weg (nicht zurück durch Gelb/Grün)
    }
    return android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.85f, 0.85f))
}

@Composable
private fun RouteColorModeSelector(
    selected: RouteColorMode,
    onSelect: (RouteColorMode) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                .clickable { expanded = true }
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (selected == RouteColorMode.STANDARD) "Standard" else "Geschwindigkeit",
                style = MaterialTheme.typography.labelSmall
            )
            Spacer(modifier = Modifier.width(2.dp))
            Icon(Icons.Filled.ArrowDropDown, contentDescription = "Routen-Farbe wählen", modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Standard (orange)") },
                onClick = { onSelect(RouteColorMode.STANDARD); expanded = false },
                leadingIcon = if (selected == RouteColorMode.STANDARD) {
                    { Icon(Icons.Filled.Check, contentDescription = null) }
                } else null
            )
            DropdownMenuItem(
                text = { Text("Nach Geschwindigkeit") },
                onClick = { onSelect(RouteColorMode.SPEED); expanded = false },
                leadingIcon = if (selected == RouteColorMode.SPEED) {
                    { Icon(Icons.Filled.Check, contentDescription = null) }
                } else null
            )
        }
    }
}

/** Erklärt die feste Geschwindigkeits-Farbskala, nur sichtbar solange RouteColorMode.SPEED aktiv ist. */
@Composable
private fun RouteColorLegend(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .width(150.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(
                    // Feine 10-km/h-Schritte, damit der Knick bei 130 (rot) glatt in den Verlauf übergeht
                    Brush.horizontalGradient(
                        (0..ROUTE_COLOR_PURPLE_KMH.toInt() step 10).map { ComposeColor(speedToColor(it.toFloat())) }
                    )
                )
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("0", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("130", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("180+ km/h", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
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
    val context = LocalContext.current
    // Median-Filter gegen einzelne GPS-Ausreißer (kurzer ungenauer Fix -> rechnerisch absurd hohe
    // Distanz/Zeit-Geschwindigkeit) - siehe medianFiltered(). Ohne den Filter sehen solche
    // Ausreißer wie künstliche Nadel-Spitzen statt eines realistischen Geschwindigkeitsverlaufs aus.
    val points = remember(trip.id) { trip.toSpeedSeries(context).medianFiltered() }

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
    // Zusätzliche Sicherheitsgrenze: trip.maxSpeedKmh kommt von loc.speed (GPS-Chip, Doppler-
    // basiert, deutlich robuster als unsere eigene Positions-Differenz-Rechnung) und ist schon in
    // den Stat-Kacheln zu sehen. scaleMax rundet das für eine lesbare Achsenbeschriftung auf.
    val maxSpeed = remember(trip.id) { trip.maxSpeedKmh.toFloat().coerceAtLeast(1f) }
    val scaleMax = remember(maxSpeed) { niceCeilSpeed(maxSpeed) }
    val totalDuration = remember(points) { points.last().offsetSeconds.coerceAtLeast(1f) }
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.GERMANY) }
    val textMeasurer = rememberTextMeasurer()
    val leftGutterPx = with(LocalDensity.current) { 34.dp.toPx() } // Platz links für die Achsenbeschriftung

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
                        val plotW = (size.width - leftGutterPx).coerceAtLeast(1f)
                        val fraction = ((offset.x - leftGutterPx) / plotW).coerceIn(0f, 1f)
                        selectedIndex = (fraction * (points.size - 1)).toInt().coerceIn(0, points.size - 1)
                    }
                }
                .pointerInput(points) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        val plotW = (size.width - leftGutterPx).coerceAtLeast(1f)
                        val fraction = ((change.position.x - leftGutterPx) / plotW).coerceIn(0f, 1f)
                        selectedIndex = (fraction * (points.size - 1)).toInt().coerceIn(0, points.size - 1)
                    }
                }
        ) {
            val w = size.width
            val h = size.height
            val leftGutter = leftGutterPx
            val plotW = (w - leftGutter).coerceAtLeast(1f)

            // Gitterlinien + Achsenbeschriftung (0 / 1/3 / 2/3 / voll), damit sich die Skala ablesen lässt
            listOf(0f, 1f / 3f, 2f / 3f, 1f).forEach { frac ->
                val y = h - frac * h
                val value = (scaleMax * frac).roundToInt()
                drawLine(
                    color = onSurfaceVariant.copy(alpha = 0.12f),
                    start = Offset(leftGutter, y),
                    end = Offset(w, y),
                    strokeWidth = 1f
                )
                val label = textMeasurer.measure(
                    text = "$value",
                    style = TextStyle(fontSize = 10.sp, color = onSurfaceVariant.copy(alpha = 0.6f))
                )
                drawText(
                    textLayoutResult = label,
                    topLeft = Offset(
                        leftGutter - 6f - label.size.width,
                        (y - label.size.height / 2f).coerceIn(0f, h - label.size.height)
                    )
                )
            }

            val path = Path()
            points.forEachIndexed { i, p ->
                val x = leftGutter + (p.offsetSeconds / totalDuration) * plotW
                val y = h - (p.speedKmh / scaleMax).coerceAtMost(1f) * h
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = accent,
                style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            val sel = points[selectedIndex]
            val selX = leftGutter + (sel.offsetSeconds / totalDuration) * plotW
            val selY = h - (sel.speedKmh / scaleMax).coerceAtMost(1f) * h

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

/**
 * Median-Filter über die Geschwindigkeit (Fenster von 5 Punkten): einzelne GPS-Ausreißer (kurzer
 * ungenauer Fix) erzeugen sonst isolierte Nadel-Spitzen statt eines realistisch wirkenden Verlaufs
 * - der Median eines Fensters ignoriert genau solche einzelnen Ausreißer, echte Beschleunigungs-/
 * Bremstrends bleiben erhalten. cumulativeKm/Position bleiben unverändert.
 */
private fun List<GraphPoint>.medianFiltered(windowRadius: Int = 2): List<GraphPoint> {
    val raw = map { it.speedKmh }
    return mapIndexed { i, p ->
        val lo = (i - windowRadius).coerceAtLeast(0)
        val hi = (i + windowRadius).coerceAtMost(raw.size - 1)
        val window = raw.subList(lo, hi + 1).sorted()
        p.copy(speedKmh = window[window.size / 2])
    }
}

/** Rundet für eine lesbare Achsenbeschriftung auf ein "glattes" Vielfaches von 10 auf. */
private fun niceCeilSpeed(value: Float): Float = maxOf(10f, kotlin.math.ceil(value / 10f) * 10f)

/** Wandelt die gespeicherten GPS-Punkte in eine Zeit/Geschwindigkeit/Distanz-Serie für den Graphen um. */
private fun Trip.toSpeedSeries(context: android.content.Context): List<GraphPoint> {
    val raw = toTrackPoints(context)
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
