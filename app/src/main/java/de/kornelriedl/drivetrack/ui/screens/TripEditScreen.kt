package de.kornelriedl.drivetrack.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.kornelriedl.drivetrack.data.GraphPoint
import de.kornelriedl.drivetrack.data.PauseCut
import de.kornelriedl.drivetrack.data.SegmentMark
import de.kornelriedl.drivetrack.data.Trip
import de.kornelriedl.drivetrack.data.TripEditPlan
import de.kornelriedl.drivetrack.data.labelIcon
import de.kornelriedl.drivetrack.data.labelList
import de.kornelriedl.drivetrack.data.segmentMarks
import de.kornelriedl.drivetrack.ui.components.RouteColorMode
import de.kornelriedl.drivetrack.ui.components.RouteDetailMap
import de.kornelriedl.drivetrack.ui.components.SegmentMarkRow
import de.kornelriedl.drivetrack.ui.components.SpeedGraph
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Presets für Fahrt-Labels und Abschnitts-Markierungen - deckt die häufigsten Fälle ab. */
private val LABEL_PRESETS = listOf("⛴ Fähre", "☕ Pause gemacht", "🌙 Nachtfahrt")

/** Eine noch nicht angewendete, destruktive Änderung in der Änderungsliste. */
private sealed class PendingAction {
    abstract val description: String
    data class TrimStart(val ts: Long, override val description: String) : PendingAction()
    data class TrimEnd(val ts: Long, override val description: String) : PendingAction()
    data class Cut(val start: Long, val end: Long, override val description: String) : PendingAction()
}

