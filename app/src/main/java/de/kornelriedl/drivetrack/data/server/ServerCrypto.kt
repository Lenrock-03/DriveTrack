package de.kornelriedl.drivetrack.data.server

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Ende-zu-Ende-Verschlüsselung für Server-Backups, wie im Architektur-Plan beschrieben:
 * - Ein zufälliger Datenschlüssel (DEK) verschlüsselt das eigentliche Backup
 * - Der DEK wird selbst zweifach "verpackt" gespeichert: einmal mit dem Passwort,
 *   einmal mit dem Recovery-Code – beides wird NUR hier auf dem Gerät berechnet,
 *   der Server bekommt nie das Passwort, den Recovery-Code oder den DEK im Klartext zu sehen.
 */
object ServerCrypto {
    private const val PBKDF2_ITERATIONS = 150_000
    private const val KEY_LENGTH_BITS = 256
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val RECOVERY_CODE_LENGTH = 24
    // Ohne leicht verwechselbare Zeichen (0/O, 1/I/l)
    private const val RECOVERY_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"

    data class EncryptedBlob(val ciphertextBase64: String, val ivBase64: String) {
        /** Kombinierte Darstellung als ein String, für Felder, die serverseitig nur eine Spalte haben. */
        fun toWrappedString(): String = "$ivBase64:$ciphertextBase64"

        companion object {
            fun fromWrappedString(s: String): EncryptedBlob {
                val parts = s.split(":", limit = 2)
                return EncryptedBlob(ivBase64 = parts[0], ciphertextBase64 = parts.getOrElse(1) { "" })
            }
        }
    }

    fun randomSaltBase64(): String {
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        return Base64.encodeToString(salt, Base64.NO_WRAP)
    }

    fun randomDek(): ByteArray {
        val dek = ByteArray(32) // 256 Bit
        SecureRandom().nextBytes(dek)
        return dek
    }

    fun randomRecoveryCode(): String {
        val random = SecureRandom()
        return (1..RECOVERY_CODE_LENGTH)
            .map { RECOVERY_ALPHABET[random.nextInt(RECOVERY_ALPHABET.length)] }
            .joinToString("")
    }

    private fun deriveKey(secret: String, saltBase64: String): SecretKeySpec {
        val salt = Base64.decode(saltBase64, Base64.NO_WRAP)
        val spec = PBEKeySpec(secret.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    private fun encrypt(data: ByteArray, key: SecretKeySpec): EncryptedBlob {
        val iv = ByteArray(GCM_IV_LENGTH).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        val ciphertext = cipher.doFinal(data)
        return EncryptedBlob(
            ciphertextBase64 = Base64.encodeToString(ciphertext, Base64.NO_WRAP),
            ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)
        )
    }

    private fun decrypt(blob: EncryptedBlob, key: SecretKeySpec): ByteArray {
        val iv = Base64.decode(blob.ivBase64, Base64.NO_WRAP)
        val ciphertext = Base64.decode(blob.ciphertextBase64, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    // --- DEK mit Passwort bzw. Recovery-Code verpacken/entpacken ---

    fun wrapDek(dek: ByteArray, secret: String, saltBase64: String): EncryptedBlob {
        return encrypt(dek, deriveKey(secret, saltBase64))
    }

    fun unwrapDek(wrapped: EncryptedBlob, secret: String, saltBase64: String): ByteArray {
        return decrypt(wrapped, deriveKey(secret, saltBase64))
    }

    // --- Eigentliches Backup mit dem DEK selbst ver-/entschlüsseln ---

    fun encryptWithDek(plainText: String, dek: ByteArray): EncryptedBlob {
        return encrypt(plainText.toByteArray(Charsets.UTF_8), SecretKeySpec(dek, "AES"))
    }

    fun decryptWithDek(blob: EncryptedBlob, dek: ByteArray): String {
        return String(decrypt(blob, SecretKeySpec(dek, "AES")), Charsets.UTF_8)
    }
}
