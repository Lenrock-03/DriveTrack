package de.kornelriedl.drivetrack.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import de.kornelriedl.drivetrack.data.Car
import de.kornelriedl.drivetrack.ui.components.CarPhoto
import de.kornelriedl.drivetrack.ui.components.SettingsSectionCard

/**
 * Fahrzeug-Detailseite: Name, Foto (nur lokal gespeichert, siehe CarPhotoStore), Bluetooth-
 * Auto-Start, Standard-Fahrzeug-Schalter, Löschen. Erreichbar über das Fahrzeuge-Untermenü in
 * den Einstellungen. Callbacks nehmen bewusst keinen Car-Parameter (MainActivity kennt bereits
 * die Car-Id) - vermeidet Stale-Object-Bugs bei den mehreren editierbaren Feldern hier.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CarDetailScreen(
    car: Car,
    isDefaultCar: Boolean,
    tripCount: Int,
    totalKm: Double,
    onRenameCar: (String) -> Unit,
    onSetPhoto: (Uri) -> Unit,
    onRemovePhoto: () -> Unit,
    onSetBluetoothDevice: (String?) -> Unit,
    onSetDefault: (Boolean) -> Unit,
    onDeleteCar: () -> Unit,
    onBack: () -> Unit
) {
    var bluetoothDialogOpen by remember { mutableStateOf(false) }
    var deleteDialogOpen by remember { mutableStateOf(false) }

    val photoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { onSetPhoto(it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(car.name) },
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

            // Foto-Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { photoLauncher.launch("image/*") }
            ) {
                CarPhoto(photoFileName = car.photoFileName, modifier = Modifier.fillMaxSize())
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { photoLauncher.launch("image/*") },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.PhotoCamera,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (car.photoFileName == null) "Foto hinzufügen" else "Foto ändern")
                }
                if (car.photoFileName != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = onRemovePhoto) {
                        Text("Entfernen", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Das Foto wird nur lokal auf diesem Gerät gespeichert – es ist nicht Teil " +
                    "des Backups und geht bei einer Neuinstallation verloren.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Name
            var nameDraft by remember(car.id) { mutableStateOf(car.name) }
            OutlinedTextField(
                value = nameDraft,
                onValueChange = { nameDraft = it },
                singleLine = true,
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth()
            )
            if (nameDraft.trim() != car.name && nameDraft.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                TextButton(
                    onClick = { onRenameCar(nameDraft.trim()) },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Speichern")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Standard-Fahrzeug
            SettingsSectionCard(title = "Standard-Fahrzeug") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Wird beim Start einer Aufzeichnung vorausgewählt.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(checked = isDefaultCar, onCheckedChange = onSetDefault)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Bluetooth-Auto-Start
            val hasBluetoothDevice = car.bluetoothDeviceAddress != null
            SettingsSectionCard(
                title = "Bluetooth-Auto-Start",
                description = "Verbindet sich das Handy mit dem gewählten Gerät (z. B. dem " +
                    "Auto-Radio), startet die Aufzeichnung automatisch.",
                icon = if (hasBluetoothDevice) Icons.Filled.BluetoothConnected else Icons.Filled.Bluetooth
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (hasBluetoothDevice) "Eingerichtet" else "Nicht eingerichtet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (hasBluetoothDevice) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { bluetoothDialogOpen = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Gerät auswählen")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Statistik
            Text(
                text = "$tripCount Fahrt${if (tripCount == 1) "" else "en"} · %.1f km".format(totalKm),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedButton(
                onClick = { deleteDialogOpen = true },
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Fahrzeug löschen")
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (bluetoothDialogOpen) {
        BluetoothDevicePickerDialog(
            car = car,
            onConfirm = { address ->
                onSetBluetoothDevice(address)
                bluetoothDialogOpen = false
            },
            onDismiss = { bluetoothDialogOpen = false }
        )
    }

    if (deleteDialogOpen) {
        AlertDialog(
            onDismissRequest = { deleteDialogOpen = false },
            title = { Text("Fahrzeug löschen?") },
            text = {
                Text(
                    "„${car.name}“ wird gelöscht. Bereits aufgezeichnete Fahrten bleiben " +
                        "erhalten, verlieren aber die Zuordnung zu diesem Fahrzeug. Das Foto wird " +
                        "ebenfalls gelöscht."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    deleteDialogOpen = false
                    onDeleteCar()
                }) {
                    Text("Löschen", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteDialogOpen = false }) {
                    Text("Abbrechen")
                }
            }
        )
    }
}

/**
 * Dialog zum Zuordnen eines gekoppelten Bluetooth-Geräts (z. B. Auto-Radio) zu einem Fahrzeug.
 * Verbindet sich das Handy künftig mit diesem Gerät, startet DriveTrack die Aufzeichnung für
 * genau dieses Auto automatisch (siehe BluetoothConnectionReceiver). Bleibt bewusst ein Dialog
 * (nicht inline in den Screen-Body), damit der Bluetooth-Berechtigungsdialog nur bei explizitem
 * Tippen auf "Gerät auswählen" erscheint, nicht schon beim Öffnen der Fahrzeug-Seite.
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
