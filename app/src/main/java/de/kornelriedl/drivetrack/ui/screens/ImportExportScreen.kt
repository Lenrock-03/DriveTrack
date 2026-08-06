package de.kornelriedl.drivetrack.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.kornelriedl.drivetrack.data.Trip
import de.kornelriedl.drivetrack.export.GpxExporter
import de.kornelriedl.drivetrack.ui.components.SettingsSectionCard

/**
 * Eigenes Untermenü für Import/Export, vorher drei einzelne Karten direkt in den Einstellungen.
 * Rein organisatorische Verschiebung - keine funktionale Änderung an GPX-Import/-Export/lokalem
 * Backup selbst.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportExportScreen(
    trips: List<Trip>,
    onImportGpx: (Uri) -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: (Uri) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    // "*/*" statt eines konkreten MIME-Typs: viele Dateimanager/Systeme kennen
    // "application/gpx+xml" nicht und graueren die Dateien sonst aus.
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { onImportGpx(it) } }

    val importBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { onImportBackup(it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import & Export") },
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
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            SettingsSectionCard(
                title = "GPX-Import",
                description = "Eine .gpx-Datei als neue Fahrt importieren. Du kannst eine GPX-Datei " +
                    "auch direkt aus einer anderen App über \"Teilen\" an DriveTrack schicken.",
                icon = Icons.Filled.FileUpload
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { importLauncher.launch("*/*") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Filled.FileUpload,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("GPX-Datei importieren")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            SettingsSectionCard(
                title = "GPX-Export",
                description = if (trips.isEmpty())
                    "Noch keine Fahrten zum Exportieren vorhanden."
                else
                    "Alle ${trips.size} Fahrten als einzelne .gpx-Dateien, gebündelt in einem ZIP.",
                icon = Icons.Filled.Archive
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { GpxExporter.shareAllTrips(context, trips) },
                    enabled = trips.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Filled.Archive,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Alle Fahrten exportieren (.zip)")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            SettingsSectionCard(
                title = "Lokales Backup",
                description = "Ein Gesamt-Backup mit allen Benutzern, Fahrzeugen und Fahrten – " +
                    "kann später wieder komplett importiert werden (z. B. bei einem neuen Handy).",
                icon = Icons.Filled.CloudUpload
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onExportBackup,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Filled.CloudUpload,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Backup exportieren")
                }
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { importBackupLauncher.launch("*/*") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Filled.CloudDownload,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Backup importieren")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
