package de.kornelriedl.drivetrack.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import de.kornelriedl.drivetrack.data.TripGroup
import kotlinx.coroutines.flow.Flow

@Dao
interface TripGroupDao {
    @Query("SELECT * FROM trip_groups ORDER BY name ASC")
    fun getAllGroups(): Flow<List<TripGroup>>

    @Insert
    suspend fun insertGroup(group: TripGroup): Long

    @Update
    suspend fun updateGroup(group: TripGroup)

    @Delete
    suspend fun deleteGroup(group: TripGroup)
}
