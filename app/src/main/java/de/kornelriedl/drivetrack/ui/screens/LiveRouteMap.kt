package de.kornelriedl.drivetrack.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import de.kornelriedl.drivetrack.R
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

/**
 * Live-Karte für den Aufzeichnen-Screen: dieselbe Mechanik wie die globale Karte
 * (MyLocationOverlay, Follow-Modus, Doppeltipp-Zoom, Recenter-Button), plus die
 * bisher zurückgelegte Strecke der laufenden Aufzeichnung als Linie.
 */
@Composable
fun LiveRouteMap(
    trackPoints: List<Pair<Double, Double>>,
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
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    val polylineRef = remember { mutableStateOf<Polyline?>(null) }
    val mapViewRef = remember { mutableStateOf<MapView?>(null) }
    val locationOverlayRef = remember { mutableStateOf<MyLocationNewOverlay?>(null) }

    Box(modifier = modifier) {
        // key(...) baut die Karte neu auf, sobald die Berechtigung erteilt wird –
        // genau wie bei der globalen Karte
        key(hasPermission) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    MapView(ctx).apply {
                        setTileSource(DarkMatterTileSource)
                        overlayManager.tilesOverlay.setColorFilter(buildContrastFilter())
                        setMultiTouchControls(true)
                        isTilesScaledToDpi = true
                        isFlingEnabled = true
                        zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
                        minZoomLevel = 4.0
                        maxZoomLevel = 20.0
                        setUseDataConnection(true)

                        controller.setZoom(17.0)
                        // Direkt bei der letzten bekannten Position starten statt bei einem Platzhalter-Ort
                        val lastKnown = if (hasPermission) {
                            val locationManager = ctx.getSystemService(android.location.LocationManager::class.java)
                            listOfNotNull(
                                locationManager?.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER),
                                locationManager?.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
                            ).firstOrNull()
                        } else null
                        controller.setCenter(
                            if (lastKnown != null) GeoPoint(lastKnown.latitude, lastKnown.longitude)
                            else GeoPoint(47.8, 11.7)
                        )

                        val polyline = Polyline(this).apply {
                            outlinePaint.color = Color.parseColor("#FF7A1A")
                            outlinePaint.strokeWidth = 10f
                            outlinePaint.isAntiAlias = true
                            setOnClickListener { _, _, _ -> true } // keine weiße Bubble beim Antippen
                        }
                        overlays.add(polyline)
                        polylineRef.value = polyline

                        if (hasPermission) {
                            val locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(ctx), this)
                            ContextCompat.getDrawable(ctx, R.drawable.ic_location_dot)
                                ?.toBitmap(width = 72, height = 72)
                                ?.let { bitmap ->
                                    locationOverlay.setPersonIcon(bitmap)
                                    locationOverlay.setPersonAnchor(0.5f, 0.5f)
                                    locationOverlay.setDirectionIcon(bitmap)
                                    locationOverlay.setDirectionAnchor(0.5f, 0.5f)
                                }
                            locationOverlay.enableMyLocation()
                            locationOverlay.enableFollowLocation()
                            locationOverlay.runOnFirstFix {
                                post {
                                    locationOverlay.myLocation?.let { controller.setCenter(it) }
                                }
                            }
                            overlays.add(locationOverlay)
                            locationOverlayRef.value = locationOverlay
                        }

                        // Als letztes Overlay hinzufügen, damit die Zoom-Geste Vorrang hat
                        overlays.add(DoubleTapDragZoomOverlay(ctx))

                        mapViewRef.value = this
                    }
                },
                update = { mapView ->
                    val geoPoints = trackPoints.map { (lat, lon) -> GeoPoint(lat, lon) }
                    polylineRef.value?.setPoints(geoPoints)
                    mapView.invalidate()
                }
            )
        }

        // Recenter-Button: springt zurück zur aktuellen Position und aktiviert das Folgen wieder
        SmallFloatingActionButton(
            onClick = {
                val overlay = locationOverlayRef.value
                if (overlay != null) {
                    overlay.enableFollowLocation()
                    overlay.myLocation?.let { mapViewRef.value?.controller?.animateTo(it) }
                } else {
                    // Falls (noch) kein Live-Standort verfügbar ist, auf den letzten aufgezeichneten Punkt springen
                    trackPoints.lastOrNull()?.let { (lat, lon) ->
                        mapViewRef.value?.controller?.animateTo(GeoPoint(lat, lon))
                    }
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Icon(
                imageVector = Icons.Filled.MyLocation,
                contentDescription = "Auf eigene Position zentrieren"
            )
        }
    }
}
