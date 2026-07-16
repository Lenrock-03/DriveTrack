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
import androidx.compose.runtime.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import de.kornelriedl.drivetrack.R
import de.kornelriedl.drivetrack.data.Car
import de.kornelriedl.drivetrack.data.Trip
import de.kornelriedl.drivetrack.ui.components.CarSelector
import org.json.JSONArray
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

// CartoDB Dark Matter Tiles (Retina/@2x für scharfe Darstellung auf High-DPI-Displays)
// – passend zum dunklen Material-3-Theme der App, komplett kostenlos, kein API-Key nötig
val DarkMatterTileSource = XYTileSource(
    "CartoDBDarkMatterRetina",
    0, 20, 512, "@2x.png",
    arrayOf(
        "https://a.basemaps.cartocdn.com/dark_all/",
        "https://b.basemaps.cartocdn.com/dark_all/",
        "https://c.basemaps.cartocdn.com/dark_all/"
    )
)

/** Kräftigt Kontrast/Sättigung der eher flachen Dark-Matter-Kacheln. */
fun buildContrastFilter(): android.graphics.ColorMatrixColorFilter {
    val contrast = 1.35f
    val brightness = 8f
    val matrix = android.graphics.ColorMatrix(
        floatArrayOf(
            contrast, 0f, 0f, 0f, brightness,
            0f, contrast, 0f, 0f, brightness,
            0f, 0f, contrast, 0f, brightness,
            0f, 0f, 0f, 1f, 0f
        )
    )
    return android.graphics.ColorMatrixColorFilter(matrix)
}

/** Wandelt das gespeicherte JSON der Fahrt zurück in GeoPoints für die Kartenanzeige. */
fun Trip.toGeoPoints(): List<GeoPoint> {
    return try {
        val arr = JSONArray(gpxTrackJson)
        (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            GeoPoint(obj.getDouble("lat"), obj.getDouble("lon"))
        }
    } catch (e: Exception) {
        emptyList()
    }
}

/** Wie toGeoPoints(), behält aber zusätzlich den Zeitstempel jedes Punkts (lat, lon, ts). */
fun Trip.toTrackPoints(): List<Triple<Double, Double, Long>> {
    return try {
        val arr = JSONArray(gpxTrackJson)
        (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            Triple(obj.getDouble("lat"), obj.getDouble("lon"), obj.getLong("ts"))
        }
    } catch (e: Exception) {
        emptyList()
    }
}

@Composable
fun MapScreen(
    trips: List<Trip> = emptyList(),
    cars: List<Car> = emptyList(),
    selectedCarId: Long? = null,
    onSelectCar: (Long?) -> Unit = {},
    onAddCar: (String) -> Unit = {},
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

    val mapViewRef = remember { mutableStateOf<MapView?>(null) }
    val locationOverlayRef = remember { mutableStateOf<MyLocationNewOverlay?>(null) }

    Box(modifier = modifier) {
        // key(...) sorgt dafür, dass die Karte neu aufgebaut wird, sobald sich Berechtigung oder Fahrten ändern
        key(hasPermission, trips.size, selectedCarId) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    MapView(ctx).apply {
                        setTileSource(DarkMatterTileSource)
                        overlayManager.tilesOverlay.setColorFilter(buildContrastFilter())
                        setMultiTouchControls(true)   // aktiviert Pinch-to-Zoom
                        isTilesScaledToDpi = true      // schärfere Kacheln auf hochauflösenden Displays
                        isFlingEnabled = true           // sanftes Ausklingen beim Wischen, wie bei Google Maps
                        zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
                        minZoomLevel = 4.0
                        maxZoomLevel = 20.0
                        setUseDataConnection(true)

                        controller.setZoom(16.0)
                        // Direkt bei der letzten bekannten Position starten statt bei einem Platzhalter-Ort –
                        // vermeidet das sichtbare "Wegfliegen" quer über die Karte beim Öffnen.
                        val lastKnown = if (hasPermission) {
                            val locationManager = ctx.getSystemService(android.location.LocationManager::class.java)
                            listOfNotNull(
                                locationManager?.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER),
                                locationManager?.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
                            ).firstOrNull()
                        } else null
                        controller.setCenter(
                            if (lastKnown != null) GeoPoint(lastKnown.latitude, lastKnown.longitude)
                            else GeoPoint(47.8, 11.7) // nur falls wirklich noch nie ein Standort bekannt war
                        )

                        // Alle bisherigen Fahrten als Linien einzeichnen
                        trips.forEach { trip ->
                            val points = trip.toGeoPoints()
                            if (points.size >= 2) {
                                val polyline = Polyline(this).apply {
                                    setPoints(points)
                                    outlinePaint.color = Color.parseColor("#FF7A1A")
                                    outlinePaint.alpha = 190
                                    outlinePaint.strokeWidth = 8f
                                    outlinePaint.isAntiAlias = true
                                    setOnClickListener { _, _, _ -> true } // keine weiße Bubble beim Antippen
                                }
                                overlays.add(polyline)
                            }
                        }

                        if (hasPermission) {
                            val locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(ctx), this)
                            // Eigenes Icon im Google-Maps-Stil (Kreis mit weißem Rand, Orange statt Blau)
                            ContextCompat.getDrawable(ctx, R.drawable.ic_location_dot)
                                ?.toBitmap(width = 72, height = 72)
                                ?.let { bitmap ->
                                    locationOverlay.setPersonIcon(bitmap)
                                    locationOverlay.setPersonAnchor(0.5f, 0.5f)
                                    locationOverlay.setDirectionIcon(bitmap)
                                    locationOverlay.setDirectionAnchor(0.5f, 0.5f)
                                }
                            locationOverlay.enableMyLocation()
                            // Bewusst KEIN enableFollowLocation() hier: das würde die Karte bei jedem
                            // GPS-Update zwangsweise zurück auf die eigene Position springen lassen,
                            // auch während man gerade woanders hinscrollt. Nur einmalig zentrieren,
                            // danach frei scrollbar – aktives Nachführen nur über den Recenter-Button.
                            locationOverlay.runOnFirstFix {
                                post {
                                    // setCenter statt animateTo: kein sichtbares Fliegen quer über die Karte
                                    locationOverlay.myLocation?.let { controller.setCenter(it) }
                                }
                            }
                            overlays.add(locationOverlay)
                            locationOverlayRef.value = locationOverlay
                        }

                        // Als letztes Overlay hinzufügen, damit die Zoom-Geste in der Touch-Reihenfolge
                        // Vorrang vor Marken/Linien-Overlays hat
                        overlays.add(DoubleTapDragZoomOverlay(ctx))

                        mapViewRef.value = this
                    }
                }
            )
        }

        // Recenter-Button: springt einmalig zurück zur aktuellen Position (kein Dauer-Nachführen)
        androidx.compose.material3.SmallFloatingActionButton(
            onClick = {
                locationOverlayRef.value?.myLocation?.let {
                    mapViewRef.value?.controller?.animateTo(it)
                }
            },
            modifier = Modifier
                .align(androidx.compose.ui.Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Icon(
                imageVector = Icons.Filled.MyLocation,
                contentDescription = "Auf eigene Position zentrieren"
            )
        }

        // Auto-Filter: schwebt oben rechts über der Karte
        CarSelector(
            cars = cars,
            selectedCarId = selectedCarId,
            onSelectCar = onSelectCar,
            onAddCar = onAddCar,
            modifier = Modifier
                .align(androidx.compose.ui.Alignment.TopEnd)
                .padding(16.dp)
        )
    }
}
