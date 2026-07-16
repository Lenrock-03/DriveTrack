package de.kornelriedl.drivetrack.data.server

/**
 * Hält den entschlüsselten Datenschlüssel (DEK) nur für die Dauer der App-Sitzung im Speicher.
 * Wird die App beendet/neu gestartet, ist der DEK weg – der Nutzer muss dann sein Passwort
 * (oder den Recovery-Code) erneut eingeben, um ihn wieder freizuschalten. Das ist beabsichtigt:
 * der DEK darf nirgends dauerhaft im Klartext gespeichert werden.
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
