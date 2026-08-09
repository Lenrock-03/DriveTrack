package de.kornelriedl.drivetrack.ui.components

import android.graphics.DashPathEffect
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import de.kornelriedl.drivetrack.R
import de.kornelriedl.drivetrack.data.Trip
import de.kornelriedl.drivetrack.data.segmentMarks
import de.kornelriedl.drivetrack.data.speedSeriesClamped
import de.kornelriedl.drivetrack.ui.screens.DarkMatterTileSource
import de.kornelriedl.drivetrack.ui.screens.DoubleTapDragZoomOverlay
import de.kornelriedl.drivetrack.ui.screens.buildContrastFilter
import de.kornelriedl.drivetrack.ui.screens.toGeoPoints
import de.kornelriedl.drivetrack.ui.screens.toTrackPoints
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.Color as ComposeColor

/**
 * Routen-Detailkarte, ursprünglich Teil von TripDetailScreen.kt - hierher verschoben, damit auch
 * TripEditScreen dieselbe Karte (samt A/B-Bearbeitungs-Pins) einbetten kann, statt osmdroid-
 * Boilerplate zu duplizieren.
 */

/** Anzeigemodus der Routen-Linie auf der Detail-Karte, umschaltbar über RouteColorModeSelector. */
enum class RouteColorMode { STANDARD, SPEED }

// Feste Signalfarbe für markierte Streckenabschnitte (z.B. Fährüberfahrt) - unabhängig vom
// Standard-/Geschwindigkeits-Modus immer gleich erkennbar, gestrichelt statt durchgezogen.
private val SEGMENT_MARK_COLOR = AndroidColor.parseColor("#26C6DA")

@Composable
fun RouteDetailMap(
    trip: Trip,
    scrubPoint: Pair<Double, Double>?,
    routeColorMode: RouteColorMode,
    modifier: Modifier = Modifier,
    markA: Long? = null,
    markB: Long? = null
) {
    val context = LocalContext.current
    val scrubMarkerRef = remember { mutableStateOf<Marker?>(null) }
    val markARef = remember { mutableStateOf<Marker?>(null) }
    val markBRef = remember { mutableStateOf<Marker?>(null) }
    // Aktuell gezeichnete Routen-Layer (Standard-Linie/Geschwindigkeits-Segmente + Markierungen),
    // damit sie sich beim Umschalten des Modus bzw. bei Markierungsänderungen gezielt entfernen
    // lassen, ohne Start-/Ziel-/Scrub-/AB-Marker anzufassen.
    val routeOverlaysRef = remember { mutableStateOf<List<Polyline>>(emptyList()) }
    val appliedModeRef = remember { mutableStateOf<RouteColorMode?>(null) }
    val appliedMarksRef = remember { mutableStateOf<String?>(null) }

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
                    appliedMarksRef.value = trip.segmentMarksJson

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

                    // A/B-Marker fürs Bearbeiten (nur genutzt von TripEditScreen, sonst immer null)
                    markARef.value = Marker(this).apply {
                        icon = ContextCompat.getDrawable(ctx, R.drawable.ic_location_dot)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        title = "A"
                        setOnMarkerClickListener { _, _ -> true }
                    }
                    markBRef.value = Marker(this).apply {
                        icon = ContextCompat.getDrawable(ctx, R.drawable.ic_location_dot)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        title = "B"
                        setOnMarkerClickListener { _, _ -> true }
                    }

                    post {
                        val boundingBox = BoundingBox.fromGeoPoints(points)
                        zoomToBoundingBox(boundingBox, false, 100)
                    }
                } else {
                    controller.setZoom(15.0)
                    controller.setCenter(GeoPoint(47.8, 11.7))
                }

                overlays.add(DoubleTapDragZoomOverlay(ctx))
            }
        },
        update = { mapView ->
            val marker = scrubMarkerRef.value
            if (marker != null) {
                if (scrubPoint != null) {
                    marker.position = GeoPoint(scrubPoint.first, scrubPoint.second)
                    if (!mapView.overlays.contains(marker)) {
                        mapView.overlays.add(marker)
                    }
                } else {
                    mapView.overlays.remove(marker)
                }
            }

            updateAbMarker(mapView, markARef.value, markA, trip, context)
            updateAbMarker(mapView, markBRef.value, markB, trip, context)

            // Route nur neu einfärben, wenn sich Modus oder Markierungen seit dem letzten Durchlauf
            // geändert haben (nicht bei jedem Scrub-Update während des Ziehens im Graphen).
            if (appliedModeRef.value != routeColorMode || appliedMarksRef.value != trip.segmentMarksJson) {
                val points = trip.toGeoPoints(context)
                if (points.size >= 2) {
                    applyRouteColorMode(mapView, trip, context, points, routeColorMode, routeOverlaysRef)
                }
                appliedModeRef.value = routeColorMode
                appliedMarksRef.value = trip.segmentMarksJson
            }

            mapView.invalidate()
        }
    )
}

