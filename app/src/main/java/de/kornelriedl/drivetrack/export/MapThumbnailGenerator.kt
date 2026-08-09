package de.kornelriedl.drivetrack.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import de.kornelriedl.drivetrack.data.Trip
import de.kornelriedl.drivetrack.data.local.TrackFileStore
import org.json.JSONArray
import java.io.File
import java.net.URL
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.tan

/**
 * Rendert für eine Fahrt ein STATISCHES Vorschaubild der Route inkl. echter OSM-Kacheln –
 * bewusst KEINE lebendige osmdroid-MapView (das war der Grund für Abstürze/Ruckler beim Scrollen
 * durch viele Fahrten, da jede Listenzeile eine eigene native Karten-Instanz erzeugt hätte).
 *
 * Läuft komplett im Hintergrund-Thread (IO-Dispatcher vom Aufrufer), Ergebnis wird als PNG
 * im Cache-Verzeichnis abgelegt, damit es beim nächsten Öffnen sofort verfügbar ist –
 * kein erneutes Kachel-Laden nötig.
 */
object MapThumbnailGenerator {

    private const val TILE_SIZE = 256
    private const val OUTPUT_SIZE = 300 // px – wird beim Anzeigen passend herunterskaliert
    private const val MAX_TILES_PER_AXIS = 3 // Deckelt Netzwerk-/Speicheraufwand pro Thumbnail

    /** Liefert das gecachte Thumbnail oder rendert (und cached) es neu. Gibt null zurück, falls unmöglich. */
    fun getOrCreate(context: Context, trip: Trip): Bitmap? {
        val cacheFile = File(cacheDir(context), "trip_${trip.id}.png")
        if (cacheFile.exists()) {
            BitmapFactory.decodeFile(cacheFile.absolutePath)?.let { return it }
        }

        val points = parsePoints(TrackFileStore.read(context, trip.id))
        if (points.size < 2) return null

        val bitmap = renderThumbnail(listOf(points)) ?: return null

        try {
            cacheFile.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
            }
        } catch (e: Exception) {
            // Cache-Schreibfehler ignorieren – Bild trotzdem zurückgeben
        }

