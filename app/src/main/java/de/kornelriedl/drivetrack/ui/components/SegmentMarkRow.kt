package de.kornelriedl.drivetrack.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.kornelriedl.drivetrack.data.SegmentMark
import de.kornelriedl.drivetrack.data.Trip
import de.kornelriedl.drivetrack.data.labelColor
import de.kornelriedl.drivetrack.data.labelIcon
import de.kornelriedl.drivetrack.data.segmentStats
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Eine Zeile für einen markierten Streckenabschnitt (z.B. Fährüberfahrt): Farbpunkt (dieselbe
 * Farbe wie die Linie auf der Karte, siehe labelColor()), Label + Zeitraum, darunter eigene
 * Distanz/Dauer/Ø-/Höchstgeschwindigkeit NUR für diesen Abschnitt (siehe Trip.segmentStats()).
 * Wiederverwendet in TripDetailScreen (nur Anzeige) und TripEditScreen (zusätzlich löschbar).
 */
@Composable
fun SegmentMarkRow(trip: Trip, mark: SegmentMark, onDelete: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.GERMANY) }
    val stats = remember(trip.id, mark) { trip.segmentStats(context, mark) }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(Color(labelColor(mark.label)))
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "${labelIcon(mark.label)} ${mark.label}: ${timeFormat.format(Date(mark.startTs))}–${timeFormat.format(Date(mark.endTs))}",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                "%.1f km · %s · Ø %.0f km/h · Max %.0f km/h".format(
                    stats.distanceKm,
                    formatDurationHm(stats.durationMinutes),
                    stats.avgSpeedKmh,
                    stats.maxSpeedKmh
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (onDelete != null) {
            IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Löschen", modifier = Modifier.size(16.dp))
            }
        }
    }
}
