package de.kornelriedl.drivetrack.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

enum class NavTab(val label: String) {
    HOME("Home"),
    FAHRTEN("Fahrten"),
    AUFZEICHNEN("Aufzeichnen"),
    KARTE("Karte"),
    EINSTELLUNGEN("Einstellungen")
}

@Composable
fun DriveTrackBottomBar(
    current: NavTab,
    onTabSelected: (NavTab) -> Unit
) {
    NavigationBar {
        NavigationBarItem(
            selected = current == NavTab.HOME,
            onClick = { onTabSelected(NavTab.HOME) },
            icon = { Icon(Icons.Filled.Home, contentDescription = null) },
            label = { Text(NavTab.HOME.label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall) }
        )
        NavigationBarItem(
            selected = current == NavTab.FAHRTEN,
            onClick = { onTabSelected(NavTab.FAHRTEN) },
            icon = { Icon(Icons.Filled.List, contentDescription = null) },
            label = { Text(NavTab.FAHRTEN.label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall) }
        )
        NavigationBarItem(
            selected = current == NavTab.AUFZEICHNEN,
            onClick = { onTabSelected(NavTab.AUFZEICHNEN) },
            icon = {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.FiberManualRecord,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            },
            label = { Text(NavTab.AUFZEICHNEN.label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall) }
        )
        NavigationBarItem(
            selected = current == NavTab.KARTE,
            onClick = { onTabSelected(NavTab.KARTE) },
            icon = { Icon(Icons.Filled.Map, contentDescription = null) },
            label = { Text(NavTab.KARTE.label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall) }
        )
        NavigationBarItem(
            selected = current == NavTab.EINSTELLUNGEN,
            onClick = { onTabSelected(NavTab.EINSTELLUNGEN) },
            icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
            label = { Text(NavTab.EINSTELLUNGEN.label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall) }
        )
    }
}
