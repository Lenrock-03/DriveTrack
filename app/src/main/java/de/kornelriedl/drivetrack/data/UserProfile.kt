package de.kornelriedl.drivetrack.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserProfile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String
)