        return bitmap
    }

    /**
     * Löscht ein gecachtes Thumbnail, z.B. nach einem destruktiven Fahrt-Schnitt (TripEditScreen) -
     * die gespeicherte Route hat sich geändert, das nächste getOrCreate() muss sie neu rendern statt
     * das alte (jetzt veraltete) PNG weiterzuverwenden.
     */
    fun invalidate(context: Context, tripId: Long) {
        File(cacheDir(context), "trip_$tripId.png").delete()
    }

    /**
     * Wie getOrCreate(), aber über die kombinierten Routen ALLER Mitgliedsfahrten einer Gruppe
     * (TripGroupListItem) - jede Fahrt bekommt einen eigenen, unverbundenen Pfad (siehe
     * renderThumbnail-Kommentar), keine "Teleport"-Linie zwischen dem Ziel einer Fahrt und dem Start
     * der nächsten. Der Cache-Dateiname enthält die sortierten Fahrt-Ids, damit sich das Bild
     * automatisch neu aufbaut, sobald sich die Gruppen-Mitgliedschaft ändert (Fahrt hinzugefügt/
     * entfernt), statt ein veraltetes Bild weiterzuverwenden.
     */
    fun getOrCreateForGroup(context: Context, groupId: Long, trips: List<Trip>): Bitmap? {
        val idsKey = trips.map { it.id }.sorted().joinToString("_")
        val cacheFile = File(cacheDir(context), "group_${groupId}_$idsKey.png")
        if (cacheFile.exists()) {
            BitmapFactory.decodeFile(cacheFile.absolutePath)?.let { return it }
        }

        val perTripPoints = trips.map { parsePoints(TrackFileStore.read(context, it.id)) }
            .filter { it.size >= 2 }
        if (perTripPoints.isEmpty()) return null

        val bitmap = renderThumbnail(perTripPoints) ?: return null

        try {
            // Cache-Dateien dieser Gruppe mit einer ANDEREN (alten) Mitgliedschaft aufräumen, sonst
            // sammeln sich verwaiste PNGs an, wenn sich die Fahrtenauswahl öfter ändert.
            cacheDir(context).listFiles { f -> f.name.startsWith("group_${groupId}_") }?.forEach { it.delete() }
            cacheFile.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 90, out) }
        } catch (e: Exception) {
            // Cache-Schreibfehler ignorieren – Bild trotzdem zurückgeben
        }

        return bitmap
    }

    /** Löscht alle gecachten Thumbnails einer Gruppe, z.B. wenn sie komplett gelöscht wird. */
    fun invalidateGroup(context: Context, groupId: Long) {
        cacheDir(context).listFiles { f -> f.name.startsWith("group_${groupId}_") }?.forEach { it.delete() }
    }

    private fun cacheDir(context: Context): File =
        File(context.cacheDir, "trip_thumbnails").apply { mkdirs() }

    private fun parsePoints(gpxTrackJson: String): List<Pair<Double, Double>> {
        return try {
            val arr = JSONArray(gpxTrackJson)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                obj.getDouble("lat") to obj.getDouble("lon")
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun lonToTileX(lon: Double, zoom: Int): Double = (lon + 180.0) / 360.0 * (1 shl zoom)
    private fun latToTileY(lat: Double, zoom: Int): Double {
        val latRad = Math.toRadians(lat)
        return (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * (1 shl zoom)
    }

    /**
     * Rendert ein oder mehrere Routen in dasselbe Thumbnail - eine Liste pro Fahrt, damit jede Route
     * einen eigenen, unverbundenen Pfad bekommt (siehe Zeichen-Schleife unten). Die Kachel-/Zoom-/
     * Zuschnitt-Berechnung nutzt weiterhin die gemeinsame Bounding-Box ALLER Punkte zusammen, damit
     * bei mehreren Fahrten (Gruppen-Thumbnail) alle Routen ins Bild passen.
     */
    private fun renderThumbnail(tripPointLists: List<List<Pair<Double, Double>>>): Bitmap? {
        val allPoints = tripPointLists.flatten()
        if (allPoints.isEmpty()) return null
        val minLat = allPoints.minOf { it.first }
        val maxLat = allPoints.maxOf { it.first }
        val minLon = allPoints.minOf { it.second }
        val maxLon = allPoints.maxOf { it.second }

        // Höchstmöglichen Zoom wählen, der noch in MAX_TILES_PER_AXIS x MAX_TILES_PER_AXIS Kacheln passt
        var zoom = 16
        var minTileX: Int
        var maxTileX: Int
        var minTileY: Int
        var maxTileY: Int
        while (true) {
            minTileX = floor(lonToTileX(minLon, zoom)).toInt()
            maxTileX = floor(lonToTileX(maxLon, zoom)).toInt()
            minTileY = floor(latToTileY(maxLat, zoom)).toInt() // Norden = kleineres Y
            maxTileY = floor(latToTileY(minLat, zoom)).toInt()
            val tilesX = maxTileX - minTileX + 1
            val tilesY = maxTileY - minTileY + 1
            if ((tilesX <= MAX_TILES_PER_AXIS && tilesY <= MAX_TILES_PER_AXIS) || zoom <= 4) break
            zoom--
        }

        val tilesX = maxTileX - minTileX + 1
        val tilesY = maxTileY - minTileY + 1
        val composite = Bitmap.createBitmap(tilesX * TILE_SIZE, tilesY * TILE_SIZE, Bitmap.Config.ARGB_8888)
        val compositeCanvas = Canvas(composite)
        compositeCanvas.drawColor(Color.parseColor("#1B1B1B")) // Fallback, falls einzelne Kacheln fehlschlagen

        for (tx in minTileX..maxTileX) {
            for (ty in minTileY..maxTileY) {
                val tileBitmap = fetchTile(zoom, tx, ty)
                if (tileBitmap != null) {
                    val destX = ((tx - minTileX) * TILE_SIZE).toFloat()
                    val destY = ((ty - minTileY) * TILE_SIZE).toFloat()
                    compositeCanvas.drawBitmap(tileBitmap, destX, destY, null)
                }
            }
        }

        // Kontrast/Helligkeit angleichen, damit es zum Dark-Theme passt (wie bei der echten Karte)
        val adjusted = Bitmap.createBitmap(composite.width, composite.height, Bitmap.Config.ARGB_8888)
        Canvas(adjusted).drawBitmap(composite, 0f, 0f, Paint().apply { colorFilter = buildContrastFilter() })

        // Route(n) in Weltpixel-Koordinaten relativ zur Kachel-Ecke oben links einzeichnen. Jede
        // Fahrt bekommt ihren EIGENEN Pfad (moveTo startet pro Fahrt neu) - sonst würde bei mehreren
        // Fahrten (Gruppen-Thumbnail) eine gerade "Teleport"-Linie zwischen dem Ziel der einen und
        // dem Start der nächsten Fahrt eingezeichnet, spiegelt dieselbe Überlegung wie
        // buildGroupSpeedSeries() in TripGeoMath.kt.
        val routePaint = Paint().apply {
            color = Color.parseColor("#FF7A1A")
            strokeWidth = 5f
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isAntiAlias = true
        }
        tripPointLists.forEach { points ->
            val path = Path()
            points.forEachIndexed { i, (lat, lon) ->
                val px = ((lonToTileX(lon, zoom) - minTileX) * TILE_SIZE).toFloat()
                val py = ((latToTileY(lat, zoom) - minTileY) * TILE_SIZE).toFloat()
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            Canvas(adjusted).drawPath(path, routePaint)
        }

        // Auf den tatsächlichen Routen-Ausschnitt zuschneiden (mit etwas Rand)
        val startPx = (lonToTileX(minLon, zoom) - minTileX) * TILE_SIZE
        val endPx = (lonToTileX(maxLon, zoom) - minTileX) * TILE_SIZE
        val startPy = (latToTileY(maxLat, zoom) - minTileY) * TILE_SIZE
        val endPy = (latToTileY(minLat, zoom) - minTileY) * TILE_SIZE

        val padding = 24
        val cropLeft = (startPx - padding).toInt().coerceIn(0, adjusted.width - 1)
        val cropTop = (startPy - padding).toInt().coerceIn(0, adjusted.height - 1)
        val cropRight = (endPx + padding).toInt().coerceIn(cropLeft + 1, adjusted.width)
        val cropBottom = (endPy + padding).toInt().coerceIn(cropTop + 1, adjusted.height)

        val cropped = Bitmap.createBitmap(adjusted, cropLeft, cropTop, cropRight - cropLeft, cropBottom - cropTop)

        // Auf quadratisches Zielformat bringen (zentriert), passend zur 1:1-Thumbnail-Anzeige
        val side = max(cropped.width, cropped.height)
        val square = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        Canvas(square).apply {
            drawColor(Color.parseColor("#1B1B1B"))
            drawBitmap(cropped, ((side - cropped.width) / 2).toFloat(), ((side - cropped.height) / 2).toFloat(), null)
        }

        return Bitmap.createScaledBitmap(square, OUTPUT_SIZE, OUTPUT_SIZE, true)
    }

    private fun fetchTile(zoom: Int, x: Int, y: Int): Bitmap? {
        return try {
            val server = listOf("a", "b", "c").random()
            val url = URL("https://$server.basemaps.cartocdn.com/dark_all/$zoom/$x/$y.png")
            val connection = url.openConnection().apply {
                connectTimeout = 5000
                readTimeout = 5000
            }
            connection.getInputStream().use { input -> BitmapFactory.decodeStream(input) }
        } catch (e: Exception) {
            null
        }
    }

    private fun buildContrastFilter(): ColorMatrixColorFilter {
        val contrast = 1.35f
        val brightness = 8f
        val matrix = ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, brightness,
                0f, contrast, 0f, 0f, brightness,
                0f, 0f, contrast, 0f, brightness,
                0f, 0f, 0f, 1f, 0f
            )
        )
        return ColorMatrixColorFilter(matrix)
    }
}
