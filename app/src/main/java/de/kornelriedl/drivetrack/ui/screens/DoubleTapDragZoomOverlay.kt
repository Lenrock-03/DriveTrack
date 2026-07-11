package de.kornelriedl.drivetrack.ui.screens

import android.content.Context
import android.view.MotionEvent
import kotlin.math.abs
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

/**
 * Fügt osmdroid die von Google Maps bekannte Geste hinzu: doppelt antippen, Finger
 * unten lassen und nach oben/unten ziehen zum Rein-/Rauszoomen.
 *
 * Wichtig: Wir erkennen den Doppeltipp hier manuell (statt über GestureDetector) und
 * konsumieren ab dem zweiten "Down" alle Events selbst. Sonst verarbeitet osmdroids
 * eigener interner Gesture-Detector den Doppeltipp zusätzlich (Standard-Zoom-Sprung),
 * was zu dem "verbuggten"/ruckeligen Zoom-Verhalten führt.
 */
class DoubleTapDragZoomOverlay(@Suppress("UNUSED_PARAMETER") context: Context) : Overlay() {

    private var lastTapTime = 0L
    private var lastTapX = 0f
    private var lastTapY = 0f

    private var isZooming = false
    private var startY = 0f
    private var startZoom = 0.0

    companion object {
        private const val DOUBLE_TAP_TIMEOUT_MS = 300L
        private const val DOUBLE_TAP_SLOP_PX = 60f
        private const val ZOOM_SENSITIVITY = 150f
    }

    override fun onTouchEvent(e: MotionEvent, mapView: MapView): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val now = System.currentTimeMillis()
                val dx = e.x - lastTapX
                val dy = e.y - lastTapY
                val isSecondTap = (now - lastTapTime) < DOUBLE_TAP_TIMEOUT_MS &&
                    abs(dx) < DOUBLE_TAP_SLOP_PX && abs(dy) < DOUBLE_TAP_SLOP_PX

                if (isSecondTap) {
                    isZooming = true
                    startY = e.y
                    startZoom = mapView.zoomLevelDouble
                    lastTapTime = 0L
                    return true // ab hier osmdroids eigenen Doppeltipp-Zoom blockieren
                } else {
                    lastTapTime = now
                    lastTapX = e.x
                    lastTapY = e.y
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (isZooming) {
                    val dy = e.y - startY
                    val zoomDelta = dy / ZOOM_SENSITIVITY
                    mapView.controller.setZoom(startZoom + zoomDelta)
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isZooming) {
                    isZooming = false
                    return true
                }
            }
        }
        return false
    }
}
