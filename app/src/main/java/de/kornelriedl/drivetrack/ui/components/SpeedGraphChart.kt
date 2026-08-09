package de.kornelriedl.drivetrack.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.kornelriedl.drivetrack.data.GraphPoint
import de.kornelriedl.drivetrack.data.Trip
import de.kornelriedl.drivetrack.data.groupSpeedSeriesClamped
import de.kornelriedl.drivetrack.data.speedSeriesClamped
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.Color as ComposeColor

/**
 * Geschwindigkeits-Graph, ursprünglich Teil von TripDetailScreen.kt - hierher verschoben, damit
 * auch TripEditScreen dieselbe Scrub-Interaktion (samt A/B-Bearbeitungsmarkern und hervorgehobenen
 * Bereichen) nutzen kann, statt sie zu duplizieren. Verhalten für bestehende Aufrufer (nur
 * trip/onScrub gesetzt) bleibt unverändert - alle neuen Parameter haben "nichts extra zeichnen"
 * als Default.
 */
@Composable
fun SpeedGraph(
    trip: Trip,
    onScrub: (GraphPoint?) -> Unit,
    modifier: Modifier = Modifier,
    markA: Long? = null,
    markB: Long? = null,
    highlightRanges: List<Pair<Long, Long>> = emptyList()
) {
    val context = LocalContext.current
    // Median-Filter gegen einzelne GPS-Ausreißer (kurzer ungenauer Fix -> rechnerisch absurd hohe
    // Distanz/Zeit-Geschwindigkeit) - siehe medianFiltered() in TripGeoMath.kt. Ohne den Filter
    // sehen solche Ausreißer wie künstliche Nadel-Spitzen statt eines realistischen
    // Geschwindigkeitsverlaufs aus.
    val points = remember(trip.id) { speedSeriesClamped(trip, context) }
    // Zusätzliche Sicherheitsgrenze: trip.maxSpeedKmh kommt von loc.speed (GPS-Chip, Doppler-
    // basiert, deutlich robuster als unsere eigene Positions-Differenz-Rechnung) und ist schon in
    // den Stat-Kacheln zu sehen.
    val maxSpeed = remember(trip.id) { trip.maxSpeedKmh.toFloat().coerceAtLeast(1f) }
    SpeedGraphCore(
        points = points,
        maxSpeedKmh = maxSpeed,
        selectionKey = trip.id,
        onScrub = onScrub,
        modifier = modifier,
        markA = markA,
        markB = markB,
        highlightRanges = highlightRanges
    )
}

/**
 * Wie SpeedGraph(), aber über die kombinierte Route mehrerer Fahrten einer Gruppe (siehe
 * TripGroupRouteScreen) - nutzt buildGroupSpeedSeries()/groupSpeedSeriesClamped() statt der
 * Einzelfahrt-Serie, die Nahtstellen zwischen Fahrten liefern dadurch KEINE künstlichen
 * Geschwindigkeits-Spitzen (siehe Doc-Kommentar dort). Kein markA/markB/highlightRanges - die
 * gehören zum Zuschneiden einer einzelnen Fahrt (TripEditScreen), nicht zur Gruppen-Übersicht.
 */
