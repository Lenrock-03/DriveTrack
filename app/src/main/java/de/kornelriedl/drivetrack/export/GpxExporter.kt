package de.kornelriedl.drivetrack.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import de.kornelriedl.drivetrack.data.Trip
import org.json.JSONArray
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object GpxExporter {

    private fun isoFormat() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    /** Baut den GPX-1.1-XML-Inhalt aus den gespeicherten GPS-Punkten der Fahrt. */
    fun buildGpxContent(trip: Trip): String {
        val points = try {
            val arr = JSONArray(trip.gpxTrackJson)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                Triple(obj.getDouble("lat"), obj.getDouble("lon"), obj.getLong("ts"))
            }
        } catch (e: Exception) {
            emptyList()
        }

        val format = isoFormat()
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<gpx version=\"1.1\" creator=\"DriveTrack\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
        sb.append("  <trk>\n")
        sb.append("    <name>${escapeXml(trip.name)}</name>\n")
        sb.append("    <trkseg>\n")
        points.forEach { (lat, lon, ts) ->
            sb.append("      <trkpt lat=\"$lat\" lon=\"$lon\">\n")
            sb.append("        <time>${format.format(Date(ts))}</time>\n")
            sb.append("      </trkpt>\n")
        }
        sb.append("    </trkseg>\n")
        sb.append("  </trk>\n")
        sb.append("</gpx>\n")
        return sb.toString()
    }

    private fun escapeXml(text: String): String =
        text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")

    /** Schreibt die GPX-Datei ins Cache-Verzeichnis und gibt eine teilbare content://-URI zurück. */
    private fun writeGpxFile(context: Context, trip: Trip): Uri {
        val gpxDir = File(context.cacheDir, "gpx").apply { mkdirs() }
        val safeName = trip.name.replace(Regex("[^A-Za-z0-9_\\-]"), "_")
        val file = File(gpxDir, "${safeName}_${trip.id}.gpx")
        file.writeText(buildGpxContent(trip))

        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    /** Öffnet den Android-Share-Dialog, um die Fahrt als .gpx zu speichern oder zu teilen. */
    fun shareTrip(context: Context, trip: Trip) {
        val uri = writeGpxFile(context, trip)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/gpx+xml"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Fahrt als GPX teilen"))
    }

    /** Packt alle Fahrten als einzelne .gpx-Dateien in ein ZIP und gibt eine teilbare URI zurück. */
    private fun writeZipFile(context: Context, trips: List<Trip>): Uri {
        val gpxDir = File(context.cacheDir, "gpx").apply { mkdirs() }
        val zipFile = File(gpxDir, "DriveTrack_Export_${System.currentTimeMillis()}.zip")
        val usedNames = mutableSetOf<String>()

        ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
            trips.forEach { trip ->
                val safeName = trip.name.replace(Regex("[^A-Za-z0-9_\\-]"), "_")
                var entryName = "${safeName}_${trip.id}.gpx"
                // Falls zwei Fahrten trotzdem denselben Dateinamen ergeben würden
                var counter = 1
                while (!usedNames.add(entryName)) {
                    entryName = "${safeName}_${trip.id}_$counter.gpx"
                    counter++
                }
                zos.putNextEntry(ZipEntry(entryName))
                zos.write(buildGpxContent(trip).toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
        }

        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            zipFile
        )
    }

    /** Öffnet den Android-Share-Dialog, um alle Fahrten gebündelt als .zip zu exportieren. */
    fun shareAllTrips(context: Context, trips: List<Trip>) {
        if (trips.isEmpty()) return
        val uri = writeZipFile(context, trips)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Alle Fahrten exportieren"))
    }
}
