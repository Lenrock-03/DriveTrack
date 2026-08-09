package de.kornelriedl.drivetrack.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import de.kornelriedl.drivetrack.R
import de.kornelriedl.drivetrack.data.Trip
import de.kornelriedl.drivetrack.ui.screens.DarkMatterTileSource
import de.kornelriedl.drivetrack.ui.screens.DoubleTapDragZoomOverlay
import de.kornelriedl.drivetrack.ui.screens.buildContrastFilter
import de.kornelriedl.drivetrack.ui.screens.toGeoPoints
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import android.graphics.Color as AndroidColor

/**
 * Übersichtskarte für eine Fahrten-Gruppe: zeichnet die Route JEDER Mitgliedsfahrt (Standard-Farbe
 * oder - wie bei einer einzelnen Fahrt - nach Geschwindigkeit eingefärbt, siehe `routeColorMode`)
 * und zoomt einmalig auf die gemeinsame Bounding-Box ALLER Punkte (spiegelt `RouteDetailMap`s
 * `zoomToBoundingBox()`-Aufruf, dort nur für eine einzelne Fahrt). Keine A-B-Bearbeitungsmarker wie
 * bei `RouteDetailMap` - das ist nur beim Zuschneiden einer einzelnen Fahrt relevant. `scrubPoint`
 * (optional) zeigt einen Marker an der aktuell im kombinierten Geschwindigkeits-Graphen ausgewählten
 * Position (siehe TripGroupRouteScreen), spiegelt RouteDetailMaps Scrub-Marker. `interactive = false`
 * (genutzt für die kleine Vorschau in TripGroupDetailScreen) macht die Karte rein statisch - ohne
 * das würde jede Berührung als Pan-Geste der Karte interpretiert, statt z.B. einen außen liegenden
 * Vergrößerungs-Button zu treffen (osmdroids Pan-Erkennung läuft in MapViews eigenem Touch-Handling,
 * unabhängig von setMultiTouchControls()/Overlays - deshalb zusätzlich per setOnTouchListener()
 * unterbunden).
 */
@Composable
fun GroupRouteMap(
    trips: List<Trip>,
    scrubPoint: Pair<Double, Double>? = null,
    routeColorMode: RouteColorMode = RouteColorMode.STANDARD,
    interactive: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrubMarkerRef = remember { mutableStateOf<Marker?>(null) }
    // Aktuell gezeichnete Routen-Layer, damit sie sich beim Umschalten des Farbmodus gezielt
    // entfernen/neu aufbauen lassen, ohne den Scrub-Marker anzufassen - spiegelt RouteDetailMaps
    // routeOverlaysRef/appliedModeRef.
    val routeOverlaysRef = remember { mutableStateOf<List<Polyline>>(emptyList()) }
    val appliedModeRef = remember { mutableStateOf<RouteColorMode?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            MapView(ctx).apply {
                setTileSource(DarkMatterTileSource)
                overlayManager.tilesOverlay.setColorFilter(buildContrastFilter())
                setMultiTouchControls(interactive)
                isTilesScaledToDpi = true
                zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
                minZoomLevel = 4.0
                maxZoomLevel = 20.0

                applyGroupRouteColorMode(this, trips, ctx, routeColorMode, routeOverlaysRef)
                appliedModeRef.value = routeColorMode

                val allPoints = trips.flatMap { it.toGeoPoints(ctx) }
                if (allPoints.size >= 2) {
                    post {
                        zoomToBoundingBox(BoundingBox.fromGeoPoints(allPoints), false, 80)
                    }
                } else {
                    controller.setZoom(15.0)
                    controller.setCenter(GeoPoint(47.8, 11.7))
                }

                scrubMarkerRef.value = Marker(this).apply {
                    icon = ContextCompat.getDrawable(ctx, R.drawable.ic_location_dot)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    setOnMarkerClickListener { _, _ -> true }
                }

                if (interactive) {
                    overlays.add(DoubleTapDragZoomOverlay(ctx))
                } else {
                    // Schluckt jede Touch-Sequenz auf View-Ebene, bevor MapViews eigene Pan-Logik
                    // sie verarbeiten kann - siehe Doc-Kommentar oben.
                    setOnTouchListener { _, _ -> true }
                }
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

            if (appliedModeRef.value != routeColorMode) {
                applyGroupRouteColorMode(mapView, trips, context, routeColorMode, routeOverlaysRef)
                appliedModeRef.value = routeColorMode
            }

            mapView.invalidate()
        }
    )
}

/**
 * Entfernt die zuvor gezeichneten Routen-Linien und zeichnet sie im gewählten Modus neu - Standard-
 * Farbe (eine Polyline je Fahrt) oder nach Geschwindigkeit eingefärbt (buildSpeedColoredSegments()
 * aus RouteDetailMap.kt, JE FAHRT einzeln über deren eigene speedSeriesClamped()-Serie, nicht über
 * die kombinierte Gruppen-Serie - für die Kartenfarbe zählt nur die tatsächliche Geschwindigkeit an
 * jedem Punkt, die "Nahtstellen"-Problematik von buildGroupSpeedSeries() betrifft nur den Graphen).
 * Wird an Index 0 eingefügt, damit sie immer unter dem Scrub-Marker liegen, egal in welcher
 * Reihenfolge das passiert.
 */
private fun applyGroupRouteColorMode(
    mapView: MapView,
    trips: List<Trip>,
    context: android.content.Context,
    mode: RouteColorMode,
    routeOverlaysRef: MutableState<List<Polyline>>
) {
    routeOverlaysRef.value.forEach { mapView.overlays.remove(it) }

    val newOverlays = trips.flatMap { trip ->
        val points = trip.toGeoPoints(context)
        if (points.size < 2) return@flatMap emptyList<Polyline>()
        when (mode) {
            RouteColorMode.STANDARD -> listOf(
                Polyline(mapView).apply {
                    setPoints(points)
                    outlinePaint.color = AndroidColor.parseColor("#FF7A1A")
                    outlinePaint.alpha = 190
                    outlinePaint.strokeWidth = 8f
                    outlinePaint.isAntiAlias = true
                    setOnClickListener { _, _, _ -> true } // keine weiße Standard-Bubble
                }
            )
            RouteColorMode.SPEED -> buildSpeedColoredSegments(mapView, trip, context)
        }
    }
    mapView.overlays.addAll(0, newOverlays)
    routeOverlaysRef.value = newOverlays
}
