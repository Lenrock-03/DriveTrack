package de.kornelriedl.drivetrack.ui.screens

import android.widget.TextView
import de.kornelriedl.drivetrack.R
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.infowindow.InfoWindow

/** Daten, die in der Marker-Bubble angezeigt werden – über Marker.relatedObject übergeben. */
data class MarkerInfo(
    val title: String,
    val time: String,
    val speedKmh: Double
)

class TripMarkerInfoWindow(mapView: MapView) : InfoWindow(R.layout.marker_info_window, mapView) {

    override fun onOpen(item: Any?) {
        val marker = item as? Marker ?: return
        val info = marker.relatedObject as? MarkerInfo ?: return

        mView.findViewById<TextView>(R.id.infoWindowTitle).text = info.title
        mView.findViewById<TextView>(R.id.infoWindowTime).text = "🕐 ${info.time} Uhr"
        mView.findViewById<TextView>(R.id.infoWindowSpeed).text = "⚡ %.0f km/h".format(info.speedKmh)
    }

    override fun onClose() {
        // nichts aufzuräumen
    }
}
