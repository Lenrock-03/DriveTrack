package de.kornelriedl.drivetrack.ui.screens

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.kornelriedl.drivetrack.data.Car
import de.kornelriedl.drivetrack.data.UserProfile
import de.kornelriedl.drivetrack.ui.components.AddCarDialog
import de.kornelriedl.drivetrack.ui.components.CarPhoto
import de.kornelriedl.drivetrack.ui.components.SettingsGroupHeader
import de.kornelriedl.drivetrack.ui.components.SettingsNavCard
import de.kornelriedl.drivetrack.ui.components.SettingsSectionCard

@Composable
fun SettingsScreen(
    cars: List<Car>,
    onAddCar: (String) -> Unit,
    defaultCarId: Long?,
    onOpenCar: (Car) -> Unit,
    users: List<UserProfile>,
    activeUserId: Long?,
    onSelectUser: (Long?) -> Unit,
    onAddUser: (String) -> Unit,
    onDeleteUser: (UserProfile) -> Unit,
    onOpenServerBackup: () -> Unit,
    onOpenImportExport: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var addCarDialogOpen by remember { mutableStateOf(false) }
    var addUserDialogOpen by remember { mutableStateOf(false) }
    var newUserName by remember { mutableStateOf("") }
    var deleteUserTarget by remember { mutableStateOf<UserProfile?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Einstellungen",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(24.dp))

        // --- Konto ---
        SettingsGroupHeader("Konto")

        SettingsSectionCard(title = "Benutzer", icon = Icons.Filled.Person) {
            if (users.isEmpty()) {
                Text(
                    text = "Noch keine Benutzer angelegt.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column {
                    users.forEach { user ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = user.id == activeUserId,
                                onClick = { onSelectUser(user.id) }
                            )
                            Text(
                                text = user.name,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { deleteUserTarget = user }) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = "Benutzer löschen",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
                onClick = { addUserDialogOpen = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Benutzer hinzufügen")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        SettingsNavCard(
            title = "Server-Backup",
            description = "Backup Ende-zu-Ende-verschlüsselt auf deinem eigenen Server sichern.",
            icon = Icons.Filled.CloudSync,
            onClick = onOpenServerBackup
        )

        Spacer(modifier = Modifier.height(24.dp))

        // --- Fahrzeuge ---
        SettingsGroupHeader("Fahrzeuge")

        SettingsSectionCard(title = "Meine Fahrzeuge", icon = Icons.Filled.DirectionsCar) {
            if (cars.isEmpty()) {
                Text(
                    text = "Noch keine Fahrzeuge angelegt.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column {
                    cars.forEach { car ->
                        val isDefault = car.id == defaultCarId
                        val hasBluetoothDevice = car.bluetoothDeviceAddress != null
                        val badges = listOfNotNull(
                            "Standard".takeIf { isDefault },
                            "Bluetooth-Auto-Start".takeIf { hasBluetoothDevice }
                        ).joinToString(" · ")

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onOpenCar(car) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CarPhoto(
                                photoFileName = car.photoFileName,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = car.name, style = MaterialTheme.typography.bodyLarge)
                                if (badges.isNotEmpty()) {
                                    Text(
                                        text = badges,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.Filled.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
                onClick = { addCarDialogOpen = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Filled.DirectionsCar,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Fahrzeug hinzufügen")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // --- Daten ---
        SettingsGroupHeader("Daten")

        SettingsNavCard(
            title = "Import & Export",
            description = "GPX-Import, GPX-Export und lokales Gesamt-Backup.",
            icon = Icons.Filled.SwapVert,
            onClick = onOpenImportExport
        )

        Spacer(modifier = Modifier.height(24.dp))

        // --- Über ---
        SettingsGroupHeader("Über")
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Version ${appVersionName(context)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    if (addCarDialogOpen) {
        AddCarDialog(
            onConfirm = onAddCar,
            onDismiss = { addCarDialogOpen = false }
        )
    }

    if (addUserDialogOpen) {
        AlertDialog(
            onDismissRequest = { addUserDialogOpen = false },
            title = { Text("Benutzer hinzufügen") },
            text = {
                OutlinedTextField(
                    value = newUserName,
                    onValueChange = { newUserName = it },
                    singleLine = true,
                    label = { Text("Name") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newUserName.isNotBlank()) onAddUser(newUserName.trim())
                    newUserName = ""
                    addUserDialogOpen = false
                }) {
                    Text("Hinzufügen")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    newUserName = ""
                    addUserDialogOpen = false
                }) {
                    Text("Abbrechen")
                }
            }
        )
    }

    deleteUserTarget?.let { user ->
        AlertDialog(
            onDismissRequest = { deleteUserTarget = null },
            title = { Text("Benutzer löschen?") },
            text = { Text("„${user.name}“ wird gelöscht.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteUser(user)
                    deleteUserTarget = null
                }) {
                    Text("Löschen", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteUserTarget = null }) {
                    Text("Abbrechen")
                }
            }
        )
    }
}

/**
 * Liest die versionName aus dem PackageManager statt aus BuildConfig (das erfordert kein
 * zusätzliches "buildFeatures.buildConfig = true" in build.gradle.kts und spiegelt garantiert
 * die tatsächlich installierte APK wider).
 */
private fun appVersionName(context: Context): String =
    try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    } catch (e: PackageManager.NameNotFoundException) {
        "?"
    }
