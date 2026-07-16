package de.kornelriedl.drivetrack.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import de.kornelriedl.drivetrack.data.Car
import kotlinx.coroutines.flow.Flow

@Dao
interface CarDao {
    @Query("SELECT * FROM cars ORDER BY name ASC")
    fun getAllCars(): Flow<List<Car>>

    @Insert
    suspend fun insertCar(car: Car): Long

    @Delete
    suspend fun deleteCar(car: Car)
}
