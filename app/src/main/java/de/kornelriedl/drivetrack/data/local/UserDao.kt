package de.kornelriedl.drivetrack.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import de.kornelriedl.drivetrack.data.UserProfile
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users ORDER BY name ASC")
    fun getAllUsers(): Flow<List<UserProfile>>

    @Insert
    suspend fun insertUser(user: UserProfile): Long

    @Delete
    suspend fun deleteUser(user: UserProfile)
}
