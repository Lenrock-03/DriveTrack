package de.kornelriedl.drivetrack.data

import android.content.Context

/**
 * Merkt sich, welche App-Version dieses Gerät zuletzt gesehen hat - Grundlage für den "Was ist
 * neu"-Dialog (siehe ReleaseNotes.kt/WhatsNewDialog.kt), der beim ersten Öffnen nach einem Update
 * erscheint. Bei einer frischen Installation (kein gespeicherter Wert) wird der Dialog bewusst
 * NICHT gezeigt - `MainActivity.kt` speichert dort nur den aktuellen Stand, ohne die Liste vorher
 * anzuzeigen (ein neuer Nutzer soll nicht mit der kompletten Änderungshistorie begrüßt werden).
 */
object ReleaseNotesPreferences {
    private const val PREFS_NAME = "release_notes_prefs"
    private const val KEY_LAST_SEEN_VERSION = "last_seen_version"

    fun getLastSeenVersion(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LAST_SEEN_VERSION, null)

    fun setLastSeenVersion(context: Context, version: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_LAST_SEEN_VERSION, version).apply()
    }
}
