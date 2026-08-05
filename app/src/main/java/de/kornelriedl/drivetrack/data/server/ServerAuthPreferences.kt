package de.kornelriedl.drivetrack.data.server

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Speichert das JWT-Token, den Salt, den (weiterhin verschlüsselten!) DEK-Blob – und seit
 * Auto-Sync (App 0.3.0) zusätzlich den ENTSCHLÜSSELTEN DEK selbst, damit die App Backups auch im
 * Hintergrund automatisch hochladen kann, ohne bei jedem Neustart nach dem Passwort zu fragen.
 * Das Passwort selbst wird weiterhin nirgends gespeichert. Alles hier liegt Ende-zu-Ende
 * verschlüsselt auf Betriebssystem-Ebene via EncryptedSharedPreferences (Android Keystore) –
 * kein Klartext auf der Festplatte, aber bewusst ein anderes Sicherheitsmodell als vorher (DEK
 * überlebt jetzt einen App-Neustart statt nur im RAM zu existieren). Wer maximale Sicherheit
 * will (DEK nie persistiert), müsste hier saveDek()/getDek() wieder entfernen.
 */
object ServerAuthPreferences {
    private const val PREFS_NAME = "server_auth_prefs_encrypted"

    private fun prefs(context: Context) = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveSession(
        context: Context,
        token: String,
        username: String,
        email: String,
        passwordSalt: String,
        dekWrappedPassword: String
    ) {
        prefs(context).edit()
            .putString("token", token)
            .putString("username", username)
            .putString("email", email)
            .putString("passwordSalt", passwordSalt)
            .putString("dekWrappedPassword", dekWrappedPassword)
            .apply()
    }

    fun saveDek(context: Context, dek: ByteArray) {
        prefs(context).edit()
            .putString("dek", Base64.encodeToString(dek, Base64.NO_WRAP))
            .apply()
    }

    fun getDek(context: Context): ByteArray? =
        prefs(context).getString("dek", null)?.let { Base64.decode(it, Base64.NO_WRAP) }

    fun getToken(context: Context): String? = prefs(context).getString("token", null)
    fun getUsername(context: Context): String? = prefs(context).getString("username", null)
    fun getEmail(context: Context): String? = prefs(context).getString("email", null)
    fun getPasswordSalt(context: Context): String? = prefs(context).getString("passwordSalt", null)
    fun getDekWrappedPassword(context: Context): String? = prefs(context).getString("dekWrappedPassword", null)

    fun isLoggedIn(context: Context): Boolean = getToken(context) != null

    fun clearSession(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