@Composable
fun GroupSpeedGraph(
    trips: List<Trip>,
    onScrub: (GraphPoint?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val tripIds = remember(trips) { trips.map { it.id } }
    val points = remember(tripIds) { groupSpeedSeriesClamped(trips, context) }
    val maxSpeed = remember(trips) { (trips.maxOfOrNull { it.maxSpeedKmh } ?: 0.0).toFloat().coerceAtLeast(1f) }
    SpeedGraphCore(
        points = points,
        maxSpeedKmh = maxSpeed,
        selectionKey = tripIds,
        onScrub = onScrub,
        modifier = modifier
    )
}

/**
 * Gemeinsamer Zeichen-/Scrub-Kern für SpeedGraph()/GroupSpeedGraph() - nimmt die fertig berechnete
 * Punktreihe statt sie selbst zu laden, damit dieselbe Canvas-/Gesten-Logik für eine einzelne Fahrt
 * UND eine kombinierte Gruppen-Serie gilt. `selectionKey` (statt fest trip.id) steuert, wann
 * selectedIndex/points-abhängiger State zurückgesetzt wird.
 */
@Composable
private fun SpeedGraphCore(
    points: List<GraphPoint>,
    maxSpeedKmh: Float,
    selectionKey: Any,
    onScrub: (GraphPoint?) -> Unit,
    modifier: Modifier = Modifier,
    markA: Long? = null,
    markB: Long? = null,
    highlightRanges: List<Pair<Long, Long>> = emptyList()
) {
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

    var selectedIndex by remember(selectionKey) { mutableStateOf(points.size - 1) }
    val scaleMax = remember(maxSpeedKmh) { niceCeilSpeed(maxSpeedKmh) }
    // points[0].offsetSeconds ist per Konstruktion immer 0 (siehe Trip.toSpeedSeries), daher
    // entspricht der Zeitstempel des ersten Punkts direkt dem Fahrtbeginn dieser (ggf. schon
    // zugeschnittenen) Punktreihe.
    val startTs = remember(points) { points.first().timestamp }
    val totalDuration = remember(points) { points.last().offsetSeconds.coerceAtLeast(1f) }
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.GERMANY) }
    val textMeasurer = rememberTextMeasurer()
    val leftGutterPx = with(LocalDensity.current) { 34.dp.toPx() } // Platz links für die Achsenbeschriftung

    // Meldet den aktuell ausgewählten Punkt (inkl. Zeitstempel) nach oben - für die Karte (Lat/Lon
    // des Scrub-Markers) UND für TripEditScreen (Zeitstempel zum Setzen von Punkt A/B).
    LaunchedEffect(selectedIndex, points) {
        onScrub(points.getOrNull(selectedIndex))
    }

    val accent = MaterialTheme.colorScheme.primary
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val highlightColor = ComposeColor(0xFF26C6DA).copy(alpha = 0.25f)
    val markColor = MaterialTheme.colorScheme.tertiary

    /** Wandelt einen Zeitstempel in eine X-Position im Graphen um (fraktional über die Fahrtdauer). */
    fun xFor(ts: Long, leftGutter: Float, plotW: Float): Float {
        val offsetSeconds = ((ts - startTs) / 1000).toFloat().coerceIn(0f, totalDuration)
        return leftGutter + (offsetSeconds / totalDuration) * plotW
    }

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

            // Hervorgehobene Bereiche (bestehende Markierungen + Vorschau eines geplanten Pause-Cuts)
            // zuerst zeichnen, damit die Geschwindigkeits-Linie danach oben drüber liegt.
            highlightRanges.forEach { (rangeStart, rangeEnd) ->
                val x1 = xFor(rangeStart, leftGutter, plotW)
                val x2 = xFor(rangeEnd, leftGutter, plotW)
                drawRect(
                    color = highlightColor,
                    topLeft = Offset(x1, 0f),
                    size = androidx.compose.ui.geometry.Size(x2 - x1, h)
                )
            }

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

            // A/B-Bearbeitungsmarker: gestrichelte vertikale Linien + Buchstaben-Label
            listOf("A" to markA, "B" to markB).forEach { (label, ts) ->
                if (ts == null) return@forEach
                val x = xFor(ts, leftGutter, plotW)
                drawLine(
                    color = markColor,
                    start = Offset(x, 0f),
                    end = Offset(x, h),
                    strokeWidth = 3f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                )
                val labelLayout = textMeasurer.measure(
                    text = label,
                    style = TextStyle(fontSize = 11.sp, color = markColor)
                )
                drawText(textLayoutResult = labelLayout, topLeft = Offset(x + 3f, 4f))
            }

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

/** Rundet für eine lesbare Achsenbeschriftung auf ein "glattes" Vielfaches von 10 auf. */
fun niceCeilSpeed(value: Float): Float = maxOf(10f, kotlin.math.ceil(value / 10f) * 10f)
