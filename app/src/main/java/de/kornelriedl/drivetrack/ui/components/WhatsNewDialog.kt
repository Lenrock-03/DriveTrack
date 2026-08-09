package de.kornelriedl.drivetrack.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.kornelriedl.drivetrack.data.ReleaseNote

/**
 * Zeigt beim ersten Öffnen nach einem Update, was sich geändert hat - `notes` kommt aus
 * `unseenReleaseNotes()` (siehe ReleaseNotes.kt), ausgelöst über einen `LaunchedEffect` in
 * MainActivity.kt, der `ReleaseNotesPreferences` gegen die aktuell installierte Version prüft.
 * Mehrere übersprungene Versionen (Nutzer hat die App eine Weile nicht geöffnet) erscheinen
 * gestapelt, neueste zuerst - spiegelt keine App-Vorlage, bewusst simpel gehalten (kein eigenes
 * Modal-System nötig, `AlertDialog` reicht für einen reinen Info-Dialog mit einem OK-Button).
 */
@Composable
fun WhatsNewDialog(notes: List<ReleaseNote>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Was ist neu") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                notes.forEach { note ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Version ${note.version}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        note.bullets.forEach { bullet ->
                            Text(
                                text = "• $bullet",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
}
