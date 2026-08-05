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
    val bluetoothDeviceAddress: String? = null
)
