package de.kornelriedl.drivetrack.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import de.kornelriedl.drivetrack.data.local.CarPhotoStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Zeigt das lokal gespeicherte Foto eines Fahrzeugs (siehe CarPhotoStore), oder einen
 * Platzhalter-Icon-Hintergrund, wenn keins gesetzt ist. Wiederverwendet in der Fahrzeug-Liste
 * (kleiner Kreis) und in CarDetailScreen (großer 16:9-Header) - Bild wird bei jedem
 * photoFileName-Wechsel neu geladen (Fotowechsel erzeugt immer einen neuen Dateinamen).
 */
@Composable
fun CarPhoto(photoFileName: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var bitmap by remember(photoFileName) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(photoFileName) {
        bitmap = if (photoFileName == null) null else withContext(Dispatchers.IO) {
            CarPhotoStore.load(context, photoFileName)
        }
    }

    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Fahrzeugfoto",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = Icons.Filled.DirectionsCar,
                contentDescription = "Kein Foto vorhanden",
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}
