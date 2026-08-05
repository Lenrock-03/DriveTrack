package de.kornelriedl.drivetrack.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import de.kornelriedl.drivetrack.data.Car
import de.kornelriedl.drivetrack.data.Trip
import de.kornelriedl.drivetrack.data.UserProfile
import de.kornelriedl.drivetrack.export.GpxExporter

@Composable
fun SettingsScreen(
    trips: List<Trip>,
    onImportGpx: (Uri) -> Unit,
    cars: List<Car>,
    onAddCar: (String) -> Unit,
    onDeleteCar: (Car) -> Unit,
    defaultCarId: Long?,
    onSetDefaultCar: (Long?) -> Unit,
    onSetCarBluetoothDevice: (Car, String?) -> Unit,
    users: List<UserProfile>,
    activeUserId: Long?,
    onSelectUser: (Long?) -> Unit,
    onAddUser: (String) -> Unit,
    onDeleteUser: (UserProfile) -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: (Uri) -> Unit,
    onOpenServerBackup: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var addCarDialogOpen by remember { mutableStateOf(false) }
    var newCarName by remember { mutableStateOf("") }
    var deleteCarTarget by remember { mutableStateOf<Car?>(null) }
    var bluetoothCarTarget by remember { mutableStateOf<Car?>(null) }
    var addUserDialogOpen by remember { mutableStateOf(false) }
    var newUserName by remember { mutableStateOf("") }
    var deleteUserTarget by remember { mutableStateOf<UserProfile?>(null) }

    // "*/*" statt eines konkreten MIME-Typs: viele Dateimanager/Systeme kennen
    // "application/gpx+xml" nicht und graueren die Dateien sonst aus.
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { onImportGpx(it) } }

    val importBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { onImportBackup(it) } }

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

        // Benutzer verwalten
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Benutzer",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))

                if (users.isEmpty()) {
                    Text(
                        text = "Noch keine Benutzer angelegt.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(modifier = Modifier.padding(top = 8.dp)) {
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
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Import
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Import",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Eine .gpx-Datei als neue Fahrt importieren. Du kannst eine GPX-Datei auch direkt aus einer anderen App über \"Teilen\" an DriveTrack schicken.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))
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
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Fahrzeuge verwalten
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Fahrzeuge",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))

                if (cars.isEmpty()) {
                    Text(
                        text = "Noch keine Fahrzeuge angelegt.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "Tippe auf den Stern, um ein Standard-Fahrzeug festzulegen – das wird dann beim Start einer Aufzeichnung vorausgewählt.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        cars.forEach { car ->
                            val isDefault = car.id == defaultCarId
                            val hasBluetoothDevice = car.bluetoothDeviceAddress != null
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = {
                                    onSetDefaultCar(if (isDefault) null else car.id)
                                }) {
                                    Icon(
                                        imageVector = if (isDefault) Icons.Filled.Star else Icons.Filled.StarBorder,
                                        contentDescription = if (isDefault) "Standard-Fahrzeug" else "Als Standard festlegen",
                                        tint = if (isDefault) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Filled.DirectionsCar,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = car.name,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    if (hasBluetoothDevice) {
                                        Text(
                                            text = "Auto-Start via Bluetooth aktiv",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                IconButton(onClick = { bluetoothCarTarget = car }) {
                                    Icon(
                                        imageVector = if (hasBluetoothDevice) Icons.Filled.BluetoothConnected else Icons.Filled.Bluetooth,
                                        contentDescription = "Bluetooth-Auto-Start einrichten",
                                        tint = if (hasBluetoothDevice) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { deleteCarTarget = car }) {
                                    Icon(
                                        imageVector = Icons.Filled.Delete,
                                        contentDescription = "Fahrzeug löschen",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
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
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Export
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Export",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (trips.isEmpty())
                        "Noch keine Fahrten zum Exportieren vorhanden."
                    else
                        "Alle ${trips.size} Fahrten als einzelne .gpx-Dateien, gebündelt in einem ZIP.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))
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
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Vollständiges Backup (Nutzer + Autos + Fahrten, wieder importierbar)
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Backup",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Ein Gesamt-Backup mit allen Benutzern, Fahrzeugen und Fahrten – " +
                        "kann später wieder komplett importiert werden (z. B. bei einem neuen Handy).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))
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
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Server-Backup (Ende-zu-Ende-verschlüsselt, optional mit Login)
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Server-Backup",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Optional: Backup Ende-zu-Ende-verschlüsselt auf deinem eigenen Server sichern. " +
                        "Nur du kannst es entschlüsseln – auch der Server selbst nicht.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))
                OutlinedButton(
                    onClick = onOpenServerBackup,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Filled.CloudUpload,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Server-Backup öffnen")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Version ${appVersionName(context)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))
    }

    if (addCarDialogOpen) {
        AlertDialog(
            onDismissRequest = { addCarDialogOpen = false },
            title = { Text("Fahrzeug hinzufügen") },
            text = {
                OutlinedTextField(
                    value = newCarName,
                    onValueChange = { newCarName = it },
                    singleLine = true,
                    label = { Text("Name (z. B. Fiat Tipo)") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newCarName.isNotBlank()) onAddCar(newCarName.trim())
                    newCarName = ""
                    addCarDialogOpen = false
                }) {
                    Text("Hinzufügen")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    newCarName = ""
                    addCarDialogOpen = false
                }) {
                    Text("Abbrechen")
                }
            }
        )
    }

    deleteCarTarget?.let { car ->
        AlertDialog(
            onDismissRequest = { deleteCarTarget = null },
            title = { Text("Fahrzeug löschen?") },
            text = {
                Text(
                    "\u201E${car.name}\u201C wird gelöscht. Bereits aufgezeichnete Fahrten bleiben " +
                        "erhalten, verlieren aber die Zuordnung zu diesem Fahrzeug."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteCar(car)
                    deleteCarTarget = null
                }) {
                    Text("Löschen", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteCarTarget = null }) {
                    Text("Abbrechen")
                }
            }
        )
    }

    bluetoothCarTarget?.let { car ->
        BluetoothDevicePickerDialog(
            car = car,
            onConfirm = { address ->
                onSetCarBluetoothDevice(car, address)
                bluetoothCarTarget = null
            },
            onDismiss = { bluetoothCarTarget = null }
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
            text = { Text("\u201E${user.name}\u201C wird gelöscht.") },
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
 * Dialog zum Zuordnen eines gekoppelten Bluetooth-Geräts (z. B. Auto-Radio) zu einem Fahrzeug.
 * Verbindet sich das Handy künftig mit diesem Gerät, startet DriveTrack die Aufzeichnung für
 * genau dieses Auto automatisch (siehe BluetoothConnectionReceiver).
 */
@Composable
private fun BluetoothDevicePickerDialog(
    car: Car,
    onConfirm: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var hasBluetoothPermission by remember { mutableStateOf(hasBluetoothConnectPermission(context)) }
    var selectedAddress by remember(car) { mutableStateOf(car.bluetoothDeviceAddress) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasBluetoothPermission = granted }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasBluetoothPermission) {
            permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }

    val bondedDevices = remember(hasBluetoothPermission) {
        if (hasBluetoothPermission) getBondedDevices(context) else emptyList()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bluetooth-Auto-Start für „${car.name}“") },
        text = {
            Column {
                Text(
                    text = "Verbindet sich das Handy mit dem gewählten Gerät (z. B. dem Auto-Radio), " +
                        "startet die Aufzeichnung für dieses Auto automatisch.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                when {
                    !hasBluetoothPermission -> Text(
                        text = "Bluetooth-Berechtigung wird benötigt, um gekoppelte Geräte anzuzeigen.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    bondedDevices.isEmpty() -> Text(
                        text = "Keine gekoppelten Bluetooth-Geräte gefunden. Erst das Auto-Radio in den " +
                            "Bluetooth-Systemeinstellungen koppeln.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    else -> Column(
                        modifier = Modifier
                            .heightIn(max = 280.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedAddress = null }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = selectedAddress == null, onClick = { selectedAddress = null })
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Kein Gerät (Auto-Start aus)")
                        }
                        bondedDevices.forEach { device ->
                            val name = bluetoothDeviceDisplayName(device)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedAddress = device.address }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedAddress == device.address,
                                    onClick = { selectedAddress = device.address }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(name)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedAddress) }) {
                Text("Übernehmen")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        }
    )
}

private fun hasBluetoothConnectPermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    return ContextCompat.checkSelfPermission(
        context, Manifest.permission.BLUETOOTH_CONNECT
    ) == PackageManager.PERMISSION_GRANTED
}

@SuppressLint("MissingPermission") // Permission wird vorher per hasBluetoothConnectPermission geprüft
private fun getBondedDevices(context: Context): List<BluetoothDevice> {
    val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager ?: return emptyList()
    val adapter = manager.adapter ?: return emptyList()
    return try {
        adapter.bondedDevices.toList()
    } catch (e: SecurityException) {
        emptyList()
    }
}

@SuppressLint("MissingPermission") // Permission wird vorher per hasBluetoothConnectPermission geprüft
private fun bluetoothDeviceDisplayName(device: BluetoothDevice): String {
    return try {
        device.name ?: device.address
    } catch (e: SecurityException) {
        device.address
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
