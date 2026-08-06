package de.kornelriedl.drivetrack.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cars")
data class Car(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    // MAC-Adresse eines gekoppelten Bluetooth-Geräts (z. B. Auto-Radio). Wenn gesetzt, startet
    // eine Verbindung zu genau diesem Gerät automatisch eine Aufzeichnung für dieses Auto
    // (siehe BluetoothConnectionReceiver).
    val bluetoothDeviceAddress: String? = null,
    // Dateiname (NICHT absoluter Pfad) eines lokal gespeicherten Fotos unter filesDir/car_photos/
    // (siehe CarPhotoStore), z. B. "car_7_1754476800000.jpg". Der Zeitstempel im Namen sorgt dafür,
    // dass ein Fotowechsel eine neue DB-Zeile erzeugt und Compose zuverlässig neu zeichnet.
    // Bewusst NICHT Teil des Backups (weder lokal noch Server) - siehe BackupExporter.kt.
    val photoFileName: String? = null
)