/**
 * "Bearbeiten"-Menü einer Fahrt: Zuschneiden (Anfang/Ende kürzen, Pause in der Mitte entfernen) und
 * Markieren (Fahrt-Labels + Streckenabschnitte, z.B. eine Fährüberfahrt). Zuschneiden ist
 * destruktiv (Punkte werden endgültig entfernt) und läuft über eine einsehbare Änderungsliste +
 * Bestätigungsdialog; Labels/Markierungen sind reine Metadaten und werden sofort gespeichert.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripEditScreen(
    trip: Trip,
    onBack: () -> Unit,
    onApplyEdit: (TripEditPlan) -> Unit,
    onUpdateLabels: (List<String>) -> Unit,
    onAddSegmentMark: (SegmentMark) -> Unit,
    onDeleteSegmentMark: (SegmentMark) -> Unit
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.GERMANY) }
    fun fmt(ts: Long) = timeFormat.format(Date(ts))
    fun fmtMinutes(ms: Long) = "${(ms / 60000).coerceAtLeast(1)} min"

    var scrubPoint by remember { mutableStateOf<GraphPoint?>(null) }
    var markA by remember { mutableStateOf<Long?>(null) }
    var markB by remember { mutableStateOf<Long?>(null) }
    val pendingActions = remember { mutableStateListOf<PendingAction>() }
    var showApplyConfirm by remember { mutableStateOf(false) }
    var showCustomLabelDialog by remember { mutableStateOf(false) }
    var showMarkDialog by remember { mutableStateOf(false) }

    val existingMarks = trip.segmentMarks()
    val labels = trip.labelList()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Fahrt bearbeiten") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text("Markieren", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Labels für die ganze Fahrt, sofort gespeichert.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowChipsRow {
                LABEL_PRESETS.forEach { preset ->
                    val active = labels.contains(preset)
                    LabelChip(
                        text = preset,
                        selected = active,
                        onClick = {
                            val updated = if (active) labels - preset else labels + preset
                            onUpdateLabels(updated)
                        }
                    )
                }
                labels.filter { it !in LABEL_PRESETS }.forEach { custom ->
                    LabelChip(
                        text = "${labelIcon(custom)} $custom",
                        selected = true,
                        onClick = { onUpdateLabels(labels - custom) }
                    )
                }
                LabelChip(text = "+ eigenes Label", selected = false, onClick = { showCustomLabelDialog = true })
            }

            Spacer(modifier = Modifier.height(20.dp))

            Card(
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth().height(220.dp)
            ) {
                RouteDetailMap(
                    trip = trip,
                    scrubPoint = scrubPoint?.let { it.lat to it.lon },
                    routeColorMode = RouteColorMode.STANDARD,
                    markA = markA,
                    markB = markB,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            val pendingHighlights = pendingActions.filterIsInstance<PendingAction.Cut>().map { it.start to it.end }
            val markHighlights = existingMarks.map { it.startTs to it.endTs }
            Box(modifier = Modifier.fillMaxWidth().height(160.dp)) {
                SpeedGraph(
                    trip = trip,
                    onScrub = { scrubPoint = it },
                    markA = markA,
                    markB = markB,
                    highlightRanges = pendingHighlights + markHighlights,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { scrubPoint?.let { markA = it.timestamp } },
                    enabled = scrubPoint != null,
                    modifier = Modifier.weight(1f)
                ) { Text("Punkt A setzen") }
                OutlinedButton(
                    onClick = { scrubPoint?.let { markB = it.timestamp } },
                    enabled = scrubPoint != null,
                    modifier = Modifier.weight(1f)
                ) { Text("Punkt B setzen") }
            }
            if (markA != null || markB != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        text = listOfNotNull(
                            markA?.let { "A = ${fmt(it)}" },
                            markB?.let { "B = ${fmt(it)}" }
                        ).joinToString("   "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = { markA = null; markB = null }) { Text("Auswahl zurücksetzen") }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("Zuschneiden", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = {
                        val a = markA ?: return@OutlinedButton
                        pendingActions.removeAll { it is PendingAction.TrimStart }
                        pendingActions.add(PendingAction.TrimStart(a, "Anfang bis ${fmt(a)} abschneiden"))
                    },
                    enabled = markA != null,
                    modifier = Modifier.weight(1f)
                ) { Text("Anfang bis A abschneiden") }
                OutlinedButton(
                    onClick = {
                        val b = markB ?: return@OutlinedButton
                        pendingActions.removeAll { it is PendingAction.TrimEnd }
                        pendingActions.add(PendingAction.TrimEnd(b, "B bis Ende abschneiden (ab ${fmt(b)})"))
                    },
                    enabled = markB != null,
                    modifier = Modifier.weight(1f)
                ) { Text("B bis Ende abschneiden") }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    val a = markA ?: return@OutlinedButton
                    val b = markB ?: return@OutlinedButton
                    val start = minOf(a, b)
                    val end = maxOf(a, b)
                    pendingActions.add(
                        PendingAction.Cut(start, end, "Pause entfernt: ${fmt(start)}–${fmt(end)} (${fmtMinutes(end - start)})")
                    )
                },
                enabled = markA != null && markB != null,
                modifier = Modifier.fillMaxWidth()
            ) { Text("A–B als Pause entfernen") }

            if (pendingActions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Geplante Änderungen",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                pendingActions.forEach { action ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .padding(top = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(action.description, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        IconButton(onClick = { pendingActions.remove(action) }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Filled.Close, contentDescription = "Entfernen", modifier = Modifier.size(16.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = { showApplyConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Änderungen anwenden") }
            }

            Spacer(modifier = Modifier.height(20.dp))
            OutlinedButton(
                onClick = { showMarkDialog = true },
                enabled = markA != null && markB != null,
                modifier = Modifier.fillMaxWidth()
            ) { Text("A–B als Abschnitt markieren…") }

            if (existingMarks.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Markierte Abschnitte",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                existingMarks.forEach { mark ->
                    SegmentMarkRow(
                        trip = trip,
                        mark = mark,
                        onDelete = { onDeleteSegmentMark(mark) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showCustomLabelDialog) {
        LabelInputDialog(
            title = "Eigenes Label",
            onConfirm = { text ->
                if (text.isNotBlank()) onUpdateLabels(labels + text.trim())
                showCustomLabelDialog = false
            },
            onDismiss = { showCustomLabelDialog = false }
        )
    }

    if (showMarkDialog) {
        val a = markA
        val b = markB
        if (a != null && b != null) {
            val start = minOf(a, b)
            val end = maxOf(a, b)
            AlertDialog(
                onDismissRequest = { showMarkDialog = false },
                title = { Text("Abschnitt markieren") },
                text = {
                    Column {
                        Text(
                            "${fmt(start)} – ${fmt(end)} (${fmtMinutes(end - start)})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        LABEL_PRESETS.forEach { preset ->
                            Text(
                                preset,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onAddSegmentMark(SegmentMark(preset, start, end))
                                        showMarkDialog = false
                                    }
                                    .padding(vertical = 10.dp)
                            )
                        }
                        var customText by remember { mutableStateOf("") }
                        OutlinedTextField(
                            value = customText,
                            onValueChange = { customText = it },
                            label = { Text("Eigener Text") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        TextButton(
                            onClick = {
                                if (customText.isNotBlank()) {
                                    onAddSegmentMark(SegmentMark(customText.trim(), start, end))
                                    showMarkDialog = false
                                }
                            },
                            modifier = Modifier.align(Alignment.End)
                        ) { Text("Hinzufügen") }
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = { showMarkDialog = false }) { Text("Abbrechen") } }
            )
        }
    }

    if (showApplyConfirm) {
        AlertDialog(
            onDismissRequest = { showApplyConfirm = false },
            title = { Text("Änderungen anwenden?") },
            text = { Text("Diese Änderung kann nicht rückgängig gemacht werden. Fortfahren?") },
            confirmButton = {
                TextButton(onClick = {
                    val plan = TripEditPlan(
                        trimStartTs = pendingActions.filterIsInstance<PendingAction.TrimStart>().firstOrNull()?.ts,
                        trimEndTs = pendingActions.filterIsInstance<PendingAction.TrimEnd>().firstOrNull()?.ts,
                        pauseCuts = pendingActions.filterIsInstance<PendingAction.Cut>().map { PauseCut(it.start, it.end) }
                    )
                    showApplyConfirm = false
                    onApplyEdit(plan)
                }) { Text("Anwenden") }
            },
            dismissButton = {
                TextButton(onClick = { showApplyConfirm = false }) { Text("Abbrechen") }
            }
        )
    }
}

@Composable
private fun LabelInputDialog(title: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text("Hinzufügen") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}

@Composable
private fun FlowChipsRow(content: @Composable () -> Unit) {
    // Kein FlowRow im Projekt eingebunden (kleine Anzahl Chips, passt normalerweise in eine Zeile
    // bzw. bricht dank horizontalScroll trotzdem nicht um) - horizontal scrollbare Row reicht.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) { content() }
}

@Composable
private fun LabelChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
