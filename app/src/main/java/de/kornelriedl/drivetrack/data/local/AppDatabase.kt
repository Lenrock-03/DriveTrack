package de.kornelriedl.drivetrack.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import de.kornelriedl.drivetrack.data.Car
import de.kornelriedl.drivetrack.data.Trip
import de.kornelriedl.drivetrack.data.TripGroup
import de.kornelriedl.drivetrack.data.UserProfile
import java.io.File

@Database(entities = [Trip::class, Car::class, UserProfile::class, TripGroup::class], version = 8, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao
    abstract fun carDao(): CarDao
    abstract fun userDao(): UserDao
    abstract fun tripGroupDao(): TripGroupDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "drivetrack.db"
                )
                    .addMigrations(migration3to4(context.applicationContext), migration4to5, migration5to6, migration6to7, migration7to8)
                    // Kein echtes Migrations-Skript nötig für eine unveröffentlichte Dev-App –
                    // baut die lokale DB bei sonstigen Schemaänderungen einfach sauber neu auf.
                    // (Für den gpxTrackJson-Umzug oben aber bewusst NICHT destruktiv, um
                    // bestehende Fahrten der Nutzer nicht zu verlieren.)
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }

        /**
         * Zieht gpxTrackJson aus der "trips"-Tabelle in je eine Datei pro Fahrt um (siehe
         * TrackFileStore) und entfernt die Spalte danach aus der Tabelle.
         *
         * Der Wert wird bewusst NICHT über eine normale Query/Cursor gelesen, sondern über
         * compileStatement()/simpleQueryForString() – das umgeht Androids CursorWindow-Limit
         * (~2 MB pro Zeile), das bei sehr langen Fahrten sonst genau hier erneut zuschlagen würde.
         */
        private fun migration3to4(appContext: Context): Migration =
            object : Migration(3, 4) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    val tracksDir = File(appContext.filesDir, "tracks").apply { mkdirs() }

                    val ids = mutableListOf<Long>()
                    db.query("SELECT id FROM trips").use { cursor ->
                        while (cursor.moveToNext()) {
                            ids.add(cursor.getLong(0))
                        }
                    }

                    ids.forEach { id ->
                        val stmt = db.compileStatement("SELECT gpxTrackJson FROM trips WHERE id = ?")
                        stmt.bindLong(1, id)
                        val json = try {
                            stmt.simpleQueryForString() ?: "[]"
                        } catch (e: Exception) {
                            "[]"
                        } finally {
                            stmt.close()
                        }
                        File(tracksDir, "trip_$id.json").writeText(json)
                    }

                    db.execSQL(
                        """
                        CREATE TABLE trips_new (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            name TEXT NOT NULL,
                            startTimestamp INTEGER NOT NULL,
                            endTimestamp INTEGER NOT NULL,
                            distanceMeters REAL NOT NULL,
                            avgSpeedKmh REAL NOT NULL,
                            maxSpeedKmh REAL NOT NULL,
                            carId INTEGER
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        INSERT INTO trips_new (id, name, startTimestamp, endTimestamp, distanceMeters, avgSpeedKmh, maxSpeedKmh, carId)
                        SELECT id, name, startTimestamp, endTimestamp, distanceMeters, avgSpeedKmh, maxSpeedKmh, carId FROM trips
                        """.trimIndent()
                    )
                    db.execSQL("DROP TABLE trips")
                    db.execSQL("ALTER TABLE trips_new RENAME TO trips")
                }
            }

        /** Neue, nullbare Spalte für die BT-Auto-Start-Zuordnung – bestehende Autos bleiben erhalten. */
        private val migration4to5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cars ADD COLUMN bluetoothDeviceAddress TEXT")
            }
        }

        /** Neue, nullbare Spalte für das lokale Fahrzeug-Foto – bestehende Autos bleiben erhalten. */
        private val migration5to6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cars ADD COLUMN photoFileName TEXT")
            }
        }

        /**
         * Neue Spalten fürs Zuschneiden/Markieren von Fahrten (TripEditScreen) – bestehende Fahrten
         * bleiben erhalten, Defaults entsprechen "noch nie bearbeitet/markiert".
         */
        private val migration6to7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE trips ADD COLUMN labels TEXT")
                db.execSQL("ALTER TABLE trips ADD COLUMN pausedMinutes INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE trips ADD COLUMN segmentMarksJson TEXT NOT NULL DEFAULT '[]'")
            }
        }

        /**
         * Neue Tabelle für manuell erstellte Fahrten-Gruppen (z.B. "Urlaub Kroatien") + neue,
         * nullbare Spalte an "trips" für die Zuordnung - bestehende Fahrten bleiben erhalten,
         * Default `groupId = NULL` entspricht "noch keiner Gruppe zugeordnet".
         */
        private val migration7to8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS trip_groups (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("ALTER TABLE trips ADD COLUMN groupId INTEGER")
            }
        }
    }
}