/** Setzt/entfernt einen A- oder B-Bearbeitungs-Pin auf der Karte anhand seines Zeitstempels. */
private fun updateAbMarker(
    mapView: MapView,
    marker: Marker?,
    timestamp: Long?,
    trip: Trip,
    context: android.content.Context
) {
    if (marker == null) return
    if (timestamp == null) {
        mapView.overlays.remove(marker)
        return
    }
    val trackPoints = trip.toTrackPoints(context)
    val nearest = trackPoints.minByOrNull { kotlin.math.abs(it.third - timestamp) } ?: return
    marker.position = GeoPoint(nearest.first, nearest.second)
    if (!mapView.overlays.contains(marker)) {
        mapView.overlays.add(marker)
    }
}

/**
 * Entfernt die zuvor gezeichnete(n) Routen-Linie(n) und zeichnet sie im gewählten Modus neu
 * (Standard-Farbe oder nach Geschwindigkeit eingefärbt), plus etwaige markierte Streckenabschnitte
 * (z.B. Fähre) als zusätzliche, gestrichelte Linie obendrauf. Wird an Index 0 eingefügt, damit sie
 * immer unter Start-/Ziel-/Scrub-/AB-Marker liegt, egal in welcher Reihenfolge das passiert.
 */
private fun applyRouteColorMode(
    mapView: MapView,
    trip: Trip,
    context: android.content.Context,
    points: List<GeoPoint>,
    mode: RouteColorMode,
    routeOverlaysRef: MutableState<List<Polyline>>
) {
    routeOverlaysRef.value.forEach { mapView.overlays.remove(it) }

    val baseOverlays = when (mode) {
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

    val markOverlays = buildSegmentMarkOverlays(mapView, trip, context)

    val newOverlays = baseOverlays + markOverlays
    mapView.overlays.addAll(0, newOverlays)
    routeOverlaysRef.value = newOverlays
}

/** Baut je markiertem Streckenabschnitt (z.B. Fähre) eine gestrichelte Signalfarben-Polyline. */
private fun buildSegmentMarkOverlays(mapView: MapView, trip: Trip, context: android.content.Context): List<Polyline> {
    val marks = trip.segmentMarks()
    if (marks.isEmpty()) return emptyList()
    val trackPoints = trip.toTrackPoints(context)
    if (trackPoints.size < 2) return emptyList()

    return marks.mapNotNull { mark ->
        val inRange = trackPoints.filter { it.third in mark.startTs..mark.endTs }
        if (inRange.size < 2) return@mapNotNull null
        Polyline(mapView).apply {
            setPoints(inRange.map { GeoPoint(it.first, it.second) })
            outlinePaint.color = SEGMENT_MARK_COLOR
            outlinePaint.strokeWidth = 12f
            outlinePaint.isAntiAlias = true
            outlinePaint.pathEffect = DashPathEffect(floatArrayOf(20f, 14f), 0f)
            setOnClickListener { _, _, _ -> true }
        }
    }
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
    val series = speedSeriesClamped(trip, context)
    if (series.size < 2) return emptyList()
    val step = ((series.size - 1) / MAX_ROUTE_COLOR_SEGMENTS).coerceAtLeast(1)

    val segments = mutableListOf<Polyline>()
    var i = 0
    while (i < series.size - 1) {
        val end = (i + step).coerceAtMost(series.size - 1)
        val segmentPoints = (i..end).map { GeoPoint(series[it].lat, series[it].lon) }
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
fun RouteColorModeSelector(
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
fun RouteColorLegend(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .width(175.dp)
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
        // Absolut statt Row/SpaceBetween: der "130"-Tick muss an der tatsächlichen Position seines
        // Werts im Gradienten sitzen (130/180 ≈ 72% der Breite, NICHT in der Mitte).
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(14.dp)) {
            val labelStyle = MaterialTheme.typography.labelSmall
            val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
            Text("0", style = labelStyle, color = labelColor, modifier = Modifier.align(Alignment.CenterStart))
            Text(
                "130",
                style = labelStyle,
                color = labelColor,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = maxWidth * (ROUTE_COLOR_RED_KMH / ROUTE_COLOR_PURPLE_KMH))
            )
            Text("180+", style = labelStyle, color = labelColor, modifier = Modifier.align(Alignment.CenterEnd))
        }
    }
}
