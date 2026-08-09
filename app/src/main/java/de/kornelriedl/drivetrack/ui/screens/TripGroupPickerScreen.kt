package de.kornelriedl.drivetrack.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.kornelriedl.drivetrack.data.Trip
import de.kornelriedl.drivetrack.data.TripGroup

/**
 * Checklisten-Screen zum Gruppieren von Fahrten. Zwei Modi über `targetGroupId` unterschieden
 * (kein eigenes enum, analog zu `editingCarId`/`editingTripId` in MainActivity):
 * - `targetGroupId == null` -> Erstellen-Modus: Namensfeld + Checkliste ALLER Fahrten.
 * - `targetGroupId != null` -> Hinzufügen-Modus (von `TripGroupDetailScreen` aus): kein Namensfeld,
 *   Checkliste aller Fahrten, die noch NICHT in dieser Gruppe sind.
 * Da eine Fahrt laut Datenmodell nur in EINER Gruppe sein kann (spiegelt `carId`), zeigen Fahrten,
 * die bereits einer ANDEREN Gruppe angehören, ein Badge mit deren Namen - Auswahl verschiebt sie
 * dorthin, das soll nicht stillschweigend passieren.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripGroupPickerScreen(
    targetGroupId: Long?,
    trips: List<Trip>,
    groups: List<TripGroup>,
    onCreateGroup: (String, Set<Long>) -> Unit,
    onAddTrips: (Set<Long>) -> Unit,
    onBack: () -> Unit
) {
    val isAddMode = targetGroupId != null
    var name by remember { mutableStateOf("") }
    val selectedIds = remember { mutableStateListOf<Long>() }

    val candidateTrips = remember(trips, targetGroupId) {
        (if (isAddMode) trips.filter { it.groupId != targetGroupId } else trips)
            .sortedByDescending { it.startTimestamp }
    }

    fun groupNameFor(groupId: Long?): String? =
        groupId?.let { id -> groups.find { it.id == id }?.name }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isAddMode) "Fahrten hinzufügen" else "Fahrten gruppieren") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Button(
                    onClick = {
                        if (isAddMode) {
                            onAddTrips(selectedIds.toSet())
                        } else {
                            onCreateGroup(name.trim(), selectedIds.toSet())
                        }
                    },
                    enabled = selectedIds.isNotEmpty() && (isAddMode || name.isNotBlank()),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(if (isAddMode) "Hinzufügen" else "Erstellen")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (!isAddMode) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("Gruppenname") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                )
            }

            if (candidateTrips.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isAddMode) "Alle Fahrten sind bereits in dieser Gruppe."
                               else "Keine Fahrten vorhanden.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(candidateTrips, key = { it.id }) { trip ->
                        val checked = trip.id in selectedIds
                        val otherGroupName = if (trip.groupId != null && trip.groupId != targetGroupId) {
                            groupNameFor(trip.groupId)
                        } else null

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (checked) selectedIds.remove(trip.id) else selectedIds.add(trip.id)
                                }
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = {
                                    if (it) selectedIds.add(trip.id) else selectedIds.remove(trip.id)
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = trip.name, style = MaterialTheme.typography.bodyLarge)
                                if (otherGroupName != null) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.secondaryContainer
                                    ) {
                                        Text(
                                            text = "Wechselt aus „$otherGroupName“",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
