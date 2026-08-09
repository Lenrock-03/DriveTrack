package de.kornelriedl.drivetrack.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
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
import de.kornelriedl.drivetrack.ui.components.GroupRouteMap
import de.kornelriedl.drivetrack.ui.components.GroupSpeedGraph
import de.kornelriedl.drivetrack.ui.components.RouteColorLegend
import de.kornelriedl.drivetrack.ui.components.RouteColorMode
import de.kornelriedl.drivetrack.ui.components.RouteColorModeSelector

/**
 * Vollbild-Kartenansicht einer Fahrten-Gruppe, erreichbar über Antippen der kleinen Übersichtskarte
 * in TripGroupDetailScreen - spiegelt TripDetailScreens Karte+Graph-Aufbau (Card mit
 * Modifier.weight(1f) über der Karte, Geschwindigkeits-Graph darunter, per Scrub verbundener
 * Marker, RouteColorModeSelector/RouteColorLegend oben rechts bzw. unten links über der Karte), nur
 * über ALLE Mitgliedsfahrten kombiniert statt einer einzelnen. Die Stat-Kacheln fehlen hier bewusst
 * (schon auf TripGroupDetailScreen zu sehen, keine Dopplung). GroupSpeedGraph nutzt
 * buildGroupSpeedSeries() (TripGeoMath.kt) - Geschwindigkeit wird dabei NIE über die Nahtstelle
 * zwischen zwei Fahrten hinweg berechnet, siehe dortiger Doc-Kommentar (sonst Ausreißer durch die
 * Luftlinie zwischen dem Ziel einer Fahrt und dem Start der nächsten).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripGroupRouteScreen(
    group: TripGroup,
    trips: List<Trip>,
    onBack: () -> Unit
) {
    var scrubPoint by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var routeColorMode by remember { mutableStateOf(RouteColorMode.STANDARD) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(group.name) },
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
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            Card(
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    GroupRouteMap(
                        trips = trips,
                        scrubPoint = scrubPoint,
                        routeColorMode = routeColorMode,
                        modifier = Modifier.fillMaxSize()
                    )
                    RouteColorModeSelector(
                        selected = routeColorMode,
                        onSelect = { routeColorMode = it },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                    )
                    if (routeColorMode == RouteColorMode.SPEED) {
                        RouteColorLegend(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(10.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            GroupSpeedGraph(
                trips = trips,
                onScrub = { point -> scrubPoint = point?.let { it.lat to it.lon } },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
