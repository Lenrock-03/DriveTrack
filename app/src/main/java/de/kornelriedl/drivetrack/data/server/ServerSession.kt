package de.kornelriedl.drivetrack.data.server

/**
 * Hält den entschlüsselten Datenschlüssel (DEK) für die aktuelle App-Sitzung im Speicher.
 * Seit Auto-Sync (App 0.3.0) wird der DEK zusätzlich verschlüsselt persistiert (siehe
 * ServerAuthPreferences.saveDek/getDek) und beim App-Start automatisch hierher zurückgeladen
 * (siehe MainActivity) – nur so kann die App auch im Hintergrund automatisch synchronisieren,
 * ohne bei jedem Neustart nach dem Passwort zu fragen. dek bleibt trotzdem NUR hier im RAM
 * unverschlüsselt; auf der Festplatte liegt er immer nur Keystore-verschlüsselt.
 */
object ServerSession {
    var dek: ByteArray? = null
        private set

    fun setDek(value: ByteArray) {
        dek = value
    }

    fun clear() {
        dek = null
    }

    val isUnlocked: Boolean get() = dek != null
}
