package de.kornelriedl.drivetrack.data.local

import android.content.Context
import java.io.File

/**
 * Speichert das gpxTrackJson einer Fahrt als eigene Datei statt als SQLite-Spalte.
 *
 * Grund: Android begrenzt eine einzelne Zeile, die über einen Cursor gelesen wird, auf ca. 2 MB
 * (CursorWindow). Bei sehr langen Fahrten (viele tausend GPS-Punkte) sprengt das JSON-Array
 * dieses Limit, was beim Laden der Fahrtenliste zu einem harten Absturz führt
 * (SQLiteBlobTooBigException) – unabhängig davon, wie die Tabelle/Query aufgebaut ist. Ein Datei-
 * basierter Speicher hat dieses Limit nicht.
 */
object TrackFileStore {

    private fun dir(context: Context): File =
        File(context.filesDir, "tracks").apply { mkdirs() }

    private fun file(context: Context, tripId: Long): File =
        File(dir(context), "trip_$tripId.json")

    fun write(context: Context, tripId: Long, gpxTrackJson: String) {
        file(context, tripId).writeText(gpxTrackJson)
    }

    fun read(context: Context, tripId: Long): String =
        try {
            file(context, tripId).readText()
        } catch (e: Exception) {
            "[]"
        }

    fun delete(context: Context, tripId: Long) {
        file(context, tripId).delete()
    }
}
