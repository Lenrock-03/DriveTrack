package de.kornelriedl.drivetrack.ui.screens

import android.graphics.Color
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TripOrigin
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import de.kornelriedl.drivetrack.data.Trip
import de.kornelriedl.drivetrack.export.GpxExporter
import org.osmdroid.util.BoundingBox
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDetailScreen(trip: Trip, onBack: () -> Unit) {
    val context = LocalContext.current

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
                SimpleDateFormat("EEEE, d. MMMM yyyy · HH:mm 'Uhr'", Locale.GERMANY)
            }
            Text(
                text = dateFormat.format(Date(trip.startTimestamp)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Routenkarte
            Card(
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
            ) {
                RouteDetailMap(trip = trip, modifier = Modifier.fillMaxSize())
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Statistiken als 2x2 Grid
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

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun RouteDetailMap(trip: Trip, modifier: Modifier = Modifier) {
    val context = LocalContext.current

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
                        outlinePaint.color = Color.parseColor("#FF7A1A")
                        outlinePaint.strokeWidth = 10f
                        outlinePaint.isAntiAlias = true
                    }
                    overlays.add(polyline)

                    val startMarker = Marker(this).apply {
                        position = points.first()
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        title = "Start"
                    }
                    overlays.add(startMarker)

                    val endMarker = Marker(this).apply {
                        position = points.last()
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        title = "Ziel"
                    }
                    overlays.add(endMarker)

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
        }
    )
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
