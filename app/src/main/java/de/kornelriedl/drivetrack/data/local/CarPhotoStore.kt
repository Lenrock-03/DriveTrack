package de.kornelriedl.drivetrack.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.File

/**
 * Speichert ein optionales Foto pro Fahrzeug lokal unter filesDir/car_photos/ – spiegelt das
 * Datei-statt-DB-Muster von TrackFileStore. Bewusst NICHT Teil des Backups (weder lokal noch
 * Server): das E2E-verschlüsselte Backup-Format ist reines JSON und wird von drei Repos geteilt,
 * Binärdaten würden das unnötig verkomplizieren. Ein Foto geht deshalb bei Neuinstallation/
 * Wiederherstellung aus einem Backup verloren – das wird in CarDetailScreen sichtbar kommuniziert.
 */
object CarPhotoStore {

    private const val MAX_DIMENSION_PX = 1280
    private const val JPEG_QUALITY = 85

    private fun dir(context: Context): File =
        File(context.filesDir, "car_photos").apply { mkdirs() }

    /** Absoluter Pfad zu einem gespeicherten Foto anhand des in der DB stehenden Dateinamens. */
    fun file(context: Context, fileName: String): File = File(dir(context), fileName)

    /**
     * Liest das Bild aus der vom Nutzer gewählten Uri, dreht es anhand der EXIF-Daten gerade,
     * skaliert es herunter und speichert es als JPEG. Gibt den neuen Dateinamen zurück, oder
     * null falls das Bild nicht gelesen/dekodiert werden konnte. Muss auf einem IO-Thread laufen.
     */
    fun savePhotoFromUri(context: Context, carId: Long, uri: Uri): String? {
        return try {
            val resolver = context.contentResolver

            // 1. Nur die Abmessungen lesen, um den Downscale-Faktor zu bestimmen (verhindert OOM
            //    bei sehr großen Fotos moderner Kameras). WICHTIG: BitmapFactory.decodeStream()
            //    gibt bei inJustDecodeBounds=true IMMER null zurück (das ist so gewollt - man
            //    liest danach options.outWidth/outHeight, nicht den Rückgabewert) - der Stream-
            //    Open-Fehler muss deshalb VOR dem Decode-Aufruf separat geprüft werden, sonst
            //    bricht die Funktion hier bei jedem echten Foto fälschlich sofort ab.
            val boundsStream = resolver.openInputStream(uri) ?: return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var sampleSize = 1
            while (bounds.outWidth / (sampleSize * 2) >= MAX_DIMENSION_PX &&
                bounds.outHeight / (sampleSize * 2) >= MAX_DIMENSION_PX
            ) {
                sampleSize *= 2
            }

            // 2. Stream erneut öffnen (ein InputStream lässt sich nach dem Bounds-Pass nicht
            //    zurückspulen) und diesmal wirklich dekodieren.
            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            var bitmap = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOptions)
            } ?: return null

            // 3. EXIF-Rotation anwenden, sonst zeigen Hochkant-Fotos vom Handy quer an.
            val rotationDegrees = resolver.openInputStream(uri)?.use { stream ->
                try {
                    when (ExifInterface(stream).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
                    )) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270
                        else -> 0
                    }
                } catch (e: Exception) {
                    0
                }
            } ?: 0
            if (rotationDegrees != 0) {
                val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            }

            // 4. Falls das Sampling allein nicht genug verkleinert hat, final auf die Zielgröße skalieren.
            val longerEdge = maxOf(bitmap.width, bitmap.height)
            if (longerEdge > MAX_DIMENSION_PX) {
                val scale = MAX_DIMENSION_PX.toFloat() / longerEdge
                bitmap = Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * scale).toInt().coerceAtLeast(1),
                    (bitmap.height * scale).toInt().coerceAtLeast(1),
                    true
                )
            }

            val fileName = "car_${carId}_${System.currentTimeMillis()}.jpg"
            file(context, fileName).outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }
            fileName
        } catch (e: Exception) {
            null
        }
    }

    /** Dekodiert ein gespeichertes Foto. Muss auf einem IO-Thread laufen. */
    fun load(context: Context, fileName: String): Bitmap? =
        try {
            BitmapFactory.decodeFile(file(context, fileName).absolutePath)
        } catch (e: Exception) {
            null
        }

    /** Löscht genau eine Datei (z. B. das alte Foto nach einem Austausch). */
    fun delete(context: Context, fileName: String) {
        file(context, fileName).delete()
    }

    /**
     * Löscht ALLE Fotodateien eines Autos (Präfix "car_<id>_") - beim Löschen des Autos, damit
     * auch nach einem früheren Absturz zwischen Schreiben und DB-Update verwaiste Dateien mitverschwinden.
     */
    fun deleteAllFor(context: Context, carId: Long) {
        val prefix = "car_${carId}_"
        dir(context).listFiles { f -> f.name.startsWith(prefix) }?.forEach { it.delete() }
    }
}
