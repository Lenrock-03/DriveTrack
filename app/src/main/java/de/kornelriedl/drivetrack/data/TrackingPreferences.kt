package de.kornelriedl.drivetrack.data

import android.content.Context

/** Einstellungen rund um die Aufzeichnung selbst (nicht Auto-/Nutzer-spezifisch, siehe CarPreferences/UserPreferences). */
object TrackingPreferences {
    private const val PREFS_NAME = "tracking_prefs"
    private const val KEY_AUTO_STOP_ON_DISCONNECT = "auto_stop_on_bluetooth_disconnect"

    /**
     * Ob eine laufende Aufzeichnung automatisch enden soll, sobald sich das Handy von einem Auto
     * zugeordneten Bluetooth-Gerät trennt (siehe BluetoothDisconnectionReceiver). Standardmäßig an.
     */
    fun isAutoStopOnDisconnectEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_AUTO_STOP_ON_DISCONNECT, true)
    }

    fun setAutoStopOnDisconnectEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_AUTO_STOP_ON_DISCONNECT, enabled).apply()
    }
}
