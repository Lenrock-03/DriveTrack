package de.kornelriedl.drivetrack.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import de.kornelriedl.drivetrack.data.Car
import kotlinx.coroutines.flow.Flow

@Dao
interface CarDao {
    @Query("SELECT * FROM cars ORDER BY name ASC")
    fun getAllCars(): Flow<List<Car>>

    @Insert
    suspend fun insertCar(car: Car): Long

    @Update
    suspend fun updateCar(car: Car)

    @Delete
    suspend fun deleteCar(car: Car)

    /** Für den automatischen Aufzeichnungsstart bei Bluetooth-Verbindung (siehe BluetoothConnectionReceiver). */
    @Query("SELECT * FROM cars WHERE bluetoothDeviceAddress = :address LIMIT 1")
    suspend fun getCarByBluetoothAddress(address: String): Car?
}
