package de.kornelriedl.drivetrack.data.server

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Speichert nur das JWT-Token, den Salt und den (weiterhin verschlüsselten!) DEK-Blob –
 * niemals das Passwort selbst und niemals den entschlüsselten DEK. Verschlüsselt auf
 * Betriebssystem-Ebene via EncryptedSharedPreferences (Android Keystore).
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
