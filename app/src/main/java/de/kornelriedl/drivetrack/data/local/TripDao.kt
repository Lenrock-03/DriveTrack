package de.kornelriedl.drivetrack.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import de.kornelriedl.drivetrack.data.Trip
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {
    @Query("SELECT * FROM trips ORDER BY startTimestamp DESC")
    fun getAllTrips(): Flow<List<Trip>>

    @Query("SELECT * FROM trips ORDER BY startTimestamp DESC LIMIT :limit")
    fun getRecentTrips(limit: Int = 5): Flow<List<Trip>>

    @Query("SELECT SUM(distanceMeters) FROM trips")
    fun getTotalDistanceMeters(): Flow<Double?>

    @Insert
    suspend fun insertTrip(trip: Trip): Long

    @Update
    suspend fun updateTrip(trip: Trip)

    @Delete
    suspend fun deleteTrip(trip: Trip)
}
