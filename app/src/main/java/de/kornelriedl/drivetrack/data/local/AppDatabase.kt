package de.kornelriedl.drivetrack.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import de.kornelriedl.drivetrack.data.Car
import de.kornelriedl.drivetrack.data.Trip
import de.kornelriedl.drivetrack.data.UserProfile

@Database(entities = [Trip::class, Car::class, UserProfile::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao
    abstract fun carDao(): CarDao
    abstract fun userDao(): UserDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "drivetrack.db"
                )
                    // Kein echtes Migrations-Skript nötig für eine unveröffentlichte Dev-App –
                    // baut die lokale DB bei Schemaänderungen einfach sauber neu auf.
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
    }
}
