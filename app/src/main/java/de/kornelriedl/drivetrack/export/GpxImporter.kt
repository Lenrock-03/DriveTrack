package de.kornelriedl.drivetrack.export

import android.content.Context
import android.net.Uri
import android.util.Xml
import de.kornelriedl.drivetrack.data.Trip
import org.xmlpull.v1.XmlPullParser
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object GpxImporter {

    /** Liest eine GPX-Datei über eine content://- oder file://-URI und wandelt sie in ein Trip-Objekt um. */
    fun importFromUri(context: Context, uri: Uri): Trip? {
        return context.contentResolver.openInputStream(uri)?.use { stream ->
            parseGpx(stream.bufferedReader().readText())
        }
    }

    /** Baut ein Trip-Objekt aus GPX-1.1-XML-Inhalt (liest den ersten <trk>/<trkseg> aus). */
    fun parseGpx(gpxContent: String): Trip? {
        val parser: XmlPullParser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(gpxContent.reader())

        var trackName: String? = null
        val points = mutableListOf<Triple<Double, Double, Long>>()

        var currentTag = ""
        var inTrkpt = false
        var lat = 0.0
        var lon = 0.0
        var timeText: String? = null

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    currentTag = parser.name
                    if (currentTag == "trkpt") {
                        inTrkpt = true
                        lat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull() ?: 0.0
                        lon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull() ?: 0.0
                        timeText = null
                    }
                }
                XmlPullParser.TEXT -> {
                    val text = parser.text?.trim()
                    if (!text.isNullOrEmpty()) {
                        if (currentTag == "name" && trackName == null) trackName = text
                        if (currentTag == "time" && inTrkpt) timeText = text
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "trkpt") {
                        val ts = timeText?.let { parseGpxTime(it) }
                            ?: (points.lastOrNull()?.third ?: System.currentTimeMillis())
                        points.add(Triple(lat, lon, ts))
                        inTrkpt = false
                    }
                    currentTag = ""
                }
            }
            eventType = parser.next()
        }

        if (points.size < 2) return null

        var distanceMeters = 0.0
        var maxSpeedKmh = 0.0
        for (i in 1 until points.size) {
            val d = haversine(points[i - 1].first, points[i - 1].second, points[i].first, points[i].second)
            distanceMeters += d
            val dtSeconds = (points[i].third - points[i - 1].third) / 1000.0
            if (dtSeconds > 0) {
                val speedKmh = (d / dtSeconds) * 3.6
                if (speedKmh in 0.0..300.0 && speedKmh > maxSpeedKmh) maxSpeedKmh = speedKmh
            }
        }

        val startTs = points.first().third
        val endTs = points.last().third
        val durationHours = (endTs - startTs) / 3_600_000.0
        val avgSpeedKmh = if (durationHours > 0) (distanceMeters / 1000.0) / durationHours else 0.0

        val pointsJson = points.joinToString(prefix = "[", postfix = "]") { (pLat, pLon, ts) ->
            "{\"lat\":$pLat,\"lon\":$pLon,\"ts\":$ts}"
        }

        return Trip(
            name = trackName ?: "Importierte Fahrt",
            startTimestamp = startTs,
            endTimestamp = endTs,
            distanceMeters = distanceMeters,
            avgSpeedKmh = avgSpeedKmh,
            maxSpeedKmh = maxSpeedKmh,
            gpxTrackJson = pointsJson
        )
    }

    private val timeFormats = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss'Z'"
    )

    private fun parseGpxTime(text: String): Long? {
        for (pattern in timeFormats) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US)
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                return sdf.parse(text)?.time
            } catch (e: Exception) {
                // nächstes Format probieren
            }
        }
        return null
    }

    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
    }
}
