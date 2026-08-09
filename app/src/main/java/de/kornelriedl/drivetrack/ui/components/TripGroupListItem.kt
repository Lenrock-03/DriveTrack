package de.kornelriedl.drivetrack.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.kornelriedl.drivetrack.data.Trip
import de.kornelriedl.drivetrack.data.TripGroup
import de.kornelriedl.drivetrack.data.groupStats
import de.kornelriedl.drivetrack.export.MapThumbnailGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Ein zusammengefasster Eintrag für eine Fahrten-Gruppe in der Fahrtenliste (spiegelt
 * `TripListItem`s Layout: Textspalte links, Thumbnail rechts). Zeigt Gesamt-Statistik über alle
 * Mitgliedsfahrten (siehe `List<Trip>.groupStats()`), Tap öffnet `TripGroupDetailScreen` mit der
 * Liste der einzelnen Fahrten.
 */
@Composable
fun TripGroupListItem(group: TripGroup, trips: List<Trip>, onClick: () -> Unit) {
    val stats = trips.groupStats()

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = LocalIndication.current,
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = group.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${stats.tripCount} Fahrten",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Länge: %.1f km".format(stats.totalKm),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Ø %.0f km/h".format(stats.avgSpeedKmh),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            GroupRouteThumbnail(group = group, trips = trips, modifier = Modifier.size(56.dp))
        }
    }
}

/**
 * Statisches Mini-Thumbnail mit den kombinierten Routen ALLER Mitgliedsfahrten (spiegelt
 * `TripListItem`s `RouteThumbnail` 1:1, nutzt `MapThumbnailGenerator.getOrCreateForGroup()` statt
 * der Einzelfahrt-Variante). Das Gruppen-Symbol selbst ist nur noch ein kleines Badge unten links
 * auf der Kachel (wie das Label-Icon-Badge bei einer einzelnen Fahrt), statt die ganze Kachel zu
 * füllen - die Miniaturkarte soll das eigentliche Blickfang-Element sein.
 */
@Composable
private fun GroupRouteThumbnail(group: TripGroup, trips: List<Trip>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val tripIdsKey = remember(trips) { trips.map { it.id } }
    var bitmap by remember(group.id, tripIdsKey) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(group.id, tripIdsKey) { mutableStateOf(false) }

    LaunchedEffect(group.id, tripIdsKey) {
        bitmap = null
        failed = false
        val result = withContext(Dispatchers.IO) {
            try {
                MapThumbnailGenerator.getOrCreateForGroup(context, group.id, trips)
            } catch (e: Exception) {
                null
            }
        }
        if (result != null) bitmap = result else failed = true
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Kombinierte Route der Gruppe",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = Icons.Filled.Map,
                contentDescription = "Karte der Gruppe",
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

        // Gruppen-Symbol als kleines Badge, wie die Label-Icons bei einer einzelnen Fahrt
        // (RouteThumbnail in TripListItem.kt) - erkennbar "das ist eine Gruppe", ohne die
        // Miniaturkarte selbst zu verdecken.
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(3.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f))
                .padding(3.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Folder,
                contentDescription = "Fahrten-Gruppe",
                tint = androidx.compose.ui.graphics.Color.White,
                modifier = Modifier.size(11.dp)
            )
        }
    }
}
